package com.android.server.x88;

import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import com.android.server.SystemService;

import de.snowworks.x88.IX88SystemService;

/**
 * Reference SystemService skeleton for AOSP integration.
 * This file is not part of the app build and does not claim device integration.
 */
public final class X88SystemService extends SystemService {
    public static final String SERVICE_NAME = "x88_system";
    public static final int PROTOCOL_VERSION = 1;
    public static final String ACCESS_PERMISSION = "de.snowworks.permission.X88_SYSTEM_ACCESS";

    private final BinderService binderService = new BinderService();

    public X88SystemService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        publishBinderService(SERVICE_NAME, binderService);
    }

    private void enforceAccess() {
        getContext().enforceCallingOrSelfPermission(
                ACCESS_PERMISSION,
                "X88 system access requires platform signature permission");
    }

    private final class BinderService extends IX88SystemService.Stub {
        @Override
        public String getServiceName() {
            enforceAccess();
            return SERVICE_NAME;
        }

        @Override
        public int getProtocolVersion() {
            enforceAccess();
            return PROTOCOL_VERSION;
        }

        @Override
        public String getPlatformCertificateSha256() {
            enforceAccess();
            return "UNCONFIGURED";
        }

        @Override
        public String getStateSnapshot() {
            enforceAccess();
            return "{}";
        }

        @Override
        public String startSession() {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public boolean stopSession(String sessionId) {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public void setMasterEnabled(boolean enabled) {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public void setCapabilityEnabled(String capability, boolean enabled) {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public String submitCommand(String commandJson) {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public void emergencyStop() {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }

        @Override
        public void clearEmergencyStop() {
            enforceAccess();
            throw new UnsupportedOperationException("Reference scaffold only");
        }
    }
}
