package ar.edu.uade.medlink.common;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiClockTest {
    @Test void clockUsesBuenosAiresAndMillisecondPrecision() {
        var clock = new ApiClock().clock();
        assertEquals(ApiClock.ZONE, clock.getZone());
        assertEquals(0, clock.instant().getNano() % 1_000_000);
        assertEquals("2026-10-14T13:49:30.123-03:00",
            ApiClock.timestamp(Instant.parse("2026-10-14T16:49:30.123Z")));
    }
}
