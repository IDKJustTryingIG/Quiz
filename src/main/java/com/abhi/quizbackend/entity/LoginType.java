package com.abhi.quizbackend.entity;

/** How an account can authenticate. BOTH means a password account later linked to Google. */
public enum LoginType
{
    LOCAL,
    GOOGLE,
    BOTH
}
