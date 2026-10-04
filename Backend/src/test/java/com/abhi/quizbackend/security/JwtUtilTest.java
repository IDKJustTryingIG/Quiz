package com.abhi.quizbackend.security;

import com.abhi.quizbackend.entity.Role;
import com.abhi.quizbackend.entity.User;
import com.abhi.quizbackend.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest
{
    private static final String SECRET = "CpjQeWLaMJe1GaCPx0HCmIAzv6htXYLBp5aAoz4PxCg=";
    private static final String OTHER_SECRET = "jbTHQmg4FV4wZZ67JwhL8MQGsNy6YU1ajusa3NLh3wXY";
    private static final String ISSUER = "quiz-backend-test";

    private JwtUtil jwtUtil(String secret,String issuer,long ttlMs)
    {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util,"secret",secret);
        ReflectionTestUtils.setField(util,"issuer",issuer);
        ReflectionTestUtils.setField(util,"expiration",ttlMs);
        ReflectionTestUtils.invokeMethod(util,"init");
        return util;
    }

    private User user()
    {
        User user = new User();
        user.setUserId(42L);
        user.setEmail("abhi@example.com");
        user.setUsername("abhi");
        user.setRole(Role.ADMIN);
        return user;
    }

    @Test
    void roundTripsExpectedClaims()
    {
        JwtUtil util = jwtUtil(SECRET,ISSUER,900_000);

        Claims claims = util.extractClaims(util.generateAccessToken(user()));

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get("email",String.class)).isEqualTo("abhi@example.com");
        assertThat(claims.get("username",String.class)).isEqualTo("abhi");
        // The ROLE_ prefix is a Spring detail and must not appear in the token.
        assertThat(claims.get("role",String.class)).isEqualTo("ADMIN");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void producesASignedTokenNotAnUnsignedOne()
    {
        // An unsigned JWT ("alg":"none") has an empty third segment and would be
        // accepted by nothing -- but is easy to emit by forgetting signWith().
        String token = jwtUtil(SECRET,ISSUER,900_000).generateAccessToken(user());

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[2]).isNotEmpty();
    }

    @Test
    void neverPutsThePasswordInTheToken()
    {
        User user = user();
        user.setPassword("{bcrypt}$2a$10$secrethash");

        JwtUtil util = jwtUtil(SECRET,ISSUER,900_000);
        Claims claims = util.extractClaims(util.generateAccessToken(user));

        assertThat(claims).doesNotContainKey("password");
        assertThat(claims.toString()).doesNotContain("bcrypt");
    }

    @Test
    void rejectsExpiredToken() throws Exception
    {
        // 1ms lifetime, then wait past both the TTL and the 60s skew allowance is not
        // practical, so verify with a util whose skew is irrelevant: expiry is in the past.
        JwtUtil issuer = jwtUtil(SECRET,ISSUER,-3_600_000);
        String stale = issuer.generateAccessToken(user());

        JwtUtil verifier = jwtUtil(SECRET,ISSUER,900_000);

        assertThatThrownBy(() -> verifier.extractClaims(stale))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret()
    {
        String foreign = jwtUtil(OTHER_SECRET,ISSUER,900_000).generateAccessToken(user());

        assertThatThrownBy(() -> jwtUtil(SECRET,ISSUER,900_000).extractClaims(foreign))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer()
    {
        String foreign = jwtUtil(SECRET,"someone-else",900_000).generateAccessToken(user());

        assertThatThrownBy(() -> jwtUtil(SECRET,ISSUER,900_000).extractClaims(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesToStartWithAWeakSecret()
    {
        assertThatThrownBy(() -> jwtUtil("too-short","iss",900_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
