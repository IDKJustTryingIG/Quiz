package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.LoginType;
import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthUserServiceTest
{
    @Mock
    private UserRepository userRepository;

    private OAuthUserService service;

    @BeforeEach
    void setUp()
    {
        service = new OAuthUserService(userRepository);
        given(userRepository.save(any(User.class))).willAnswer(call -> call.getArgument(0));
    }

    private GoogleUserDTO google(String email,boolean verified)
    {
        return new GoogleUserDTO("google-sub-1","Abhi",email,"https://pic",verified);
    }

    @Test
    void refusesUnverifiedGoogleEmail()
    {
        // Otherwise anyone able to create a Google account claiming an unverified
        // address could take over the existing local account on that address.
        assertThatThrownBy(() -> service.findOrCreate(google("abhi@example.com",false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not verified");

        verify(userRepository,never()).save(any());
    }

    @Test
    void createsAGoogleOnlyAccountWithNoPassword()
    {
        given(userRepository.findByGoogleId(anyString())).willReturn(Optional.empty());
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(userRepository.existsByUsernameIgnoreCase(anyString())).willReturn(false);

        User created = service.findOrCreate(google("Abhi@Example.COM",true));

        assertThat(created.getEmail()).isEqualTo("abhi@example.com");
        assertThat(created.getPassword()).isNull();
        assertThat(created.getLoginType()).isEqualTo(LoginType.GOOGLE);
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.getGoogleId()).isEqualTo("google-sub-1");
        assertThat(created.getUsername()).isEqualTo("abhi");
    }

    @Test
    void disambiguatesAUsernameThatIsAlreadyTaken()
    {
        given(userRepository.findByGoogleId(anyString())).willReturn(Optional.empty());
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(userRepository.existsByUsernameIgnoreCase("abhi")).willReturn(true);
        given(userRepository.existsByUsernameIgnoreCase("abhi1")).willReturn(true);
        given(userRepository.existsByUsernameIgnoreCase("abhi2")).willReturn(false);

        assertThat(service.findOrCreate(google("abhi@example.com",true)).getUsername())
                .isEqualTo("abhi2");
    }

    @Test
    void linksGoogleToAnExistingPasswordAccount()
    {
        User existing = new User();
        existing.setUserId(5L);
        existing.setEmail("abhi@example.com");
        existing.setUsername("abhi");
        existing.setPassword("{bcrypt}hash");
        existing.setLoginType(LoginType.LOCAL);

        given(userRepository.findByGoogleId(anyString())).willReturn(Optional.empty());
        given(userRepository.findByEmail("abhi@example.com")).willReturn(Optional.of(existing));

        User linked = service.findOrCreate(google("abhi@example.com",true));

        assertThat(linked.getUserId()).isEqualTo(5L);
        assertThat(linked.getGoogleId()).isEqualTo("google-sub-1");
        // Keeps its password, so both sign-in routes now work.
        assertThat(linked.getLoginType()).isEqualTo(LoginType.BOTH);
        assertThat(linked.getPassword()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void matchesOnGoogleIdEvenWhenTheEmailChanged()
    {
        User existing = new User();
        existing.setUserId(9L);
        existing.setEmail("old@example.com");
        existing.setGoogleId("google-sub-1");
        existing.setLoginType(LoginType.GOOGLE);

        given(userRepository.findByGoogleId("google-sub-1")).willReturn(Optional.of(existing));

        User found = service.findOrCreate(google("new@example.com",true));

        assertThat(found.getUserId()).isEqualTo(9L);
        // Matched on the stable subject, so no duplicate account was created.
        verify(userRepository,never()).save(any());
    }
}
