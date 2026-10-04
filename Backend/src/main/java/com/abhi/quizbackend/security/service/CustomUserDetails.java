package com.abhi.quizbackend.security.service;

import com.abhi.quizbackend.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails
{
    private final transient User user;
    private final Long userId;
    private final String email;
    private final String displayName;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user)
    {
        this.user = user;
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.displayName = user.getUsername();
        this.password = user.getPassword();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /** Spring Security's "username" is the login identifier, which here is the email. */
    @Override
    public String getUsername()
    {
        return email;
    }
}
