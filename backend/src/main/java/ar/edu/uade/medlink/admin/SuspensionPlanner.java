package ar.edu.uade.medlink.admin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Pure RN-31..36 planner. Input order does not change the intervals. */
public final class SuspensionPlanner {
    private SuspensionPlanner() { }
    public record Event(long id, Instant expires) { }
    public record Interval(Instant from, Instant to) { }

    public static List<Interval> plan(List<Event> events, Instant lastPersistedEnd) {
        var sorted=events.stream().sorted((a,b) -> {
            int byExpiry=a.expires().compareTo(b.expires());
            return byExpiry!=0 ? byExpiry : Long.compare(a.id(),b.id());
        }).toList();
        ArrayDeque<Event> window=new ArrayDeque<>();
        List<Interval> results=new ArrayList<>();
        Instant cursor=lastPersistedEnd;
        for (Event event : sorted) {
            Instant e=event.expires();
            if (cursor!=null && e.isBefore(cursor)) continue;
            while (!window.isEmpty() && Duration.between(window.peekFirst().expires(),e)
                    .compareTo(Duration.ofDays(30))>0) window.removeFirst();
            window.addLast(event);
            if (window.size()==3) {
                Instant end=e.plus(Duration.ofDays(7));
                results.add(new Interval(e,end));
                cursor=end;
                window.clear();
            }
        }
        return List.copyOf(results);
    }
}
