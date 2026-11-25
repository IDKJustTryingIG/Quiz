package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.models.CreateFullTrackRequest;
import com.abhi.quizbackend.models.CreateTrackRequest;
import com.abhi.quizbackend.models.EditTrackRequest;
import com.abhi.quizbackend.models.TrackDetailResponse;
import com.abhi.quizbackend.models.TrackPageResponse;
import com.abhi.quizbackend.models.TrackResponse;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.service.TrackCreationService;
import com.abhi.quizbackend.service.TrackEditService;
import com.abhi.quizbackend.service.TrackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;
    private final TrackCreationService trackCreationService;
    private final TrackEditService trackEditService;

    @PostMapping
    public ResponseEntity<TrackResponse> create(
            @Valid @RequestBody CreateTrackRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(trackService.create(request, principal.getUserId()));
    }

    /**
     * Creates a track with its topics and their files in one request.
     *
     * Multipart parts: "track" holds a {@link CreateFullTrackRequest} as JSON,
     * and "files-{i}" holds the files for topic i (repeat the part per file).
     */
    @PostMapping(path = "/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrackResponse> createFull(
            @Valid @RequestPart("track") CreateFullTrackRequest request,
            MultipartHttpServletRequest multipartRequest,
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<List<MultipartFile>> filesByTopic = IntStream
                .range(0, request.topics().size())
                .mapToObj(i -> multipartRequest.getFiles("files-" + i))
                .toList();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(trackCreationService.create(request, filesByTopic, principal.getUserId()));
    }

    /**
     * Without a scope: every public track plus the user's own.
     * With scope "public" or "mine": that slice only, filtered by name.
     */
    @GetMapping
    public ResponseEntity<List<TrackResponse>> getTracks(
            @RequestParam(required = false) @Pattern(regexp = "public|mine") String scope,
            @RequestParam(required = false) @Size(max = 255) String q,
            @AuthenticationPrincipal CustomUserDetails principal) {

        if (scope == null) {
            return ResponseEntity.ok(
                    trackService.getTracks(principal.getUserId())
            );
        }

        return ResponseEntity.ok(
                trackService.searchTracks(scope, q, principal.getUserId())
        );
    }

    /** Public tracks one page at a time, for the Top tracks carousel. */
    @GetMapping("/public")
    public ResponseEntity<TrackPageResponse> getPublicTracks(
            @RequestParam(required = false) @Size(max = 255) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "3") @Min(1) @Max(24) int size) {

        return ResponseEntity.ok(
                trackService.searchPublicTracks(q, page, size)
        );
    }

    /** Lets the form say "name taken" while the user is still typing. */
    @GetMapping("/name-availability")
    public ResponseEntity<Map<String, Boolean>> nameAvailability(
            @RequestParam @NotBlank @Size(max = 255) String name) {

        return ResponseEntity.ok(
                Map.of("available", trackService.isNameAvailable(name.trim()))
        );
    }

    /** Everything the track page shows: days, topics and their files. */
    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<TrackDetailResponse> getDetailBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(
                trackService.getDetailBySlug(slug, principal.getUserId())
        );
    }

    @GetMapping("/{trackId}")
    public ResponseEntity<TrackResponse> getById(
            @PathVariable Long trackId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        return ResponseEntity.ok(
                trackService.getById(trackId, principal.getUserId())
        );
    }

    /**
     * Saves the owner's edit of a track in one request. The name cannot change.
     *
     * Multipart parts: "track" holds an {@link EditTrackRequest} as JSON, and
     * "files-{i}" holds the new files for day i (repeat the part per file).
     */
    @PutMapping(path = "/{trackId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrackResponse> update(
            @PathVariable Long trackId,
            @Valid @RequestPart("track") EditTrackRequest request,
            MultipartHttpServletRequest multipartRequest,
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<List<MultipartFile>> filesByDay = IntStream
                .range(0, request.days().size())
                .mapToObj(i -> multipartRequest.getFiles("files-" + i))
                .toList();

        return ResponseEntity.ok(
                trackEditService.edit(trackId, request, filesByDay, principal.getUserId())
        );
    }

    @DeleteMapping("/{trackId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long trackId,
            @AuthenticationPrincipal CustomUserDetails principal) {

        trackService.delete(trackId, principal.getUserId());

        return ResponseEntity.noContent().build();
    }
}
