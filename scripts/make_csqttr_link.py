#!/usr/bin/env python3
"""Generate AES-GCM encrypted csqttr:// links compatible with CSQTT 2.5.

Requires: pip install cryptography
Does NOT accept/store MAX access tokens. Such tokens belong only in Android Settings.
"""
import argparse
import base64
import hashlib
import hmac
import re
from urllib.parse import urlencode

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError as e:
    raise SystemExit('Install dependency first: python -m pip install cryptography') from e

SEED = b'csqtt-chiper-key'  # Must match web_panel.rs / UiUtils.kt exactly
KEY = hashlib.sha256(SEED).digest()


def encrypt_link(payload: str) -> str:
    raw = payload.encode('utf-8')
    nonce = hmac.new(KEY, raw, hashlib.sha256).digest()[:12]
    encrypted = nonce + AESGCM(KEY).encrypt(nonce, raw, b'')
    return 'csqttr://' + base64.urlsafe_b64encode(encrypted).decode('ascii').rstrip('=')


def decrypt_link(link: str) -> str:
    if not link.startswith('csqttr://'):
        raise ValueError('Supported scheme: csqttr:// only')
    value = link.removeprefix('csqttr://')
    raw = base64.urlsafe_b64decode(value + '=' * (-len(value) % 4))
    return AESGCM(KEY).decrypt(raw[:12], raw[12:], b'').decode('utf-8')


def main():
    parser = argparse.ArgumentParser(description='Create MAX or VK encrypted csqttr:// link')
    parser.add_argument('--host', required=True)
    parser.add_argument('--peer', required=True, type=int)
    parser.add_argument('--password', required=True)
    choice = parser.add_mutually_exclusive_group(required=True)
    choice.add_argument('--max', metavar='GROUP_ID', help='numeric MAX chat/group ID')
    choice.add_argument('--hashes', metavar='VK_HASH1[,HASH2...]')
    args = parser.parse_args()
    if not 1 <= args.peer <= 65535:
        parser.error('peer must be in range 1..65535')
    if not args.host.strip() or args.host in {'127.0.0.1', '0.0.0.0', 'localhost'}:
        parser.error('host must be a remote address')
    if not args.password:
        parser.error('password cannot be empty')
    params = [('v', '2'), ('host', args.host), ('peer', str(args.peer)), ('password', args.password)]
    if args.max is not None:
        if not args.max or len(args.max) > 64 or re.fullmatch(r'-?[0-9]+', args.max) is None:
            parser.error('MAX group ID must be numeric, optionally negative')
        params.append(('max', args.max))
    else:
        hashes = [s.strip() for s in args.hashes.split(',') if s.strip()]
        if not 1 <= len(hashes) <= 6 or any(len(h) < 16 for h in hashes):
            parser.error('provide 1..6 VK hashes (length >= 16)')
        params.append(('hashes', '+'.join(hashes)))
    # Match Kotlin parser: + in hashes is a separator, not an encoded space.
    query = urlencode(params, safe='+')
    print(encrypt_link('connect?' + query))


if __name__ == '__main__':
    main()
