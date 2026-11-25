package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.TopicFile;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.exception.InvalidUploadException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import com.abhi.quizbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates a track, its topics and their uploaded files in one go, so the user
 * never ends up with half a track.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackCreationService {

    private final TrackRepository trackRepository;
    private final TopicRepository topicRepository;
    private final TrackTopicRepository trackTopicRepository;
    private final UserRepository userRepository;
    private final TrackFileStore fileStore;
    private final TrackService trackService;

    /**
     * @param filesByTopic files for each topic, same order as request.topics()
     */
    public TrackResponse create(
            CreateFullTrackRequest request,
            List<List<MultipartFile>> filesByTopic,
            Long currentUserId) {

        String trackName = request.name().trim();
        trackService.requireNameAvailable(trackName);

        // Check every file before writing anything to disk.
        for (int i = 0; i < filesByTopic.size(); i++) {
            // The same file twice in one day is a mistake, not two resources.
            // Name and size are all the upload tells us that identifies it.
            Set<String> seen = new HashSet<>();
            for (MultipartFile file : filesByTopic.get(i)) {
                fileStore.validate(file, "files-" + i);
                if (!seen.add(TrackFileStore.duplicateKey(file.getOriginalFilename(), file.getSize()))) {
                    throw new InvalidUploadException(
                            "files-" + i,
                            file.getOriginalFilename() + " is added to day " + (i + 1) + " more than once"
                    );
                }
            }
        }

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        Track track;
        try {
            // Flush now so a name taken by a concurrent request fails here as
            // a 409, rather than as a 500 at commit.
            track = trackRepository.saveAndFlush(Track.builder()
                    .name(trackName)
                    .description(StringUtils.hasText(request.description())
                            ? request.description().trim()
                            : null)
                    .slug(Slugs.unique(Slugs.slugify(trackName, "track"), trackRepository::existsBySlug))
                    .visibility(request.visibility())
                    .owner(owner)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("name", "Track name is already taken");
        }

        List<String> storedKeys = fileStore.deleteStoredFilesOnRollback();
        // Topic slugs are global, and topics saved in this transaction may not
        // be visible to existsBySlug yet, so remember them here as well.
        Set<String> usedTopicSlugs = new HashSet<>();

        for (int i = 0; i < request.topics().size(); i++) {
            String topicName = request.topics().get(i).name().trim();
            String topicSlug = Slugs.unique(
                    track.getSlug() + "-" + Slugs.slugify(topicName, "topic"),
                    slug -> usedTopicSlugs.contains(slug) || topicRepository.existsBySlug(slug));
            usedTopicSlugs.add(topicSlug);

            Topic topic = Topic.builder()
                    .name(topicName)
                    .slug(topicSlug)
                    .build();

            List<MultipartFile> files = filesByTopic.get(i);
            for (int position = 0; position < files.size(); position++) {
                FileEntity stored = fileStore.store(files.get(position), storedKeys);
                topic.getTopicFiles().add(TopicFile.builder()
                        .topic(topic)
                        .file(stored)
                        .position(position)
                        .build());
            }

            topicRepository.save(topic);

            // Position i is "day i + 1" in the UI.
            TrackTopic trackTopic = trackTopicRepository.save(TrackTopic.builder()
                    .track(track)
                    .topic(topic)
                    .position(i)
                    .build());
            track.getTrackTopics().add(trackTopic);
        }

        return trackService.toResponse(track);
    }
}
