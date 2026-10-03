"""Run safety tests and a static compatibility audit against an APK. Requires JDK 17."""
import argparse
import os
import subprocess
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--apk', required=True)
parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
parser.add_argument('--output', default='app/build/compat-report')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
output = (root / args.output).resolve()
classes = output / 'classes'
classes.mkdir(parents=True, exist_ok=True)
suffix = '.exe' if os.name == 'nt' else ''
java = Path(args.java_home) / 'bin' / ('java' + suffix) if args.java_home else 'java'
javac = Path(args.java_home) / 'bin' / ('javac' + suffix) if args.java_home else 'javac'
base = root / 'app/src/main/java/com/my/televip'
sources = list((base / 'obfuscate/dex').glob('*.java')) + list((base / 'obfuscate/resolve').glob('*.java'))
sources += [base / 'obfuscate/StartupRequestQueue.java', base / 'obfuscate/StartupDispatchSelector.java', base / 'features/ui/AdResponseCallbacks.java']
sources += [base / 'compat/ReflectionLookup.java']
sources += list((root / 'tests/compat/src').rglob('*.java'))
source_list = output / 'sources.txt'
source_list.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf-8')
subprocess.run([str(javac), '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-d', str(classes), '@' + str(source_list)], check=True)
subprocess.run([str(java), '-cp', str(classes), 'com.my.televip.obfuscate.resolve.CompatSafetyTest'], check=True)
subprocess.run([str(java), '-cp', str(classes), 'com.my.televip.compat.ReflectionLookupTest'], check=True)
subprocess.run([sys.executable, str(root / 'tests/compat/test_audit.py')], check=True)
inventory = output / 'inventory.tsv'
subprocess.run([sys.executable, str(root / 'tests/compat/inventory.py'), str(inventory)], check=True)
subprocess.run([str(java), '-Xmx2g', '-cp', str(classes), 'com.my.televip.obfuscate.resolve.CompatAudit', str(Path(args.apk).resolve()), str(output), str(inventory)], check=True)

subprocess.run([sys.executable, str(root / 'tests/compat/features.py'), str(output)], check=True)
