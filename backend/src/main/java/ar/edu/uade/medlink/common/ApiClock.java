package ar.edu.uade.medlink.common;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiClock {
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Bean
    Clock clock() {
        return Clock.tickMillis(ZONE);
    }

    public static String timestamp(java.time.Instant instant) {
        return argentina(instant).format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"));
    }

    public static OffsetDateTime argentina(java.time.Instant instant) {
        return instant.atZone(ZONE).toOffsetDateTime();
    }
}
