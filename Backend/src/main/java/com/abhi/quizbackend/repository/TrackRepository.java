package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.Track;
import com.abhi.quizbackend.entity.TrackVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    List<Track> findByVisibility(TrackVisibility visibility);

    List<Track> findByOwnerUserId(Long userId);

    List<Track> findByVisibilityAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
            TrackVisibility visibility,
            String name
    );

    Page<Track> findByVisibilityAndNameContainingIgnoreCase(
            TrackVisibility visibility,
            String name,
            Pageable pageable
    );

    List<Track> findByOwnerUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
            Long userId,
            String name
    );
}