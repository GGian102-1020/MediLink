package ar.edu.uade.medlink.security;

import ar.edu.uade.medlink.auth.AccountRepository.Account;
import ar.edu.uade.medlink.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Service
public class JwtTokens {
    public static final long TTL_SECONDS = 30 * 60;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public JwtTokens(@Value("${medlink.jwt.secret-base64}") String encoded, Clock clock) {
        byte[] key;
        try { key = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET_BASE64 debe contener base64 válido"); }
        if (key.length < 32) throw new IllegalStateException("JWT_SECRET_BASE64 requiere al menos 32 bytes aleatorios");
        SecretKey secret = new SecretKeySpec(key, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(secret).macAlgorithm(MacAlgorithm.HS256).build();
        // Normative expiration is exact: do not silently accept 60 s of clock skew.
        nimbus.setJwtValidator(new JwtTimestampValidator(java.time.Duration.ZERO));
        decoder = nimbus;
        this.clock = clock;
    }

    public String create(Account account) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(Long.toString(account.id()))
            .claim("rol", account.rol())
            .claim("tokenVersion", account.tokenVersion())
            .id(UUID.randomUUID().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(TTL_SECONDS)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
    public Jwt verify(String token) {
        try { return decoder.decode(token); }
        catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token inválido, expirado o revocado.");
        }
    }
}
