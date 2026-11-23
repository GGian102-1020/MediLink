package ar.edu.uade.medlink.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Borra cada hora el historial de las conversaciones cerradas hace más que medlink.chat.retencion-dias. */
@Component
@ConditionalOnProperty(name = "medlink.chat.purga.enabled", havingValue = "true", matchIfMissing = true)
class ChatPurgaScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ChatPurgaScheduler.class);
    private final ChatService service;
    ChatPurgaScheduler(ChatService service) { this.service = service; }

    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    void purgar() {
        try {
            int borrados = service.purgarConversacionesCerradas();
            if (borrados > 0) LOG.info("Chat: {} mensajes de conversaciones cerradas borrados", borrados);
        } catch (RuntimeException ex) {
            // Sin detalles: el mensaje podría incluir SQL o datos personales.
            LOG.warn("Purga de chat pendiente: tipo={}", ex.getClass().getSimpleName());
        }
    }
}
