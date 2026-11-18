package edu.group17.roommates.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirebaseJwtConfigurationTest {
    @Test
    void missingProjectFailsClosedWithoutFetchingKeys() {
        var decoder = new FirebaseJwtConfiguration().jwtDecoder("");
        assertThrows(BadJwtException.class, () -> decoder.decode("any-token"));
    }
}
