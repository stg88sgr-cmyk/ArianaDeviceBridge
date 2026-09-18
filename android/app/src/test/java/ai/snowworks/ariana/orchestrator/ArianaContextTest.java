package ai.snowworks.ariana.orchestrator;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArianaContextTest {
    @Test
    public void secretLikeKeysNeverEnterSnapshot() {
        ArianaContext context = new ArianaContext();
        context.putRedacted("display_name", "Ariana");
        context.putRedacted("providerToken", "must-not-appear");
        context.putRedacted("authorization", "must-not-appear");
        context.putRedacted("session_cookie", "must-not-appear");
        context.putRedacted("passwordHint", "must-not-appear");

        Map<String, Object> snapshot = context.snapshotRedacted();
        assertTrue(snapshot.containsKey("display_name"));
        assertFalse(snapshot.containsKey("providerToken"));
        assertFalse(snapshot.containsKey("authorization"));
        assertFalse(snapshot.containsKey("session_cookie"));
        assertFalse(snapshot.containsKey("passwordHint"));
    }
}
