package com.unihub.workshop.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<com.unihub.workshop.shared.web.RateLimitingFilter> rateLimitingFilterProvider,
            com.unihub.workshop.shared.web.RequestLoggingFilter requestLoggingFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            TemporaryPasswordEnforcementFilter temporaryPasswordEnforcementFilter,
            SecurityExceptionHandler securityExceptionHandler) throws Exception {
        HttpSecurity configuredHttp = http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations", "/api/v1/registrations/*/cancel", "/api/v1/payments").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/registrations/*/ticket").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workshops", "/api/v1/workshops/*").permitAll()
                        .requestMatchers("/api/v1/users/**").authenticated()
                        .requestMatchers("/api/v1/organizations/**").authenticated()
                        .requestMatchers("/api/v1/workshops/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(temporaryPasswordEnforcementFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(requestLoggingFilter, JwtAuthenticationFilter.class);

        rateLimitingFilterProvider.ifAvailable(rateLimitingFilter -> configuredHttp.addFilterBefore(rateLimitingFilter,
                com.unihub.workshop.shared.web.RequestLoggingFilter.class));

        return configuredHttp.build();
    }
}
