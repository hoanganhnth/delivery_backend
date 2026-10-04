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


class SchedulingCapabilityGateTest(unittest.TestCase):
    path = Path("tracking/infrastructure/src/main/java/com/delivery/tracking_service/config/PublisherSessionConfig.java")
    safe = '@ConditionalOnProperty(name = "spring.task.scheduling.enabled", havingValue = "true", matchIfMissing = true)'

    def run_gate(self, source, path=None):
        start = SCRIPT.index('unsafe_match_if_missing=')
        end = SCRIPT.index('\nauth_properties=', start)
        with tempfile.TemporaryDirectory(prefix="tracking-scheduling-gate-") as directory:
            target = Path(directory) / (path or self.path)
            target.parent.mkdir(parents=True)
            target.write_text(source)
            return subprocess.run(['bash', '-eu', '-c', 'ROOT_DIR="$1"\n' + SCRIPT[start:end], 'gate', directory], text=True, capture_output=True)

    def test_exact_documented_periodic_scheduler_is_allowed(self):
        self.assertEqual(self.run_gate(self.safe).returncode, 0)

    def test_other_default_enabled_capability_in_same_file_is_rejected(self):
        self.assertNotEqual(self.run_gate(self.safe + '\n' + self.safe.replace('spring.task.scheduling.enabled', 'app.feature.enabled')).returncode, 0)

    def test_same_annotation_in_another_class_is_rejected(self):
        self.assertNotEqual(self.run_gate(self.safe, Path('other/src/main/java/Other.java')).returncode, 0)

    def test_changed_property_is_not_allowlisted(self):
        self.assertNotEqual(self.run_gate(self.safe.replace('spring.task.scheduling.enabled', 'app.optional.enabled')).returncode, 0)


class CodSchedulingCapabilityGateTest(SchedulingCapabilityGateTest):
    path = Path("settlement-service/src/main/java/com/delivery/settlement_service/config/CodCapacitySchedulingConfig.java")


if __name__ == "__main__":
    unittest.main()
