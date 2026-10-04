package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.TopicFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final TopicFileService topicFileService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long fileId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        TopicFileService.Download download = topicFileService.load(fileId, principal.getUserId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.file().getContentType()))
                .contentLength(download.file().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.file().getOriginalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }
}
