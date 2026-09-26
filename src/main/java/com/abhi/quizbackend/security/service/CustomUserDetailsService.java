package com.abhi.quizbackend.security.service;

import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService
{
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException
    {
        // Emails are stored lowercased at signup, so normalise the lookup the same way.
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                // Deliberately vague, and hidden by the provider anyway: login must not
                // reveal which emails are registered.
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        // A Google-only account has no password and must not be loginable by password.
        if (!user.hasPassword())
        {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadByUserId(Long userId)
    {
        return userRepository.findById(userId)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User no longer exists"));
    }
}
