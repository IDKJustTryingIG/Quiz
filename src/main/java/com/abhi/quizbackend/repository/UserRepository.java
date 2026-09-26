package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);
}
