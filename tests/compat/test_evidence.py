import tempfile
import unittest
from pathlib import Path
import evidence
from unittest.mock import patch
from types import SimpleNamespace

class EvidenceInputsTest(unittest.TestCase):
    def test_baseline_update_keeps_signer_pinned(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'new.apk'
            apk.write_bytes(b'new authenticated APK')
            old = dict(certificate_sha256='a' * 64, apk_sha256='b' * 64)
            def outputs(cert):
                return [SimpleNamespace(stdout='Signer #1 certificate SHA-256 digest: ' + cert),
                        SimpleNamespace(stdout="package: name='org.telegram.messenger' versionCode='73031' versionName='13.2.2'")]
            with patch.object(evidence.subprocess, 'run', side_effect=outputs('a' * 64)):
                with self.assertRaises(ValueError): evidence.authenticated(apk, temporary, old)
            with patch.object(evidence.subprocess, 'run', side_effect=outputs('a' * 64)):
                self.assertEqual(evidence.authenticated(apk, temporary, old, allow_update=True)['version_name'], '13.2.2')
            with patch.object(evidence.subprocess, 'run', side_effect=outputs('c' * 64)):
                with self.assertRaises(ValueError): evidence.authenticated(apk, temporary, old, allow_update=True)

    def test_git_text_endings_and_changed_contract(self):
        import subprocess
        previous_root, previous_record = evidence.ROOT, evidence.RECORD
        with tempfile.TemporaryDirectory(prefix="televip-evidence-") as temporary:
            root = Path(temporary)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            evidence.ROOT, evidence.RECORD = root, root / 'tests/compat/verified-play.json'
            try:
                paths = [root / 'app/src/main/resources/META-INF/xposed/module.prop', root / 'app/src/main/resources/META-INF/xposed/scope.list', root / 'gradle/libs.versions.toml']
                for p in paths:
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_bytes(b'first\nsecond\n')
                original = evidence.inputs_digest()
                for p in paths:
                    p.write_bytes(b'first\r\nsecond\r\n')
                self.assertEqual(original, evidence.inputs_digest())
                paths[0].write_bytes(b'changed\nsecond\n')
                self.assertNotEqual(original, evidence.inputs_digest())
            finally:
                evidence.ROOT, evidence.RECORD = previous_root, previous_record

if __name__ == '__main__':
    unittest.main()
