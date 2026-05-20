package edu.cit.capstone.voxsight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for OMR file uploads
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/convert", "/outputs/**").permitAll()
                .anyRequest().permitAll() // Permit everything for MVP/development simplicity
            );
        return http.build();
    }
}
