package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.entity.Topic;
import com.abhi.quizbackend.entity.TopicFile;
import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackTopic;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.exception.FileStorageException;
import com.abhi.quizbackend.exception.InvalidUploadException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.repository.FileRepository;
import com.abhi.quizbackend.repository.TopicRepository;
import com.abhi.quizbackend.repository.TrackRepository;
import com.abhi.quizbackend.repository.TrackTopicRepository;
import com.abhi.quizbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Creates a track, its topics and their uploaded files in one go, so the user
 * never ends up with half a track.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackCreationService {

    /** Content type to the extensions it may arrive with. */
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("application/pdf", Set.of("pdf")),
            Map.entry("application/msword", Set.of("doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx")),
            Map.entry("application/vnd.ms-powerpoint", Set.of("ppt")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of("pptx")),
            Map.entry("application/vnd.ms-excel", Set.of("xls")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of("xlsx")),
            Map.entry("text/plain", Set.of("txt")),
            Map.entry("text/markdown", Set.of("md")),
            Map.entry("image/png", Set.of("png")),
            Map.entry("image/jpeg", Set.of("jpg", "jpeg")),
            Map.entry("image/gif", Set.of("gif")),
            Map.entry("image/webp", Set.of("webp"))
    );

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final TrackRepository trackRepository;
    private final TopicRepository topicRepository;
    private final TrackTopicRepository trackTopicRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
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
                validate(file, "files-" + i);
                if (!seen.add(file.getOriginalFilename() + "|" + file.getSize())) {
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
                    .slug(uniqueSlug(slugify(trackName, "track"), trackRepository::existsBySlug))
                    .visibility(request.visibility())
                    .owner(owner)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("name", "Track name is already taken");
        }

        List<String> storedKeys = deleteStoredFilesOnRollback();
        // Topic slugs are global, and topics saved in this transaction may not
        // be visible to existsBySlug yet, so remember them here as well.
        Set<String> usedTopicSlugs = new HashSet<>();

        for (int i = 0; i < request.topics().size(); i++) {
            String topicName = request.topics().get(i).name().trim();
            String topicSlug = uniqueSlug(
                    track.getSlug() + "-" + slugify(topicName, "topic"),
                    slug -> usedTopicSlugs.contains(slug) || topicRepository.existsBySlug(slug));
            usedTopicSlugs.add(topicSlug);

            Topic topic = Topic.builder()
                    .name(topicName)
                    .slug(topicSlug)
                    .build();

            List<MultipartFile> files = filesByTopic.get(i);
            for (int position = 0; position < files.size(); position++) {
                FileEntity stored = store(files.get(position), storedKeys);
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

    private void validate(MultipartFile file, String field) {
        if (file.isEmpty()) {
            throw new InvalidUploadException(field, "File is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidUploadException(field, "File is larger than 10MB");
        }
        Set<String> extensions = ALLOWED_TYPES.get(file.getContentType());
        String extension = extensionOf(file.getOriginalFilename());
        if (extensions == null || !extensions.contains(extension)) {
            throw new InvalidUploadException(
                    field,
                    "Only PDF, Word, PowerPoint, Excel, text and image files are allowed"
            );
        }
    }

    private FileEntity store(MultipartFile file, List<String> storedKeys) {
        String originalFilename = displayName(file.getOriginalFilename());
        // Never build the path from the user's filename.
        String storageKey = "resources/" + UUID.randomUUID() + "." + extensionOf(originalFilename);

        try {
            fileStorageService.store(file, storageKey);
        } catch (IOException e) {
            throw new FileStorageException("Could not store file", e);
        }
        storedKeys.add(storageKey);

        return fileRepository.save(FileEntity.builder()
                .name(originalFilename)
                .originalFilename(originalFilename)
                .storageKey(storageKey)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build());
    }

    /**
     * Files land on disk before the transaction commits. If it then rolls back
     * the rows vanish but the files would not, so delete them here.
     */
    private List<String> deleteStoredFilesOnRollback() {
        List<String> storedKeys = new ArrayList<>();
        /* TransactionSynchronizationManager.registerSynchronization()
        this states that I want you to run this specific block of code right after the current database transaction finishes
        */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            /* this method afterCompletion states that it'll be executed only after completion of transaction
            */
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedKeys.forEach(fileStorageService::delete);
                }
            }
        });

        return storedKeys;
    }

    private static String uniqueSlug(String base, Predicate<String> taken) {
        String slug = base;
        for (int n = 2; taken.test(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private static String slugify(String value, String fallback) {
        String slug = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-$", "");
        }
        return slug.isEmpty() ? fallback : slug;
    }

    private static String displayName(String originalFilename) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
                originalFilename == null ? "" : originalFilename));
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String extensionOf(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }
}
