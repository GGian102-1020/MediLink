package ar.edu.uade.medlink.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.auth.AccountRepository.Account;
import ar.edu.uade.medlink.common.error.ErrorResponses;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class BearerAuthenticationFilterTest {
    private final JwtTokens tokens=mock(JwtTokens.class);
    private final AccountRepository accounts=mock(AccountRepository.class);
    private final BearerAuthenticationFilter filter=new BearerAuthenticationFilter(tokens,accounts,
        new ErrorResponses(Clock.systemUTC()));
    private Jwt validJwt() {
        return Jwt.withTokenValue("jwt").header("alg","HS256").subject("10")
            .claim("rol","USUARIO").claim("jti","jti1").claim("tokenVersion",3)
            .issuedAt(Instant.now().minusSeconds(30)).expiresAt(Instant.now().plusSeconds(900)).build();
    }
    private MockHttpServletResponse getResponse() throws Exception {
        MockHttpServletRequest req=new MockHttpServletRequest("GET","/api/me");
        req.addHeader("Authorization","Bearer jwt");
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(req,response,(r,s)->{});
        return response;
    }
    @Test void publicCatalogIsAccessibleWithoutValidJwtEvenIfBrowserSendsExpiredToken() throws Exception {
        String[] paths = {"/api/health", "/api/medicamentos",
            "/api/medicamentos/12/presentaciones", "/api/presentaciones/4",
            "/api/presentaciones/4/disponibilidad"};
        for (String path: paths) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET",path);
            req.addHeader("Authorization","Bearer expired");
            assertTrue(filter.shouldNotFilter(req),path);
        }
        var protectedRequest = new MockHttpServletRequest("GET","/api/me");
        assertFalse(filter.shouldNotFilter(protectedRequest));
    }
    @Test void tokenVersionChangeInvalidatesPreviouslySignedJwt() throws Exception {
        when(tokens.verify("jwt")).thenReturn(validJwt());
        when(accounts.byId(10L)).thenReturn(Optional.of(new Account(10,"user@t.test","h","USUARIO","ACTIVA",4)));
        var response=getResponse();
        assertEquals(401,response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_TOKEN"));
    }
    @Test void disabledAccountIs403EvenWithValidSignature() throws Exception {
        when(tokens.verify("jwt")).thenReturn(validJwt());
        when(accounts.byId(10L)).thenReturn(Optional.of(new Account(10,"user@t.test","h","USUARIO","DESHABILITADA",3)));
        var response=getResponse();
        assertEquals(403,response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_DISABLED"));
    }
    @Test void revokedJtiIsNeverAccepted() throws Exception {
        when(tokens.verify("jwt")).thenReturn(validJwt());
        when(accounts.byId(10L)).thenReturn(Optional.of(new Account(10,"user@t.test","h","USUARIO","ACTIVA",3)));
        when(accounts.isRevoked("jti1")).thenReturn(true);
        assertEquals(401,getResponse().getStatus());
    }
    @Test void healthyAuthenticatedRequestPopulatesRoleAndAccountId() throws Exception {
        when(tokens.verify("jwt")).thenReturn(validJwt());
        when(accounts.byId(10L)).thenReturn(Optional.of(new Account(10,"user@t.test","h","USUARIO","ACTIVA",3)));
        MockHttpServletRequest req=new MockHttpServletRequest("GET","/api/me");
        req.addHeader("Authorization","Bearer jwt");
        var resp=new MockHttpServletResponse();
        AtomicBoolean visited=new AtomicBoolean(false);
        filter.doFilter(req,resp,(r,s)-> {
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            assertEquals(10L,((CurrentAccount)auth.getPrincipal()).id());
            assertEquals("ROLE_USUARIO",auth.getAuthorities().iterator().next().getAuthority());
            visited.set(true);
        });
        assertTrue(visited.get());
        assertEquals(200,resp.getStatus());
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }
}
