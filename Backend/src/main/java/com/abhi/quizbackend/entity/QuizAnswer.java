package com.abhi.quizbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** One answer of an attempt. The first answer to a question is final. */
@Entity
@Table(
        name = "quiz_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quiz_answers_question",
                columnNames = {"attempt_id", "quiz_question_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuizAttempt attempt;

    @Column(name = "quiz_question_id", nullable = false)
    private Long questionId;

    @Column(name = "chosen_index", nullable = false)
    private Short chosenIndex;

    /** Null until the attempt is scored. */
    @Column
    private Boolean correct;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;
}
