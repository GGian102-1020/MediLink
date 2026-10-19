package ar.edu.uade.medlink.security;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.*;
import ar.edu.uade.medlink.medicamento.controller.*;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.presentacion.controller.*;
import ar.edu.uade.medlink.presentacion.service.PresentacionService;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MedicamentoController.class,MedicamentoAdminController.class,
    PresentacionController.class,PresentacionAdminController.class})
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class CatalogoSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean MedicamentoService medicamentos;
    @MockitoBean PresentacionService presentaciones;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        var actor=new CurrentAccount(1,role,"test",0);
        return authentication(new UsernamePasswordAuthenticationToken(actor,null,
            List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @Test void readsArePublicAndMutationsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/medicamentos")).andExpect(status().isOk());
        mvc.perform(get("/api/medicamentos/2/presentaciones")).andExpect(status().isOk());
        mvc.perform(get("/api/presentaciones/3")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/medicamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Nuevo\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/presentaciones").contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isUnauthorized());
        verify(medicamentos).listarPublico(isNull(),eq(0),eq(20),isNull());
        verify(presentaciones).listarPublico(eq(2L),eq(0),eq(20),isNull());
        verify(presentaciones).detallePublico(3L);
        verifyNoMoreInteractions(medicamentos,presentaciones);
    }
    @Test void userAndOperatorCannotModifyCatalog() throws Exception {
        for(String role:List.of("USUARIO","OPERADOR")) {
            mvc.perform(post("/api/admin/medicamentos").with(as(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Nuevo\"}")).andExpect(status().isForbidden());
            // Probar autorización con un body válido: @Valid puede devolver 400 antes
            // de que @PreAuthorize evalúe el rol del usuario.
            mvc.perform(post("/api/admin/presentaciones").with(as(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"medicamentoId\":2,\"concentracionTexto\":\"5 mg\",\"formaFarmaceutica\":\"capsulas\",\"cantidadEnvase\":10,\"unidadExpendio\":\"caja\",\"condicionDispensa\":\"VENTA_LIBRE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FORBIDDEN"));
            mvc.perform(post("/api/admin/presentaciones/1/desactivacion").with(as(role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cambio\"}"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(medicamentos,presentaciones);
    }
    @Test void invalidAdminInputUsesUniform400() throws Exception {
        mvc.perform(post("/api/admin/medicamentos").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/admin/presentaciones").with(as("ADMINISTRADOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"medicamentoId\":2,\"concentracionTexto\":\"5 mg\",\"formaFarmaceutica\":\"capsulas\",\"cantidadEnvase\":0,\"unidadExpendio\":\"caja\",\"condicionDispensa\":\"VENTA_LIBRE\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        verifyNoInteractions(medicamentos,presentaciones);
    }
}
