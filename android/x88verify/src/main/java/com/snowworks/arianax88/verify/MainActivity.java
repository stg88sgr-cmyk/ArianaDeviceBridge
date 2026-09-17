package com.snowworks.arianax88.verify;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private EditText generation;
    private Spinner buildStatus;
    private EditText exitCode;
    private Spinner artifactVerified;
    private EditText buildLog;
    private EditText passed;
    private EditText failed;
    private Spinner testStatus;
    private EditText testLog;
    private TextView verdict;
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadPassExample();
        verify();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(5, 5, 8));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(root);

        TextView title = text("ᚨᚱ  ARIANA X-88 // VERIFY V20", 20, Color.rgb(0,255,136));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);
        root.addView(text("CONTROL → CREATION → VERIFY → REPAIR / LEARN", 11, Color.LTGRAY));

        generation = input("Generation", "gen_x88_v20_0001", false); root.addView(generation);
        buildStatus = spinner(new String[]{"SUCCESS","FAILED","UNKNOWN","NOT_RUN"}); root.addView(buildStatus);
        exitCode = input("Build exit code", "0", true); root.addView(exitCode);
        artifactVerified = spinner(new String[]{"true","false","unknown"}); root.addView(artifactVerified);
        buildLog = multiline("Build log", 5); root.addView(buildLog);
        passed = input("Tests passed", "12", true); root.addView(passed);
        failed = input("Tests failed", "0", true); root.addView(failed);
        testStatus = spinner(new String[]{"PASS","FAIL","UNKNOWN"}); root.addView(testStatus);
        testLog = multiline("Test log", 4); root.addView(testLog);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button pass = button("EX PASS", v -> { loadPassExample(); verify(); });
        Button repair = button("EX REPAIR", v -> { loadRepairExample(); verify(); });
        Button stop = button("EX STOP", v -> { loadStopExample(); verify(); });
        buttons.addView(pass); buttons.addView(repair); buttons.addView(stop);
        root.addView(buttons);

        Button run = button("ᛋᛖ  VERIFY", v -> verify());
        run.setTextSize(16);
        root.addView(run);

        verdict = text("", 18, Color.WHITE);
        verdict.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(verdict);

        output = text("", 12, Color.rgb(184,192,184));
        output.setTextIsSelectable(true);
        root.addView(output);

        root.addView(text("BEWEISREGEL: fehlende echte Build-Belege → STOP · Fehler → REPAIR · nur SUCCESS + exit 0 + artifact true + 12/12 → LEARN", 11, Color.GRAY));
        return scroll;
    }

    private void verify() {
        String gen = value(generation, "gen_unknown");
        String build = (String) buildStatus.getSelectedItem();
        Integer exit = parseNullableInt(exitCode.getText().toString());
        Boolean artifact = parseTriBool((String) artifactVerified.getSelectedItem());
        String bLog = buildLog.getText().toString().trim();
        int p = parseInt(passed.getText().toString(), 0);
        int f = parseInt(failed.getText().toString(), 0);
        String tests = (String) testStatus.getSelectedItem();
        String tLog = testLog.getText().toString().trim();

        JSONArray findings = new JSONArray();
        JSONArray repairs = new JSONArray();
        String status;
        String next;

        boolean missing = "UNKNOWN".equals(build) || "NOT_RUN".equals(build) || exit == null || artifact == null || bLog.isEmpty();
        if (missing) {
            status = "FAIL";
            next = "STOP";
            findings.put("BUILD_AND_TEST_RESULTS_NOT_OBSERVED");
            if ("UNKNOWN".equals(build) || "NOT_RUN".equals(build)) findings.put("missing: status=" + build);
            if (exit == null) findings.put("missing: exit_code=null");
            if (artifact == null) findings.put("missing: artifact=null");
            if (bLog.isEmpty()) findings.put("missing: log=null");
        } else {
            String lower = bLog.toLowerCase();
            boolean errorMarker = lower.contains("failed") || lower.contains("error") || lower.contains("exception");
            if (exit != 0 || errorMarker || f > 0 || "FAILED".equals(build) || "FAIL".equals(tests)) {
                status = "REPAIR";
                next = "ᚱᛁ REPAIR";
                findings.put("build=" + build + " exit=" + exit + " tests=" + p + "/" + (p+f) + " status=" + tests);
                if (!bLog.isEmpty()) findings.put(firstUsefulLine(bLog));
                repairs.put("Fix the concrete build/test error, rebuild, then VERIFY again.");
            } else if ("SUCCESS".equals(build) && "PASS".equals(tests) && exit == 0 && Boolean.TRUE.equals(artifact) && p == 12 && f == 0) {
                status = "PASS";
                next = "ᛚᛗ LEARN";
            } else {
                status = "REPAIR";
                next = "ᚱᛁ REPAIR";
                findings.put("partial-pass: build=" + build + " tests=" + tests + " exit=" + exit + " artifact=" + artifact + " passed=" + p + "/12");
                repairs.put("Require SUCCESS + PASS + exit 0 + artifact true + exactly 12/12 tests.");
            }
        }

        try {
            JSONObject json = new JSONObject();
            json.put("protocol", "X88_VERIFY_V20");
            json.put("status", status);
            json.put("generation", gen);
            json.put("candidate", "ARIANA_X88_NATIVE_VERIFY_V20");
            json.put("findings", findings);
            json.put("repairs", repairs);
            json.put("next", next);
            json.put("exit_code", exit == null ? JSONObject.NULL : exit);
            json.put("artifact_verified", artifact == null ? JSONObject.NULL : artifact);
            json.put("tests_passed", p);
            json.put("tests_failed", f);
            json.put("test_log_observed", !tLog.isEmpty());
            output.setText(json.toString(2));
        } catch (Exception e) {
            output.setText("JSON error: " + e.getMessage());
        }

        verdict.setText(status + "  →  " + next);
        verdict.setTextColor("PASS".equals(status) ? Color.rgb(0,255,136) : "REPAIR".equals(status) ? Color.rgb(255,51,68) : Color.LTGRAY);
    }

    private void loadPassExample() {
        generation.setText("gen_x88_v20_0001");
        buildStatus.setSelection(0);
        exitCode.setText("0");
        artifactVerified.setSelection(0);
        buildLog.setText("[ᛏᛉ BUILD] ARIANA_X88_NATIVE_VERIFY_V20 compiling...\nsuccess: artifact built\nexit_code: 0");
        passed.setText("12"); failed.setText("0"); testStatus.setSelection(0);
        testLog.setText("[TEST] 12/12 passing\n✓ verify core\n✓ repair path\n✓ learn gate");
    }

    private void loadRepairExample() {
        buildStatus.setSelection(1); exitCode.setText("1"); artifactVerified.setSelection(1);
        buildLog.setText("[ᛏᛉ BUILD] FAILED\nerror: unresolved symbol X88_CORE\nfailed: build");
        passed.setText("10"); failed.setText("2"); testStatus.setSelection(1); testLog.setText("2/12 failed");
    }

    private void loadStopExample() {
        buildStatus.setSelection(2); exitCode.setText(""); artifactVerified.setSelection(2);
        buildLog.setText(""); passed.setText("0"); failed.setText("0"); testStatus.setSelection(2); testLog.setText("");
    }

    private TextView text(String value, int sp, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setPadding(0, dp(8), 0, dp(8)); return v;
    }
    private EditText input(String hint, String value, boolean numeric) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.DKGRAY); e.setTextColor(Color.WHITE); e.setText(value); e.setSingleLine(true); e.setPadding(dp(8),dp(8),dp(8),dp(8));
        if (numeric) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    private EditText multiline(String hint, int lines) {
        EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(Color.DKGRAY); e.setTextColor(Color.WHITE); e.setMinLines(lines); e.setGravity(Gravity.TOP); e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); e.setPadding(dp(8),dp(8),dp(8),dp(8)); return e;
    }
    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this); ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); return s;
    }
    private Button button(String label, View.OnClickListener l) { Button b = new Button(this); b.setText(label); b.setOnClickListener(l); return b; }
    private String value(EditText e, String fallback) { String s = e.getText().toString().trim(); return s.isEmpty() ? fallback : s; }
    private Integer parseNullableInt(String s) { try { return s.trim().isEmpty() ? null : Integer.valueOf(s.trim()); } catch (Exception e) { return null; } }
    private int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private Boolean parseTriBool(String s) { if ("true".equals(s)) return Boolean.TRUE; if ("false".equals(s)) return Boolean.FALSE; return null; }
    private String firstUsefulLine(String s) { for (String line : s.split("\\n")) { String l = line.trim(); if (!l.isEmpty()) return l; } return "build error observed"; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
