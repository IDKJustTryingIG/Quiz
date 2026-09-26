package com.abhi.quizbackend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The refresh token is deliberately absent: it travels only in an HttpOnly cookie,
 * so no script on the page can read it.
 */
public record TokenResponseDTO(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn)
{
    public static TokenResponseDTO bearer(String accessToken,long expiresInSeconds)
    {
        return new TokenResponseDTO(accessToken,"Bearer",expiresInSeconds);
    }
}
