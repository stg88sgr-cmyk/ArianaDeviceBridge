package com.snowworks.arianax88.verify;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(0, 255, 136);
    private static final int RED = Color.rgb(255, 70, 80);
    private static final int AMBER = Color.rgb(255, 190, 60);
    private static final int DIM = Color.rgb(140, 145, 150);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Deque<String> logs = new ArrayDeque<>();

    private TextView coreChip, bridgeChip, runtimeChip, deviceBlock, proofBlock, jsonBlock, logBlock, title;
    private LinearLayout developerPanel;
    private int titleTaps = 0;
    private boolean polling = false;
    private BridgeProbe lastBridge = new BridgeProbe("UNKNOWN", "not probed", null);
    private SelfProof lastSelf;

    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            if (!polling) return;
            probeAll(false);
            ui.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        appendLog("V24 boot: native activity alive");
        probeAll(true);
    }

    @Override protected void onResume() {
        super.onResume();
        polling = true;
        ui.removeCallbacks(pulse);
        ui.post(pulse);
    }

    @Override protected void onPause() {
        polling = false;
        ui.removeCallbacks(pulse);
        super.onPause();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(4, 5, 8));
        LinearLayout root = column();
        root.setPadding(dp(16), dp(20), dp(16), dp(36));
        scroll.addView(root);

        title = text("ᚨᚱ  ARIANA X-88 // LIVE PULSE V24", 21, GREEN);
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> {
            titleTaps++;
            if (titleTaps >= 7) {
                titleTaps = 0;
                developerPanel.setVisibility(developerPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                appendLog("Developer mode toggled");
            }
        });
        root.addView(title);
        root.addView(text("OBSERVE → DEVICE PROOF → BRIDGE → VERIFY", 11, Color.LTGRAY));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        coreChip = chip("CORE • LIVE", GREEN);
        bridgeChip = chip("BRIDGE • …", DIM);
        runtimeChip = chip("RUNTIME • …", DIM);
        chips.addView(coreChip); chips.addView(bridgeChip); chips.addView(runtimeChip);
        root.addView(chips);

        root.addView(section("DEVICE STATE"));
        deviceBlock = text("reading…", 13, Color.WHITE);
        root.addView(deviceBlock);

        root.addView(section("INSTALLED APK PROOF"));
        proofBlock = text("calculating…", 12, Color.rgb(190, 200, 190));
        proofBlock.setTextIsSelectable(true);
        root.addView(proofBlock);

        Button full = button("ᚨ  RUN FULL VERIFY", v -> probeAll(true));
        full.setTextSize(17);
        root.addView(full);

        root.addView(section("OBSERVED RESULT"));
        jsonBlock = text("{}", 12, Color.rgb(180, 190, 185));
        jsonBlock.setTextIsSelectable(true);
        root.addView(jsonBlock);

        root.addView(section("LIVE LOG"));
        logBlock = text("", 11, Color.LTGRAY);
        logBlock.setTextIsSelectable(true);
        root.addView(logBlock);

        developerPanel = column();
        developerPanel.setVisibility(View.GONE);
        developerPanel.addView(section("DEVELOPER MODE"));
        developerPanel.addView(text("7× Titel schaltet diesen Bereich. Keine Demo-Werte fließen in den Device Proof ein.", 11, DIM));
        developerPanel.addView(button("PROBE BRIDGE NOW", v -> probeBridgeOnly()));
        developerPanel.addView(button("CLEAR LOG", v -> { logs.clear(); renderLogs(); }));
        root.addView(developerPanel);

        root.addView(text("V21 Device State · V22 Bridge Status · V23 Live Logs · V24 Self-Proof + Full Verify", 10, DIM));
        return scroll;
    }

    private void probeAll(boolean explicit) {
        if (explicit) appendLog("Full verify requested");
        io.execute(() -> {
            SelfProof self = buildSelfProof();
            BridgeProbe bridge = probeBridge();
            lastSelf = self;
            lastBridge = bridge;
            ui.post(() -> render(self, bridge));
        });
    }

    private void probeBridgeOnly() {
        appendLog("Bridge probe requested");
        io.execute(() -> {
            BridgeProbe b = probeBridge();
            lastBridge = b;
            ui.post(() -> render(lastSelf != null ? lastSelf : buildSelfProof(), b));
        });
    }

    private SelfProof buildSelfProof() {
        try {
            File apk = new File(getApplicationInfo().sourceDir);
            boolean exists = apk.isFile() && apk.length() > 0;
            String sha = exists ? sha256(apk) : null;
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] sigs = pi.signingInfo != null ? pi.signingInfo.getApkContentsSigners() : new Signature[0];
            String certSha = sigs.length > 0 ? hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())) : null;
            return new SelfProof(exists, apk.length(), sha, certSha, pi.versionName, pi.getLongVersionCode());
        } catch (Exception e) {
            appendLog("Self proof error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return new SelfProof(false, 0, null, null, "unknown", 0);
        }
    }

    private BridgeProbe probeBridge() {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 8765), 900);
            socket.setSoTimeout(900);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /state HTTP/1.1\r\nHost: 127.0.0.1:8765\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String first = r.readLine();
            if (first == null) return new BridgeProbe("REACHABLE", "socket open; empty HTTP response", null);
            int code = parseHttpCode(first);
            StringBuilder body = new StringBuilder();
            String line;
            boolean inBody = false;
            while ((line = r.readLine()) != null) {
                if (inBody) body.append(line).append('\n');
                else if (line.isEmpty()) inBody = true;
            }
            long ms = System.currentTimeMillis() - start;
            String raw = body.toString().trim();
            if (code == 200) {
                appendLog("Bridge observed: HTTP 200 in " + ms + " ms");
                return new BridgeProbe("ONLINE", "HTTP 200 · " + ms + " ms", raw);
            }
            if (code == 401 || code == 403) {
                appendLog("Bridge observed and locked: HTTP " + code + " in " + ms + " ms");
                return new BridgeProbe("LOCKED", "HTTP " + code + " · auth required · " + ms + " ms", raw);
            }
            appendLog("Bridge reachable: HTTP " + code + " in " + ms + " ms");
            return new BridgeProbe("REACHABLE", "HTTP " + code + " · " + ms + " ms", raw);
        } catch (Exception e) {
            appendLog("Bridge offline: " + e.getClass().getSimpleName());
            return new BridgeProbe("OFFLINE", e.getClass().getSimpleName(), null);
        }
    }

    private void render(SelfProof self, BridgeProbe bridge) {
        coreChip.setText("CORE • LIVE"); coreChip.setTextColor(GREEN);
        setChip(bridgeChip, "BRIDGE • " + bridge.state, "ONLINE".equals(bridge.state) ? GREEN : "LOCKED".equals(bridge.state) || "REACHABLE".equals(bridge.state) ? AMBER : RED);

        String runtime = runtimeObservation(bridge.body);
        int runtimeColor = "OBSERVED".equals(runtime) ? GREEN : DIM;
        setChip(runtimeChip, "RUNTIME • " + runtime, runtimeColor);

        deviceBlock.setText(
                Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Android " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT + "\n" +
                "package: " + getPackageName() + "\n" +
                "bridge: " + bridge.detail);

        proofBlock.setText(
                "version: " + self.versionName + " (" + self.versionCode + ")\n" +
                "apk_observed: " + self.apkObserved + "\n" +
                "apk_bytes: " + self.bytes + "\n" +
                "apk_sha256: " + safe(self.apkSha256) + "\n" +
                "signer_sha256: " + safe(self.signerSha256));

        boolean selfPass = self.apkObserved && self.apkSha256 != null && self.signerSha256 != null;
        String overall = selfPass ? "PASS" : "STOP";
        String next = selfPass ? "ᛚᛗ LEARN" : "STOP";
        try {
            JSONObject j = new JSONObject();
            j.put("protocol", "X88_DEVICE_VERIFY_V24");
            j.put("status", overall);
            j.put("next", next);
            j.put("core", "LIVE");
            j.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            j.put("android_sdk", Build.VERSION.SDK_INT);
            j.put("version", self.versionName);
            j.put("installed_apk_observed", self.apkObserved);
            j.put("installed_apk_sha256", self.apkSha256 == null ? JSONObject.NULL : self.apkSha256);
            j.put("signer_sha256", self.signerSha256 == null ? JSONObject.NULL : self.signerSha256);
            j.put("bridge", bridge.state);
            j.put("bridge_detail", bridge.detail);
            j.put("runtime", runtime);
            j.put("bridge_required_for_self_pass", false);
            jsonBlock.setText(j.toString(2));
            jsonBlock.setTextColor(selfPass ? GREEN : RED);
        } catch (Exception e) {
            jsonBlock.setText("JSON error: " + e.getMessage());
        }
        renderLogs();
    }

    private String runtimeObservation(String body) {
        if (body == null || body.isEmpty()) return "UNPROVEN";
        String l = body.toLowerCase(Locale.ROOT);
        return (l.contains("runtime") || l.contains("termux") || l.contains("tmux")) ? "OBSERVED" : "UNPROVEN";
    }

    private void appendLog(String line) {
        synchronized (logs) {
            if (logs.size() >= 14) logs.removeFirst();
            logs.addLast(String.format(Locale.ROOT, "%1$tH:%1$tM:%1$tS  %2$s", System.currentTimeMillis(), line));
        }
        ui.post(this::renderLogs);
    }

    private void renderLogs() {
        if (logBlock == null) return;
        StringBuilder b = new StringBuilder();
        synchronized (logs) { for (String s : logs) b.append(s).append('\n'); }
        logBlock.setText(b.toString());
    }

    private String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return sb.toString();
    }

    private static int parseHttpCode(String line) {
        try { String[] p = line.split(" "); return p.length > 1 ? Integer.parseInt(p[1]) : -1; }
        catch (Exception e) { return -1; }
    }

    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, int sp, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setPadding(0, dp(8), 0, dp(8)); return v; }
    private TextView section(String s) { TextView v = text(s, 11, DIM); v.setPadding(0, dp(18), 0, dp(4)); return v; }
    private TextView chip(String s, int color) { TextView v = text(s, 10, color); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)); return v; }
    private void setChip(TextView v, String s, int color) { v.setText(s); v.setTextColor(color); }
    private Button button(String label, View.OnClickListener l) { Button b = new Button(this); b.setText(label); b.setOnClickListener(l); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String safe(String s) { return s == null ? "null" : s; }

    private static class SelfProof {
        final boolean apkObserved; final long bytes; final String apkSha256; final String signerSha256; final String versionName; final long versionCode;
        SelfProof(boolean a, long b, String s, String c, String v, long vc) { apkObserved=a; bytes=b; apkSha256=s; signerSha256=c; versionName=v; versionCode=vc; }
    }
    private static class BridgeProbe {
        final String state; final String detail; final String body;
        BridgeProbe(String s, String d, String b) { state=s; detail=d; body=b; }
    }
}
