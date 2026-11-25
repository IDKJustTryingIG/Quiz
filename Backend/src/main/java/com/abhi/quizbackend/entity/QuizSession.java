package com.abhi.quizbackend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The 18:00 contest of one track on one date. Its state comes from the
 * clock: upcoming before opensAt, open until closesAt, then closed.
 */
@Entity
@Table(
        name = "quiz_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quiz_sessions_track_date",
                columnNames = {"track_id", "session_date"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Track track;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    /** The last moment a user can start. */
    @Column(name = "start_deadline", nullable = false)
    private Instant startDeadline;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    /** When the server scored the attempts nobody submitted. Null before. */
    @Column(name = "scored_at")
    private Instant scoredAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    private List<SessionDay> days = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isOpenAt(Instant now) {
        return !now.isBefore(opensAt) && now.isBefore(closesAt);
    }

    public boolean isClosedAt(Instant now) {
        return !now.isBefore(closesAt);
    }
}
