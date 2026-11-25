package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.TopicFile;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.exception.InvalidTrackEditException;
import com.abhi.quizbackend.exception.InvalidUploadException;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest.DayEdit;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.repository.TopicFileRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Saves an owner's edit of a track in one transaction: all of it, or none of it.
 *
 * The owner can change the description, the visibility, the topic names, the
 * days and the files. The track name never changes. Removed days and files that
 * no other track uses are deleted, and their files leave the disk after commit.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackEditService {

    /**
     * Added to positions before they are renumbered. Each position is unique per
     * track (and per topic), so renumbering in place could collide mid-update.
     */
    private static final int TEMPORARY_POSITION_OFFSET = 1_000_000;

    private final TrackService trackService;
    private final TrackTopicRepository trackTopicRepository;
    private final TopicRepository topicRepository;
    private final TopicFileRepository topicFileRepository;
    private final TrackFileStore fileStore;
    private final OrphanCleaner orphanCleaner;

    /**
     * @param filesByDay the new files for each day, in the same order as request.days()
     */
    public TrackResponse edit(Long trackId,
                              EditTrackRequest request,
                              List<List<MultipartFile>> filesByDay,
                              Long currentUserId) {

        // 404 if the user cannot see the track, 403 if they can but do not own it.
        Track track = trackService.findOwnedTrack(trackId, currentUserId);

        Map<Long, TrackTopic> currentDays = new LinkedHashMap<>();
        track.getTrackTopics().forEach(tt -> currentDays.put(tt.getTopic().getTopicId(), tt));

        // Check everything before anything is written to the database or the disk.
        validate(request, filesByDay, currentDays);

        track.setDescription(StringUtils.hasText(request.description())
                ? request.description().trim()
                : null);
        track.setVisibility(request.visibility());

        List<Topic> removedTopics = removeDays(track, request);
        moveToTemporaryPositions(track);

        List<String> storedKeys = fileStore.deleteStoredFilesOnRollback();
        List<FileEntity> removedFiles = new ArrayList<>();
        Set<String> usedTopicSlugs = new HashSet<>();

        for (int i = 0; i < request.days().size(); i++) {
            DayEdit day = request.days().get(i);
            List<MultipartFile> newFiles = filesByDay.get(i);
            String name = day.name().trim();

            if (day.topicId() == null) {
                Topic topic = newTopic(track, name, usedTopicSlugs);
                addFiles(topic, List.of(), newFiles, storedKeys);
                topicRepository.save(topic);
                track.getTrackTopics().add(TrackTopic.builder()
                        .track(track)
                        .topic(topic)
                        .position(i)
                        .build());
            } else {
                TrackTopic trackTopic = currentDays.get(day.topicId());
                editDay(track, trackTopic, day, name, newFiles, storedKeys, removedFiles, usedTopicSlugs);
                trackTopic.setPosition(i);
            }
        }

        orphanCleaner.deleteUnusedTopics(removedTopics);
        orphanCleaner.deleteUnusedFiles(removedFiles);

        return trackService.toResponse(track);
    }

    /** Removes the days that are not in the request. Returns their topics. */
    private List<Topic> removeDays(Track track, EditTrackRequest request) {
        Set<Long> keptTopicIds = request.days().stream()
                .map(DayEdit::topicId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Topic> removedTopics = new ArrayList<>();
        // orphanRemoval deletes each removed track_topics row.
        track.getTrackTopics().removeIf(trackTopic -> {
            boolean removed = !keptTopicIds.contains(trackTopic.getTopic().getTopicId());
            if (removed) {
                removedTopics.add(trackTopic.getTopic());
            }
            return removed;
        });
        trackTopicRepository.flush();
        return removedTopics;
    }

    private void moveToTemporaryPositions(Track track) {
        track.getTrackTopics().forEach(tt -> tt.setPosition(tt.getPosition() + TEMPORARY_POSITION_OFFSET));
        trackTopicRepository.flush();
    }

    private void editDay(Track track,
                         TrackTopic trackTopic,
                         DayEdit day,
                         String name,
                         List<MultipartFile> newFiles,
                         List<String> storedKeys,
                         List<FileEntity> removedFiles,
                         Set<String> usedTopicSlugs) {

        Topic topic = trackTopic.getTopic();
        Set<Long> keepIds = day.keepFileIds() == null ? Set.of() : new HashSet<>(day.keepFileIds());

        List<TopicFile> kept = topic.getTopicFiles().stream()
                .filter(tf -> keepIds.contains(tf.getFile().getFileId()))
                .toList();
        List<TopicFile> removed = topic.getTopicFiles().stream()
                .filter(tf -> !keepIds.contains(tf.getFile().getFileId()))
                .toList();

        boolean changed = !topic.getName().equals(name) || !removed.isEmpty() || !newFiles.isEmpty();
        if (!changed) {
            return;
        }

        if (trackTopicRepository.countByTopicTopicId(topic.getTopicId()) > 1) {
            // Another track also uses this topic. Give this track its own copy,
            // so the other track keeps the topic exactly as it was.
            Topic copy = newTopic(track, name, usedTopicSlugs);
            addFiles(copy, kept.stream().map(TopicFile::getFile).toList(), newFiles, storedKeys);
            topicRepository.save(copy);
            trackTopic.setTopic(copy);
            return;
        }

        topic.setName(name);

        // orphanRemoval deletes each removed topic_files row.
        removed.forEach(tf -> removedFiles.add(tf.getFile()));
        topic.getTopicFiles().removeAll(removed);
        topicFileRepository.flush();

        topic.getTopicFiles().forEach(tf -> tf.setPosition(tf.getPosition() + TEMPORARY_POSITION_OFFSET));
        topicFileRepository.flush();

        int position = 0;
        for (TopicFile topicFile : topic.getTopicFiles()) {
            topicFile.setPosition(position++);
        }
        for (MultipartFile file : newFiles) {
            topic.getTopicFiles().add(TopicFile.builder()
                    .topic(topic)
                    .file(fileStore.store(file, storedKeys))
                    .position(position++)
                    .build());
        }
    }

    /** Links the existing files, then stores and links the new ones, in that order. */
    private void addFiles(Topic topic,
                          List<FileEntity> existingFiles,
                          List<MultipartFile> newFiles,
                          List<String> storedKeys) {
        int position = 0;
        for (FileEntity file : existingFiles) {
            topic.getTopicFiles().add(TopicFile.builder().topic(topic).file(file).position(position++).build());
        }
        for (MultipartFile file : newFiles) {
            topic.getTopicFiles().add(TopicFile.builder()
                    .topic(topic)
                    .file(fileStore.store(file, storedKeys))
                    .position(position++)
                    .build());
        }
    }

    private Topic newTopic(Track track, String name, Set<String> usedTopicSlugs) {
        // Topic slugs are global. Topics saved in this transaction may not be
        // visible to existsBySlug yet, so remember them here as well.
        String slug = Slugs.unique(
                track.getSlug() + "-" + Slugs.slugify(name, "topic"),
                candidate -> usedTopicSlugs.contains(candidate) || topicRepository.existsBySlug(candidate));
        usedTopicSlugs.add(slug);
        return Topic.builder().name(name).slug(slug).build();
    }

    private void validate(EditTrackRequest request,
                          List<List<MultipartFile>> filesByDay,
                          Map<Long, TrackTopic> currentDays) {

        Set<Long> seenTopicIds = new HashSet<>();

        for (int i = 0; i < request.days().size(); i++) {
            DayEdit day = request.days().get(i);
            String field = "days[" + i + "]";
            Set<String> fileKeys = new HashSet<>();

            if (day.topicId() == null) {
                if (day.keepFileIds() != null && !day.keepFileIds().isEmpty()) {
                    throw new InvalidTrackEditException(field + ".keepFileIds", "A new day has no files to keep");
                }
            } else {
                TrackTopic trackTopic = currentDays.get(day.topicId());
                if (trackTopic == null) {
                    throw new InvalidTrackEditException(field + ".topicId", "Day " + (i + 1) + " is not part of this track");
                }
                if (!seenTopicIds.add(day.topicId())) {
                    throw new InvalidTrackEditException(field + ".topicId", "Day " + (i + 1) + " appears more than once");
                }

                Map<Long, FileEntity> dayFiles = trackTopic.getTopic().getTopicFiles().stream()
                        .collect(Collectors.toMap(tf -> tf.getFile().getFileId(), TopicFile::getFile));
                for (Long fileId : day.keepFileIds() == null ? List.<Long>of() : day.keepFileIds()) {
                    FileEntity file = dayFiles.get(fileId);
                    if (file == null) {
                        throw new InvalidTrackEditException(field + ".keepFileIds", "A kept file is not part of day " + (i + 1));
                    }
                    fileKeys.add(TrackFileStore.duplicateKey(file.getOriginalFilename(), file.getSizeBytes()));
                }
            }

            for (MultipartFile file : filesByDay.get(i)) {
                fileStore.validate(file, "files-" + i);
                if (!fileKeys.add(TrackFileStore.duplicateKey(file.getOriginalFilename(), file.getSize()))) {
                    throw new InvalidUploadException(
                            "files-" + i,
                            file.getOriginalFilename() + " is added to day " + (i + 1) + " more than once");
                }
            }
        }
    }
}
