package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.models.*;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.security.support.RefreshCookieFactory;
import com.abhi.quizbackend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    /**
     * Creates the account and signs the user straight in, so the response is the same
     * shape as login: access token in the body, refresh token in the cookie.
     */
    @PostMapping("/signup")
    public ResponseEntity<TokenResponseDTO> signup(@Valid @RequestBody SignUpRequestDTO body)
    {
        AuthService.AuthTokens tokens = authService.signup(body);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE,refreshCookieFactory.create(tokens.refreshToken()).toString())
                .body(tokens.response());
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO body)
    {
        return withRefreshCookie(authService.login(body.getEmail(),body.getPassword()));
    }

    /**
     * Reads the refresh token from the HttpOnly cookie, never the body: the whole point
     * of the cookie is that no script on the page can get at the value.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(HttpServletRequest request)
    {
        String raw = refreshCookieFactory.read(request).orElse(null);

        return withRefreshCookie(authService.refresh(raw));
    }


    /**
     * Revokes this device's refresh token and clears the cookie. Always 204, even for an
     * unknown token: logout must not report whether a session existed.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @AuthenticationPrincipal CustomUserDetails principal)
    {
        Optional<String> cookie = refreshCookieFactory.read(request);

        if (cookie.isPresent())
        {
            authService.logout(cookie.get());
        }
        else if (principal != null)
        {
            // No cookie to revoke -- blocked, cleared, or lost to a SameSite mismatch --
            // but the caller proved who they are. End their session anyway, rather than
            // leaving a refresh token live for its full lifetime with no way to reach it.
            authService.logoutAll(principal.getUser());
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,refreshCookieFactory.clear().toString())
                .build();
    }


    @GetMapping("/me")
    public UserProfileDTO me(@AuthenticationPrincipal CustomUserDetails principal)
    {
        User user = principal.getUser();

        return new UserProfileDTO(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole().name(),
                user.getLoginType().name(),
                user.getProfilePicture());
    }

    private ResponseEntity<TokenResponseDTO> withRefreshCookie(AuthService.AuthTokens tokens)
    {
        ResponseCookie cookie = refreshCookieFactory.create(tokens.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,cookie.toString())
                .body(tokens.response());
    }


}
