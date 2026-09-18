package ai.snowworks.ariana.orchestrator;

import java.util.Objects;

public final class RoutingPolicy {
    public enum Route {
        LOCAL,
        CONFIGURED_HTTPS,
        OFFLINE_SAFE
    }

    public Route select(ModuleHealth localHealth, boolean httpsFallbackConfigured) {
        Objects.requireNonNull(localHealth, "localHealth");
        if (localHealth == ModuleHealth.HEALTHY) {
            return Route.LOCAL;
        }
        return httpsFallbackConfigured ? Route.CONFIGURED_HTTPS : Route.OFFLINE_SAFE;
    }
}
