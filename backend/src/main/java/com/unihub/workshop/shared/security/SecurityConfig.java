package com.unihub.workshop.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<com.unihub.workshop.shared.web.RateLimitingFilter> rateLimitingFilterProvider,
            com.unihub.workshop.shared.web.RequestLoggingFilter requestLoggingFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        HttpSecurity configuredHttp = http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestLoggingFilter, JwtAuthenticationFilter.class);

        rateLimitingFilterProvider.ifAvailable(rateLimitingFilter -> configuredHttp.addFilterBefore(rateLimitingFilter,
                com.unihub.workshop.shared.web.RequestLoggingFilter.class));

        return configuredHttp.build();
    }
}
