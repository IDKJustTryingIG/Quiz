package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.LoginType;
import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Finds, links or creates the local account behind a Google sign-in. */
@Service
@RequiredArgsConstructor
public class OAuthUserService
{
    private static final Logger log = LoggerFactory.getLogger(OAuthUserService.class);
    private static final int MAX_USERNAME = 30;
    private static final int BASE_USERNAME_BUDGET = 24;
    private static final int MAX_SEQUENTIAL_ATTEMPTS = 50;

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(GoogleUserDTO google)
    {
        // Without this check, anyone able to create a Google account claiming someone
        // else's unverified address could take over that existing local account.
        if (!Boolean.TRUE.equals(google.getEmailVerified()))
        {
            throw new IllegalStateException("Google account email is not verified");
        }

        if (google.getEmail() == null || google.getEmail().isBlank())
        {
            throw new IllegalStateException("Google did not return an email address");
        }

        String email = google.getEmail().trim().toLowerCase(Locale.ROOT);

        // Match on the stable subject first: the user may have changed their Gmail address.
        Optional<User> byGoogleId = userRepository.findByGoogleId(google.getGoogleId());
        if (byGoogleId.isPresent())
        {
            User user = byGoogleId.get();
            updatePicture(user,google);
            return user;
        }

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent())
        {
            // Existing local account, verified same address: link the two.
            User user = byEmail.get();
            user.setGoogleId(google.getGoogleId());
            // it means user can now sign in with both google and tradtional login
            user.setLoginType(LoginType.BOTH);
            updatePicture(user,google);
            log.info("Linked Google identity to existing userId={}",user.getUserId());
            return user;
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(generateUsername(email));
        // No password: this account can only ever sign in through Google until one is set.
        user.setPassword(null);
        user.setRole(Role.USER);
        user.setLoginType(LoginType.GOOGLE);
        user.setGoogleId(google.getGoogleId());
        user.setProfilePicture(google.getPicture());

        User saved = userRepository.save(user);
        log.info("Provisioned new Google user userId={}",saved.getUserId());
        return saved;
    }

    private void updatePicture(User user,GoogleUserDTO google)
    {
        if (google.getPicture() != null)
        {
            user.setProfilePicture(google.getPicture());
        }
    }

    /**
     * Google gives a display name, not a username. Derive one from the email local part
     * and disambiguate on collision, since username is unique and 3-30 characters.
     */
    private String generateUsername(String email)
    {
        String base = email.substring(0,email.indexOf('@'))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]","");

        if (base.length() < 3)
        {
            base = base + "user";
        }
        if (base.length() > BASE_USERNAME_BUDGET)
        {
            base = base.substring(0,BASE_USERNAME_BUDGET);
        }

        if (!userRepository.existsByUsernameIgnoreCase(base))
        {
            return base;
        }

        for (int suffix = 1; suffix <= MAX_SEQUENTIAL_ATTEMPTS; suffix++)
        {
            String candidate = base + suffix;
            if (!userRepository.existsByUsernameIgnoreCase(candidate))
            {
                return candidate;
            }
        }

        // Popular base name: stop scanning linearly and jump to a random suffix.
        for (int attempt = 0; attempt < MAX_SEQUENTIAL_ATTEMPTS; attempt++)
        {
            String candidate = truncate(base) + ThreadLocalRandom.current().nextInt(100_000,999_999);
            if (!userRepository.existsByUsernameIgnoreCase(candidate))
            {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to allocate a username for " + email);
    }

    private String truncate(String base)
    {
        int budget = MAX_USERNAME - 6;
        return base.length() <= budget ? base : base.substring(0,budget);
    }
}
