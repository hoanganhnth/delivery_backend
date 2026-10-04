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

    def test_relocated_runtime_dependency_source_invalidates_package(self):
        namespace = 'xmlns="http://maven.apache.org/POM/4.0.0"'
        boot = self.root / 'settlement/boot/pom.xml'
        boot.parent.mkdir(parents=True)
        boot.write_text(f'<project {namespace}><artifactId>settlement-service</artifactId>'
                        '<dependencies><dependency><groupId>com.delivery</groupId>'
                        '<artifactId>settlement-infrastructure</artifactId></dependency></dependencies></project>')
        infrastructure = self.root / 'settlement/infrastructure'
        infrastructure.mkdir()
        (infrastructure / 'pom.xml').write_text(
            f'<project {namespace}><artifactId>settlement-infrastructure</artifactId></project>')
        jar = self.package('settlement/boot/target/settlement-service-0.0.1-SNAPSHOT.jar', nested=True)
        result = self.run_script()
        self.assertIn('Docker daemon is unavailable', result.stderr)
        self.assertEqual((self.root / 'docker.log').read_text(), 'info\n')
        (self.root / 'docker.log').unlink()
        source = infrastructure / 'src/main/java/Adapter.java'
        source.parent.mkdir(parents=True)
        source.write_text('class Adapter {}')
        os.utime(source, ns=(jar.stat().st_mtime_ns + 1000000,) * 2)
        result = self.run_script()
        self.assertIn('Stale Settlement JAR', result.stderr)
        self.assertIn('Adapter.java', result.stderr)
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

    def test_shared_runtime_source_invalidates_package(self):
        namespace = 'xmlns="http://maven.apache.org/POM/4.0.0"'
        boot = self.root / 'settlement-service/pom.xml'
        boot.parent.mkdir()
        boot.write_text(f'<project {namespace}><artifactId>settlement-service</artifactId>'
                        '<dependencies><dependency><groupId>com.delivery</groupId>'
                        '<artifactId>fixture-shared-starter</artifactId></dependency></dependencies></project>')
        shared = self.root / 'libs/fixture-shared-starter'
        shared.mkdir(parents=True)
        (shared / 'pom.xml').write_text(f'<project {namespace}><artifactId>fixture-shared-starter</artifactId></project>')
        jar = self.package('settlement-service/target/settlement-service-0.0.1-SNAPSHOT.jar')
        source = shared / 'src/main/java/Shared.java'
        source.parent.mkdir(parents=True)
        source.write_text('class Shared {}')
        os.utime(source, ns=(jar.stat().st_mtime_ns + 1000000,) * 2)
        result = self.run_script()
        self.assertIn('Stale Settlement JAR', result.stderr)
        self.assertIn('Shared.java', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_transient_coordinator_failure_is_retried_under_pipefail(self):
        docker = self.bin / 'docker'
        docker.write_text('#!/bin/sh\nif [ ! -f "$DOCKER_CALL_LOG" ]; then touch "$DOCKER_CALL_LOG"; exit 1; fi\n'
                          'echo "group fixture.topic 0 0 0 0 consumer host client"\n')
        text = SCRIPT.read_text()
        functions = text[text.index('bounded_command()'):text.index('database_invariants_hold()')]
        command = ('set -euo pipefail\n' + functions +
                   '\nKAFKA_CONTAINER=owned TEST_GROUP=group TEST_TOPIC=fixture.topic TIMEOUT_SECONDS=5\n'
                   'wait_for_group_assignment\ngroup_field 4\n')
        result = subprocess.run(['bash', '-c', command], text=True, capture_output=True,
                                env=dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'],
                                         DOCKER_CALL_LOG=str(self.root / 'docker.log')), timeout=8)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, '0\n')

    def test_command_timeout_is_bounded(self):
        import time
        text = SCRIPT.read_text()
        function = text[text.index('bounded_command()'):text.index('group_field()')]
        started = time.monotonic()
        result = subprocess.run(['bash', '-c', function + '\nbounded_command 0.15 python3 -c "import time; time.sleep(30)"'],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 124)
        self.assertLess(time.monotonic() - started, 3)

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
