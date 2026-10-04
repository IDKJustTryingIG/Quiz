package com.abhi.quizbackend.service;

import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.models.SignUpRequestDTO;
import com.abhi.quizbackend.models.TokenResponseDTO;
import com.abhi.quizbackend.security.jwt.JwtUtil;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService
{
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final OAuthUserService oAuthUserService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** Access token plus the raw refresh token, which the controller puts in a cookie. */
    public record AuthTokens(TokenResponseDTO response,String refreshToken,User user)
    {
    }

    /**
     * Signup signs the user in. They chose the password moments ago, so making them
     * submit it again is friction with no security benefit. One transaction, so an
     * account is never created without the session that goes with it.
     */
    @Transactional
    public AuthTokens signup(SignUpRequestDTO request)
    {
        return issueFor(userService.createUser(request));
    }

    @Transactional
    public AuthTokens login(String email,String password)
    {
        // Throws AuthenticationException on bad credentials; mapped to 401 by the advice.
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email,password));

        User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();

        return issueFor(user);
    }

    /**
     * Rotates the refresh token and mints a new access token. Rotation means a stolen
     * token stops working as soon as the legitimate client refreshes.
     */
    @Transactional
    public AuthTokens refresh(String rawRefreshToken)
    {
        RefreshTokenService.Issued issued =
                refreshTokenService.rotate(rawRefreshToken);

        return new AuthTokens(
                TokenResponseDTO.bearer(
                        jwtUtil.generateAccessToken(issued.user()),
                        jwtUtil.getAccessTokenTtlSeconds()),
                issued.rawToken(),
                issued.user());
    }

    /**
     * Completes a Google sign-in: provision or link the account and start a session,
     * in one transaction. Returns the raw refresh token for the caller to put in a
     * cookie; no access token is minted here, because the OAuth callback is a browser
     * navigation whose body the frontend can never read.
     */
    @Transactional
    public String startOAuthSession(GoogleUserDTO google)
    {
        User user = oAuthUserService.findOrCreate(google);

        // Exclusive: signing in with Google ends any session this user has elsewhere.
        return refreshTokenService.issueExclusive(user);
    }

    @Transactional
    public void logout(String rawRefreshToken)
    {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional
    public int logoutAll(User user)
    {
        return refreshTokenService.revokeAllForUser(user);
    }

    private AuthTokens issueFor(User user)
    {
        // Exclusive: signing in here ends any session this user has elsewhere.
        String refreshToken = refreshTokenService.issueExclusive(user);

        return new AuthTokens(
                TokenResponseDTO.bearer(
                        jwtUtil.generateAccessToken(user),
                        jwtUtil.getAccessTokenTtlSeconds()),
                refreshToken,
                user);
    }
}
