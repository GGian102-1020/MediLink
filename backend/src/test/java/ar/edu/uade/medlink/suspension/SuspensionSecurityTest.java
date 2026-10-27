package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ErrorResponses;
import ar.edu.uade.medlink.security.*;
import ar.edu.uade.medlink.suspension.controller.SuspensionController;
import ar.edu.uade.medlink.suspension.service.SuspensionService;
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

@WebMvcTest(SuspensionController.class)
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class SuspensionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SuspensionService service;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;

    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(new CurrentAccount(1,role,"test",0),null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }

    @Test void onlyAdminCanConsultAndLegacyMutationsAreUnavailable() throws Exception {
        String path="/api/admin/usuarios/15/suspensiones";
        when(service.listar(eq(15L),isNull(),isNull(),isNull(),eq(0),eq(20),isNull(),any()))
            .thenReturn(PageDTO.from(List.of(),0,20,0));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role:List.of("USUARIO","OPERADOR"))
            mvc.perform(get(path).with(as(role))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(as("ADMINISTRADOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/suspensiones").with(as("ADMINISTRADOR")))
            .andExpect(status().isNotFound());
        mvc.perform(patch("/api/suspensiones/1/levantar").with(as("ADMINISTRADOR")))
            .andExpect(status().isNotFound());
        verify(service,times(1)).listar(eq(15L),isNull(),isNull(),isNull(),eq(0),eq(20),isNull(),any());
    }

    @Test void invalidFiltersDoNotReachService() throws Exception {
        String path="/api/admin/usuarios/15/suspensiones";
        for (String[] invalid:List.of(new String[]{"vigente","nope"},new String[]{"desde","2026-10-27"},
            new String[]{"vigente",""})) {
            mvc.perform(get(path).with(as("ADMINISTRADOR")).param(invalid[0],invalid[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        }
        mvc.perform(get(path).with(as("ADMINISTRADOR")).param("vigente","true","false"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
