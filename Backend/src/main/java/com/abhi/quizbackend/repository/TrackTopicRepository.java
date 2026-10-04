package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.TrackTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrackTopicRepository
        extends JpaRepository<TrackTopic, Long> {

    List<TrackTopic> findByTrackTrackIdOrderByPositionAsc(Long trackId);

    Optional<TrackTopic> findByTrackTrackIdAndTopicTopicId(
            Long trackId,
            Long topicId
    );

    boolean existsByTrackTrackIdAndTopicTopicId(
            Long trackId,
            Long topicId
    );

    boolean existsByTrackTrackIdAndPosition(
            Long trackId,
            Integer position
    );

    void deleteByTrackTrackIdAndTopicTopicId(
            Long trackId,
            Long topicId
    );
}
