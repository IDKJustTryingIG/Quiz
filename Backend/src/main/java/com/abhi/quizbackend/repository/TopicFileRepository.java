package com.abhi.quizbackend.repository;
import com.abhi.quizbackend.entity.TopicFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TopicFileRepository
        extends JpaRepository<TopicFile, Long> {

    List<TopicFile> findByTopicTopicIdOrderByPositionAsc(Long topicId);

    Optional<TopicFile> findByTopicTopicIdAndFileFileId(
            Long topicId,
            Long fileId
    );

    boolean existsByTopicTopicIdAndFileFileId(
            Long topicId,
            Long fileId
    );

    void deleteByTopicTopicIdAndFileFileId(
            Long topicId,
            Long fileId
    );
}
