package com.abhi.quizbackend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Setter
@Getter
@Table(name = "users",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email",columnNames = "email"),
        @UniqueConstraint(name = "uk_users_username",columnNames = "username")
        })
public class User
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank
    @Column(nullable = false)
    @Email(message = "Provide a valid email address")
    private String email;

    /**
     * Encoded password, or null for accounts that only ever sign in with Google.
     * Nullable is the reason this column needed a migration.
     */
    @Column(length = 100)
    private String password;

    @NotBlank
    @Column(nullable = false,length = 30)
    @Size(min = 3,max = 30,message = "username must be between 3 and 30 characters")
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_type",nullable = false,length = 20)
    private LoginType loginType = LoginType.LOCAL;

    /** Google's stable subject id ("sub"), never the email, which a user can change. */
    @Column(name = "google_id",length = 255)
    private String googleId;

    @Column(name = "profile_picture",length = 500)
    private String profilePicture;

    @Column(nullable = false,updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate()
    {
        createdAt = Instant.now();
    }

    public boolean hasPassword()
    {
        return password != null && !password.isBlank();
    }
}
