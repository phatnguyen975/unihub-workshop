package com.unihub.workshop.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final SecurityProperties properties;
    private final RsaKeyProperties rsaKeyProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final RedisJtiValidator redisJtiValidator;

    public SecurityConfig(
        SecurityProperties properties,
        RsaKeyProperties rsaKeyProperties,
        HandlerExceptionResolver handlerExceptionResolver,
        RedisJtiValidator redisJtiValidator
    ) {
        this.properties = properties;
        this.rsaKeyProperties = rsaKeyProperties;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.redisJtiValidator = redisJtiValidator;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withPublicKey(
            rsaKeyProperties.publicKey()
        ).build();
    }

    @Bean
    public JwtClaimsExtractor jwtClaimsExtractor() {
        return new JwtClaimsExtractor();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtDecoder jwtDecoder,
        JwtClaimsExtractor extractor
    ) {
        return new JwtAuthenticationFilter(
            jwtDecoder,
            extractor,
            redisJtiValidator,
            handlerExceptionResolver
        );
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
        JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
            new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtFilter
    ) throws Exception {
        return http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> {
                if (
                    properties.publicPaths() != null &&
                    !properties.publicPaths().isEmpty()
                ) {
                    auth.requestMatchers(
                        properties.publicPaths().toArray(new String[0])
                    ).permitAll();
                }
                auth.requestMatchers("/api/v1/admin/**").hasRole("ORGANIZER");
                auth.requestMatchers("/api/v1/**").authenticated();
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            )
            .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
            handlerExceptionResolver.resolveException(
                request,
                response,
                null,
                authException
            );
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
            handlerExceptionResolver.resolveException(
                request,
                response,
                null,
                accessDeniedException
            );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
