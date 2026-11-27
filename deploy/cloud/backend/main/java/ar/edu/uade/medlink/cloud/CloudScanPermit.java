package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.common.error.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Request-thread capability, renewed against the persistent lease immediately before external I/O. */
public final class CloudScanPermit {
    private record Permit(CloudScanBudget budget,UUID owner) { }
    private static final ThreadLocal<Permit> CURRENT=new ThreadLocal<>();
    private CloudScanPermit() { }
    static void bind(CloudScanBudget budget,UUID owner) { CURRENT.set(new Permit(budget,owner)); }
    static void clear() { CURRENT.remove(); }
    public static void beforeProvider() {
        var permit=CURRENT.get();
        if (permit==null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ESCANEO_SIN_PERMISO","El análisis no tiene permiso activo.");
        permit.budget.renew(permit.owner);
    }
}
