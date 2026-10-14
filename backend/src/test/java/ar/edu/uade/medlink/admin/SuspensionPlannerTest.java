package ar.edu.uade.medlink.admin;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuspensionPlannerTest {
    private static final Instant T=Instant.parse("2026-10-01T12:00:00Z");
    private static SuspensionPlanner.Event at(long id, Instant instant) {
        return new SuspensionPlanner.Event(id,instant);
    }
    @Test void boundary30DaysInclusiveAndSchedulingOrderIrrelevant() {
        var e=List.of(at(3,T),at(2,T.minus(Duration.ofDays(2))),at(1,T.minus(Duration.ofDays(30))));
        var planned=SuspensionPlanner.plan(e,null);
        assertEquals(List.of(new SuspensionPlanner.Interval(T,T.plus(Duration.ofDays(7)))),planned);
        assertEquals(planned,SuspensionPlanner.plan(List.of(e.get(2),e.get(0),e.get(1)),null));
    }
    @Test void olderThan30DaysExcludedByOneMillisecond() {
        var e=List.of(at(1,T.minus(Duration.ofDays(30)).minusMillis(1)),at(2,T.minusSeconds(60)),at(3,T));
        assertTrue(SuspensionPlanner.plan(e,null).isEmpty());
    }
    @Test void pendingBacklogMakesMultipleSeparateIntervalsAndDoesNotReuseDuringSuspension() {
        var e=List.of(at(1,T),at(2,T.plusSeconds(1)),at(3,T.plusSeconds(2)),
            at(4,T.plus(Duration.ofDays(3))),at(5,T.plus(Duration.ofDays(7)).plusSeconds(2)),
            at(6,T.plus(Duration.ofDays(7)).plusSeconds(3)),at(7,T.plus(Duration.ofDays(7)).plusSeconds(4)));
        var plan=SuspensionPlanner.plan(e,null);
        assertEquals(2,plan.size());
        assertEquals(T.plusSeconds(2),plan.get(0).from());
        assertEquals(T.plus(Duration.ofDays(7)).plusSeconds(4),plan.get(1).from());
        assertEquals(List.of(plan.get(1)),SuspensionPlanner.plan(e,plan.get(0).to()));
    }
    @Test void exactlyEndOfPreviousSuspensionIsEligible() {
        var end=T.plus(Duration.ofDays(7));
        var events=List.of(at(1,T),at(2,end),at(3,end.plusSeconds(1)),at(4,end.plusSeconds(2)));
        assertEquals(end.plusSeconds(2),SuspensionPlanner.plan(events,end).get(0).from());
    }
}
