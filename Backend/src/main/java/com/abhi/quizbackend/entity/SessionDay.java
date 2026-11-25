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

/**
 * One day group of a contest: the users on dayNumber of the track, and the
 * quiz they answer. The quiz is pinned before the contest opens, so a file
 * change during the contest cannot change the questions.
 */
@Entity
@Table(
        name = "session_days",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_session_days_day",
                columnNames = {"session_id", "day_number"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_day_id")
    private Long sessionDayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private QuizSession session;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    /** Null if the owner removed the day after the contest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Topic topic;

    /** Null until a quiz is ready. Fixed once the contest opens. */
    @Column(name = "quiz_id")
    private Long quizId;
}
