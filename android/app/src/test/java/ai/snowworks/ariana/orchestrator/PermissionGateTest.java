package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.assertEquals;

public class PermissionGateTest {
    private final PermissionGate gate = new PermissionGate();

    @Test
    public void missingPermissionRejectsSensorModule() {
        ArianaModule voice = ModuleRegistryTest.module(
            "voice", Set.of(Capability.VOICE_INPUT), Set.of("RECORD_AUDIO"));

        assertEquals(
            PermissionGate.Decision.REJECT_MISSING_PERMISSION,
            gate.evaluate(voice, Set.of(), LifecycleState.FOREGROUND));
    }

    @Test
    public void backgroundRejectsSensorStartEvenWhenPermissionGranted() {
        ArianaModule camera = ModuleRegistryTest.module(
            "camera", Set.of(Capability.CAMERA_INPUT), Set.of("CAMERA"));

        assertEquals(
            PermissionGate.Decision.REJECT_BACKGROUND_SENSOR,
            gate.evaluate(camera, Set.of("CAMERA"), LifecycleState.BACKGROUND));
    }
}
