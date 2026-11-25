package com.abhi.quizbackend.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequestDTO {

    @NotBlank(message = "message must not be blank")
    private String message;
}
