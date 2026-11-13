package edu.group17.roommates.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Account authentication and household authorization are future features.
        // Serve the public app shell and health check; keep application APIs closed.
        return http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health", "/", "/index.html", "/assets/**").permitAll()
                .anyRequest().denyAll())
                .build();
    }
}
