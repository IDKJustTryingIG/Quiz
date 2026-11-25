package com.abhi.quizbackend.service;

import com.abhi.quizbackend.models.CreateTrackRequest;
import com.abhi.quizbackend.models.FileResponse;
import com.abhi.quizbackend.models.TrackDetailResponse;
import com.abhi.quizbackend.models.TrackPageResponse;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TrackService {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final OrphanCleaner orphanCleaner;

    public TrackResponse create(
            CreateTrackRequest request,
            Long currentUserId) {

        if (trackRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("slug", "Track slug already exists");
        }

        requireNameAvailable(request.name());

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        Track track = Track.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .visibility(request.visibility())
                .owner(owner)
                .build();

        trackRepository.save(track);

        return toResponse(track);
    }

    @Transactional(readOnly = true)
    public TrackResponse getById(
            Long trackId,
            Long currentUserId) {

        return toResponse(findReadableTrack(trackId, currentUserId));
    }

    /**
     * Searches one slice of the tracks by name, newest first.
     *
     * @param scope "public" for every public track, "mine" for the user's own
     */
    @Transactional(readOnly = true)
    public List<TrackResponse> searchTracks(
            String scope,
            String query,
            Long currentUserId) {

        String name = query == null ? "" : query.trim();

        List<Track> tracks = switch (scope) {
            case "public" -> trackRepository
                    .findByVisibilityAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                            TrackVisibility.PUBLIC, name);
            case "mine" -> trackRepository
                    .findByOwnerUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                            currentUserId, name);
            default -> throw new IllegalArgumentException("Unknown scope: " + scope);
        };

        return tracks.stream()
                .map(this::toResponse)
                .toList();
    }

    /** One page of public tracks matching the name, newest first. */
    @Transactional(readOnly = true)
    public TrackPageResponse searchPublicTracks(String query, int page, int size) {
        String name = query == null ? "" : query.trim();

        // Tie-break on id so tracks created in the same instant never swap
        // places between pages, which would show one twice and skip another.
        Page<Track> result = trackRepository.findByVisibilityAndNameContainingIgnoreCase(
                TrackVisibility.PUBLIC,
                name,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("trackId")))
        );

        return new TrackPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<TrackResponse> getTracks(Long currentUserId) {

        List<Track> tracks = new ArrayList<>(
                trackRepository.findByVisibility(TrackVisibility.PUBLIC)
        );

        tracks.addAll(
                trackRepository.findByOwnerUserId(currentUserId)
        );

        return tracks.stream()
                .distinct()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Deletes a track the user owns, then every topic and file that only this
     * track used. Their files leave the disk after the transaction commits.
     */
    public void delete(
            Long trackId,
            Long currentUserId) {

        Track track = findOwnedTrack(trackId, currentUserId);
        List<Topic> topics = track.getTrackTopics().stream()
                .map(TrackTopic::getTopic)
                .toList();

        // Cascades to the track's track_topics rows.
        trackRepository.delete(track);
        orphanCleaner.deleteUnusedTopics(topics);
    }

    /** A readable track with its days and files, for the track page. */
    @Transactional(readOnly = true)
    public TrackDetailResponse getDetailBySlug(String slug, Long currentUserId) {
        Track track = trackRepository.findBySlug(slug)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Track not found"));

        if (track.getVisibility() == TrackVisibility.PRIVATE
                && !isOwner(track, currentUserId)) {
            // 404 rather than 403, so private track slugs are not discoverable.
            throw new ResourceNotFoundException("Track not found");
        }

        List<TrackDetailResponse.Day> days = track.getTrackTopics().stream()
                .map(tt -> new TrackDetailResponse.Day(
                        tt.getPosition(),
                        tt.getTopic().getTopicId(),
                        tt.getTopic().getName(),
                        tt.getTopic().getTopicFiles().stream()
                                .map(tf -> new FileResponse(
                                        tf.getFile().getFileId(),
                                        tf.getFile().getName(),
                                        tf.getFile().getOriginalFilename(),
                                        tf.getFile().getContentType(),
                                        tf.getFile().getSizeBytes(),
                                        tf.getPosition()))
                                .toList()))
                .toList();

        return new TrackDetailResponse(
                track.getTrackId(),
                track.getName(),
                track.getSlug(),
                track.getDescription(),
                track.getVisibility(),
                track.getOwner().getUserId(),
                track.getOwner().getUsername(),
                days
        );
    }

    /** Track names are unique across all users, compared exactly. */
    @Transactional(readOnly = true)
    public boolean isNameAvailable(String name) {
        return !trackRepository.existsByName(name);
    }

    public void requireNameAvailable(String name) {
        if (!isNameAvailable(name)) {
            throw new DuplicateResourceException("name", "Track name is already taken");
        }
    }

    /** Loads a track the user may view: any public track, or a private one they own. */
    public Track findReadableTrack(Long trackId, Long currentUserId) {
        Track track = findTrack(trackId);

        if (track.getVisibility() == TrackVisibility.PRIVATE
                && !isOwner(track, currentUserId)) {
            // 404 rather than 403, so private track ids are not discoverable.
            throw new ResourceNotFoundException("Track not found");
        }

        return track;
    }

    /** Loads a track the user may modify: only one they own. */
    public Track findOwnedTrack(Long trackId, Long currentUserId) {
        Track track = findReadableTrack(trackId, currentUserId);

        if (!isOwner(track, currentUserId)) {
            throw new AccessDeniedException("You do not own this track");
        }

        return track;
    }

    private Track findTrack(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Track not found"));
    }

    private boolean isOwner(Track track, Long currentUserId) {
        return track.getOwner().getUserId().equals(currentUserId);
    }

    public TrackResponse toResponse(Track track) {
        return new TrackResponse(
                track.getTrackId(),
                track.getName(),
                track.getSlug(),
                track.getDescription(),
                track.getVisibility(),
                track.getOwner().getUserId(),
                track.getOwner().getUsername(),
                track.getTrackTopics().size()
        );
    }
}