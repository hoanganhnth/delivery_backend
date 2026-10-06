#!/usr/bin/env python3
"""Fast package, ownership, cleanup and observation proofs without Docker."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = Path(__file__).with_name('verify-saga-match-crash-replay.sh')
HELPER = Path(__file__).with_name('saga_match_crash_fixture.py')
SPEC = importlib.util.spec_from_file_location('fixture', HELPER)
FIXTURE = importlib.util.module_from_spec(SPEC)
sys.dont_write_bytecode = True
SPEC.loader.exec_module(FIXTURE)
NAMESPACE = 'xmlns="http://maven.apache.org/POM/4.0.0"'


class HarnessPreflight(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.original_cwd = Path.cwd()
        os.chdir(self.root)
        (self.root / 'scripts').mkdir()
        for source in (SCRIPT, HELPER):
            shutil.copyfile(source, self.root / 'scripts' / source.name)
        self.pom('.', 'reactor')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        for name in ('docker', 'jq'):
            executable = self.bin / name
            executable.write_text('#!/bin/sh\necho "$*" >> "$DOCKER_CALL_LOG"\nexit 1\n')
            executable.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'],
                        DOCKER_CALL_LOG=str(self.root / 'docker.log'))
        self.env.pop('SAGA_MATCH_CRASH_RUN_ID', None)
        self.env.pop('SAGA_MATCH_CRASH_TIMEOUT_SECONDS', None)

    def tearDown(self):
        os.chdir(self.original_cwd)
        self.temp.cleanup()

    def pom(self, layout, artifact, dependencies='', parent=''):
        path = self.root / layout / 'pom.xml'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f'<project {NAMESPACE}>{parent}<artifactId>{artifact}</artifactId><version>0.0.1-SNAPSHOT</version>'
                        f'<dependencies>{dependencies}</dependencies></project>')
        return path

    def dependency(self, artifact, scope='compile'):
        return ('<dependency><groupId>com.delivery</groupId>'
                f'<artifactId>{artifact}</artifactId><scope>{scope}</scope></dependency>')

    def package(self, service, layout=None):
        layout = layout or FIXTURE.LAYOUTS[service]
        if not (self.root / layout / 'pom.xml').exists():
            self.pom(layout, service)
        jar = self.root / layout / 'target' / f'{service}-0.0.1-SNAPSHOT.jar'
        jar.parent.mkdir(parents=True, exist_ok=True)
        jar.write_bytes(b'packaged fixture')
        return jar

    def packages(self):
        return [self.package(service) for service in FIXTURE.LAYOUTS]

    def run_script(self, arguments=(), **extra):
        return subprocess.run(['bash', str(self.root / 'scripts' / SCRIPT.name), *arguments],
                              env=dict(self.env, **extra), text=True, capture_output=True, timeout=8)

    def test_missing_package_never_calls_docker(self):
        self.pom('dispatch/boot', 'saga-orchestrator-service')
        result = self.run_script()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Missing', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_current_layout_does_not_fall_back_to_newer_legacy_packages(self):
        for service, layout in FIXTURE.LAYOUTS.items():
            with self.subTest(service=service):
                current = self.package(service)
                legacy = self.package(service, service)
                os.utime(legacy, ns=(current.stat().st_mtime_ns + 1000000,) * 2)
                self.assertEqual(FIXTURE.select_jar(service), current)
                current.unlink()
                with self.assertRaisesRegex(ValueError, layout + '/target'):
                    FIXTURE.select_jar(service)
                legacy.unlink()
                (self.root / service / 'pom.xml').unlink()

    def test_both_current_packages_reach_only_readonly_daemon_preflight(self):
        jars = self.packages()
        result = self.run_script()
        self.assertIn('Docker daemon is unavailable', result.stderr)
        self.assertEqual(result.stdout.splitlines(), [str(jar) for jar in jars])
        self.assertEqual((self.root / 'docker.log').read_text(), 'info\n')

    def test_each_boot_source_invalidates_package(self):
        for service, layout in FIXTURE.LAYOUTS.items():
            with self.subTest(service=service):
                jar = self.package(service)
                source = self.root / layout / 'src/main/resources/application.properties'
                source.parent.mkdir(parents=True)
                source.write_text('changed=true')
                os.utime(source, ns=(jar.stat().st_mtime_ns + 1000000,) * 2)
                with self.assertRaisesRegex(ValueError, 'Stale ' + service):
                    FIXTURE.select_jar(service)

    def test_transitive_runtime_shared_source_invalidates_both_packages(self):
        for service, layout in FIXTURE.LAYOUTS.items():
            self.pom(layout, service, self.dependency('runtime-infrastructure'))
        self.pom('runtime/infrastructure', 'runtime-infrastructure', self.dependency('shared-starter'))
        self.pom('shared/starter', 'shared-starter')
        jars = self.packages()
        source = self.root / 'shared/starter/src/main/java/Shared.java'
        source.parent.mkdir(parents=True)
        source.write_text('class Shared {}')
        os.utime(source, ns=(max(jar.stat().st_mtime_ns for jar in jars) + 1000000,) * 2)
        for service in FIXTURE.LAYOUTS:
            with self.assertRaisesRegex(ValueError, 'Shared.java'):
                FIXTURE.select_jar(service)
        result = self.run_script()
        self.assertIn('Stale', result.stderr)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_parent_and_root_poms_are_package_inputs(self):
        parent = self.pom('dispatch', 'dispatch-parent')
        self.pom('dispatch/boot', 'saga-orchestrator-service', parent=(
            '<parent><groupId>com.delivery</groupId><artifactId>dispatch-parent</artifactId>'
            '<version>1</version><relativePath>../pom.xml</relativePath></parent>'))
        jar = self.package('saga-orchestrator-service')
        for source in (parent, self.root / 'pom.xml'):
            with self.subTest(source=source):
                os.utime(source, ns=(jar.stat().st_mtime_ns + 1000000,) * 2)
                with self.assertRaisesRegex(ValueError, 'Stale'):
                    FIXTURE.select_jar('saga-orchestrator-service')
                os.utime(source, ns=(1, 1))

    def test_missing_or_ambiguous_runtime_dependencies_fail_closed(self):
        self.pom('match/boot', 'match-service', self.dependency('runtime'))
        self.package('match-service')
        with self.assertRaisesRegex(ValueError, 'Missing or ambiguous'):
            FIXTURE.select_jar('match-service')
        self.pom('runtime/one', 'runtime')
        self.pom('runtime/two', 'runtime')
        with self.assertRaisesRegex(ValueError, 'Missing or ambiguous'):
            FIXTURE.select_jar('match-service')

    def test_test_and_provided_dependencies_are_not_runtime_inputs(self):
        self.pom('match/boot', 'match-service', self.dependency('absent-test', 'test')
                 + self.dependency('absent-provided', 'provided'))
        jar = self.package('match-service')
        self.assertEqual(FIXTURE.select_jar('match-service'), jar)

    def test_control_plane_package_uses_its_pom_version_not_service_default(self):
        pom = self.pom('config-server', 'config-server')
        pom.write_text(pom.read_text().replace('0.0.1-SNAPSHOT', '1.0.0-SNAPSHOT'))
        jar = self.root / 'config-server/target/config-server-1.0.0-SNAPSHOT.jar'
        jar.parent.mkdir()
        jar.write_bytes(b'fixture')
        self.assertEqual(FIXTURE.select_jar('config-server', 'config-server'), jar)

    def test_invalid_arguments_fail_before_packages_or_docker(self):
        for value in ('', '../canonical', 'a_b', 'a' * 41):
            with self.subTest(run_id=value):
                result = self.run_script(SAGA_MATCH_CRASH_RUN_ID=value or '!')
                self.assertEqual(result.returncode, 2)
                self.assertIn('RUN_ID', result.stderr)
        for value in ('0', '-1', '1.5', 'abc', '01', '86401', '999999999999999999'):
            with self.subTest(timeout=value):
                result = self.run_script(SAGA_MATCH_CRASH_TIMEOUT_SECONDS=value)
                self.assertEqual(result.returncode, 2)
                self.assertIn('TIMEOUT_SECONDS', result.stderr)
        self.assertEqual(self.run_script(arguments=('unexpected',)).returncode, 2)
        self.assertFalse((self.root / 'docker.log').exists())

    def test_fixture_has_only_owned_resources_and_exact_jar_builds(self):
        self.packages()
        services = {name: {'build': {'args': {'SERVICE_PATH': layout}}, 'environment': {},
                           'container_name': name, 'ports': ['1234:1234']}
                    for name, layout in FIXTURE.LAYOUTS.items()}
        services.update({name: {'image': name, 'ports': ['1234:1234']}
                         for name in ('api-gateway', 'postgres', 'kafka', 'redis',
                                      'shipper-service', 'tracking-service', 'delivery-service')})
        for name in ('shipper-service', 'tracking-service', 'delivery-service'):
            services[name]['environment'] = {}
        services['optional'] = {'profiles': ['optional'], 'image': 'optional'}
        config = {'name': 'backend_delivery', 'services': services,
                  'networks': {'delivery-network': {'external': True, 'name': 'canonical'}},
                  'volumes': {'postgres_data': {'external': True, 'name': 'canonical'}, 'kafka_data': {}}}
        owner = 'unique-owner'
        result = FIXTURE.owned_config(config, owner)
        self.assertEqual(result['services']['shipper-service']['environment']['SHIPPER_IDENTITY_OUTBOX_RELAY_ENABLED'], 'true')
        for name in ('tracking-service', 'delivery-service'):
            self.assertEqual(result['services'][name]['environment']['SHIPPER_IDENTITY_PROJECTION_ENFORCED'], 'true')
        self.assertNotIn('name', result)
        self.assertNotIn('optional', result['services'])
        for kind in ('volumes', 'networks'):
            for resource in result[kind].values():
                self.assertNotIn('external', resource)
                self.assertTrue(resource['name'].startswith(owner + '-'))
                self.assertEqual(resource['labels'], {FIXTURE.LABEL: owner})
        for name, service in result['services'].items():
            self.assertNotIn('container_name', service)
            self.assertEqual(service['labels'], {FIXTURE.LABEL: owner})
            if name != 'api-gateway':
                self.assertNotIn('ports', service)
            if name in FIXTURE.LAYOUTS:
                dockerfile = service['build']['dockerfile_inline']
                self.assertIn(f'{FIXTURE.LAYOUTS[name]}/target/{name}-0.0.1-SNAPSHOT.jar', dockerfile)
                self.assertNotIn('*.jar', dockerfile)
                self.assertEqual(service['build']['labels'], {FIXTURE.LABEL: owner})
                self.assertTrue(service['image'].startswith(owner + '-'))
        self.assertEqual(result['services']['api-gateway']['ports'][0]['host_ip'], '127.0.0.1')
        path = self.root / 'compose.json'
        path.write_text(json.dumps(result))
        subprocess.run(['python3', str(HELPER), 'match', str(path)],
                       env=dict(self.env, MATCH_REDIS_HOST='127.0.0.1', MATCH_OUTBOX_RELAY_ENABLED='true'), check=True)
        environment = json.loads(path.read_text())['services']['match-service']['environment']
        self.assertEqual(environment['SPRING_DATA_REDIS_HOST'], '127.0.0.1')
        self.assertEqual(environment['MATCH_OUTBOX_RELAY_ENABLED'], 'true')

    def test_restaurant_route_readiness_requires_success_before_seed(self):
        text = SCRIPT.read_text()
        start = text.index('restaurant_route_is_ready()')
        end = text.index("wait_for 'Gateway restaurant route readiness", start)
        function = text[start:end]
        self.assertLess(end, text.index('BASE="$BASE" bash scripts/seed.sh'))
        for status, expected in [('503', 1), ('401', 1), ('200', 0)]:
            result = subprocess.run(['bash', '-c',
                'curl() { printf "%s" "$RESPONSE_STATUS"; }\n' + function +
                '\nrestaurant_route_is_ready'],
                env=dict(self.env, RESPONSE_STATUS=status, BASE='http://fixture'),
                capture_output=True, text=True)
            self.assertEqual(result.returncode, expected, result.stderr)

    def cleanup_proof(self, exit_action):
        executable = self.bin / 'docker'
        executable.write_text('''#!/bin/sh
echo "$*" >> "$DOCKER_CALL_LOG"
case "$2" in
  ls) printf 'owned-%s\nforeign-%s\n' "$1" "$1" ;;
  inspect) case "$*" in *foreign*) echo someone-else ;; *) echo test-owner ;; esac ;;
esac
''')
        text = SCRIPT.read_text()
        bounded = text[text.index('bounded_command()'):text.index('docker()')]
        cleanup = text[text.index('cleanup()'):text.index('compose()')]
        fixture_dir = self.root / 'disposable'
        fixture_dir.mkdir()
        command = ('set -euo pipefail\n' + bounded + '\n'
                   + f'OWNER=test-owner OWNERSHIP_KEY={FIXTURE.LABEL} '
                   + f'OWNERSHIP_LABEL={FIXTURE.LABEL}=test-owner started=true '
                   + f'fixture_dir="{fixture_dir}"\n' + cleanup + '\n' + exit_action)
        result = subprocess.run(['bash', '-c', command], env=self.env, capture_output=True, timeout=8)
        self.assertFalse(fixture_dir.exists())
        calls = (self.root / 'docker.log').read_text()
        self.assertIn('container ls -aq --filter label=', calls)
        for kind in ('container', 'volume', 'network', 'image'):
            self.assertIn(f'{kind} rm ' + ('-fv ' if kind == 'container' else '') + f'owned-{kind}', calls)
            self.assertNotIn(f'rm foreign-{kind}', calls)
            self.assertNotIn(f'rm -fv foreign-{kind}', calls)
        self.assertNotIn('compose down', calls)
        return result.returncode

    def test_cleanup_on_success_removes_only_label_verified_resources(self):
        self.assertEqual(self.cleanup_proof('exit 0'), 0)

    def test_cleanup_on_partial_start_failure_preserves_status(self):
        self.assertEqual(self.cleanup_proof('exit 7'), 7)

    def test_cleanup_on_term_and_int_preserves_signal_status(self):
        for signal, status in (('TERM', 143), ('INT', 130)):
            with self.subTest(signal=signal):
                self.assertEqual(self.cleanup_proof(f'kill -{signal} $$'), status)

    def test_command_timeout_kills_hung_observation(self):
        text = SCRIPT.read_text()
        bounded = text[text.index('bounded_command()'):text.index('docker()')]
        started = time.monotonic()
        result = subprocess.run(['bash', '-c', bounded +
                                 '\nbounded_command 0.15 python3 -c "import time; time.sleep(30)"'],
                                capture_output=True, timeout=3)
        self.assertEqual(result.returncode, 124)
        self.assertLess(time.monotonic() - started, 3)

    def test_wait_deadline_bounds_a_hung_docker_poll(self):
        (self.bin / 'docker').write_text('#!/bin/sh\nsleep 30\n')
        text = SCRIPT.read_text()
        functions = text[text.index('bounded_command()'):text.index('if ! docker info')]
        wait = text[text.index('wait_for()'):text.index('psql_value()')]
        command = ('set -euo pipefail\nTIMEOUT_SECONDS=30 POLL_SECONDS=0\n'
                   + functions + wait + '\nTIMEOUT_SECONDS=1\nwait_for fixture docker info')
        started = time.monotonic()
        result = subprocess.run(['bash', '-c', command], env=self.env, capture_output=True, timeout=4)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b'Timed out waiting', result.stderr)
        self.assertLess(time.monotonic() - started, 4)

    def test_unavailable_kafka_is_not_proof_of_an_empty_dlt(self):
        text = SCRIPT.read_text()
        function = text[text.index('stop_dlt_is_empty()'):text.index('stop_tombstone_projection_is_recovered()')]
        result = subprocess.run(['bash', '-c', 'compose() { return 1; }\n' + function + '\nstop_dlt_is_empty'],
                                capture_output=True, timeout=3)
        self.assertNotEqual(result.returncode, 0)

    def test_cod_timeout_dumps_payloads_balances_and_reservations_after_deadline(self):
        text = SCRIPT.read_text()
        wait = text[text.index('wait_for()'):text.index('psql_value()')]
        diagnostics = text[text.index('dump_match_timeout_diagnostics()'):text.index('wait_for_service_healthy()')]
        for diagnostic_status in (0, 7):
            with self.subTest(diagnostic_status=diagnostic_status):
                command = ('set -euo pipefail\nreadonly TIMEOUT_SECONDS=0\nPOLL_SECONDS=0 order_id=42\n'
                           'psql_value() { printf "db=%s sql=%s budget=%s deadline=%s\\n" '
                           '"$1" "$2" "$DIAGNOSTIC_COMMAND_TIMEOUT_SECONDS" "$OBSERVATION_DEADLINE"; '
                           f'return {diagnostic_status}; }}\n'
                           'compose() { printf "compose=%s budget=%s deadline=%s\\n" '
                           '"$*" "$DIAGNOSTIC_COMMAND_TIMEOUT_SECONDS" "$OBSERVATION_DEADLINE"; '
                           f'return {diagnostic_status}; }}\n'
                           + wait + diagnostics +
                           "\nwait_for 'Match command and unsent result outbox' false")
                result = subprocess.run(['bash', '-c', command], capture_output=True, text=True, timeout=3)
                self.assertEqual(result.returncode, 1)
                self.assertIn('Timed out waiting', result.stderr)
                for expected in ('match_commands WHERE order_id = 42',
                                 "match_outbox_events WHERE aggregate_id = '42'",
                                 'SELECT * FROM cod_capacity_holds WHERE order_id = 42',
                                 "SELECT * FROM balances WHERE entity_type = 'SHIPPER'",
                                 'row_to_json(r)', 'budget=10 deadline=\n',
                                 'match:shipper:*', 'match:delivery:offer*',
                                 'redis-cli TTL', 'redis-cli --raw GET',
                                 'logs --no-color --tail=300 match-service settlement-service'):
                    self.assertIn(expected, result.stderr)
                self.assertEqual(result.stdout, '')

    def test_diagnostic_budget_reaches_docker_wrapper_with_readonly_timeout(self):
        text = SCRIPT.read_text()
        docker = text[text.index('docker()'):text.index('curl()')]
        diagnostics = text[text.index('dump_match_timeout_diagnostics()'):text.index('wait_for_service_healthy()')]
        command = ('set -euo pipefail\nreadonly TIMEOUT_SECONDS=900\n'
                   'OBSERVATION_DEADLINE=0 order_id=42\n'
                   'bounded_command() { printf "limit=%s\\n" "$1"; }\n'
                   'psql_value() { docker query "$@"; }\n'
                   'compose() { docker compose "$@"; }\n'
                   + docker + diagnostics + '\ndump_match_timeout_diagnostics')
        result = subprocess.run(['bash', '-c', command], capture_output=True, text=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr.count('limit=10\n'), 6)
        self.assertNotIn('readonly variable', result.stderr)

    def test_match_diagnostics_only_run_for_failed_match_staging_wait(self):
        text = SCRIPT.read_text()
        wait = text[text.index('wait_for()'):text.index('psql_value()')]
        for description, timeout, predicate, expected in (
                ('Match command and unsent result outbox', 1, 'true', 0),
                ('unrelated readiness', 0, 'false', 1)):
            with self.subTest(description=description):
                command = ('set -euo pipefail\n'
                           f'TIMEOUT_SECONDS={timeout} POLL_SECONDS=0\n'
                           'dump_match_timeout_diagnostics() { echo unexpected-diagnostics >&2; }\n'
                           + wait + f'\nwait_for "{description}" {predicate}')
                result = subprocess.run(['bash', '-c', command], capture_output=True,
                                        text=True, timeout=3)
                self.assertEqual(result.returncode, expected, result.stderr)
                self.assertNotIn('unexpected-diagnostics', result.stderr)

    def test_seed_waits_for_both_canonical_identity_projections_before_locations(self):
        seed = SCRIPT.with_name('seed.sh').read_text()
        start = seed.index('  if [[ "$SEED_WAIT_SHIPPER_IDENTITY_PROJECTION" == "true" ]]; then', seed.index('shipper_profile='))
        end = seed.index('  "${COMPOSE_COMMAND[@]}" exec -T postgres psql -U postgres -d settlement_db', start)
        self.assertLess(end, seed.index('"$BASE/api/tracking/shipper-locations/update"', start))
        command = ('set -euo pipefail\nCOMPOSE_COMMAND=(mock_compose)\n'
                   'SEED_WAIT_SHIPPER_IDENTITY_PROJECTION=true shipper_id=1 shipper_user_id=4\n'
                   'mock_compose() { printf "%s\\n" "$*" >&2; '
                   'if [[ -f "$FIXTURE_PROJECTION_CALLS/$8" ]]; then echo 1; '
                   'else touch "$FIXTURE_PROJECTION_CALLS/$8"; echo 0; fi; }\n'
                   'sleep() { echo projection-poll >&2; }\n'
                   + seed[start:end])
        result = subprocess.run(['bash', '-c', command],
                                env=dict(self.env, FIXTURE_PROJECTION_CALLS=str(self.root)),
                                capture_output=True, text=True, timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        for database in ('tracking_db', 'delivery_db'):
            self.assertIn('-d ' + database, result.stderr)
        self.assertEqual(result.stderr.count('WHERE shipper_id = 1 AND legacy_user_id = 4'), 4)
        self.assertEqual(result.stderr.count('projection-poll'), 2)

    def test_staging_requires_one_business_result_and_a_found_event(self):
        text = SCRIPT.read_text()
        function = text[text.index('match_result_is_pending()'):text.index("wait_for 'Match command and unsent result outbox'")]
        snapshot = text[text.index('match_snapshot()'):text.index('match_result_is_pending()')]
        self.assertEqual(snapshot.count("topic <> 'matching.decision-trace'"), 2)
        for state, found_count, expected in (
                ('1|RESULT_STAGED|1|PENDING', '1', 0),
                ('1|RESULT_STAGED|1|PENDING', '0', 1),
                ('1|RESULT_STAGED|2|PENDING', '1', 1),
                ('1|RESULT_STAGED|1|SENT', '1', 1)):
            result = subprocess.run(['bash', '-c',
                'match_snapshot() { echo "$STATE"; }\npsql_value() { echo "$FOUND_COUNT"; }\n'
                + function + '\nmatch_result_is_pending'],
                env=dict(self.env, STATE=state, FOUND_COUNT=found_count), capture_output=True, timeout=3)
            self.assertEqual(result.returncode, expected)

    def test_original_crash_window_assertions_remain_present(self):
        text = SCRIPT.read_text()
        for assertion in (
            "== '503'", '== "$stopped_order_before_global_redis_outage"',
            "AND status = 'SENT'", "AND projection_status = 'PENDING'",
            'AND projection_attempts >= 1', "AND projection_status = 'PROJECTED'",
            'AND redis_projected_at IS NOT NULL', 'match:cancelled:$stopped_delivery_id:$stopped_session_id',
            "== '1|CANCELLED|0'", "== '1|RESULT_STAGED|1|PENDING'",
            "== '1|RESULT_STAGED|1|SENT'", 'compose kill -s KILL match-service',
            'stop_dlt_is_empty', 'stop_source_offset_is_committed',
            'if stopped_order_offer_exists', '"$delivery_count" == \'1\'',
            '"$delivery_status" == \'WAIT_SHIPPER_CONFIRM\'', '"$notification_count" == \'1\'',
            '"$saga_cache_commands" == \'1\'',
        ):
            with self.subTest(assertion=assertion):
                self.assertIn(assertion, text)


if __name__ == '__main__':
    unittest.main()
