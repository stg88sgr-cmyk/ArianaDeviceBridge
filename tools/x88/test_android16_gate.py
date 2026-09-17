from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from android16_gate import run_checks


SAFE_MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <application>
        <service android:name=".widget.ArianaWakewordService" android:foregroundServiceType="microphone" />
        <service android:name="de.snowworks.ariana.session.ArianaCaptureService" android:foregroundServiceType="camera|microphone|mediaProjection" />
    </application>
</manifest>
'''

SAFE_NETWORK = '''<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
'''


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def make_repo(root: Path) -> None:
    write(
        root / "android/build.gradle.kts",
        'plugins { id("com.android.application") version "8.9.1" apply false }\n',
    )
    write(
        root / "android/app/build.gradle.kts",
        'android { compileSdk = 36\n defaultConfig { targetSdk = 36 } }\n',
    )
    write(root / "android/app/src/main/AndroidManifest.xml", SAFE_MANIFEST)
    write(
        root / "android/app/src/main/java/de/snowworks/app/boot/X88BootReceiver.kt",
        'class X88BootReceiver { fun onReceive() { /* passive */ } }\n',
    )
    write(
        root / "android/app/src/main/java/de/snowworks/ariana/bridge/LocalBridgeServer.kt",
        'val bindAddress = InetAddress.getByName("127.0.0.1")\n',
    )
    write(root / "android/app/src/main/res/xml/network_security_config.xml", SAFE_NETWORK)
    write(
        root / "android/app/src/main/java/de/snowworks/app/ui/SafeActivity.kt",
        'class SafeActivity\n',
    )


def failed_names(root: Path) -> set[str]:
    return {item.name for item in run_checks(root) if not item.ok}


class Android16GateTest(unittest.TestCase):
    def test_safe_fixture_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_repo(root)
            self.assertEqual(failed_names(root), set())

    def test_target_35_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_repo(root)
            write(
                root / "android/app/build.gradle.kts",
                'android { compileSdk = 36\n defaultConfig { targetSdk = 35 } }\n',
            )
            self.assertIn("targetSdk >= 36", failed_names(root))

    def test_boot_service_start_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_repo(root)
            write(
                root / "android/app/src/main/java/de/snowworks/app/boot/X88BootReceiver.kt",
                'class X88BootReceiver { fun onReceive() { startForegroundService(intent) } }\n',
            )
            self.assertIn("BOOT_COMPLETED stays passive", failed_names(root))

    def test_global_cleartext_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_repo(root)
            write(
                root / "android/app/src/main/res/xml/network_security_config.xml",
                SAFE_NETWORK.replace('cleartextTrafficPermitted="false"', 'cleartextTrafficPermitted="true"', 1),
            )
            self.assertIn("cleartext denied by default", failed_names(root))

    def test_legacy_back_interception_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_repo(root)
            write(
                root / "android/app/src/main/java/de/snowworks/app/ui/SafeActivity.kt",
                'class SafeActivity { override fun onBackPressed() {} }\n',
            )
            self.assertIn("no legacy back interception", failed_names(root))


if __name__ == "__main__":
    unittest.main()
