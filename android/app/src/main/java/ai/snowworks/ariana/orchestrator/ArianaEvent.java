package ai.snowworks.ariana.orchestrator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ArianaEvent {
    private final ArianaEventType type;
    private final String sessionId;
    private final Map<String, Object> payload;
    private final long timestampEpochMs;

    public ArianaEvent(ArianaEventType type, String sessionId, Map<String, ?> payload, long timestampEpochMs) {
        this.type = Objects.requireNonNull(type, "type");
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        this.sessionId = sessionId;
        Map<String, Object> copy = new LinkedHashMap<>();
        if (payload != null) {
            payload.forEach(copy::put);
        }
        this.payload = Collections.unmodifiableMap(copy);
        this.timestampEpochMs = timestampEpochMs;
    }

    public ArianaEventType type() { return type; }
    public String sessionId() { return sessionId; }
    public Map<String, Object> payload() { return payload; }
    public long timestampEpochMs() { return timestampEpochMs; }
}
