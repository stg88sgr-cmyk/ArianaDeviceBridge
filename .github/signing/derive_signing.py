#!/usr/bin/env python3
"""Derive Ariana X-88's stable sideload private key from one GitHub secret.

The signing certificate is public and committed to the repository. The private
key is reconstructed only inside the GitHub Actions runner from
ARIANA_SIGNING_SEED and is never written to the repository.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import pkcs12

P256_ORDER = int(
    "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16
)
DOMAIN = b"ARIANA-X88-SIGNING-v1\0"
ALIAS = b"ariana-sideload"


def derive_key(seed: str) -> ec.EllipticCurvePrivateKey:
    normalized = seed.strip()
    if len(normalized) < 32:
        raise ValueError("ARIANA_SIGNING_SEED must contain at least 32 characters")
    digest = hashlib.sha256(DOMAIN + normalized.encode("utf-8")).digest()
    scalar = (int.from_bytes(digest, "big") % (P256_ORDER - 1)) + 1
    return ec.derive_private_key(scalar, ec.SECP256R1())


def spki_bytes(key) -> bytes:
    return key.public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--certificate", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    seed = os.environ.get("ARIANA_SIGNING_SEED", "").strip()
    if not seed:
        raise SystemExit("ARIANA_SIGNING_SEED is empty")

    cert = x509.load_pem_x509_certificate(Path(args.certificate).read_bytes())
    key = derive_key(seed)
    if spki_bytes(cert.public_key()) != spki_bytes(key.public_key()):
        raise SystemExit(
            "ARIANA_SIGNING_SEED does not match the committed signing certificate"
        )

    blob = pkcs12.serialize_key_and_certificates(
        name=ALIAS,
        key=key,
        cert=cert,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(seed.encode("utf-8")),
    )
    target = Path(args.output)
    target.write_bytes(blob)
    os.chmod(target, 0o600)


if __name__ == "__main__":
    main()
