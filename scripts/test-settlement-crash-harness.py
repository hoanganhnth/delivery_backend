#!/usr/bin/env python3
"""Focused preflight proofs; no Docker daemon or application runtime required."""
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).with_name('verify-settlement-crash-window.sh')
CALLBACK = 'com/delivery/settlement_service/listener/DeliveryCompletedEventListener$1.class'


class HarnessPreflight(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'scripts').mkdir()
        shutil.copyfile(SCRIPT, self.root / 'scripts' / SCRIPT.name)
        (self.root / 'pom.xml').write_text('<project/>')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        docker = self.bin / 'docker'
        docker.write_text('#!/bin/sh\necho "$*" >> "$DOCKER_CALL_LOG"\nexit 1\n')
        docker.chmod(0o755)

    def tearDown(self):
        self.temp.cleanup()

    def package(self, path, nested=False):
        jar = self.root / path
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, 'w') as boot:
            if nested:
                library = io.BytesIO()
                with zipfile.ZipFile(library, 'w') as z:
                    z.writestr(CALLBACK, b'fixture')
                boot.writestr('BOOT-INF/lib/settlement-infrastructure.jar', library.getvalue())
            else:
                boot.writestr('BOOT-INF/classes/' + CALLBACK, b'fixture')
        return jar

    def run_script(self, **extra):
        env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'],
                   DOCKER_CALL_LOG=str(self.root / 'docker.log'))
        env.update(extra)
        return subprocess.run(['bash', str(self.root / 'scripts' / SCRIPT.name)],
                              env=env, text=True, capture_output=True)

    def test_missing_package_fails_before_docker(self):
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Missing', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_relocated_layout_never_falls_back_to_legacy_jar(self):
        self.package('settlement-service/target/settlement-service-0.0.1-SNAPSHOT.jar')
        (self.root / 'settlement/boot').mkdir(parents=True)
        (self.root / 'settlement/boot/pom.xml').write_text('<project/>')
        result = self.run_script()
        self.assertIn('settlement/boot/target', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_explicit_current_package_reaches_only_readonly_docker_preflight(self):
        jar = self.package('custom/current.jar')
        result = self.run_script(SETTLEMENT_CRASH_JAR=str(jar))
        self.assertIn('Docker daemon is unavailable', result.stderr)
        self.assertEqual((self.root / 'docker.log').read_text(), 'info\n')

    def test_stale_package_rejected_before_docker(self):
        jar = self.package('settlement-service/target/settlement-service-0.0.1-SNAPSHOT.jar')
        source = self.root / 'settlement-service/src/main/resources/application.properties'
        source.parent.mkdir(parents=True)
        source.write_text('changed=true')
        os.utime(jar, ns=(1, 1))
        result = self.run_script()
        self.assertIn('Stale Settlement JAR', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_callback_check_accepts_host_and_nested_library_rejects_absent(self):
        text = SCRIPT.read_text()
        checker = text.split("<<'PYCLASS'\n", 1)[1].split('\nPYCLASS', 1)[0]
        for nested in (False, True):
            jar = self.package(f'check-{nested}.jar', nested=nested)
            result = subprocess.run(['python3', '-c', checker, str(jar)], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
        jar = self.root / 'absent.jar'
        with zipfile.ZipFile(jar, 'w'):
            pass
        result = subprocess.run(['python3', '-c', checker, str(jar)], capture_output=True)
        self.assertNotEqual(result.returncode, 0)


if __name__ == '__main__':
    unittest.main()
