package com.abhi.quizbackend.security.filters;

import com.abhi.quizbackend.security.jwt.JwtUtil;
import com.abhi.quizbackend.security.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Deliberately NOT a {@code @Component}: Spring Boot auto-registers every Filter bean
 * into the servlet chain, which would run this once outside the security chain as well
 * as inside it. {@code SecurityConfig} constructs it instead.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Endpoints that are reachable without authentication. A stale bearer token must not
     * block them: clients routinely attach the Authorization header to every request,
     * including the refresh call they are making precisely because the token expired.
     * Rejecting there would lock a user out while holding a perfectly valid cookie.
     */
    private static final Set<String> PUBLIC_AUTH_ENDPOINTS = Set.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout");

    private static final List<String> PUBLIC_PREFIXES = List.of("/oauth2/","/login/oauth2/");

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException
    {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No bearer token: continue unauthenticated. Public endpoints still work and
        // protected ones are rejected later by the entry point.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX))
        {
            filterChain.doFilter(request,response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try
        {
            // Verify the signature BEFORE trusting any claim: reading the subject from
            // an unverified token and looking it up would act on attacker-controlled input.
            Long userId = jwtUtil.extractUserId(token);

            // Loading the user per request costs a query but means a deleted account or a
            // changed role takes effect immediately, instead of lingering until expiry.
            UserDetails userDetails = userDetailsService.loadByUserId(userId);

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            userDetails,null,userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        catch (JwtException | IllegalArgumentException | org.springframework.security.core.userdetails.UsernameNotFoundException ex)
        {
            // On a protected endpoint a present-but-invalid token is reported rather
            // than silently downgraded to anonymous, so the client knows to refresh.
            SecurityContextHolder.clearContext();

            if (isPublicEndpoint(request))
            {
                // Continue unauthenticated rather than rejecting. The endpoint authorises
                // itself from the refresh cookie, not from this header.
                log.debug("Ignoring invalid bearer token on public endpoint {}",
                        request.getRequestURI());
                filterChain.doFilter(request,response);
                return;
            }

            log.debug("Rejected bearer token on {}: {}",request.getRequestURI(),ex.getMessage());
            authenticationEntryPoint.commence(request,response,
                    new BadCredentialsException("Invalid or expired token",ex));
            return;
        }

        filterChain.doFilter(request,response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request)
    {
        String path = request.getRequestURI();

        return PUBLIC_AUTH_ENDPOINTS.contains(path)
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
