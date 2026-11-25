package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.repository.FileRepository;
import com.abhi.quizbackend.repository.TopicFileRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deletes topics and files that nothing uses any more, so they do not stay on
 * disk after a track or a day is removed.
 *
 * A topic is in use while a track links to it. A file is in use while a topic
 * links to it. A topic or file that another track still uses is kept.
 *
 * Call this inside the transaction that removed the links. The rows go in that
 * transaction; the bytes on disk go only after it commits.
 */
@Component
@RequiredArgsConstructor
public class OrphanCleaner {

    private final TrackTopicRepository trackTopicRepository;
    private final TopicFileRepository topicFileRepository;
    private final TopicRepository topicRepository;
    private final FileRepository fileRepository;
    private final TrackFileStore fileStore;

    /** Deletes each topic that no track uses, then each of its files that no topic uses. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUnusedTopics(Collection<Topic> candidates) {
        // Make the removed links visible to the queries below.
        trackTopicRepository.flush();

        List<FileEntity> fileCandidates = new ArrayList<>();
        for (Topic topic : distinct(candidates, Topic::getTopicId)) {
            if (!trackTopicRepository.existsByTopicTopicId(topic.getTopicId())) {
                topic.getTopicFiles().forEach(topicFile -> fileCandidates.add(topicFile.getFile()));
                // Cascades to the topic's topic_files rows.
                topicRepository.delete(topic);
            }
        }
        deleteUnusedFiles(fileCandidates);
    }

    /** Deletes each file that no topic uses: the row now, the bytes after commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteUnusedFiles(Collection<FileEntity> candidates) {
        topicFileRepository.flush();

        List<String> storageKeys = new ArrayList<>();
        for (FileEntity file : distinct(candidates, FileEntity::getFileId)) {
            if (!topicFileRepository.existsByFileFileId(file.getFileId())) {
                storageKeys.add(file.getStorageKey());
                fileRepository.delete(file);
            }
        }
        fileStore.deleteFromDiskAfterCommit(storageKeys);
    }

    private static <T> Collection<T> distinct(Collection<T> items, java.util.function.Function<T, Long> id) {
        Map<Long, T> byId = new LinkedHashMap<>();
        items.forEach(item -> byId.putIfAbsent(id.apply(item), item));
        return byId.values();
    }
}
