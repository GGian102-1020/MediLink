package ar.edu.uade.medlink.auditoria;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.common.error.*;
import ar.edu.uade.medlink.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditoriaController.class)
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class AuditoriaSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuditService audit;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(new CurrentAccount(1,role,"test",0),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @Test void onlyAdministratorCanReadHistory() throws Exception {
        when(audit.listar(any(),eq(0),eq(20),isNull(),any())).thenReturn(PageDTO.from(List.of(),0,20,0));
        mvc.perform(get("/api/admin/auditoria").with(as("ADMINISTRADOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        for (String role:List.of("OPERADOR","USUARIO"))
            mvc.perform(get("/api/admin/auditoria").with(as(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FORBIDDEN"));
        verify(audit,times(1)).listar(any(),eq(0),eq(20),isNull(),any());
    }
    @Test void missingAndInvalidJwtHaveUniformCorrelatedErrors() throws Exception {
        String id="ee029275-9016-4f5c-af97-31514e4fd34c";
        mvc.perform(get("/api/admin/auditoria").header("X-Correlation-Id",id))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("INVALID_TOKEN"))
            .andExpect(jsonPath("$.correlationId").value(id)).andExpect(header().string("X-Correlation-Id",id))
            .andExpect(jsonPath("$.timestamp").value(org.hamcrest.Matchers.matchesPattern(".*\\.\\d{3}-03:00")));
        when(tokens.verify("invalid")).thenThrow(new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
            "INVALID_TOKEN","Token inválido."));
        mvc.perform(get("/api/admin/auditoria").header("Authorization","Bearer invalid"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("INVALID_TOKEN"))
            .andExpect(jsonPath("$.correlationId").isString());
        verifyNoInteractions(audit,accounts);
    }
    @Test void malformedAndEmptyFiltersUseFunctionalValidationErrors() throws Exception {
        for (String[] invalid:List.of(new String[]{"usuarioId","abc"},new String[]{"desde","2026-10-01"},
            new String[]{"usuarioId",""},new String[]{"rol","INVALIDO"},new String[]{"accion",""},
            new String[]{"tipoEntidad","DESCONOCIDA"},new String[]{"entidadId","0"}))
            mvc.perform(get("/api/admin/auditoria").with(as("ADMINISTRADOR")).param(invalid[0],invalid[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.mensaje").isString()).andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.correlationId").isString());
        mvc.perform(get("/api/admin/auditoria").with(as("ADMINISTRADOR")).param("usuarioId","1","2"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(audit);
    }
}
