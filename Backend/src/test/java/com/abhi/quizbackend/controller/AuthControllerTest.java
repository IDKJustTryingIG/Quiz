package com.abhi.quizbackend.controller;

import com.abhi.quizbackend.config.CorsConfig;
import com.abhi.quizbackend.config.SecurityConfig;
import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.exception.DuplicateResourceException;
import com.abhi.quizbackend.exception.InvalidRefreshTokenException;
import com.abhi.quizbackend.models.*;
import com.abhi.quizbackend.security.RestAccessDeniedHandler;
import com.abhi.quizbackend.security.RestAuthenticationEntryPoint;
import com.abhi.quizbackend.security.jwt.JwtUtil;
import com.abhi.quizbackend.security.oauth2.CustomOAuth2FailureHandler;
import com.abhi.quizbackend.security.oauth2.CustomOAuth2SuccessHandler;
import com.abhi.quizbackend.security.service.CustomUserDetails;
import com.abhi.quizbackend.security.service.CustomUserDetailsService;
import com.abhi.quizbackend.security.support.RefreshCookieFactory;
import com.abhi.quizbackend.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtUtil.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, RefreshCookieFactory.class})
@TestPropertySource(properties = {
        "jwt.secret=CpjQeWLaMJe1GaCPx0HCmIAzv6htXYLBp5aAoz4PxCg=",
        "jwt.issuer=quiz-backend-test",
        "jwt.expiration=900000",
        "jwt.refresh-expiration=2592000000",
        "auth.cookie.name=refresh_token",
        "auth.cookie.path=/api/auth",
        "auth.cookie.secure=false",
        "auth.cookie.same-site=Strict",
        "auth.cors.allowed-origins=http://localhost:5173"
})
class AuthControllerTest
{
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private CustomOAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    private CustomOAuth2FailureHandler oAuth2FailureHandler;

    private User user()
    {
        User user = new User();
        user.setUserId(1L);
        user.setEmail("abhi@example.com");
        user.setUsername("abhi");
        user.setPassword("{bcrypt}irrelevant");
        user.setRole(Role.USER);
        return user;
    }

    private AuthService.AuthTokens tokens()
    {
        return new AuthService.AuthTokens(
                TokenResponseDTO.bearer("access-token-value",900L),"refresh-token-value",user());
    }

    private String authHeader()
    {
        return "Bearer " + jwtUtil.generateAccessToken(user());
    }

    private void stubAuthenticatedUser()
    {
        given(userDetailsService.loadByUserId(1L)).willReturn(new CustomUserDetails(user()));
    }

    // ---------- signup ----------

    @Test
    void signupCreatesTheAccountAndSignsTheUserIn() throws Exception
    {
        given(authService.signup(any())).willReturn(tokens());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignUpRequestDTO("abhi@example.com","abhi","supersecret"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access-token-value"))
                .andExpect(jsonPath("$.password").doesNotExist())
                // Signed in immediately: the refresh cookie ships with the 201.
                .andExpect(content().string(not(containsString("refresh-token-value"))))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("refresh_token=refresh-token-value"),
                                containsString("HttpOnly"))));
    }

    @Test
    void returns409WhenEmailAlreadyExists() throws Exception
    {
        given(authService.signup(any()))
                .willThrow(new DuplicateResourceException("email","An account with this email already exists"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignUpRequestDTO("abhi@example.com","abhi","supersecret"))))
                .andExpect(status().isConflict());
    }

    // ---------- login ----------

    @Test
    void loginPutsTheRefreshTokenInAnHttpOnlyCookieAndNotInTheBody() throws Exception
    {
        given(authService.login("abhi@example.com","supersecret"))
                .willReturn(tokens());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("abhi@example.com","supersecret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token-value"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                // The refresh token must never be readable by page scripts.
                .andExpect(content().string(not(containsString("refresh-token-value"))))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("refresh_token=refresh-token-value"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Strict"),
                                containsString("Path=/api/auth"))));
    }

    @Test
    void returns401WithGenericMessageOnBadCredentials() throws Exception
    {
        willThrow(new org.springframework.security.authentication.BadCredentialsException(
                "Bad credentials for abhi@example.com"))
                .given(authService).login(any(),any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("abhi@example.com","wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                // The internal message names the account; the response must not.
                .andExpect(content().string(not(containsString("abhi@example.com"))));
    }

    @Test
    void rejectsBlankPasswordWith400() throws Exception
    {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("abhi@example.com",""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------- refresh ----------

    @Test
    void refreshReadsTheCookieAndReturnsARotatedCookie() throws Exception
    {
        given(authService.refresh("refresh-token-value")).willReturn(
                new AuthService.AuthTokens(
                        TokenResponseDTO.bearer("new-access",900L),"rotated-refresh",user()));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token","refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=rotated-refresh")));
    }

    @Test
    void refreshWithoutACookieIs401() throws Exception
    {
        willThrow(new InvalidRefreshTokenException("Refresh token is missing"))
                .given(authService).refresh(any());

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Session expired. Please sign in again."));
    }

    @Test
    void reusedRefreshTokenIs401() throws Exception
    {
        willThrow(new InvalidRefreshTokenException("Refresh token has already been used"))
                .given(authService).refresh(any());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token","stolen")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- logout ----------

    @Test
    void logoutRevokesTheTokenAndClearsTheCookie() throws Exception
    {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token","refresh-token-value")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")));

        verify(authService).logout("refresh-token-value");
    }

    @Test
    void logoutWithoutACookieIsStill204() throws Exception
    {
        // Logout must not report whether a session existed.
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutACookieFallsBackToTheAuthenticatedUser() throws Exception
    {
        stubAuthenticatedUser();

        // A lost or blocked cookie must not leave the refresh token live with no way
        // to revoke it, so an authenticated caller can still end their session.
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION,authHeader()))
                .andExpect(status().isNoContent());

        verify(authService).logoutAll(any());
    }

    @Test
    void logoutAllRouteIsGone() throws Exception
    {
        stubAuthenticatedUser();

        // Redundant under single-device login: logout already ends the only session.
        mockMvc.perform(post("/api/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION,authHeader()))
                .andExpect(status().isNotFound());
    }

    // ---------- protected endpoint ----------

    @Test
    void staleAccessTokenDoesNotBlockRefresh() throws Exception
    {
        // Clients commonly attach Authorization to every request, including the refresh
        // they are making because the access token just expired. Rejecting on the header
        // would lock the user out while their cookie is still perfectly valid.
        given(authService.refresh("refresh-token-value")).willReturn(
                new AuthService.AuthTokens(
                        TokenResponseDTO.bearer("new-access",900L),"rotated-refresh",user()));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer not-a-real-token")
                        .cookie(new Cookie("refresh_token","refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new-access"));
    }

    @Test
    void staleAccessTokenDoesNotBlockLogout() throws Exception
    {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer not-a-real-token")
                        .cookie(new Cookie("refresh_token","refresh-token-value")))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh-token-value");
    }

    @Test
    void staleAccessTokenStillFailsOnAProtectedEndpoint() throws Exception
    {
        // The leniency is scoped to the public auth endpoints only.
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresATokenAndReturnsJson401() throws Exception
    {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,"Bearer"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void meReturnsTheProfileForAValidToken() throws Exception
    {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("abhi@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void rejectsTamperedToken() throws Exception
    {
        String header = authHeader();
        String tampered = header.substring(0,header.length() - 2) + (header.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPathIs404NotMaskedAs500() throws Exception
    {
        stubAuthenticatedUser();

        mockMvc.perform(get("/api/auth/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION,authHeader()))
                .andExpect(status().isNotFound());
    }
}
