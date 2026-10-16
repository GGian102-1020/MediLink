package ar.edu.uade.medlink.security;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.*;
import ar.edu.uade.medlink.farmacia.controller.FarmaciaController;
import ar.edu.uade.medlink.farmacia.service.FarmaciaService;
import ar.edu.uade.medlink.stock.controller.*;
import ar.edu.uade.medlink.stock.service.StockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({FarmaciaController.class,StockController.class,DisponibilidadController.class})
@Import({SecurityConfig.class,BearerAuthenticationFilter.class,ErrorResponses.class,ApiClock.class})
class FarmaciaStockSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean FarmaciaService farmacias;
    @MockitoBean StockService stocks;
    @MockitoBean JwtTokens tokens;
    @MockitoBean AccountRepository accounts;
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String role) {
        var actor=new CurrentAccount(1,role,"test",0);
        return authentication(new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @Test void anonymousCannotReadOrWriteProtectedResources() throws Exception {
        mvc.perform(get("/api/admin/farmacias")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("INVALID_TOKEN"));
        mvc.perform(get("/api/farmacias/1/stock")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/farmacias/1/stock/1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidadDisponible\":3,\"motivo\":\"Conteo\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(farmacias,stocks);
    }
    @Test void userCannotManageAnyPharmacyOrStockResource() throws Exception {
        mvc.perform(get("/api/admin/farmacias").with(as("USUARIO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/farmacias/1/stock").with(as("USUARIO"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/farmacias/1/stock/movimientos").with(as("USUARIO"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/farmacias/1/stock/1").with(as("USUARIO")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidadDisponible\":3,\"motivo\":\"Conteo\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(farmacias,stocks);
    }
    @Test void operatorCannotAdministerPharmacies() throws Exception {
        mvc.perform(get("/api/admin/farmacias").with(as("OPERADOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/farmacias").with(as("OPERADOR")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Central\",\"direccion\":\"Calle 1\"}")).andExpect(status().isForbidden());
        for (String action:List.of("desactivacion","reactivacion"))
            mvc.perform(post("/api/admin/farmacias/1/"+action).with(as("OPERADOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Cambio\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/farmacias/1").with(as("OPERADOR")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Central\",\"direccion\":\"Calle 1\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(farmacias);
    }
    @Test void adminAndOperatorCanReachStockServiceWithAuthenticatedPrincipal() throws Exception {
        for (String role:List.of("ADMINISTRADOR","OPERADOR")) {
            mvc.perform(get("/api/farmacias/1/stock").with(as(role))).andExpect(status().isOk());
            mvc.perform(put("/api/farmacias/1/stock/2").with(as(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cantidadDisponible\":0,\"motivo\":\"Conteo\"}")).andExpect(status().isOk());
        }
        verify(stocks,times(2)).listar(eq(1L),eq(0),eq(20),isNull(),any(CurrentAccount.class));
        verify(stocks,times(2)).ajustar(eq(1L),eq(2L),any(),any(CurrentAccount.class),any());
    }
    @Test void anonymousAvailabilityIsPublic() throws Exception {
        when(stocks.disponibilidad(2)).thenReturn(List.of());
        mvc.perform(get("/api/presentaciones/2/disponibilidad")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void beanValidationRejectsMalformedInputsWithoutCallingService() throws Exception {
        for (String body:List.of("{\"cantidadDisponible\":-1,\"motivo\":\"Conteo\"}",
            "{\"cantidadDisponible\":1,\"motivo\":\"   \"}","{\"motivo\":\"Conteo\"}","{\"cantidadDisponible\":1.2,\"motivo\":\"Conteo\"}",
            "{\"cantidadDisponible\":\"5\",\"motivo\":\"Conteo\"}"))
            mvc.perform(put("/api/farmacias/1/stock/1").with(as("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/admin/farmacias").with(as("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"   \" ,\"direccion\":\"Calle\",\"emailContacto\":\"invalido\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(farmacias,stocks);
    }
}
