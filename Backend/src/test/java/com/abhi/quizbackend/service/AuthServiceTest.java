package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.models.SignUpRequestDTO;
import com.abhi.quizbackend.security.jwt.JwtUtil;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Guards which paths start a new session. Single-device login depends entirely on
 * login going through issueExclusive and refresh not.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest
{
    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private OAuthUserService oAuthUserService;

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp()
    {
        authService = new AuthService(
                authenticationManager,refreshTokenService,oAuthUserService,userService,jwtUtil);

        user = new User();
        user.setUserId(1L);
        user.setEmail("abhi@example.com");
        user.setUsername("abhi");
        user.setPassword("{bcrypt}hash");
        user.setRole(Role.USER);
    }

    private void stubSuccessfulAuthentication()
    {
        CustomUserDetails principal = new CustomUserDetails(user);
        given(authenticationManager.authenticate(any())).willReturn(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,null,principal.getAuthorities()));
    }

    @Test
    void signupSignsTheNewUserStraightIn()
    {
        SignUpRequestDTO request = new SignUpRequestDTO("abhi@example.com","abhi","supersecret");
        given(userService.createUser(request)).willReturn(user);
        given(refreshTokenService.issueExclusive(user)).willReturn("new-refresh");
        given(jwtUtil.generateAccessToken(user)).willReturn("access");

        AuthService.AuthTokens tokens = authService.signup(request);

        // Same shape as login: no second password submission required.
        assertThat(tokens.response().accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void passwordLoginEndsEveryOtherSession()
    {
        stubSuccessfulAuthentication();
        given(refreshTokenService.issueExclusive(user)).willReturn("new-refresh");
        given(jwtUtil.generateAccessToken(user)).willReturn("access");

        AuthService.AuthTokens tokens =
                authService.login("abhi@example.com","secret");

        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).issueExclusive(user);
        verify(refreshTokenService,never()).issue(any());
    }

    @Test
    void googleLoginProvisionsTheAccountAndEndsEveryOtherSession()
    {
        GoogleUserDTO google = new GoogleUserDTO("sub-1","Abhi","abhi@example.com","pic",true);
        given(oAuthUserService.findOrCreate(google)).willReturn(user);
        given(refreshTokenService.issueExclusive(user)).willReturn("new-refresh");

        assertThat(authService.startOAuthSession(google)).isEqualTo("new-refresh");

        // Signing in with Google is a login, so it displaces other devices as well.
        verify(refreshTokenService).issueExclusive(user);
        // No access token is minted here: the callback body can never reach the frontend.
        verify(jwtUtil,never()).generateAccessToken(any());
    }

    @Test
    void refreshContinuesTheSessionWithoutDisplacingIt()
    {
        given(refreshTokenService.rotate("old-refresh"))
                .willReturn(new RefreshTokenService.Issued(user,"rotated-refresh"));
        given(jwtUtil.generateAccessToken(user)).willReturn("access");

        AuthService.AuthTokens tokens =
                authService.refresh("old-refresh");

        assertThat(tokens.refreshToken()).isEqualTo("rotated-refresh");
        // Refreshing is not a login: it must never revoke anything.
        verify(refreshTokenService,never()).issueExclusive(any());
    }
}
