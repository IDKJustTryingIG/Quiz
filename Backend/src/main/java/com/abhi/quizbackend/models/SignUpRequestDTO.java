package com.abhi.quizbackend.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDTO
{
    @NotBlank(message = "Email is required")
    @Email(message = "Provide a valid email address")
    @Size(max = 255,message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3,max = 30,message = "Username must be between 3 and 30 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8,max = 72,message = "Password must be between 8 and 72 characters")
    private String password;
}
