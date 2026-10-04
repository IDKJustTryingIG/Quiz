package com.abhi.quizbackend.config;

import com.abhi.quizbackend.security.filters.JwtAuthenticationFilter;
import com.abhi.quizbackend.security.jwt.JwtUtil;
import com.abhi.quizbackend.security.oauth2.CustomOAuth2FailureHandler;
import com.abhi.quizbackend.security.oauth2.CustomOAuth2SuccessHandler;
import com.abhi.quizbackend.security.service.CustomUserDetailsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Clock;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtUtil jwtUtil,
                                                   CustomUserDetailsService userDetailsService,
                                                   AuthenticationEntryPoint authenticationEntryPoint,
                                                   AccessDeniedHandler accessDeniedHandler,
                                                   CustomOAuth2SuccessHandler oAuth2SuccessHandler,
                                                   CustomOAuth2FailureHandler oAuth2FailureHandler,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception
    {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(req -> req
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        // Google redirect endpoints handled by Spring itself.
                        .requestMatchers("/oauth2/**","/login/oauth2/**").permitAll()
                        // Default deny: a new endpoint is protected unless listed above.
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The refresh cookie is SameSite-restricted and scoped to /api/auth, and
                // every state-changing API call is authorised by a bearer token rather
                // than by the cookie, so there is no ambient credential to forge with.
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtUtil,userDetailsService,authenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class);

        // Only wire Google sign-in when credentials are configured. Boot creates a
        // ClientRegistrationRepository only if a registration exists, and calling
        // oauth2Login() without one fails startup -- which would make GOOGLE_CLIENT_ID
        // mandatory just to run the app locally.
        if (clientRegistrations.getIfAvailable() != null)
        {
            http.oauth2Login(oauth -> oauth
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler));
        }

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder)
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Compares against a dummy hash when the user is missing, so a non-existent
        // account costs the same time as a wrong password.
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    /**
     * Delegating encoder: hashes new passwords with BCrypt and stores an algorithm
     * prefix, so existing hashes stay verifiable if the algorithm is changed later.
     */
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public Clock clock()
    {
        return Clock.systemUTC();
    }
}
