package com.abhi.quizbackend.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserDTO
{
    private String googleId;
    private String name;
    private String email;
    private String picture;
    private Boolean emailVerified;
}
