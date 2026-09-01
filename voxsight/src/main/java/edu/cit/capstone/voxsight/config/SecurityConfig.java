package edu.cit.capstone.voxsight.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable()) // Disable CSRF for OMR stateless API file uploads
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/**", "/outputs/**", "/musicxml/**").permitAll()
                .anyRequest().permitAll() // Permit all for MVP/development simplicity
            );
        return http.build();
    }
}

