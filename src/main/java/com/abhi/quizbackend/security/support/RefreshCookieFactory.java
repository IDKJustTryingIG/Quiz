package com.abhi.quizbackend.security.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Builds the refresh-token cookie. HttpOnly keeps it out of reach of any script on the
 * page, so an XSS cannot steal it; the narrow Path means it is not attached to ordinary
 * API calls; SameSite blocks cross-site submission.
 */
@Component
public class RefreshCookieFactory
{
    @Getter
    @Value("${auth.cookie.name}")
    private String cookieName;

    @Value("${auth.cookie.path}")
    private String cookiePath;

    @Value("${auth.cookie.secure}")
    private boolean secure;

    @Value("${auth.cookie.same-site}")
    private String sameSite;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public ResponseCookie create(String rawToken)
    {
        return base(rawToken)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build();
    }

    /** Zero max-age tells the browser to drop it immediately, on logout. */
    public ResponseCookie clear()
    {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> read(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
        {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value)
    {
        return ResponseCookie.from(cookieName,value)
                .httpOnly(true)
                .secure(secure)
                .path(cookiePath)
                .sameSite(sameSite);
    }
}
