package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LifecycleCoordinatorTest {
    @Test
    public void backgroundStopsActiveVoiceAndCameraExactlyOnce() {
        LifecycleCoordinator lifecycle = new LifecycleCoordinator();
        lifecycle.markVoiceActive("voice-session");
        lifecycle.markCameraActive("camera-session");

        List<ArianaEvent> first = lifecycle.transitionTo(LifecycleState.BACKGROUND);
        assertEquals(2, first.size());
        assertEquals(ArianaEventType.VOICE_STOP, first.get(0).type());
        assertEquals(ArianaEventType.CAMERA_STOP, first.get(1).type());
        assertFalse(lifecycle.voiceActive());
        assertFalse(lifecycle.cameraActive());

        List<ArianaEvent> second = lifecycle.transitionTo(LifecycleState.BACKGROUND);
        assertTrue(second.isEmpty());
    }
}
