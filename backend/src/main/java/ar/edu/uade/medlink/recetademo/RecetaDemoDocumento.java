package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.Estado;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.Lectura;
import ar.edu.uade.medlink.recetademo.RecetaDemoDtos.Verificacion;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;

final class RecetaDemoDocumento {
    static final String TITULO="RECETA ELECTRÓNICA SIMULADA";
    static final String LEYENDA="RECETA FICTICIA — SIN VALIDEZ MÉDICA";
    static final String PROFESIONAL="Profesional Demo MediLink";
    static final String INSTITUCION="Centro Académico Ficticio MediLink";
    static final String PACIENTE="Titular pendiente de asignación";
    static final String INDICACIONES="Ejercicio de reserva. No usar como indicación médica.";

    private RecetaDemoDocumento() { }

    static void validarLecturaCompleta(Lectura lectura) {
        if (lectura==null || lectura.estado()!=Estado.COMPLETA || lectura.camposFaltantes()==null
            || !lectura.camposFaltantes().isEmpty() || lectura.documento()==null
            || lectura.medicamentos()==null || lectura.medicamentos().isEmpty()) throw inconclusa();
        var documento=lectura.documento();
        if (!legibles(lectura.codigoReceta(),lectura.fechaEmision(),lectura.fechaVencimiento(),
            documento.titulo(),documento.leyenda(),documento.profesional(),documento.institucion(),
            documento.paciente(),documento.indicaciones())
            || lectura.medicamentos().stream().anyMatch(m -> m==null || m.cantidadAutorizada()==null
                || !legibles(m.medicamento(),m.concentracion(),m.presentacion()))) throw inconclusa();
    }

    static Verificacion verificar(RecetaDemo receta, Lectura lectura) {
        String esperado=receta.codigoVerificacion();
        String leido=lectura.codigoVerificacion();
        if (esperado!=null) {
            if (!legibles(leido)) throw inconclusa();
            if (!esperado.equals(leido))
                throw ApiException.conflict("RECETA_DEMO_CODIGO_VERIFICACION_INCORRECTO",
                    "El código secundario impreso no coincide con el registrado.");
        }
        if (!coincide(receta,lectura) || (esperado==null && leido!=null))
            throw ApiException.conflict("RECETA_DEMO_DATOS_NO_COINCIDEN",
                "Los datos leídos no coinciden con la receta de demostración.");
        return esperado==null?Verificacion.RECETA_VERIFICADA_LEGADA:Verificacion.RECETA_VERIFICADA;
    }

    private static boolean coincide(RecetaDemo receta, Lectura lectura) {
        var documento=lectura.documento();
        if (lectura.medicamentos().size()!=1) return false;
        var item=lectura.medicamentos().getFirst();
        return igual(TITULO,documento.titulo()) && igual(LEYENDA,documento.leyenda())
            && igual(PROFESIONAL,documento.profesional()) && igual(INSTITUCION,documento.institucion())
            && igual(PACIENTE,documento.paciente()) && igual(INDICACIONES,documento.indicaciones())
            && receta.fechaEmision().toString().equals(lectura.fechaEmision())
            && receta.fechaVencimiento().toString().equals(lectura.fechaVencimiento())
            && igual(receta.medicamento(),item.medicamento())
            && concentracionNormalizada(receta.concentracion()).equals(concentracionNormalizada(item.concentracion()))
            && igual(receta.presentacion(),item.presentacion())
            && Integer.valueOf(receta.cantidadAutorizada()).equals(item.cantidadAutorizada());
    }

    private static ApiException inconclusa() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,"LECTURA_INCONCLUSA",
            "No se pudo leer la receta completa. Probá con una imagen más clara o ingresá el código.");
    }

    private static boolean legibles(String... valores) {
        for (String valor:valores) if (valor==null || valor.isBlank()) return false;
        return true;
    }

    private static String concentracionNormalizada(String valor) {
        return Normalizer.normalize(valor,Normalizer.Form.NFC).replace('µ','μ').strip().replaceAll("(?U)\\s+"," ");
    }

    private static boolean igual(String esperado, String leido) {
        return leido!=null && normalizar(esperado).equals(normalizar(leido));
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor,Normalizer.Form.NFKC).strip().replaceAll("(?U)\\s+"," ")
            .toLowerCase(Locale.ROOT);
    }
}
