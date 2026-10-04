package com.abhi.quizbackend.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Google denied or cancelled: send the browser back to the frontend, not to a stack trace. */
@Component
public class CustomOAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler
{
    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2FailureHandler.class);

    @Value("${auth.oauth2.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,HttpServletResponse response,
                                        AuthenticationException exception) throws IOException
    {
        log.warn("Google sign-in failed: {}",exception.getMessage());

        String separator = failureRedirect.contains("?") ? "&" : "?";
        response.sendRedirect(failureRedirect + separator + "error="
                + URLEncoder.encode("authentication_failed",StandardCharsets.UTF_8));
    }
}
