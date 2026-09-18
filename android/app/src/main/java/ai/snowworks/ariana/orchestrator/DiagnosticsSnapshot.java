package ai.snowworks.ariana.orchestrator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DiagnosticsSnapshot {
    private final Map<String, ModuleHealth> moduleHealth;
    private final RoutingPolicy.Route routingPath;
    private final PermissionGate.Decision permissionDecision;
    private final ActionPolicy.Decision actionDecision;

    public DiagnosticsSnapshot(
        Map<String, ModuleHealth> moduleHealth,
        RoutingPolicy.Route routingPath,
        PermissionGate.Decision permissionDecision,
        ActionPolicy.Decision actionDecision
    ) {
        this.moduleHealth = Collections.unmodifiableMap(
            new LinkedHashMap<>(moduleHealth == null ? Map.of() : moduleHealth));
        this.routingPath = routingPath;
        this.permissionDecision = permissionDecision;
        this.actionDecision = actionDecision;
    }

    public Map<String, ModuleHealth> moduleHealth() { return moduleHealth; }
    public RoutingPolicy.Route routingPath() { return routingPath; }
    public PermissionGate.Decision permissionDecision() { return permissionDecision; }
    public ActionPolicy.Decision actionDecision() { return actionDecision; }
}
