package ai.snowworks.ariana.orchestrator;

import java.util.Set;

public interface ArianaModule {
    String id();
    Set<Capability> capabilities();
    Set<String> requiredPermissions();
    ModuleHealth health();
    void handle(ArianaEvent event, ArianaContext context);
}
