package ar.edu.uade.medlink.recetaadjunta;

import static org.junit.jupiter.api.Assertions.*;

import ar.edu.uade.medlink.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class RecetaAdjuntaServiceTest {
    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
    }

    @Test void reconoceElTipoPorContenidoYNoPorExtension() {
        assertEquals("application/pdf", RecetaAdjuntaService.tipo("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("image/jpeg", RecetaAdjuntaService.tipo(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}));
        assertEquals("image/png", RecetaAdjuntaService.tipo(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}));
        assertEquals("image/webp", RecetaAdjuntaService.tipo("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
        assertNull(RecetaAdjuntaService.tipo("<html>".getBytes(StandardCharsets.US_ASCII)));
        // Un ejecutable renombrado a .pdf se rechaza.
        var e = assertThrows(ApiException.class, () -> RecetaAdjuntaService.validar(archivo("receta.pdf", "MZ\u0090".getBytes(StandardCharsets.ISO_8859_1))));
        assertEquals("VALIDATION_ERROR", e.codigo());
    }

    @Test void rechazaVacioYDemasiadoGrande() {
        assertThrows(ApiException.class, () -> RecetaAdjuntaService.validar(null));
        assertThrows(ApiException.class, () -> RecetaAdjuntaService.validar(archivo("a.pdf", new byte[0])));
        byte[] grande = new byte[(int) RecetaAdjuntaService.MAX_BYTES + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, grande, 0, 5);
        assertEquals("ARCHIVO_DEMASIADO_GRANDE",
            assertThrows(ApiException.class, () -> RecetaAdjuntaService.validar(archivo("a.pdf", grande))).codigo());
    }

    @Test void normalizaElNombreConLaExtensionDelTipoReal() {
        assertEquals("Receta Dr Pérez.pdf", RecetaAdjuntaService.nombre("C:\\fotos\\Receta Dr Pérez.PDF", "application/pdf"));
        assertEquals("evilscript.jpg", RecetaAdjuntaService.nombre("../../evil<script>.exe", "image/jpeg"));
        assertEquals("receta.png", RecetaAdjuntaService.nombre(null, "image/png"));
        var ok = RecetaAdjuntaService.validar(archivo("scan.jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
        assertEquals("image/jpeg", ok.contentType());
        assertEquals("scan.jpg", ok.nombre());
    }
}
