package com.abhi.quizbackend.security.oauth2;

import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.security.support.RefreshCookieFactory;
import com.abhi.quizbackend.service.AuthService;
import com.abhi.quizbackend.service.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2SuccessHandlerTest
{
    private static final String SUCCESS = "http://localhost:5173/oauth2/success";
    private static final String FAILURE = "http://localhost:5173/oauth2/failure";

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private AuthService authService;

    @Mock
    private RefreshCookieFactory refreshCookieFactory;

    private CustomOAuth2SuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp()
    {
        handler = new CustomOAuth2SuccessHandler(googleOAuthService,authService,refreshCookieFactory);
        ReflectionTestUtils.setField(handler,"successRedirect",SUCCESS);
        ReflectionTestUtils.setField(handler,"failureRedirect",FAILURE);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private Authentication googleAuthentication()
    {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub","sub-1","email","abhi@example.com"),
                "sub");

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,null,principal.getAuthorities());
    }

    @Test
    void setsTheRefreshCookieOnTheRedirectAndLeavesTheUrlClean() throws Exception
    {
        GoogleUserDTO google = new GoogleUserDTO("sub-1","Abhi","abhi@example.com","pic",true);
        given(googleOAuthService.getGoogleUser(any())).willReturn(google);
        given(authService.startOAuthSession(google)).willReturn("raw-refresh-token");
        given(refreshCookieFactory.create("raw-refresh-token")).willReturn(
                ResponseCookie.from("refresh_token","raw-refresh-token")
                        .httpOnly(true).path("/api/auth").sameSite("Strict")
                        .maxAge(Duration.ofDays(30)).build());

        handler.onAuthenticationSuccess(request,response,googleAuthentication());

        assertThat(response.getStatus()).isEqualTo(302);
        // No code, no token, no parameters at all.
        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("refresh_token=raw-refresh-token")
                .contains("HttpOnly");
    }

    @Test
    void redirectsToFailureWhenProvisioningIsRejected() throws Exception
    {
        given(googleOAuthService.getGoogleUser(any())).willReturn(
                new GoogleUserDTO("sub-1","Abhi","abhi@example.com","pic",false));
        willThrow(new IllegalStateException("Google account email is not verified"))
                .given(authService).startOAuthSession(any());

        handler.onAuthenticationSuccess(request,response,googleAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE + "?error=account_not_provisioned");
        // No session was started, so nothing may be set on the browser.
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void redirectsToFailureForAnUnexpectedPrincipal() throws Exception
    {
        Authentication notOAuth = UsernamePasswordAuthenticationToken.authenticated(
                "plain-string-principal",null,List.of());

        handler.onAuthenticationSuccess(request,response,notOAuth);

        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE + "?error=unsupported_principal");
        verify(authService,org.mockito.Mockito.never()).startOAuthSession(any());
    }
}
