package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ErrorResponses;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SuspensionAdminController.class)
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class SuspensionAdminSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SuspensionConsultaService service;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;

    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new CurrentAccount(1,role,"jti",0),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }

    @Test void authenticationAndAdministrativeRoleAreRequired() throws Exception {
        mvc.perform(get("/api/admin/usuarios/9/suspensiones"))
            .andExpect(status().isUnauthorized());
        for (String role : List.of("USUARIO","OPERADOR"))
            mvc.perform(get("/api/admin/usuarios/9/suspensiones").with(as(role)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test void usesNormativeAdministrativeRouteAndPagination() throws Exception {
        when(service.listar(eq(9L),isNull(),isNull(),isNull(),eq(0),eq(20),isNull(),any()))
            .thenReturn(PageDTO.from(List.of(),0,20,0));
        mvc.perform(get("/api/admin/usuarios/9/suspensiones").with(as("ADMINISTRADOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        verify(service).listar(eq(9L),isNull(),isNull(),isNull(),eq(0),eq(20),isNull(),any());
    }

    @Test void invalidAndRepeatedParametersGetUniformValidationErrors() throws Exception {
        for (String[] pair : List.of(new String[]{"vigente","nope"},
            new String[]{"desde","2026-10-27"}, new String[]{"size","oops"},
            new String[]{"vigente",""},new String[]{"hasta",""}))
            mvc.perform(get("/api/admin/usuarios/9/suspensiones")
                .with(as("ADMINISTRADOR")).param(pair[0],pair[1]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/admin/usuarios/9/suspensiones")
            .with(as("ADMINISTRADOR")).param("vigente","true","false"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void doesNotExposeRemovedManualCrudEndpoints() throws Exception {
        mvc.perform(post("/api/suspensiones").with(as("ADMINISTRADOR")))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/suspensiones/1/levantar").with(as("ADMINISTRADOR")))
            .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
}
