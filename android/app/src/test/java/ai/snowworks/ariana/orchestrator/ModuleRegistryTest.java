package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import java.util.Map;
import java.util.Set;

public class ModuleRegistryTest {
    @Test(expected = IllegalArgumentException.class)
    public void duplicateIdFailsFast() {
        ModuleRegistry registry = new ModuleRegistry();
        ArianaModule module = module("presence", Set.of(Capability.PRESENCE_SIGNAL), Set.of());
        registry.register(module);
        registry.register(module);
    }

    static ArianaModule module(String id, Set<Capability> capabilities, Set<String> permissions) {
        return new ArianaModule() {
            public String id() { return id; }
            public Set<Capability> capabilities() { return capabilities; }
            public Set<String> requiredPermissions() { return permissions; }
            public ModuleHealth health() { return ModuleHealth.HEALTHY; }
            public void handle(ArianaEvent event, ArianaContext context) {}
        };
    }
}
