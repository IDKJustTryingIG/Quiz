package com.abhi.quizbackend.service;

import com.abhi.quizbackend.exception.FileStorageException;
import com.abhi.quizbackend.exception.ResourceNotFoundException;
import com.abhi.quizbackend.properties.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
@RequiredArgsConstructor
public class LocalFileStorageService
        implements FileStorageService {

    private final FileStorageProperties properties;

    @Override
    public String store(
            MultipartFile file,
            String storageKey) throws IOException {

        Path target = resolve(storageKey);

        Files.createDirectories(target.getParent());

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return storageKey;
    }

    @Override
    public Resource load(String storageKey) {

        Resource resource = new FileSystemResource(resolve(storageKey));

        if (!resource.exists()) {
            throw new ResourceNotFoundException("File not found");
        }

        return resource;
    }

    @Override
    public void delete(String storageKey) {

        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new FileStorageException(
                    "Could not delete file",
                    e
            );
        }
    }

    /** Resolves a key under the storage root, rejecting keys like "../x" that escape it. */
    private Path resolve(String storageKey) {
        Path root = Paths.get(properties.getLocation())
                .toAbsolutePath()
                .normalize();

        Path path = root.resolve(storageKey).normalize();

        if (!path.startsWith(root) || path.equals(root)) {
            throw new FileStorageException("Invalid storage key");
        }

        return path;
    }
}
