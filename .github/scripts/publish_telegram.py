"""Publish one verified stable release; secrets never appear in command arguments or logs."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

REPO = 'ceigt/TeleVip-LSPosed'
CERTIFICATE = '60f2274c49aac137bd0cc6a30b522f8930c95fe19c53ba7c2469a939665a2efc'
MAX_APK = 20 * 1024 * 1024  # Public Bot API getFile can verify files up to 20 MB.
RECEIPT = 'TELEGRAM-POST.json'
ROOT = Path(__file__).resolve().parents[2]

class DeliveryError(Exception):
    pass

def require(value, message):
    if not value:
        raise DeliveryError(message)

def download(url, *, headers=None, data=None, method='GET', limit=1024 * 1024):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}, data=data, method=method), timeout=60) as response:
            body = response.read(limit + 1)
            require(len(body) <= limit, 'Response exceeds the allowed size')
            return body
    except urllib.error.HTTPError as error:
        raise DeliveryError('Remote service rejected the request (HTTP %d)' % error.code) from None
    except (urllib.error.URLError, TimeoutError):
        raise DeliveryError('Remote service connection failed; check publication state before retrying') from None

def github(path, *, method='GET', body=None, upload=False):
    require(path.startswith('/'), 'Invalid GitHub API path')
    headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2026-03-10', 'User-Agent': 'TeleVip-Release-Publisher'}
    headers['Content-Type'] = 'application/json'
    host = 'https://uploads.github.com' if upload else 'https://api.github.com'
    data = json.dumps(body, ensure_ascii=False).encode() if body is not None else None
    raw = download(host + path, headers=headers, data=data, method=method)
    return json.loads(raw) if raw else None

def telegram(method, *, body=None, data=None, content_type=None, limit=1024 * 1024):
    require(re.fullmatch('[A-Za-z]+', method), 'Invalid Telegram method')
    if body is not None:
        data = json.dumps(body).encode()
        content_type = 'application/json'
    raw = download('https://api.telegram.org/bot' + os.environ['TELEGRAM_BOT_TOKEN'] + '/' + method,
                   headers={'Content-Type': content_type or 'application/json'}, data=data, method='POST', limit=limit)
    result = json.loads(raw)
    require(result.get('ok') is True, 'Telegram rejected the publication')
    return result['result']

def tag_version(tag):
    require(re.fullmatch(r'v\d{2}\.\d{2}\.\d{2}(?:\.\d+)?', tag), 'Unsupported Release tag')
    return tag[1:]

def one_asset(release, name):
    values = [asset for asset in release['assets'] if asset['name'] == name]
    require(len(values) == 1, 'Missing or ambiguous Release asset: ' + name)
    require(values[0].get('state') == 'uploaded', 'Incomplete Release asset: ' + name)
    return values[0]

def release_bytes(release, asset, *, limit):
    name = asset['name']
    require(re.fullmatch(r'[A-Za-z0-9_.-]+', name), 'Unsafe asset filename')
    url = 'https://github.com/' + REPO + '/releases/download/' + urllib.parse.quote(release['tag_name'], safe='') + '/' + name
    raw = download(url, limit=limit)
    require(len(raw) == asset['size'], 'Release asset size differs')
    require(asset.get('digest') == 'sha256:' + hashlib.sha256(raw).hexdigest(), 'Release asset digest differs')
    return raw

def verify_payload(release, info, checksum, apk):
    version = tag_version(release['tag_name'])
    require(not release.get('draft') and not release.get('prerelease') and release.get('published_at'), 'Only published stable Releases may be sent')
    require(info.get('package') == 'org.televip' and info.get('version_name') == version, 'APK identity/version differs from Release')
    require(info.get('certificate_sha256') == CERTIFICATE, 'Untrusted signing certificate metadata')
    require(re.fullmatch('[0-9a-f]{40}', info.get('source_commit', '')), 'Missing exact source commit')
    digest = hashlib.sha256(apk).hexdigest()
    require(info.get('signed_sha256') == digest, 'APK differs from signing provenance')
    expected = digest + '  TeleVip-' + version + '.apk'
    require(checksum.strip() == expected, 'APK differs from published SHA256SUMS')
    require(len(apk) <= MAX_APK, 'APK exceeds the verified Telegram transfer limit')
    return digest

def verify_apk(path, info):
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(sdk, 'Android SDK is unavailable')
    tools = Path(sdk) / 'build-tools/36.0.0'
    suffix = '.bat' if os.name == 'nt' else ''
    try:
        signing = subprocess.check_output([str(tools / ('apksigner' + suffix)), 'verify', '--verbose', '--print-certs', str(path)], stderr=subprocess.STDOUT, text=True)
        match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signing)
        require(match and match[1] == CERTIFICATE, 'APK certificate verification failed')
        package = subprocess.check_output([str(tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2')), 'dump', 'badging', str(path)], stderr=subprocess.STDOUT, text=True)
        match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", package)
        require(match and match[1] == 'org.televip' and int(match[2]) == info['version_code'] and match[3] == info['version_name'], 'Signed package metadata differs')
        with zipfile.ZipFile(path) as archive:
            properties = archive.read('META-INF/xposed/module.prop').decode()
            require('minApiVersion=102' in properties and 'targetApiVersion=102' in properties, 'Unexpected module API')
    except subprocess.CalledProcessError:
        raise DeliveryError('APK verification tool rejected the artifact') from None

def caption(release):
    title = 'TeleVip Next · ' + release['tag_name']
    lines = []
    for line in (release.get('body') or '').splitlines():
        if line.startswith('- '):
            # Underscores and asterisks may be part of URLs; plain-text captions need no Markdown escaping.
            value = line[2:].replace('`', '').strip()
            if value:
                lines.append('• ' + value[:150])
        if len(lines) == 4:
            break
    url = 'https://github.com/' + REPO + '/releases/tag/' + release['tag_name']
    middle = '\n'.join(lines) or '签名 APK 已发布，完整更新说明见 GitHub。'
    while len((title + '\n\n' + middle + '\n\n' + url).encode('utf-16-le')) // 2 > 1000:
        middle = middle[:-1]
    return title + '\n\n' + middle + '\n\n' + url

def multipart(chat, text, filename, apk):
    boundary = 'TeleVip-' + uuid.uuid4().hex
    parts = []
    for name, value in [('chat_id', str(chat)), ('caption', text)]:
        parts += [('\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="' + name + '"\r\n\r\n').encode(), value.encode()]
    parts += [('\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="document"; filename="' + filename + '"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n').encode(), apk, ('\r\n--' + boundary + '--\r\n').encode()]
    return b''.join(parts)[2:], 'multipart/form-data; boundary=' + boundary

def expected_channel():
    text = (ROOT / 'app/src/main/java/com/my/televip/ProjectLinks.java').read_text()
    match = re.search(r'TELEGRAM_CHANNEL_USERNAME\s*=\s*"([A-Za-z0-9_]{5,32})"', text)
    require(match, 'Public channel is not configured in the APK source')
    return match[1].lower()

def prior_receipt(release, chat_id, digest):
    values = [asset for asset in release['assets'] if asset['name'] == RECEIPT]
    if not values:
        return None
    require(len(values) == 1, 'Ambiguous Telegram publication receipt')
    receipt = json.loads(release_bytes(release, values[0], limit=65536))
    require(receipt.get('tag') == release['tag_name'] and receipt.get('chat_id') == chat_id and receipt.get('apk_sha256') == digest and isinstance(receipt.get('message_id'), int) and receipt['message_id'] > 0, 'Existing receipt differs; manual review required')
    return receipt

def run():
    require(os.environ.get('GITHUB_REPOSITORY') == REPO and os.environ.get('GITHUB_REF') == 'refs/heads/main', 'Publisher must run from this repository main')
    tag = os.environ['RELEASE_TAG']
    version = tag_version(tag)
    release = github('/repos/' + REPO + '/releases/tags/' + tag)
    require(not release.get('draft') and not release.get('prerelease'), 'Drafts and prereleases are not published to the channel')
    filename = 'TeleVip-' + version + '.apk'
    apk = release_bytes(release, one_asset(release, filename), limit=MAX_APK)
    info = json.loads(release_bytes(release, one_asset(release, 'SIGNING-INFO.json'), limit=65536))
    sums = release_bytes(release, one_asset(release, 'SHA256SUMS.txt'), limit=65536).decode().lstrip('\ufeff')
    digest = verify_payload(release, info, sums, apk)
    commit = github('/repos/' + REPO + '/git/ref/tags/' + tag)['object']
    for _ in range(5):
        if commit['type'] != 'tag':
            break
        commit = github('/repos/' + REPO + '/git/tags/' + commit['sha'])['object']
    require(commit['type'] == 'commit' and commit['sha'] == info['source_commit'], 'Release tag differs from the signed source')
    require(subprocess.run(['git', 'merge-base', '--is-ancestor', commit['sha'], 'origin/main'], cwd=ROOT, capture_output=True).returncode == 0, 'Release source is not merged into main')
    with tempfile.TemporaryDirectory(prefix='televip-publish-') as temporary:
        path = Path(temporary) / filename
        path.write_bytes(apk)
        verify_apk(path, info)
    channel = expected_channel()
    require(os.environ.get('TELEGRAM_CHANNEL_USERNAME', '').lower() == channel, 'Configured channel differs from APK destination')
    raw_id = os.environ.get('TELEGRAM_CHAT_ID', '')
    require(re.fullmatch(r'-100\d+', raw_id), 'Invalid fixed channel ID')
    chat_id = int(raw_id)
    me = telegram('getMe', body={})
    chat = telegram('getChat', body={'chat_id': chat_id})
    require(chat['id'] == chat_id and chat['type'] == 'channel' and chat.get('username', '').lower() == channel, 'Destination is not the configured public channel')
    member = telegram('getChatMember', body={'chat_id': chat_id, 'user_id': me['id']})
    require(me.get('is_bot') and member['status'] == 'administrator' and member.get('can_post_messages'), 'Bot is not a posting administrator')
    extra = [key for key, value in member.items() if key.startswith('can_') and value is True and key not in {'can_manage_chat', 'can_post_messages', 'can_be_edited'}]
    require(not extra, 'Bot has permissions outside the post-only policy')
    if os.environ.get('DRY_RUN', 'false').lower() == 'true':
        print('TELEGRAM_DRY_RUN_PASS: verified signed Release, source and post-only bot; nothing sent')
        return
    existing = prior_receipt(release, chat_id, digest)
    if existing:
        print('Already published: ' + existing['post_url'])
        return
    body, content_type = multipart(chat_id, caption(release), filename, apk)
    sent = telegram('sendDocument', data=body, content_type=content_type)
    require(sent['chat']['id'] == chat_id and sent.get('document', {}).get('file_name') == filename and sent['document']['file_size'] == len(apk), 'Telegram response differs from the uploaded document')
    receipt = {'tag': tag, 'source_commit': commit['sha'], 'apk_sha256': digest, 'chat_id': chat_id, 'message_id': sent['message_id'], 'post_url': 'https://t.me/' + channel + '/' + str(sent['message_id'])}
    # Preserve the acknowledged message even if download verification or GitHub upload fails.
    (ROOT / 'telegram-publication.json').write_text(json.dumps(receipt, indent=2) + '\n')
    remote = telegram('getFile', body={'file_id': sent['document']['file_id']})
    file_path = remote.get('file_path', '')
    require(file_path and '..' not in file_path and not file_path.startswith('/'), 'Unexpected Telegram document path')
    received = download('https://api.telegram.org/file/bot' + os.environ['TELEGRAM_BOT_TOKEN'] + '/' + urllib.parse.quote(file_path, safe='/'), limit=MAX_APK)
    require(hashlib.sha256(received).hexdigest() == digest, 'Telegram document download checksum differs')
    receipt['telegram_download_verified'] = True
    (ROOT / 'telegram-publication.json').write_text(json.dumps(receipt, indent=2) + '\n')
    github('/repos/' + REPO + '/releases/' + str(release['id']) + '/assets?name=' + RECEIPT, method='POST', body=receipt, upload=True)
    print('Published verified APK: ' + receipt['post_url'])
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as out:
            out.write('Published [' + tag + '](' + receipt['post_url'] + '). APK SHA256: `' + digest + '`.\n')

if __name__ == '__main__':
    try:
        run()
    except DeliveryError as error:
        print('Publication stopped: ' + str(error), file=sys.stderr)
        sys.exit(1)
    except Exception as error:
        # urllib URLs contain the bot credential. Never emit tracebacks or raw exceptions.
        print('Publication stopped (' + type(error).__name__ + '); inspect configured inputs and public receipt', file=sys.stderr)
        sys.exit(1)
