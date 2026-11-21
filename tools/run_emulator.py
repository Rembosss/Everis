#!/usr/bin/env python3
"""Start the installed prototype and a dedicated Logcat terminal; never builds an APK.

--clear-data requires an explicit user request: it deletes only this app's emulator data.
"""
import argparse
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--clear-data', action='store_true')
parser.add_argument('--boot-only', action='store_true', help='Boot with live Logcat; do not launch or modify an app')
parser.add_argument('--avd', default='Diary_API_37')
parser.add_argument('--port', type=int, default=5554)
parser.add_argument('--package', default='org.everis.app.debug')
parser.add_argument('--headless', action='store_true')
parser.add_argument('--memory-mb', type=int, help='Temporary emulator RAM override; preserves the AVD configuration')
args = parser.parse_args()
if args.memory_mb is not None and not 1024 <= args.memory_mb <= 8192:
    parser.error('--memory-mb must be between 1024 and 8192')
if args.boot_only and args.clear_data:
    parser.error('--boot-only cannot clear application data')
if args.port < 5554 or args.port > 5682 or args.port % 2:
    parser.error('Emulator port must be an even number from 5554 to 5682')
project = Path(__file__).resolve().parent.parent
sdk = Path.home() / 'Android/Sdk'
adb = str(sdk / 'platform-tools/adb')
serial = f'emulator-{args.port}'
package = args.package
log_directory = project / 'app/build'
log_directory.mkdir(parents=True, exist_ok=True)

def command(*arguments, timeout=30):
    return subprocess.run([adb, '-s', serial, *arguments], capture_output=True, text=True, timeout=timeout)

# Start the console first. It waits for ADB/package manager while the emulator boots.
subprocess.Popen(['konsole', '--separate', '--hold', '-p', 'tabtitle=Everis — Logcat', '-e', 'python3', str(project / 'tools/app_logcat.py'), '--serial', serial, '--package', package],
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
status = command('get-state')
process = None
if status.returncode != 0:
    with (log_directory / 'emulator-window.log').open('ab') as log:
        process = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', args.avd, '-port', str(args.port), '-gpu', 'swiftshader', '-no-snapshot', '-no-boot-anim'] + (['-no-window'] if args.headless else []) + (['-memory', str(args.memory_mb)] if args.memory_mb is not None else []),
                                   stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    print('Emulator starting, PID', process.pid, flush=True)
for _ in range(120):
    status = command('shell', 'getprop', 'sys.boot_completed', timeout=5)
    if status.returncode == 0 and status.stdout.strip() == '1':
        break
    if process is not None and process.poll() is not None:
        raise SystemExit('Emulator exited. See app/build/emulator-window.log')
    time.sleep(1)
else:
    raise SystemExit('Emulator boot timeout; debug terminal was left open')
if args.boot_only:
    print(f'{serial} booted with a dedicated live Logcat terminal. No app launched or data cleared.', flush=True)
    raise SystemExit(0)
if not command('shell', 'pm', 'path', package).stdout.strip():
    raise SystemExit('No installed prototype. This launcher does not build or install APKs.')
if args.clear_data:
    cleared = command('shell', 'pm', 'clear', package)
    if cleared.returncode != 0 or 'Success' not in cleared.stdout:
        raise SystemExit('Could not clear app data: ' + cleared.stderr)
    print('Application data cleared', flush=True)
for arguments in [('shell', 'cmd', 'uimode', 'night', 'yes'), ('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'),
                  ('shell', 'wm', 'dismiss-keyguard'), ('shell', 'am', 'start', '-W', '-n', package + '/org.everis.app.MainActivity')]:
    result = command(*arguments)
    print(result.stdout.strip(), flush=True)
    if result.returncode != 0:
        raise SystemExit(result.stderr)
print('Installed prototype opened with dark system theme and a dedicated live Logcat terminal. No build performed.', flush=True)
