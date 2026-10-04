package com.abhi.quizbackend.security.oauth2;

import com.abhi.quizbackend.models.GoogleUserDTO;
import com.abhi.quizbackend.security.support.RefreshCookieFactory;
import com.abhi.quizbackend.service.AuthService;
import com.abhi.quizbackend.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Runs after Google has authenticated the user. Provisions or links the local account,
 * starts a session, and attaches the refresh cookie to the redirect itself.
 *
 * <p>Nothing sensitive travels in the URL. The access token cannot be handed over here
 * at all -- this response is a browser navigation, not a fetch the frontend can read --
 * so the frontend calls {@code POST /api/auth/refresh} on landing, exactly as it already
 * does after a page reload.
 */
@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler
{
    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2SuccessHandler.class);

    private final GoogleOAuthService googleOAuthService;
    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;

    @Value("${auth.oauth2.success-redirect}")
    private String successRedirect;

    @Value("${auth.oauth2.failure-redirect}")
    private String failureRedirect;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,HttpServletResponse response,
                                        Authentication authentication) throws IOException
    {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauthUser))
        {
            log.error("OAuth2 success handler received an unexpected principal: {}",
                    authentication.getPrincipal().getClass().getName());
            redirectWithError(response,"unsupported_principal");
            return;
        }

        try
        {
            GoogleUserDTO google = googleOAuthService.getGoogleUser(oauthUser);
            String refreshToken = authService.startOAuthSession(google);

            // Set-Cookie on a 302 is honoured before the browser follows Location, so the
            // frontend can call /api/auth/refresh the moment it loads.
            response.addHeader(HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.create(refreshToken).toString());

            getRedirectStrategy().sendRedirect(request,response,successRedirect);
        }
        catch (IllegalStateException ex)
        {
            // Unverified email, missing email, or username exhaustion. Never send the
            // reason detail to the browser beyond a stable, non-descriptive code.
            log.warn("Google sign-in rejected: {}",ex.getMessage());
            redirectWithError(response,"account_not_provisioned");
        }
    }

    private void redirectWithError(HttpServletResponse response,String reason) throws IOException
    {
        String separator = failureRedirect.contains("?") ? "&" : "?";
        response.sendRedirect(failureRedirect + separator + "error="
                + URLEncoder.encode(reason,StandardCharsets.UTF_8));
    }
}
