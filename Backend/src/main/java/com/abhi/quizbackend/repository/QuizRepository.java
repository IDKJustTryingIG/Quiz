package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.Quiz;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /** The id of the quiz made from these files by this generator. Empty if there is none. */
    @Query("""
            select q.quizId from Quiz q
            where q.topic.topicId = :topicId
              and q.sourceHash = :sourceHash
              and q.generatorVersion = :generatorVersion
            """)
    Optional<Long> findQuizId(
            @Param("topicId") Long topicId,
            @Param("sourceHash") String sourceHash,
            @Param("generatorVersion") String generatorVersion
    );

    /** The quiz with its questions, in quiz order, read in one query. */
    @EntityGraph(attributePaths = {"questions", "topic"})
    Optional<Quiz> findWithQuestionsByQuizId(Long quizId);

    /**
     * Deletes the topic's quizzes that were made from other files or by
     * another generator. The database deletes their questions.
     *
     * A quiz that a contest or an attempt uses is kept: its results must
     * still show the questions.
     */
    @Modifying
    @Query("""
            delete from Quiz q
            where q.topic.topicId = :topicId
              and (q.sourceHash <> :sourceHash or q.generatorVersion <> :generatorVersion)
              and not exists (select d from SessionDay d where d.quizId = q.quizId)
              and not exists (select a from QuizAttempt a where a.quizId = q.quizId)
            """)
    int deleteOtherVersions(
            @Param("topicId") Long topicId,
            @Param("sourceHash") String sourceHash,
            @Param("generatorVersion") String generatorVersion
    );
}
