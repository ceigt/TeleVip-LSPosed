"""Bind an authenticated real-Play APK audit to the exact source inputs, without redistributing Telegram."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
RECORD = ROOT / 'tests/compat/verified-play.json'

def inputs_digest():
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', 'app/src/main', 'app/build.gradle', 'settingsadapter', 'tests/compat', 'tests/regression', '.github/workflows', 'gradle', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat'], cwd=ROOT).decode('utf-8').split('\0')
    files = [ROOT / name for name in names if name and (ROOT / name).is_file() and ROOT / name != RECORD and '__pycache__' not in Path(name).parts and not name.endswith('.pyc')]
    digest = hashlib.sha256()
    for p in sorted(files,key=lambda p:p.relative_to(ROOT).as_posix()):
        data = p.read_bytes()
        if p.suffix in {'.java', '.py', '.json', '.xml', '.txt', '.ps1', '.gradle', '.md', '.yml', '.properties', '.pro', '.sh', '.bat', '.cfg', '.list', '.prop', '.toml'} or p.name in {'gradlew', '.gitignore', '.gitattributes'}:
            data = data.replace(b'\r\n', b'\n')
        digest.update(p.relative_to(ROOT).as_posix().encode() + b'\0' + hashlib.sha256(data).digest())
    return digest.hexdigest()

def authenticated(apk, sdk, expected=None):
    tools = Path(sdk) / 'build-tools/36.0.0'
    suffix = '.bat' if sys.platform == 'win32' else ''
    signed = subprocess.run([str(tools / ('apksigner' + suffix)), 'verify', '--verbose', '--print-certs', str(apk)], check=True, capture_output=True, text=True).stdout
    cert = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signed).group(1)
    badging = subprocess.run([str(tools / ('aapt2.exe' if sys.platform == 'win32' else 'aapt2')), 'dump', 'badging', str(apk)], check=True, capture_output=True, text=True).stdout
    package, code, version = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging).groups()
    if package != 'org.telegram.messenger':
        raise ValueError('A real Play package is required')
    sha = hashlib.sha256(Path(apk).read_bytes()).hexdigest()
    if expected and (cert != expected['certificate_sha256'] or sha != expected['apk_sha256']):
        raise ValueError('APK differs from the authenticated Play baseline')
    return dict(package=package, version_name=version, version_code=int(code), certificate_sha256=cert, apk_sha256=sha)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--record', action='store_true')
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--apk')
    parser.add_argument('--sdk')
    parser.add_argument('--java-home')
    parser.add_argument('--output', default='app/build/play-compat')
    args = parser.parse_args()
    if args.verify:
        proof = json.loads(RECORD.read_text(encoding='utf-8'))
        if proof['input_digest'] != inputs_digest() or proof['package'] != 'org.telegram.messenger' or proof['active_failures'] != 0:
            raise ValueError('Play evidence is missing or stale: rerun with the authenticated APK')
        print(f"PLAY_EVIDENCE_PASS: {proof['version_name']} ({proof['version_code']}); exact source inputs match the recorded real-APK audit")
        return
    if not args.apk or not args.sdk:
        parser.error('--apk and --sdk are required for an actual APK audit')
    previous = json.loads(RECORD.read_text(encoding='utf-8')) if RECORD.exists() else None
    proof = authenticated(args.apk, args.sdk, previous)
    command = [sys.executable, str(ROOT / 'tests/compat/run.py'), '--apk', args.apk, '--output', args.output]
    if args.java_home:
        command += ['--java-home', args.java_home]
    subprocess.run(command, cwd=ROOT, check=True)
    proof.update(input_digest=inputs_digest(), active_failures=0)
    if args.record:
        RECORD.write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
    print('PLAY_APK_AUDIT_PASS')

if __name__ == '__main__':
    main()
