package ai.snowworks.ariana.orchestrator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class PermissionGate {
    public enum Decision {
        ALLOW,
        REJECT_MISSING_PERMISSION,
        REJECT_BACKGROUND_SENSOR
    }

    public Decision evaluate(
        ArianaModule module,
        Set<String> grantedPermissions,
        LifecycleState lifecycle
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(lifecycle, "lifecycle");

        Set<String> granted = grantedPermissions == null
            ? Collections.emptySet()
            : new HashSet<>(grantedPermissions);

        boolean sensorModule = module.capabilities().contains(Capability.VOICE_INPUT)
            || module.capabilities().contains(Capability.CAMERA_INPUT);

        if (sensorModule && lifecycle != LifecycleState.FOREGROUND) {
            return Decision.REJECT_BACKGROUND_SENSOR;
        }

        if (!granted.containsAll(module.requiredPermissions())) {
            return Decision.REJECT_MISSING_PERMISSION;
        }

        return Decision.ALLOW;
    }
}
