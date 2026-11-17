package edu.group17.roommates.config;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
public class FirebaseJwtConfiguration {
    private static final String GOOGLE_KEYS =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.project-id:}") String projectId) {
        // Keep the public shell available without configuration; protected APIs fail closed.
        if (projectId.isBlank()) {
            return token -> { throw new BadJwtException("Authentication is not configured"); };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS)
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(validators(projectId));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validators(String projectId) {
        OAuth2TokenValidator<Jwt> firebaseClaims = jwt -> {
            Instant now = Instant.now();
            Object authTime = jwt.getClaim("auth_time");
            boolean valid = List.of(projectId).equals(jwt.getAudience())
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getSubject().length() <= 128
                    && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(now)
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(now)
                    && authTime instanceof Number timestamp && timestamp.longValue() <= now.getEpochSecond();
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid Firebase ID token claims", null));
        };
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + projectId), firebaseClaims);
    }
}
