import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).resolve().parents[2] / '.github/scripts/publish_telegram.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)


class PublicationGuards(unittest.TestCase):
    def setUp(self):
        self.apk = b'APK\x00\r\n\xffbinary'
        self.digest = hashlib.sha256(self.apk).hexdigest()
        self.release = dict(tag_name='v26.11.13.1', draft=False, prerelease=False, published_at='2026-11-13T00:00:00Z', assets=[])
        self.info = dict(package='org.televip', version_name='26.11.13.1', certificate_sha256=p.CERTIFICATE, source_commit='a' * 40, signed_sha256=self.digest)
        self.sums = self.digest + '  TeleVip-26.11.13.1.apk\n'

    def test_authentic_payload_and_tamper_rejection(self):
        self.assertEqual(p.verify_payload(self.release, self.info, self.sums, self.apk), self.digest)
        for key, value in [('package', 'another.app'), ('version_name', '26.11.12'), ('certificate_sha256', 'b' * 64), ('source_commit', 'main'), ('signed_sha256', 'c' * 64)]:
            with self.subTest(key=key), self.assertRaises(p.DeliveryError):
                p.verify_payload(self.release, dict(self.info, **{key: value}), self.sums, self.apk)
        for key in ['draft', 'prerelease']:
            with self.subTest(key=key), self.assertRaises(p.DeliveryError):
                p.verify_payload(dict(self.release, **{key: True}), self.info, self.sums, self.apk)
        with self.assertRaises(p.DeliveryError):
            p.verify_payload(self.release, self.info, self.sums, self.apk + b'tampered')
        with self.assertRaises(p.DeliveryError):
            p.verify_payload(self.release, self.info, self.sums.replace('.apk', '.zip'), self.apk)

    def test_caption_bounds_preserve_release_link(self):
        self.release['body'] = '\n'.join('- ' + '😀更新' * 200 for _ in range(10))
        text = p.caption(self.release)
        self.assertLessEqual(len(text.encode('utf-16-le')) // 2, 1000)
        self.assertTrue(text.endswith('/releases/tag/v26.11.13.1'))
        self.assertLessEqual(text.count('• '), 4)

    def test_caption_preserves_channel_username_and_url_characters(self):
        self.release['body'] = '- 频道 https://t.me/televip_next\n- `name_with_underscore`'
        text = p.caption(self.release)
        self.assertIn('https://t.me/televip_next', text)
        self.assertIn('name_with_underscore', text)

    def test_upload_preserves_binary_bytes(self):
        body, content_type = p.multipart(-100123, '更新', 'TeleVip-26.11.13.1.apk', self.apk)
        self.assertIn(b'\r\n\r\n' + self.apk + b'\r\n--', body)
        self.assertIn('更新'.encode(), body)
        self.assertTrue(content_type.startswith('multipart/form-data; boundary='))

    def test_receipt_deduplication_and_mismatch_rejection(self):
        self.assertIsNone(p.prior_receipt(self.release, -100123, self.digest))
        self.release['assets'] = [dict(name=p.RECEIPT)]
        receipt = dict(tag=self.release['tag_name'], chat_id=-100123, apk_sha256=self.digest, message_id=7)
        with patch.object(p, 'release_bytes', return_value=json.dumps(receipt).encode()):
            self.assertEqual(p.prior_receipt(self.release, -100123, self.digest)['message_id'], 7)
            with self.assertRaises(p.DeliveryError):
                p.prior_receipt(self.release, -100456, self.digest)

    def test_wrong_ref_stops_before_network_or_secret_access(self):
        with patch.dict(os.environ, {'GITHUB_REPOSITORY': p.REPO, 'GITHUB_REF': 'refs/pull/1/merge'}, clear=True), patch.object(p, 'github') as network:
            with self.assertRaises(p.DeliveryError):
                p.run()
            network.assert_not_called()

    def test_network_errors_do_not_expose_credentials(self):
        private_url = 'https://api.telegram.org/botSECRET-TOKEN/sendDocument'
        errors = [urllib.error.HTTPError(private_url, 403, 'SECRET-TOKEN', {}, None), urllib.error.URLError(private_url)]
        for error in errors:
            with patch.object(p.urllib.request, 'urlopen', side_effect=error), self.assertRaises(p.DeliveryError) as caught:
                p.download(private_url)
            self.assertNotIn('SECRET-TOKEN', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
