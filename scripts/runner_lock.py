#!/usr/bin/env python3
"""Verify an inherited runner token against its live ancestor's POSIX kernel lock."""
import argparse
import json
import os
from pathlib import Path
import sys


def verify(state, token):
    state = Path(state).absolute()
    for path in [*reversed(state.parents), state, state / 'owner.json', state / 'runner.lock']:
        if path.is_symlink():
            raise ValueError(f'unsafe runner lock path: {path}')
    owner = json.loads((state / 'owner.json').read_text())
    if not token or owner.get('token') != token:
        raise ValueError('runner lock token mismatch')
    pid = owner['pid']
    current = os.getpid()
    ancestors = set()
    while current > 1 and current not in ancestors:
        ancestors.add(current)
        status = Path(f'/proc/{current}/status').read_text()
        current = int(next(line.split()[1] for line in status.splitlines() if line.startswith('PPid:')))
    if pid not in ancestors:
        raise ValueError('runner owner is not a live ancestor')
    st = (state / 'runner.lock').stat()
    key = (os.major(st.st_dev), os.minor(st.st_dev), st.st_ino)
    for line in Path('/proc/locks').read_text().splitlines():
        fields = line.split()
        if len(fields) < 8 or fields[1:4] != ['POSIX', 'ADVISORY', 'WRITE']:
            continue
        device = fields[5].split(':')
        if int(fields[4]) == pid and (int(device[0], 16), int(device[1], 16), int(device[2])) == key and fields[6] == '0' and fields[7] == 'EOF':
            return owner
    raise ValueError('runner owner does not hold the full kernel lock')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state', default=os.environ.get('BC_PACK_TEST_STATE_ROOT', str(Path.home() / '.local/share/worklane/pack-tests')))
    parser.add_argument('--token', default=os.environ.get('BC_PACK_TEST_LOCK_TOKEN'))
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.state, args.token)))
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(75)
