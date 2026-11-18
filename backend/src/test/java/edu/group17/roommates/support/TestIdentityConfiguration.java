package edu.group17.roommates.support;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.group17.roommates.config.FirebaseJwtConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/** Test-only signing keys: production never accepts local or unsigned tokens. */
@TestConfiguration(proxyBeanMethods = false)
public class TestIdentityConfiguration {
    public static final String PROJECT = "demo-roommate-expenses";
    private static final RSAKey KEY = key();

    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).generate(); }
        catch (JOSEException e) { throw new IllegalStateException(e); }
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() throws JOSEException {
        var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(FirebaseJwtConfiguration.validators(PROJECT));
        return decoder;
    }

    public static String token(String uid) throws JOSEException {
        return token(uid, Map.of(), false);
    }

    public static String token(String uid, Map<String, Object> overrides, boolean forged) throws JOSEException {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject(uid)
                .issuer("https://securetoken.google.com/" + PROJECT).audience(PROJECT)
                .issueTime(Date.from(now.minusSeconds(10))).expirationTime(Date.from(now.plusSeconds(600)))
                .claim("auth_time", now.minusSeconds(20).getEpochSecond())
                .claim("email", uid + "@example.test").claim("name", "Test user");
        overrides.forEach(claims::claim);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(forged ? key() : KEY));
        return jwt.serialize();
    }
}
