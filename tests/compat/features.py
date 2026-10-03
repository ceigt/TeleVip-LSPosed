"""Turn the bytecode inventory into an enforced, feature-level verdict.
Rules describe actual client branches, never grant an unknown symbol a blanket exemption.
Inspired by Re:TeleVIP CallSites; kept separate from runtime hooking.
"""
from collections import Counter
from pathlib import Path
import sys
from inventory import JAVA

PLAY = 'org.telegram.messenger'
LEGACY_SOURCES = {
    'settings/hook/SettingsHook.java', 'settings/ui/SettingsActivity.java',
    'virtuals/TeleVip/Bridge/Bridge.java', 'virtuals/Adapters/DrawerLayoutAdapter.java',
    'virtuals/ui/Components/RecyclerListView.java', 'virtuals/androidx/Adapter.java',
    'virtuals/ui/Cells/ShadowSectionCell.java', 'virtuals/ui/Cells/TextCheckCell.java',
}
PLAY_OTHER_BRANCHES = {
    ('PhotoViewer', 'openPhoto', 'virtuals/ui/PhotoViewer.java'),
    ('PhotoViewer', 'setParentActivity', 'virtuals/ui/PhotoViewer.java'),
    ('SecretMediaViewer', 'openMedia', 'virtuals/ui/SecretMediaViewer.java'),
    ('ChatActivity', 'sendSecretMediaDelete', 'features/media/PreventMedia.java'),
    ('ChatActivity', 'sendSecretMessageRead', 'features/media/PreventMedia.java'),
}
DYNAMIC = {
    'Clients/Telegraph.java: dynamic hook owner/signature': 'Telegraph-only feature; Play never runs it',
    'features/other/TelePremium.java: dynamic hook owner/signature': 'ForkPremiumPreferenc fallback; primary UserConfig.isPremium is audited',
    'settings/hook/SettingsHook.java: onClick dynamic parameters': 'legacy settings entry; Play uses NativeSettingsEntry',
    'virtuals/TeleVip/Bridge/Bridge.java: dynamic hook owner/signature': 'injected legacy adapter; Play uses platform settings dialog',
}
PLATFORM = {('android.app.Activity', 'onCreate'), ('android.app.Activity', 'onResume'), ('android.app.Application', 'attach')}

def classify(row, package):
    status, kind, owner, member, source, *_ = row
    if package == PLAY:
        if source in LEGACY_SOURCES: return 'INACTIVE', 'Play uses NativeSettingsEntry and SettingsFallback, bypassing the legacy adapter/drawer'
        if (owner, member, source) in PLAY_OTHER_BRANCHES: return 'INACTIVE', 'non-Play branch; Play uses the db/N4 hooks and regular photo viewer'
        if source in {'Clients/Telegraph.java', 'features/ui/HideProxySponsor.java'}: return 'INACTIVE', 'not started by the Play configuration; AdBlock replaces HideProxySponsor'
    if source == 'settings/NativeSettingsEntry.java' and kind == 'M' and (owner, member) in {('SettingsActivity', 'fillItems'), ('SettingsActivity', 'onClick'), ('SettingsActivity$SettingCell$Factory', 'of'), ('SettingsActivity$SettingCell$Factory', 'ofIIIICCC')}:
        return 'ROUTE', 'factory and direct/callback alternatives validated by SettingsRouteAudit'
    if (owner, member) in PLATFORM and source == 'MainHook.java': return 'PLATFORM', 'Android framework member, outside the client APK; startup checked on device'
    if kind == 'C' and owner.startswith('com.televip.SettingsAdapter.'): return 'MODULE', 'injected module DEX, outside the host APK'
    return ('PASS', '') if status == 'RESOLVED' else ('FAIL', 'required client symbol/signature is missing')

def verdict(rows, package):
    if not rows: raise ValueError('Empty compatibility inventory')
    result = []
    for row in rows:
        if len(row) != 8 or row[0] not in {'RESOLVED', 'UNRESOLVED'}: raise ValueError('Malformed call-site row')
        label, reason = classify(row, package)
        result.append((label, reason, row))
    return result

def main(report):
    package = (report / 'package.txt').read_text(encoding='utf-8').strip()
    rows = [line.split('\t') for line in (report / 'call-sites.tsv').read_text(encoding='utf-8').splitlines()]
    result = verdict(rows, package)
    gaps = (report / 'dynamic-sites.txt').read_text(encoding='utf-8').splitlines()
    unknown = [gap for gap in gaps if gap not in DYNAMIC]
    stale = [gap for gap in DYNAMIC if gap not in gaps]
    if unknown or stale: raise ValueError(f'Dynamic-selector review required: new={unknown}, stale={stale}')
    # Source changes must not silently invalidate the branch exemptions.
    for file, marker in [('TeleVip.java', 'NativeSettingsEntry.init();'), ('features/media/PreventMedia.java', 'ClientManager.Client.Telegram'), ('features/media/SecretMediaSave.java', '!ClientManager.is(ClientManager.Client.Telegram)')]:
        if marker not in (JAVA / file).read_text(encoding='utf-8'): raise ValueError('Review client branches after source change: ' + file)
    counts = Counter(item[0] for item in result)
    lines = [f'# Client compatibility: {package}', '',
             'Static symbol/signature checks do not prove media, networking or account behavior. Functional device tests remain required.', '',
             f'Inventory: {len(rows)} sites; exact hook signatures: {sum(row[1] == "H" for row in rows)}.',
             '; '.join(f'{key}: {value}' for key, value in sorted(counts.items())), '',
             '| Source / feature | Result | Active sites |', '|---|---|---|']
    sources = sorted({row[4] for row in rows})
    for source in sources:
        items = [item for item in result if item[2][4] == source]
        label = 'FAIL' if any(item[0] == 'FAIL' for item in items) else ('PASS' if any(item[0] == 'PASS' for item in items) else 'OUTSIDE CLIENT')
        lines.append(f'| {source} | {label} | {sum(item[0] in {"PASS", "FAIL", "ROUTE"} for item in items)} |')
    lines += ['', '## Inactive and external paths', '']
    for label, reason, row in result:
        if label not in {'PASS', 'FAIL'}: lines.append(f'- {label}: {row[2]}#{row[3]} ({row[4]}): {reason}')
    lines += ['', '## Dynamic selectors reviewed', ''] + [f'- {gap}: {DYNAMIC[gap]}' for gap in gaps]
    failures = [item for item in result if item[0] == 'FAIL']
    if failures:
        lines += ['', '## Required sites missing', ''] + [f'- {row[2]}#{row[3]} ({row[4]}), signature `{row[6]}`' for _, _, row in failures]
    (report / 'features.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
    (report / 'feature-verdict.tsv').write_text('\n'.join('\t'.join([label, reason] + row) for label, reason, row in result) + '\n', encoding='utf-8')
    if failures: raise AssertionError(f'{len(failures)} required feature call sites are unresolved; see features.md')
    print(f'FEATURE_AUDIT_PASS: {counts["PASS"]} active sites; {counts["INACTIVE"]} inactive; {counts["PLATFORM"]} framework; {counts["ROUTE"]} route alternatives; {counts["MODULE"]} module DEX')

if __name__ == '__main__': main(Path(sys.argv[1]))
