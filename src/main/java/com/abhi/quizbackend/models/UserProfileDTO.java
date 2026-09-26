package com.abhi.quizbackend.models;

public record UserProfileDTO(
        Long userId,
        String email,
        String username,
        String role,
        String loginType,
        String profilePicture)
{
}
