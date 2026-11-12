import tempfile
import unittest
from pathlib import Path
import evidence

class EvidenceInputsTest(unittest.TestCase):
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
