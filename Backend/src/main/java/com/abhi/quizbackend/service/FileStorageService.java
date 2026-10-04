package com.abhi.quizbackend.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    String store(
            MultipartFile file,
            String storageKey
    ) throws IOException;

    Resource load(String storageKey);

    void delete(String storageKey);
}