package ai.snowworks.ariana.orchestrator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ArianaContext {
    private final Map<String, Object> redactedValues = new LinkedHashMap<>();

    public Optional<Object> get(String key) {
        return Optional.ofNullable(redactedValues.get(key));
    }

    public boolean putRedacted(String key, Object value) {
        if (key == null || key.isBlank() || isSecretLike(key)) {
            return false;
        }
        redactedValues.put(key, value);
        return true;
    }

    public Optional<Object> remove(String key) {
        return Optional.ofNullable(redactedValues.remove(key));
    }

    public Map<String, Object> snapshotRedacted() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(redactedValues));
    }

    private static boolean isSecretLike(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("authorization")
            || normalized.contains("cookie");
    }
}
