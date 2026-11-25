package com.abhi.quizbackend.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for quiz generation, bound from app.quiz.* in application.properties. */
@ConfigurationProperties(prefix = "app.quiz")
@Getter
@Setter
public class QuizProperties {

    /** The number of questions in one quiz. */
    private int questionCount = 10;
}
