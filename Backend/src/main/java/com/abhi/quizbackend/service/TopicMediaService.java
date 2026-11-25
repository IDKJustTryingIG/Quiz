package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Turns the files of one day (a topic in a track) into Spring AI {@link Media},
 * so a chat model can read them together with the user's message.
 */
@Service
@RequiredArgsConstructor
public class TopicMediaService {

    private static final Logger log = LoggerFactory.getLogger(TopicMediaService.class);

    /**
     * Stored content type to the type sent to the model. Gemini reads PDFs, these
     * images and plain text. Markdown goes as plain text, because it is plain text.
     * Every other type (Word, PowerPoint, Excel, GIF) is skipped.
     */
    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "application/pdf", "application/pdf",
            "image/png", "image/png",
            "image/jpeg", "image/jpeg",
            "image/webp", "image/webp",
            "text/plain", "text/plain",
            "text/markdown", "text/plain"
    );

    private final TrackTopicRepository trackTopicRepository;
    private final FileStorageService fileStorageService;

    /**
     * The files of one day that the model can read, plus a fingerprint of them.
     *
     * @param sourceHash SHA-256 of the files' ids, sorted. A stored file never
     *                   changes and each upload gets a new id, so the hash changes
     *                   exactly when the model would see other files. Sorted, so
     *                   reordering the files keeps the cached quiz.
     */
    public record QuizSources(Long topicId, String sourceHash, List<FileEntity> files) {
    }

    /**
     * Finds the supported files of the day, in their day order. Reads no file
     * bytes, so it is cheap enough to run before every cache lookup.
     *
     * @throws ResourceNotFoundException if the topic is not part of the track
     */
    @Transactional(readOnly = true)
    public QuizSources getSources(Long trackId, Long topicId) {
        TrackTopic trackTopic = trackTopicRepository
                .findByTrackTrackIdAndTopicTopicId(trackId, topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found in this track"));

        List<FileEntity> files = trackTopic.getTopic().getTopicFiles().stream()
                .map(topicFile -> topicFile.getFile())
                .filter(file -> SUPPORTED_TYPES.containsKey(file.getContentType()))
                .toList();

        return new QuizSources(topicId, sourceHash(files), files);
    }

    /** Reads the files into memory for the model. Files gone from disk are skipped. */
    public Media[] toMedia(List<FileEntity> files) {
        return files.stream()
                .map(this::toMedia)
                .filter(Objects::nonNull)
                .toArray(Media[]::new);
    }

    private static String sourceHash(List<FileEntity> files) {
        String ids = files.stream()
                .map(FileEntity::getFileId)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ids.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime must provide SHA-256.
            throw new IllegalStateException(e);
        }
    }

    /** Reads one file into memory, or returns null if its bytes are gone from disk. */
    private Media toMedia(FileEntity file) {
        try {
            byte[] bytes = fileStorageService.load(file.getStorageKey()).getContentAsByteArray();
            return Media.builder()
                    .mimeType(MimeType.valueOf(SUPPORTED_TYPES.get(file.getContentType())))
                    .data(bytes)
                    .name(file.getOriginalFilename())
                    .build();
        } catch (ResourceNotFoundException | IOException e) {
            // One lost file should not stop the chat; the model gets the other files.
            log.warn("Skipping file {} ({}): {}", file.getFileId(), file.getStorageKey(), e.getMessage());
            return null;
        }
    }
}
