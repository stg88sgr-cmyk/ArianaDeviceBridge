package ai.snowworks.ariana.orchestrator;

import java.util.Objects;

public final class ActionPolicy {
    public enum Decision {
        AUTO,
        CONFIRM,
        DENY,
        BLOCK
    }

    public enum Outcome {
        EXECUTOR_PERMITTED,
        PENDING_CONFIRMATION,
        DENIED,
        BLOCKED
    }

    public record Evaluation(
        Decision decision,
        Outcome outcome,
        boolean executorPermit
    ) {}

    public Evaluation evaluate(Decision decision) {
        Objects.requireNonNull(decision, "decision");
        return switch (decision) {
            case AUTO -> new Evaluation(decision, Outcome.EXECUTOR_PERMITTED, true);
            case CONFIRM -> new Evaluation(decision, Outcome.PENDING_CONFIRMATION, false);
            case DENY -> new Evaluation(decision, Outcome.DENIED, false);
            case BLOCK -> new Evaluation(decision, Outcome.BLOCKED, false);
        };
    }
}
