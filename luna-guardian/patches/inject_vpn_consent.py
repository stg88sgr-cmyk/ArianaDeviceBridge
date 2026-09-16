from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
src = app / "src" / "main"
manifest = src / "AndroidManifest.xml"
if not manifest.exists():
    raise SystemExit(f"manifest not found: {manifest}")

kt_files = list((src / "java").rglob("*.kt")) + list((src / "kotlin").rglob("*.kt"))
if not kt_files:
    raise SystemExit("no Kotlin sources found")

# Prefer the package that already owns the VPN service, otherwise use the first app package.
package_name = None
for path in kt_files:
    text = path.read_text(encoding="utf-8")
    if "VpnService" in text:
        m = re.search(r"^\s*package\s+([A-Za-z0-9_.]+)", text, re.M)
        if m:
            package_name = m.group(1)
            break
if package_name is None:
    for path in kt_files:
        text = path.read_text(encoding="utf-8")
        m = re.search(r"^\s*package\s+([A-Za-z0-9_.]+)", text, re.M)
        if m:
            package_name = m.group(1)
            break
if package_name is None:
    raise SystemExit("could not determine Kotlin package")

out_dir = src / "java" / Path(*package_name.split("."))
out_dir.mkdir(parents=True, exist_ok=True)
out = out_dir / "X88VpnConsentActivity.kt"
out.write_text(f'''package {package_name}

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button

/**
 * User-visible consent bridge for Luna Guardian.
 * Android owns the authorization dialog; this activity only launches it.
 */
class X88VpnConsentActivity : Activity() {{
    private val requestCode = 8806

    override fun onCreate(savedInstanceState: Bundle?) {{
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {{
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }}
        val title = TextView(this).apply {{
            text = "Luna XXY VPN-Freigabe"
            textSize = 24f
        }}
        val info = TextView(this).apply {{
            text = "Android zeigt dir gleich den offiziellen VPN-Dialog. Erst nach deiner Zustimmung darf Luna Guardian den geschützten Netzwerkpfad aktivieren."
            textSize = 16f
        }}
        val button = Button(this).apply {{
            text = "VPN-Freigabe öffnen"
            setOnClickListener {{ requestVpnConsent() }}
        }}
        layout.addView(title)
        layout.addView(info)
        layout.addView(button)
        setContentView(layout)
    }}

    private fun requestVpnConsent() {{
        val intent = VpnService.prepare(this)
        if (intent == null) {{
            setResult(RESULT_OK)
            finish()
        }} else {{
            startActivityForResult(intent, requestCode)
        }}
    }}

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {{
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode) {{
            setResult(if (resultCode == RESULT_OK) RESULT_OK else RESULT_CANCELED)
            finish()
        }}
    }}
}}
''', encoding="utf-8")

xml = manifest.read_text(encoding="utf-8")
fqcn = f"{package_name}.X88VpnConsentActivity"
if fqcn not in xml:
    activity = f'''\n        <activity\n            android:name="{fqcn}"\n            android:exported="true"\n            android:label="Luna VPN-Freigabe">\n            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>\n'''
    marker = "</application>"
    if marker not in xml:
        raise SystemExit("application closing tag not found")
    xml = xml.replace(marker, activity + "    " + marker, 1)
    manifest.write_text(xml, encoding="utf-8")

print(f"Injected VPN consent activity: {fqcn}")
print(f"Manifest: {manifest}")
