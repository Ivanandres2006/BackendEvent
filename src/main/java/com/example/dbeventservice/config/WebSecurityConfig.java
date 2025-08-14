package com.example.dbeventservice.config;

import com.example.dbeventservice.auth.jwt.AuthEntryPointJwt;
import com.example.dbeventservice.auth.jwt.AuthTokenFilter;
import com.example.dbeventservice.service.UserDetailServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    @Autowired
    private UserDetailServiceImpl userDetailsService;

    @Autowired
    private AuthEntryPointJwt unauthorizedHandler;

    @Autowired
    private AuthTokenFilter authTokenFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedHandler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/login",
                                "/auth/register",
                                "/auth/verify",
                                "/auth/resend",
                                "/auth/forgot-password",
                                "/auth/verify-reset-code",
                                "/auth/reset-password",
                                "/auth/apple-login",
                                "/auth/google-login",
                                "/auth/set-password",
                                "/events/upload-image",
                                "/auth/refresh",
                                "/uploads/**",
                                "/qr/**",
                                "/api/payments/webhook",
                                "/api/payments/reunion-checkout",
                                "/api/payments/confirm-reunion-paid",
                                "/api/chat/image/**",
                                "/api/notifications/register",
                                "/api/notifications/send-test",
                                "/ping",
                                "/api/waitlist",
                                "/test-email",
                                "/api/tickets/my",
                                "/api/tickets/validate/{ticketId}",
                                "/api/passes/ticket/{ticketId}",
                                "/resale/available",
                                "/events/{eventId}/waitlist/request",
                                "/events/{eventId}/waitlist/{entryId}/status",
                                "/events/{eventId}/waitlist",
                                "/api/attendance/events/*/going"
                        ).permitAll()
                        .requestMatchers("/auth/validate").authenticated()
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
