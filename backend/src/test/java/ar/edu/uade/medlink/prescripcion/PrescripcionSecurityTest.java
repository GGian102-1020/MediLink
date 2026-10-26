package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.*;
import ar.edu.uade.medlink.prescripcion.controller.*;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({PrescripcionAdminController.class,PrescripcionController.class})
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class PrescripcionSecurityTest {
    static final String VALID="{\"codigoExterno\":\"RX-1\",\"usuarioId\":1,\"fechaEmision\":\"2026-10-01\","+
        "\"fechaVencimiento\":\"2026-11-01\",\"items\":[{\"presentacionId\":6,\"cantidadAutorizada\":1}]}";
    @Autowired MockMvc mvc;
    @MockitoBean PrescripcionService service;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        var actor=new CurrentAccount(1,role,"test",0);
        return authentication(new UsernamePasswordAuthenticationToken(actor,null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    // Prescriptions are personal data: never part of the public GET routes.
    @Test void everyRouteRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/prescripciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/prescripciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/prescripciones").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void onlyAdministratorsManagePrescriptions() throws Exception {
        for (String role:List.of("USUARIO","OPERADOR")) {
            mvc.perform(post("/api/admin/prescripciones").with(as(role))
                .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FORBIDDEN"));
            mvc.perform(post("/api/admin/prescripciones/1/anulacion").with(as(role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error\"}"))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/prescripciones").with(as(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void onlyPatientsReadTheirOwnList() throws Exception {
        for (String role:List.of("OPERADOR","ADMINISTRADOR"))
            mvc.perform(get("/api/me/prescripciones").with(as(role))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/prescripciones").with(as("USUARIO"))).andExpect(status().isOk());
        verify(service).listarPropias(eq(0),eq(20),isNull(),any());
        verifyNoMoreInteractions(service);
    }
    @Test void adminRoutesReachTheService() throws Exception {
        mvc.perform(post("/api/admin/prescripciones").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/prescripciones/5/anulacion").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error\"}")).andExpect(status().isOk());
        verify(service).crear(any(),any(),any());
        verify(service).anular(eq(5L),eq("Error"),any(),any());
    }
    @Test void invalidInputUsesUniform400() throws Exception {
        mvc.perform(post("/api/admin/prescripciones").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("\"cantidadAutorizada\":1","\"cantidadAutorizada\":0")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/admin/prescripciones").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content(VALID.replace("[{\"presentacionId\":6,\"cantidadAutorizada\":1}]","[]")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
