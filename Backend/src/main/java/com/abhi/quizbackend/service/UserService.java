package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.LoginType;
import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.models.SignUpRequestDTO;
import com.abhi.quizbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService
{
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Returns the persisted entity rather than a DTO so the caller can start a session
     * in the same transaction: an account created without one would leave the user
     * apparently signed up but unable to proceed.
     */
    @Transactional
    public User createUser(SignUpRequestDTO request)
    {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();

        // Pre-checks give a clean 409 for the common case; the unique indexes
        // below are what actually make this safe under concurrent signups.
        if (userRepository.existsByEmail(email))
        {
            throw new DuplicateResourceException("email","An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username))
        {
            throw new DuplicateResourceException("username","This username is already taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setLoginType(LoginType.LOCAL);

        try
        {
            user = userRepository.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException ex)
        {
            // Lost the race between the checks above and this insert.
            log.warn("Signup lost a uniqueness race for username={}",username);
            throw new DuplicateResourceException("email","An account with this email or username already exists");
        }

        log.info("Created user id={}",user.getUserId());
        return user;
    }
}
