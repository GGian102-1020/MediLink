package ar.edu.uade.medlink.stock;

import ar.edu.uade.medlink.admin.ReservationClosureService;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.auth.AuthDtos.CuentaPerfilDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.stock.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class StockScopeTest {
    @Test void operatorCanOnlyReadOrWriteOwnPharmacyAndMustHaveActiveAccount() {
        var accounts=mock(AccountRepository.class);
        var integration=new StockIntegration(mock(EntityManager.class),accounts,mock(ReservationClosureService.class),mock(MovimientoStockRepository.class));
        var operator=new CurrentAccount(2,"OPERADOR","test",0);
        when(accounts.perfil(2)).thenReturn(new CuentaPerfilDTO(2,1,"OPERADOR","a@b.test","ACTIVA","A","B",4L));
        assertDoesNotThrow(()->integration.autorizar(4,operator));
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,()->integration.autorizar(5,operator)).codigo());
        when(accounts.perfil(2)).thenReturn(new CuentaPerfilDTO(2,1,"OPERADOR","a@b.test","DESHABILITADA","A","B",4L));
        assertThrows(ApiException.class,()->integration.autorizar(4,operator));
        assertThrows(ApiException.class,()->integration.autorizar(4,new CurrentAccount(3,"USUARIO","test",0)));
    }
}
