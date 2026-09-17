package de.snowworks.x88;

/**
 * Reference Binder contract for a future platform-owned X88 service.
 * Every implementation must enforce caller identity before mutating state.
 */
interface IX88SystemService {
    String getServiceName();
    int getProtocolVersion();
    String getPlatformCertificateSha256();
    String getStateSnapshot();
    String startSession();
    boolean stopSession(String sessionId);
    void setMasterEnabled(boolean enabled);
    void setCapabilityEnabled(String capability, boolean enabled);
    String submitCommand(String commandJson);
    void emergencyStop();
    void clearEmergencyStop();
}
