package ai.snowworks.ariana.orchestrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModuleRegistry {
    private final Map<String, ArianaModule> modules = new LinkedHashMap<>();

    public void register(ArianaModule module) {
        Objects.requireNonNull(module, "module");
        String id = module.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("module id must not be blank");
        }
        if (modules.containsKey(id)) {
            throw new IllegalArgumentException("duplicate module id: " + id);
        }
        modules.put(id, module);
    }

    public List<ArianaModule> resolve(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        List<ArianaModule> result = new ArrayList<>();
        for (ArianaModule module : modules.values()) {
            if (module.capabilities().contains(capability)) {
                result.add(module);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<ArianaModule> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }
}
