package ar.edu.uade.medlink.farmacia.repository;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Reads existing Cuenta/Operador/Reserva tables without introducing duplicate entities. */
@Repository
public class FarmaciaIntegration {
    private final EntityManager em;
    public FarmaciaIntegration(EntityManager em) { this.em=em; }

    public boolean tieneOperadorActivo(long farmaciaId) {
        return (Boolean)em.createNativeQuery("""
            SELECT EXISTS (SELECT 1 FROM operador o JOIN cuenta c ON c.id_cuenta=o.id_cuenta
            WHERE o.id_farmacia=:id AND c.estado='ACTIVA')
            """).setParameter("id",farmaciaId).getSingleResult();
    }
    /** One availability read for an entire page; avoids N extra queries in admin lists. */
    public Set<Long> farmaciasConOperadorActivo(Collection<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        @SuppressWarnings("unchecked")
        List<Number> rows = em.createNativeQuery("""
            SELECT DISTINCT o.id_farmacia FROM operador o
            JOIN cuenta c ON c.id_cuenta=o.id_cuenta
            WHERE o.id_farmacia IN (:ids) AND c.estado='ACTIVA'
            """).setParameter("ids",ids).getResultList();
        Set<Long> active=new HashSet<>();
        for (Number row:rows) active.add(row.longValue());
        return active;
    }
    public boolean tieneReservasActivas(long farmaciaId, Instant t) {
        return (Boolean)em.createNativeQuery("""
            SELECT EXISTS (SELECT 1 FROM reserva WHERE id_farmacia=:id
            AND estado_persistido='ACTIVA' AND fecha_hora_vencimiento>:t)
            """).setParameter("id",farmaciaId).setParameter("t",t).getSingleResult();
    }
}
