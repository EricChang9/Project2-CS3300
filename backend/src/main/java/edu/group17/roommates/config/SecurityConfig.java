package edu.group17.roommates.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Credentials are explicit bearer headers, never browser cookies/sessions.
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/health", "/", "/index.html", "/assets/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/households").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/households/*/expenses", "/api/households/*/expenses/*",
                        "/api/households/*/balances").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/households").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/households/*/expenses").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/households/*/settlements").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/households/*/invitations", "/api/invitations/redeem").authenticated()
                .anyRequest().denyAll())
                .build();
    }
}
