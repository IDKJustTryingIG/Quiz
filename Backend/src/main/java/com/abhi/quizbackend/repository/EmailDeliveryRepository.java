package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.EmailDelivery;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, Long> {

    List<EmailDelivery> findBySendDateAndKind(LocalDate sendDate, String kind);

    /** The date's emails with their user and track, for the admin page. */
    @EntityGraph(attributePaths = {"enrollment", "enrollment.user", "enrollment.track"})
    List<EmailDelivery> findBySendDateOrderByDeliveryIdAsc(LocalDate sendDate);

    /**
     * The next PENDING email whose delay is over, locked for this transaction.
     * SKIP LOCKED (timeout -2), so two servers never send the same email.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select d from EmailDelivery d
            where d.status = com.abhi.quizbackend.entity.EmailStatus.PENDING
              and d.nextAttemptAt <= :now
            order by d.nextAttemptAt, d.deliveryId
            """)
    List<EmailDelivery> findDue(@Param("now") Instant now, Pageable limit);
}
