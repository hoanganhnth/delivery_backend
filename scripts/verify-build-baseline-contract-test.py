#!/usr/bin/env python3
"""Exercise the actual envelope/secret gate against isolated controller fixtures."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = (ROOT / "scripts/verify-build-baseline.sh").read_text()
START = SCRIPT.index("# The existing principal lookup is an internal wire contract")
END = SCRIPT.index("if rg -n -U 'BaseResponse", START)
GATE = SCRIPT[START:END]
CONTROLLER_PATH = Path("auth/infrastructure/src/main/java/com/delivery/auth_service/controller/PrincipalInternalController.java")
SOURCE = (ROOT / CONTROLLER_PATH).read_text()


class EnvelopeBoundaryGateTest(unittest.TestCase):
    def run_gate(self, controller=SOURCE, public_source=None):
        with tempfile.TemporaryDirectory(prefix="auth-envelope-gate-") as directory:
            fixture = Path(directory)
            principal = fixture / CONTROLLER_PATH
            principal.parent.mkdir(parents=True)
            principal.write_text(controller)
            if public_source is not None:
                public = fixture / "example/src/main/java/example/PublicController.java"
                public.parent.mkdir(parents=True)
                public.write_text(public_source)
            return subprocess.run(
                ["bash", "-c", 'set -euo pipefail\nROOT_DIR="$1"\n' + GATE, "gate", directory],
                text=True, capture_output=True, check=False,
            )

    def test_existing_internal_wire_contract_is_accepted(self):
        result = self.run_gate()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_public_route_cannot_use_internal_exemption(self):
        result = self.run_gate(SOURCE.replace('/api/auth/internal/principals', '/api/auth/principals'))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("mandatory secret guard", result.stderr)

    def test_removed_secret_authorization_is_rejected(self):
        result = self.run_gate(SOURCE.replace('authorize(suppliedSecret);', ''))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("mandatory secret guard", result.stderr)

    def test_constant_time_secret_check_remains_required(self):
        result = self.run_gate(SOURCE.replace('MessageDigest.isEqual(', 'java.util.Arrays.equals('))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("mandatory secret guard", result.stderr)

    def test_another_raw_public_controller_is_still_rejected(self):
        result = self.run_gate(public_source='public ResponseEntity<String> get() { return null; }')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("canonical BaseResponse envelope", result.stderr)


if __name__ == "__main__":
    unittest.main()
