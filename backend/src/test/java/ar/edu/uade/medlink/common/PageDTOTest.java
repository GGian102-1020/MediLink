package ar.edu.uade.medlink.common;

import ar.edu.uade.medlink.common.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageDTOTest {
    @Test void preservesPageEnvelopeContract() {
        var page=PageDTO.from(List.of("a","b"),0,20,21);
        assertEquals(2,page.totalPages());
        assertEquals(21,page.totalElements());
        assertTrue(page.first());
        assertFalse(page.last());
    }
    @Test void commonValidationProtectsNativeAndJpaPaging() {
        PageRequests.validate(0,20);
        for (int[] invalid: new int[][]{{-1,20},{0,0},{0,101},{Integer.MAX_VALUE,2}})
            assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
                ()->PageRequests.validate(invalid[0], invalid[1])).codigo());
    }
}
