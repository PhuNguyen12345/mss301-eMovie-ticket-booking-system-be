package fu.mss301.user.application.projection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventIngestionTest {
    @Test
    void invokesEveryHandlerRegisteredForTheEventType() {
        List<String> processed = new ArrayList<>();
        EventIngestion ingestion = new EventIngestion(List.of(
                new RecordingHandler("first", processed),
                new RecordingHandler("second", processed)));

        ingestion.process("event");
        assertEquals(List.of("first:event", "second:event"), processed);

        ingestion.reject("event");
        assertEquals(List.of("first:event", "second:event", "first:rejected", "second:rejected"), processed);
    }

    @Test
    void rejectsUnknownEventType() {
        EventIngestion ingestion = new EventIngestion(List.of(new RecordingHandler("only", new ArrayList<>())));
        assertThrows(IllegalArgumentException.class, () -> ingestion.process(42));
    }

    private record RecordingHandler(String name, List<String> calls) implements ProjectionHandler<String> {
        public Class<String> eventType() { return String.class; }
        public void process(String event) { calls.add(name + ":" + event); }
        public void onRejected(String event) { calls.add(name + ":rejected"); }
    }
}
