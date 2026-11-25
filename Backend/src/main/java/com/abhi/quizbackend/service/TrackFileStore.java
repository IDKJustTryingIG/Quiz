package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.FileEntity;
import com.abhi.quizbackend.exception.FileStorageException;
import com.abhi.quizbackend.exception.InvalidUploadException;
import com.abhi.quizbackend.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks, stores and deletes the resource files of a track. Track creation and
 * track editing both use it, so both follow the same rules.
 *
 * Files go to disk before the database transaction commits. This class keeps the
 * disk and the database in step: it removes new files if the transaction rolls
 * back, and it removes deleted files only after the transaction commits.
 */
@Component
@RequiredArgsConstructor
public class TrackFileStore {

    private static final Logger log = LoggerFactory.getLogger(TrackFileStore.class);

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

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    /** Rejects an empty, too large or disallowed file. */
    public void validate(MultipartFile file, String field) {
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

    /**
     * The key that identifies a file within one day: name and size. This is all
     * an upload tells us, and the frontend uses the same key.
     */
    public static String duplicateKey(String originalFilename, long size) {
        return originalFilename + "|" + size;
    }

    /**
     * Starts tracking the files written in the current transaction. If the
     * transaction does not commit, every key added to the returned list is
     * deleted from disk.
     */
    public List<String> deleteStoredFilesOnRollback() {
        List<String> storedKeys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storedKeys.forEach(TrackFileStore.this::deleteQuietly);
                }
            }
        });
        return storedKeys;
    }

    /** Writes the file to disk under a random key and saves its row. */
    public FileEntity store(MultipartFile file, List<String> storedKeys) {
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
     * Deletes these files from disk once the current transaction commits. If it
     * rolls back, the rows come back and so the files must stay.
     */
    public void deleteFromDiskAfterCommit(Collection<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        List<String> keys = List.copyOf(storageKeys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(TrackFileStore.this::deleteQuietly);
            }
        });
    }

    /**
     * An exception here would reach the caller after the commit and turn a
     * successful request into an error, so log it instead.
     */
    private void deleteQuietly(String storageKey) {
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException e) {
            log.error("Could not delete stored file {}", storageKey, e);
        }
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
