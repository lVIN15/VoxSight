import subprocess
import sys

from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent

suites = [
    ('Task 1B (Dedicated Suite)', [sys.executable, str(TEST_DIR / 'test_task_1b.py')]),
    ('Task 1B-M (Renderer Live Asset Contract)', ['node', str(TEST_DIR / 'test_task_1b_m.js')]),
    ('Task 1A.1 (Safe Classifier Suite)', [sys.executable, str(TEST_DIR / 'test_task_1a1.py')]),
    ('Phase 1A.2 (Diagnostic Isolation Suite)', [sys.executable, str(TEST_DIR / 'test_phase1a2.py')]),
    ('Task 1A.3 (Pre-Scan & Stamping Suite)', [sys.executable, str(TEST_DIR / 'test_task_1a3.py')]),
    ('MXL Round-Trip Stamping Test', [sys.executable, str(TEST_DIR / 'test_mxl_stamping.py')]),
    ('Remediation Regression Suite (Findings 1-4)', [sys.executable, str(TEST_DIR / 'test_remediation_suite.py')]),
]

all_ok = True
results = []
for name, cmd in suites:
    p = subprocess.run(cmd, capture_output=True, text=True)
    status = 'PASS' if p.returncode == 0 else 'FAIL'
    results.append((name, status))
    if p.returncode != 0:
        all_ok = False
        print(f'FAILED: {name}')
        print(p.stdout[-500:])
        print(p.stderr[-500:])

print('=' * 65)
print('TASK 1B & PHASE 1A COMPREHENSIVE REGRESSION SUMMARY')
print('=' * 65)
for name, status in results:
    print(f'[{status}] {name}')
print('=' * 65)
if all_ok:
    print('ALL 7 SUITES PASSED CLEANLY (100% SUCCESS RATE)')
else:
    print('ONE OR MORE SUITES FAILED')
print('=' * 65)
sys.exit(0 if all_ok else 1)
