#!/usr/bin/env python3
"""Verify already-built APKs and export into Releases/<version>/{Debug,Release}. No devices."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess

project = Path(__file__).resolve().parent.parent
sdk = Path.home() / 'Android/Sdk/build-tools/36.0.0'
configuration = (project / 'app/build.gradle.kts').read_text()
version = re.search(r'versionName = "([^"]+)"', configuration).group(1)
code = int(re.search(r'versionCode = (\d+)', configuration).group(1))
release_root = project / 'Releases' / version

def run(*args):
    return subprocess.run([str(a) for a in args], capture_output=True, text=True, check=True).stdout

def verify(apk, abi, machine, debug):
    run(sdk / 'zipalign', '-c', '-P', '16', '4', apk)
    badging = run(sdk / 'aapt2', 'dump', 'badging', apk)
    application = 'org.everis.app.debug' if debug else 'org.everis.app'
    assert f"name='{application}'" in badging and f"versionCode='{code}'" in badging
    assert f"versionName='{version}'" in badging
    assert "minSdkVersion:'31'" in badging and "targetSdkVersion:'37'" in badging
    assert "application-label:'Everis'" in badging
    assert ('application-debuggable' in badging) == debug
    with __import__('zipfile').ZipFile(apk) as archive:
        libraries = [n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
        assert {n.split('/')[1] for n in libraries} == {abi}
        for name in libraries:
            data = archive.read(name)
            assert data[:4] == b'\x7fELF' and data[4] == 2 and data[5] == 1, name
            assert struct.unpack_from('<H', data, 18)[0] == machine, name
            offset = struct.unpack_from('<Q', data, 32)[0]
            entry_size, entries = struct.unpack_from('<HH', data, 54)
            loads = 0
            for i in range(entries):
                entry = offset + i * entry_size
                if struct.unpack_from('<I', data, entry)[0] != 1:
                    continue
                loads += 1
                file_offset, virtual_address = struct.unpack_from('<QQ', data, entry + 8)
                alignment = struct.unpack_from('<Q', data, entry + 48)[0]
                assert alignment >= 16384 and (virtual_address - file_offset) % 16384 == 0, name
            assert loads > 0
    return application

report = []
for variant, abi, machine in [('debug', 'arm64-v8a', 183)]:
    apk = project / f'app/build/outputs/apk/{variant}/app-{variant}.apk'
    cert = run(sdk / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in cert
    fingerprint = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', cert).group(1)
    application = verify(apk, abi, machine, debug=True)
    destination = release_root / 'Debug' / f'Everis-{version}-{abi}-debug.apk'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, destination)
    report.append({'file': str(destination.relative_to(project)), 'applicationId': application, 'versionName': version,
                   'versionCode': code, 'abi': abi, 'debuggable': True, 'signed': True, 'minSdk': 31, 'targetSdk': 37,
                   'bytes': destination.stat().st_size, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
                   'debugCertificateSha256': fingerprint, 'nativeAlignment': 16384,
                   'verifiedAtUtc': datetime.now(timezone.utc).isoformat()})
    print('VERIFIED', destination.relative_to(project), flush=True)
assert len(report) == 1

previous = project / 'Releases/0.0.2-prototype/verification.json'
if previous.exists():
    old = json.loads(previous.read_text())
    if old and old[0].get('debugCertificateSha256'):
        assert old[0]['debugCertificateSha256'] == report[0]['debugCertificateSha256'], 'Debug signing identity changed'

release_candidates = [project / 'app/build/outputs/apk/release' / name for name in ('app-release.apk', 'app-release-unsigned.apk')]
release_apk = next((p for p in release_candidates if p.exists() and f"versionName='{version}'" in run(sdk / 'aapt2', 'dump', 'badging', p)), None)
if release_apk is not None:
    signed = release_apk.name == 'app-release.apk'
    certificate = {}
    if signed:
        # Expand signature verification to API 24 so v2 is checked too; at minSdk 31
        # apksigner normally selects v3 and reports v2 as unused, not absent.
        cert = run(sdk / 'apksigner', 'verify', '--verbose', '--min-sdk-version', '24', '--print-certs', release_apk)
        assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in cert
        assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in cert
        fingerprint = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', cert).group(1)
        identity = json.loads((project / 'docs/signing/release-identity.json').read_text())
        assert fingerprint == identity['certificateSha256'], 'Unexpected Release signing identity'
        assert fingerprint != report[0]['debugCertificateSha256'], 'Release must not use the Debug key'
        certificate = {'releaseCertificateSha256': fingerprint, 'signatureSchemes': ['v2', 'v3']}
    application = verify(release_apk, 'arm64-v8a', 183, debug=False)
    destination = release_root / 'Release' / f'Everis-{version}-arm64-v8a-release{"" if signed else "-unsigned"}.apk'
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(release_apk, destination)
    report.append({'file': str(destination.relative_to(project)), 'applicationId': application, 'versionName': version,
                   'versionCode': code, 'abi': 'arm64-v8a', 'debuggable': False, 'signed': signed, 'installable': signed,
                   'bytes': destination.stat().st_size, 'sha256': hashlib.sha256(destination.read_bytes()).hexdigest(),
                   'nativeAlignment': 16384, 'minSdk': 31, 'targetSdk': 37,
                   'verifiedAtUtc': datetime.now(timezone.utc).isoformat(), **certificate})
    print('VERIFIED signed Release:' if signed else 'VERIFIED unsigned Release (requires signing):', destination.relative_to(project), flush=True)
(release_root / 'verification.json').write_text(json.dumps(report, indent=2) + '\n')
(project / 'Releases/LATEST.json').write_text(json.dumps({'versionName': version, 'versionCode': code, 'artifacts': report}, indent=2) + '\n')
print('Signing identities preserved; ARM64 only; ZIP and ELF 16 KB alignment passed.')
