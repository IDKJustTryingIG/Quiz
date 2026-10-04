package com.abhi.quizbackend.models;

public record FileResponse(
        Long fileId,
        String name,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        Integer position
) {
}
