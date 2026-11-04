#!/usr/bin/env python3
"""Application UID log stream, including subsequent application process restarts."""
import os
from pathlib import Path
import re
import subprocess
import time
import argparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--package', default='org.everis.app.debug')
args = parser.parse_args()

adb = str(Path.home() / 'Android/Sdk/platform-tools/adb')
serial = args.serial
package = args.package
print('Everis live Logcat · waiting for emulator and package manager…', flush=True)
match = None
for _ in range(180):
    try:
        result = subprocess.run([adb, '-s', serial, 'shell', 'cmd', 'package', 'list', 'packages', '-U', package],
                                capture_output=True, text=True, timeout=10)
    except subprocess.TimeoutExpired:
        time.sleep(1)
        continue
    match = re.search(r'package:' + re.escape(package) + r'\s+uid:(\d+)', result.stdout)
    if match:
        break
    time.sleep(1)
if not match:
    raise SystemExit(f'No {package} found on {serial}. This console does not install APKs.')
print('Everis live Logcat · Ctrl+C to stop', flush=True)
os.execv(adb, [adb, '-s', serial, 'logcat', '--uid=' + match.group(1), '-v', 'threadtime', '-T', '1'])
