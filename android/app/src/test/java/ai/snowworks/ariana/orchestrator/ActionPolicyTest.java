package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ActionPolicyTest {
    @Test
    public void confirmDenyBlockExposeNoExecutorPermit() {
        ActionPolicy policy = new ActionPolicy();

        for (ActionPolicy.Decision decision : new ActionPolicy.Decision[] {
            ActionPolicy.Decision.CONFIRM,
            ActionPolicy.Decision.DENY,
            ActionPolicy.Decision.BLOCK
        }) {
            ActionPolicy.Evaluation evaluation = policy.evaluate(decision);
            assertFalse(evaluation.executorPermit());
        }

        assertEquals(
            ActionPolicy.Outcome.PENDING_CONFIRMATION,
            policy.evaluate(ActionPolicy.Decision.CONFIRM).outcome());
    }
}
