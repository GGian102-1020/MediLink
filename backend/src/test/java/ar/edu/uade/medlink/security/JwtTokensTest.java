package ar.edu.uade.medlink.security;

import static org.junit.jupiter.api.Assertions.*;
import ar.edu.uade.medlink.auth.AccountRepository.Account;
import ar.edu.uade.medlink.common.error.ApiException;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokensTest {
    @Test void emitsSignedShortLivedTokenWithRequiredClaimsAndRejectsTampering() {
        byte[] secret=new byte[32]; java.util.Arrays.fill(secret,(byte) 93);
        var tokens=new JwtTokens(Base64.getEncoder().encodeToString(secret),Clock.systemUTC());
        var value=tokens.create(new Account(30,"user@test.com","hash","USUARIO","ACTIVA",4));
        var decoded=tokens.verify(value);
        assertEquals("30",decoded.getSubject());
        assertEquals("USUARIO",decoded.getClaimAsString("rol"));
        assertEquals(4,((Number)decoded.getClaim("tokenVersion")).intValue());
        assertNotNull(decoded.getId());
        assertEquals(JwtTokens.TTL_SECONDS, decoded.getExpiresAt().getEpochSecond()-decoded.getIssuedAt().getEpochSecond());
        assertThrows(ApiException.class,()->tokens.verify(value.substring(0,value.length()-5)+"AAAAA"));
    }
    @Test void refusesMissingOrShortSecret() {
        assertThrows(IllegalStateException.class,()->new JwtTokens("",Clock.systemUTC()));
        assertThrows(IllegalStateException.class,()->new JwtTokens(Base64.getEncoder().encodeToString(new byte[16]),Clock.systemUTC()));
    }
}
