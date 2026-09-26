package com.abhi.quizbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One issued refresh token. The raw value never touches the database: only its
 * SHA-256 hash is stored, so a database leak yields nothing usable.
 */
@Entity
@Getter
@Setter
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_hash",columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_tokens_user",columnList = "user_id"))
public class RefreshToken
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    /** Hex SHA-256 of the raw token. Unique and indexed, so lookup is a single hit. */
    @Column(name = "token_hash",nullable = false,length = 64)
    private String tokenHash;

    @Column(name = "issued_at",nullable = false,updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at",nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when this token was rotated, forming the chain that reuse detection walks. */
    @Column(name = "replaced_by_id")
    private Long replacedById;

    public boolean isRevoked()
    {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now)
    {
        return expiresAt.isBefore(now);
    }

    public boolean isUsableAt(Instant now)
    {
        return !isRevoked() && !isExpiredAt(now);
    }
}
