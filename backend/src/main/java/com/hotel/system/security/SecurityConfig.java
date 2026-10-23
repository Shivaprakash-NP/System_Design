package com.hotel.system.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimiterFilter rateLimiterFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, RateLimiterFilter rateLimiterFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimiterFilter = rateLimiterFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // Allow all CORS preflight requests without authentication
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/search/**",
                    "/api/v1/hotels/**",
                    "/api/v1/rooms/**",
                    "/api/v1/crawler/**",
                    "/api/v1/system-design/**",
                    "/api/v1/short-url/**",
                    "/r/**",
                    "/actuator/**",
                    "/error"
                ).permitAll()
                // Role-restricted endpoints
                .requestMatchers("/api/v1/housekeeping/**").hasAnyAuthority("HOUSEKEEPING", "ADMIN")
                .requestMatchers("/api/v1/reception/**").hasAnyAuthority("RECEPTIONIST", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings").hasAnyAuthority("RECEPTIONIST", "ADMIN")
                .requestMatchers("/api/v1/bookings/*/check-in").hasAnyAuthority("RECEPTIONIST", "ADMIN")
                .requestMatchers("/api/v1/billing/checkout/**").hasAnyAuthority("RECEPTIONIST", "ADMIN")
                .requestMatchers("/api/v1/billing/invoices").hasAnyAuthority("RECEPTIONIST", "ADMIN")
                .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")
                // Authenticated endpoints (bookings, billing, notifications)
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimiterFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow any origin pattern (supports credentials while permitting all localhost/IP variations)
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Total-Count",
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Latency-Ms", "Retry-After"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public org.springframework.web.filter.CorsFilter corsFilter() {
        return new org.springframework.web.filter.CorsFilter(corsConfigurationSource());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
