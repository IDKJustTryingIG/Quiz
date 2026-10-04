package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.TrackVisibility;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.repository.FileRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicFileService {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public record Download(FileEntity file, Resource resource) {
    }

    /**
     * Loads a file the user may read: one attached to a topic in any public
     * track, or in a private track they own.
     */
    public Download load(Long fileId, Long currentUserId) {
        FileEntity file = fileRepository.findById(fileId)
                .filter(f -> isReadable(f, currentUserId))
                // 404 rather than 403, so private file ids are not discoverable.
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));

        return new Download(file, fileStorageService.load(file.getStorageKey()));
    }

    private boolean isReadable(FileEntity file, Long currentUserId) {
        return file.getTopicFiles().stream()
                .flatMap(tf -> tf.getTopic().getTrackTopics().stream())
                .map(tt -> tt.getTrack())
                .anyMatch(track -> track.getVisibility() == TrackVisibility.PUBLIC
                        || track.getOwner().getUserId().equals(currentUserId));
    }
}
