package ar.edu.uade.medlink.chat;

import static org.junit.jupiter.api.Assertions.*;

import ar.edu.uade.medlink.chat.ChatDtos.EnviarMensajeRequest;
import ar.edu.uade.medlink.common.error.ApiException;
import org.junit.jupiter.api.Test;

class ChatServiceTest {
    @Test void textoSeRecortaYValida() {
        assertEquals("Hola", ChatService.texto(new EnviarMensajeRequest("  Hola \n")));
        for (var vacio : new EnviarMensajeRequest[]{null, new EnviarMensajeRequest(null), new EnviarMensajeRequest("   ")})
            assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class, () -> ChatService.texto(vacio)).codigo());
        assertEquals(1000, ChatService.texto(new EnviarMensajeRequest("a".repeat(1000))).length());
        assertThrows(ApiException.class, () -> ChatService.texto(new EnviarMensajeRequest("a".repeat(1001))));
    }
}
