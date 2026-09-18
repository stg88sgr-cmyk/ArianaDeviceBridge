package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RoutingPolicyTest {
    private final RoutingPolicy policy = new RoutingPolicy();

    @Test
    public void healthyLocalAlwaysWins() {
        assertEquals(
            RoutingPolicy.Route.LOCAL,
            policy.select(ModuleHealth.HEALTHY, true));
    }

    @Test
    public void offlineSafeDoesNotRequestNetwork() {
        assertEquals(
            RoutingPolicy.Route.OFFLINE_SAFE,
            policy.select(ModuleHealth.UNAVAILABLE, false));
    }
}
