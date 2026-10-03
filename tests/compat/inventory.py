"""Collect resolver calls and exact hook signatures without executing client code."""
import json
import re
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/com/my/televip'
CONSTANTS = dict(re.findall(r'String\s+(\w+)\s*=\s*"([^"]+)"', (JAVA / 'Class/ClassNames.java').read_text(encoding='utf-8')))

def split_args(text):
    result, start, depth, quote, escape = [], 0, 0, False, False
    for i, ch in enumerate(text):
        if quote:
            if escape: escape = False
            elif ch == '\\': escape = True
            elif ch == '"': quote = False
        elif ch == '"': quote = True
        elif ch in '([{': depth += 1
        elif ch in ')]}': depth -= 1
        elif ch == ',' and depth == 0:
            result.append(text[start:i].strip()); start = i + 1
    result.append(text[start:].strip())
    return result

def calls(source, function):
    for match in re.finditer(re.escape(function) + r'\s*\(', source):
        start, depth, quote, escape = match.end(), 1, False, False
        for i in range(start, len(source)):
            ch = source[i]
            if quote:
                if escape: escape = False
                elif ch == '\\': escape = True
                elif ch == '"': quote = False
            elif ch == '"': quote = True
            elif ch == '(': depth += 1
            elif ch == ')':
                depth -= 1
                if depth == 0:
                    yield split_args(source[start:i]); break
        else: raise ValueError('Unbalanced call: ' + function)

def class_type(expr, imports):
    expr = expr.strip()
    match = re.fullmatch(r'ClassLoad\.getClass\(\s*ClassNames\.(\w+)\s*\)', expr)
    if match: return CONSTANTS[match[1]]
    match = re.fullmatch(r'ClassLoad\.getClass\(\s*"([^"]+)"\s*\)', expr)
    if match: return match[1]
    match = re.fullmatch(r'ClientManager\.is\(ClientManager\.Client\.Telegram\)\s*\?\s*(.+?)\s*:\s*(.+)', expr)
    if match: return class_type(match[1], imports)
    if expr.endswith('.class'):
        name, array = expr[:-6], ''
        while name.endswith('[]'): array += '[]'; name = name[:-2]
        if name in {'int', 'long', 'float', 'double', 'boolean', 'byte', 'short', 'char', 'void'}: return name + array
        return (imports.get(name) or ('java.lang.' + name if name in {'Object', 'String', 'CharSequence', 'Runnable'} else name)) + array
    return None

def collect(java=JAVA):
    sites, gaps = set(), []
    for path in java.rglob('*.java'):
        name = path.relative_to(java).as_posix()
        if 'obfuscate' in path.relative_to(java).parts or path.name in {'ClassNames.java', 'XposedHelpers.java', 'ReflectionLookup.java'}: continue
        source = path.read_text(encoding='utf-8')
        source = re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', lambda m: m[0] if m[0].startswith('"') else ' ', source, flags=re.S)
        imports = {full.rsplit('.', 1)[-1]: full for full in re.findall(r'import\s+([\w.$]+);', source)}
        def add(kind, owner, member='', signature=''): sites.add((kind, owner, member, name, signature))
        for constant in re.findall(r'ClassNames\.(\w+)', source):
            if constant in CONSTANTS: add('C', CONSTANTS[constant])
        for cls in re.findall(r'ClassLoad\.getClass\(\s*"([^"]+)"\s*[,)]', source): add('C', cls)
        for kind, owner, member in re.findall(r'Obfuscate\.get(Method|Field)Name\(\s*"([^"]+)"\s*,\s*"([^"]+)"', source): add(kind[0], owner, member)
        for args in calls(source, 'HMethod.hookMethod'):
            if len(args) < 3: continue
            mapped = re.fullmatch(r'Obfuscate\.getMethodName\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', args[1])
            owner = mapped[1] if mapped else class_type(args[0], imports)
            names = [mapped[2]] if mapped else []
            params = args[2:-1]
            if not mapped and args[1].startswith('"'):
                if args[2].startswith('new String'):
                    owner = args[1].strip('"'); names = re.findall(r'"([^"]+)"', args[2]); params = args[3:-1]
                else: names = [args[1].strip('"')]
            if not names or not owner:
                gaps.append(name + ': dynamic hook owner/signature'); continue
            merge = list(calls(args[-1], 'ArgsResolver.merge')) if args[-1].startswith('ArgsResolver.merge') else []
            if merge:
                match = re.fullmatch(r'new Class(?:<\?>)?\[\]\s*\{(.*)\}', merge[0][1], flags=re.S)
                params = split_args(match[1]) if match else [merge[0][1]]
                if params == ['']: params = []
            types = [class_type(p, imports) for p in params]
            for member in names:
                add('M', owner, member)
                if all(types): add('H', owner, member, ','.join(types) or '-')
                else: gaps.append(name + ': ' + member + ' dynamic parameters')
        for prefix, array in re.findall(r'ClassLoad\.getClass\(\s*"([^"]+)"\s*\+\s*(\w+)\[', source):
            values = re.search(r'\b' + re.escape(array) + r'\s*=\s*\{([^}]+)\}', source)
            if not values: raise ValueError('Untracked dynamic class array: ' + name + ':' + array)
            for value in re.findall(r'"([^"]+)"', values[1]): add('C', prefix + value)
        if name == 'features/ghostMode/HideSeen.java':
            for owner in re.findall(r'(?:objectName|msgName)\s*=\s*"([^"]+)"', source): add('F', owner, 'peer')
    return sites, sorted(set(gaps))

def main():
    table = json.loads((ROOT / 'app/src/main/assets/clients/Telegram.json').read_text(encoding='utf-8'))
    fallback = {}
    for kind, group in [('C', 'classes'), ('F', 'fields'), ('M', 'methods')]:
        for row in table[group]: fallback[kind, row['o'] if kind == 'C' else row['c'] + '#' + row['o']] = row['r']
    sites, gaps = collect()
    if not sites: raise ValueError('Empty source inventory')
    out = Path(sys.argv[1]); out.parent.mkdir(parents=True, exist_ok=True)
    lines = []
    for kind, owner, member, source, signature in sorted(sites):
        key = owner if kind == 'C' else owner + '#' + member
        lines.append('\t'.join([kind, owner, member, source, fallback.get(('M' if kind == 'H' else kind, key), ''), signature]))
    out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    out.with_name('dynamic-sites.txt').write_text('\n'.join(gaps) + '\n', encoding='utf-8')
    print(f'Inventory: {len(sites)} sites, {sum(s[0] == "H" for s in sites)} exact hook signatures; {len(gaps)} dynamic selectors listed separately')
if __name__ == '__main__': main()
