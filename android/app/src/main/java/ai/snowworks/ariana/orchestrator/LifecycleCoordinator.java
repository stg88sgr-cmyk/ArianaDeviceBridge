package ai.snowworks.ariana.orchestrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class LifecycleCoordinator {
    private LifecycleState state = LifecycleState.FOREGROUND;
    private String voiceSessionId;
    private String cameraSessionId;

    public LifecycleState state() {
        return state;
    }

    public void markVoiceActive(String sessionId) {
        requireSessionId(sessionId);
        if (state != LifecycleState.FOREGROUND) {
            throw new IllegalStateException("voice session requires foreground");
        }
        voiceSessionId = sessionId;
    }

    public void markCameraActive(String sessionId) {
        requireSessionId(sessionId);
        if (state != LifecycleState.FOREGROUND) {
            throw new IllegalStateException("camera session requires foreground");
        }
        cameraSessionId = sessionId;
    }

    public boolean voiceActive() { return voiceSessionId != null; }
    public boolean cameraActive() { return cameraSessionId != null; }

    public List<ArianaEvent> transitionTo(LifecycleState next) {
        if (next == null) {
            throw new IllegalArgumentException("next lifecycle state is required");
        }
        if (next == state) {
            return Collections.emptyList();
        }

        state = next;
        if (next != LifecycleState.BACKGROUND) {
            return Collections.emptyList();
        }

        long now = System.currentTimeMillis();
        List<ArianaEvent> emitted = new ArrayList<>(2);
        if (voiceSessionId != null) {
            emitted.add(new ArianaEvent(
                ArianaEventType.VOICE_STOP, voiceSessionId, Map.of(), now));
            voiceSessionId = null;
        }
        if (cameraSessionId != null) {
            emitted.add(new ArianaEvent(
                ArianaEventType.CAMERA_STOP, cameraSessionId, Map.of(), now));
            cameraSessionId = null;
        }
        return Collections.unmodifiableList(emitted);
    }

    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }
}

enum LifecycleState {
    FOREGROUND,
    BACKGROUND
}
