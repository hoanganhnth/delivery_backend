#!/usr/bin/env python3
"""Packaged two-JVM Tracking proof with owned PostgreSQL/Redis/Kafka fixtures.

JWKS and Delivery permission responses are explicit HTTP fixtures. No live
platform account, database or container is touched. Requires Java17+, Docker,
OpenSSL and the packaged tracking/boot JAR. Logs remain under /tmp.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import struct
import subprocess
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.parse import urlsplit, parse_qs
from urllib.request import ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
HTTP = build_opener(ProxyHandler({}))
SECRET = 'tracking-runtime-fixture-only'


def encoded(value):
    if isinstance(value, dict): value = json.dumps(value, separators=(',', ':')).encode()
    return base64.urlsafe_b64encode(value).rstrip(b'=').decode()


def wait_for(check, label, seconds=30):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if check(): return
        time.sleep(.25)
    raise AssertionError('Timed out: ' + label)


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True).strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def request(base, path, method='GET', token=None, body=None, internal=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if internal is not None: headers['Internal-Token'] = internal
    req = Request(base + path, headers=headers, method=method,
                  data=None if body is None else json.dumps(body).encode())
    try: response = HTTP.open(req, timeout=10)
    except HTTPError as error: response = error
    with response:
        raw = response.read().decode()
        return response.status, json.loads(raw) if raw else None


class Boundary(BaseHTTPRequestHandler):
    jwks = {}
    calls = []

    def do_GET(self):
        uri = urlsplit(self.path)
        status = 200
        if uri.path == '/.well-known/jwks.json': payload = self.jwks
        elif uri.path == '/api/deliveries/internal/9002/tracking-access':
            query = parse_qs(uri.query)
            self.calls.append((query, self.headers.get('Internal-Token')))
            allowed = self.headers.get('Internal-Token') == SECRET and query == {
                'userId': ['300'], 'role': ['USER'], 'shipperId': ['7002']}
            payload = {'status': 1, 'data': allowed}
        else: status, payload = 404, {}
        raw = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, *_): pass


class WebSocket:
    """Small fixture client: masked text/control frames, no external dependency."""
    def __init__(self, port, token=None, expected=101):
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        auth = '' if token is None else 'Authorization: Bearer ' + token + '\r\n'
        self.sock.sendall(('GET /ws/shipper-locations HTTP/1.1\r\nHost: localhost\r\n'
                           'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\n'
                           f'Sec-WebSocket-Key: {key}\r\n{auth}\r\n').encode())
        header = b''
        while not header.endswith(b'\r\n\r\n'): header += self.exact(1)
        assert int(header.split(b' ')[1]) == expected, header
        if expected == 101:
            accept = base64.b64encode(hashlib.sha1((key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
            assert accept.lower() in header.lower(), header
        else: self.sock.close()

    def exact(self, count):
        result = b''
        while len(result) < count:
            part = self.sock.recv(count - len(result))
            if not part: raise EOFError('WebSocket closed')
            result += part
        return result

    def frame(self, opcode, payload):
        mask = os.urandom(4)
        size = len(payload)
        header = bytes([0x80 | opcode, 0x80 | size]) if size < 126 else bytes([0x80 | opcode, 0xfe]) + struct.pack('!H', size)
        self.sock.sendall(header + mask + bytes(byte ^ mask[i % 4] for i, byte in enumerate(payload)))

    def send(self, payload): self.frame(1, json.dumps(payload).encode())

    def receive(self, predicate, seconds=10):
        deadline = time.monotonic() + seconds
        seen = []
        while time.monotonic() < deadline:
            self.sock.settimeout(max(.1, deadline - time.monotonic()))
            first, second = self.exact(2)
            assert first & 0x80, 'Fixture expects complete server frames'
            count = second & 127
            if count == 126: count = struct.unpack('!H', self.exact(2))[0]
            elif count == 127: count = struct.unpack('!Q', self.exact(8))[0]
            assert not second & 0x80, 'Server frames must be unmasked'
            raw = self.exact(count)
            opcode = first & 15
            if opcode == 9: self.frame(10, raw); continue
            if opcode == 8: raise AssertionError('Closed before expected response: ' + str(seen))
            if opcode != 1: continue
            value = json.loads(raw); seen.append(value)
            if predicate(value): return value
        raise AssertionError('Expected response absent: ' + str(seen))

    def close(self):
        try: self.frame(8, struct.pack('!H', 1000))
        except OSError: pass
        self.sock.close()


def main():
    jars = list((ROOT / 'tracking/boot/target').glob('*.jar'))
    assert len(jars) == 1, 'Package exactly one Tracking boot JAR first'
    directory = Path(tempfile.mkdtemp(prefix='tracking-runtime-proof-'))
    key = directory / 'fixture.pem'
    prefix = 'tracking-proof-' + uuid.uuid4().hex[:10]
    names, children, sockets = [], [], []
    boundary = ThreadingHTTPServer(('127.0.0.1', 0), Boundary)
    threading.Thread(target=boundary.serve_forever, daemon=True).start()
    print('Fixture artifacts: ' + str(directory), flush=True)
    try:
        def container(suffix, image, args):
            name = prefix + suffix
            docker('run', '--rm', '-d', '--name', name, *args, image)
            names.append(name)
            return name
        pg = container('-pg', 'postgres:16-alpine', ['-e', 'POSTGRES_PASSWORD=fixture-only', '-e', 'POSTGRES_DB=trackingproof', '-p', '127.0.0.1::5432'])
        redis = container('-redis', 'redis:7-alpine', ['-p', '127.0.0.1::6379'])
        kafka_port = free_port()
        settings = {
            'KAFKA_NODE_ID': '1', 'KAFKA_PROCESS_ROLES': 'broker,controller',
            'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@localhost:9093',
            'KAFKA_LISTENERS': 'INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:9094,CONTROLLER://0.0.0.0:9093',
            'KAFKA_ADVERTISED_LISTENERS': f'INTERNAL://localhost:9092,EXTERNAL://127.0.0.1:{kafka_port}',
            'KAFKA_INTER_BROKER_LISTENER_NAME': 'INTERNAL', 'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER',
            'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT',
            'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR': '1', 'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR': '1',
            'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR': '1', 'KAFKA_AUTO_CREATE_TOPICS_ENABLE': 'true',
            'CLUSTER_ID': 'MkU3OEVBNTcwNTJENDM2Qk'}
        kafka_args = ['-p', f'127.0.0.1:{kafka_port}:9094']
        for name, value in settings.items(): kafka_args += ['-e', name + '=' + value]
        kafka = container('-kafka', 'confluentinc/cp-kafka:7.4.0', kafka_args)
        pg_port = docker('port', pg, '5432/tcp').rsplit(':', 1)[1]
        redis_port = docker('port', redis, '6379/tcp').rsplit(':', 1)[1]
        def ready(name, *command):
            return subprocess.run(['docker', 'exec', name, *command], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
        wait_for(lambda: ready(pg, 'pg_isready', '-U', 'postgres'), 'PostgreSQL readiness')
        wait_for(lambda: ready(kafka, 'kafka-broker-api-versions', '--bootstrap-server', 'localhost:9092'), 'Kafka readiness', 90)
        def sql(query): return docker('exec', pg, 'psql', '-U', 'postgres', '-d', 'trackingproof', '-At', '-v', 'ON_ERROR_STOP=1', '-c', query)
        def redis_command(*args): return docker('exec', redis, 'redis-cli', '--raw', *args)
        for topic in ['shipper.location-updated', 'shipper.status-change', 'shipper.identity.upserted']:
            docker('exec', kafka, 'kafka-topics', '--bootstrap-server', 'localhost:9092', '--create', '--topic', topic, '--partitions', '1', '--replication-factor', '1')
        def publish(topic, value):
            subprocess.run(['docker', 'exec', '-i', kafka, 'kafka-console-producer', '--bootstrap-server', 'localhost:9092', '--topic', topic],
                           input=json.dumps(value) + '\n', text=True, check=True, stdout=subprocess.DEVNULL)
        def offsets(group, topic):
            result = subprocess.run(['docker', 'exec', kafka, 'kafka-consumer-groups', '--bootstrap-server', 'localhost:9092', '--describe', '--group', group], text=True, capture_output=True)
            for line in result.stdout.splitlines():
                fields = line.split()
                if len(fields) >= 4 and fields[1] == topic and fields[3].isdigit():
                    return int(fields[3]), int(fields[4])
            return None
        def consumed(group, topic, offset):
            position = offsets(group, topic)
            return position is not None and position[0] >= offset
        subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(key)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        modulus = subprocess.check_output(['openssl', 'rsa', '-in', str(key), '-noout', '-modulus'], text=True).strip().split('=', 1)[1]
        Boundary.jwks = {'keys': [{'kty': 'RSA', 'kid': 'fixture', 'use': 'sig', 'alg': 'RS256', 'n': encoded(bytes.fromhex(modulus)), 'e': 'AQAB'}]}
        def token(principal, user, role):
            now = int(time.time())
            claims = {'iss': 'delivery-auth', 'aud': 'delivery-api', 'sub': str(principal), 'iat': now, 'exp': now + 900,
                      'principal_id': principal, 'legacy_user_id': user, 'identity_claims_version': 1, 'roles': [role],
                      'email': 'fixture@example.test', 'token_type': 'access'}
            unsigned = encoded({'alg': 'RS256', 'kid': 'fixture'}) + '.' + encoded(claims)
            signature = subprocess.check_output(['openssl', 'dgst', '-sha256', '-sign', str(key)], input=unsigned.encode())
            return unsigned + '.' + encoded(signature)
        shipper, customer, denied, admin = token(100, 200, 'SHIPPER'), token(1300, 300, 'USER'), token(1301, 301, 'USER'), token(1302, 302, 'ADMIN')
        environment = {k: v for k, v in os.environ.items() if not k.startswith(('SPRING_', 'EUREKA_', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'))}
        def start(label, history_group='tracking-proof-history'):
            log = directory / (label + '.log')
            with log.open('w') as output:
                child = subprocess.Popen(['java', '-Xmx256m', '-jar', str(jars[0]), '--server.address=127.0.0.1', '--server.port=0',
                    '--management.server.address=127.0.0.1', '--management.server.port=0',
                    f'--spring.config.import=optional:configtree:{directory}/', '--spring.cloud.config.enabled=false', '--eureka.client.enabled=false',
                    f'--spring.datasource.url=jdbc:postgresql://127.0.0.1:{pg_port}/trackingproof', '--spring.datasource.username=postgres', '--spring.datasource.password=fixture-only',
                    f'--spring.data.redis.port={redis_port}', f'--spring.kafka.bootstrap-servers=127.0.0.1:{kafka_port}',
                    f'--app.auth.jwks-uri=http://127.0.0.1:{boundary.server_port}/.well-known/jwks.json', '--app.jwt.issuer=delivery-auth', '--app.jwt.audience=delivery-api',
                    f'--app.internal.secret={SECRET}', '--app.shipper.identity-projection.enforced=true',
                    '--delivery.service.url=http://delivery-service', f'--spring.cloud.discovery.client.simple.instances.delivery-service[0].uri=http://127.0.0.1:{boundary.server_port}',
                    f'--app.kafka.groups.delivery-rooms={prefix}-{label}', f'--app.kafka.groups.location-history={history_group}',
                    '--app.kafka.retry.auto-create-topics=true', '--app.shipper.identity.retry.auto-create-topics=true',
                    '--app.websocket.publisher.disconnect-grace-seconds=2', '--app.websocket.publisher.lease-ttl-seconds=5',
                    '--app.websocket.publisher.expiry-sweep-interval-ms=250', '--app.websocket.publisher.expiry-claim-seconds=2',
                    '--management.otlp.tracing.export.enabled=false'], cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
            children.append(child)
            def started():
                if child.poll() is not None: raise AssertionError('JVM startup failed: ' + log.read_text()[-5000:])
                return len(re.findall(r'Tomcat started on port (\d+)', log.read_text())) >= 2
            wait_for(started, label + ' startup', 90)
            ports = re.findall(r'Tomcat started on port (\d+)', log.read_text())
            base = 'http://127.0.0.1:' + ports[0]
            health = 'http://127.0.0.1:' + ports[1]
            wait_for(lambda: request(health, '/actuator/health/readiness')[0] == 200, label + ' readiness')
            print('READY: ' + label + ' PID=' + str(child.pid), flush=True)
            return child, base, int(ports[0])
        a, base_a, port_a = start('a')
        b, base_b, port_b = start('b')
        identity = {'eventId': str(uuid.uuid4()), 'eventType': 'shipper.identity.upserted', 'schemaVersion': 1, 'occurredAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), 'correlationId': str(uuid.uuid4()), 'causationId': str(uuid.uuid4()), 'principalId': 100, 'legacyUserId': 200, 'shipperId': 7002, 'mappingVersion': 1}
        publish('shipper.identity.upserted', identity)
        wait_for(lambda: sql('SELECT shipper_id FROM shipper_identity_projection WHERE principal_id=100') == '7002', 'Kafka identity projection')
        assignment = {'eventId': str(uuid.uuid4()), 'shipperId': 7002, 'deliveryId': 9002, 'orderId': 8002, 'timestamp': int(time.time() * 1000), 'status': 'BUSY'}
        publish('shipper.status-change', assignment)
        wait_for(lambda: redis_command('GET', 'tracking:shipper:active-delivery:7002').startswith('9002|'), 'Kafka assignment')
        assert request(base_a, '/api/tracking/shipper-locations/update', 'POST', body={'latitude': 10.8, 'longitude': 106.7})[0] == 401
        WebSocket(port_b, expected=401)
        def connect(port, bearer):
            ws = WebSocket(port, bearer); sockets.append(ws)
            return ws, ws.receive(lambda value: value.get('type') == 'connection_established')
        subscriber, _ = connect(port_b, customer)
        subscriber.send({'action': 'subscribe_shipper', 'deliveryId': 9002, 'shipperId': 7002})
        subscriber.receive(lambda value: value.get('type') == 'subscription_confirmed')
        forbidden, _ = connect(port_b, denied)
        forbidden.send({'action': 'subscribe_shipper', 'deliveryId': 9002, 'shipperId': 7002})
        forbidden.receive(lambda value: value.get('code') == 'FORBIDDEN')
        publisher, first = connect(port_a, shipper)
        def location(ws, latitude): ws.send({'action': 'update_location', 'shipperId': 999, 'latitude': latitude, 'longitude': 106.7})
        def received(latitude, online=True):
            value = subscriber.receive(lambda v: v.get('type') == 'location_update' and v.get('isOnline') == online and (latitude is None or v.get('latitude') == latitude))
            assert value['shipperId'] == 7002, value
            return value
        location(publisher, 10.8); received(10.8)
        assert request(base_a, '/api/tracking/shipper-locations/update', 'POST', shipper, {'latitude': 10.81, 'longitude': 106.7, 'isOnline': True})[0] == 200
        received(10.81)
        assert request(base_a, '/api/tracking/shipper-locations/offline', 'POST', shipper)[0] == 200
        received(10.81, False)
        replacement, second = connect(port_b, shipper)
        assert second['publisherGeneration'] > first['publisherGeneration']
        location(replacement, 10.82); received(10.82)
        location(publisher, 10.99)
        publisher.receive(lambda value: value.get('code') == 'PUBLISHER_SUPERSEDED')
        assert redis_command('SISMEMBER', 'shippers:online:set', json.dumps('7002')) == '1'
        replacement.close(); received(10.82, False)
        crash_publisher, _ = connect(port_a, shipper)
        location(crash_publisher, 10.83); received(10.83)
        assert redis_command('SISMEMBER', 'shippers:online:set', json.dumps('7002')) == '1'
        a.kill(); a.wait(timeout=10)
        wait_for(lambda: redis_command('SISMEMBER', 'shippers:online:set', json.dumps('7002')) == '0', 'surviving JVM expiry sweep', 20)
        received(10.83, False)
        # Re-subscribe also recovers the latest cached offline source.
        subscriber.send({'action': 'subscribe_shipper', 'deliveryId': 9002, 'shipperId': 7002})
        received(10.83, False)
        wait_for(lambda: int(sql('SELECT count(*) FROM shipper_location_history')) >= 1, 'Kafka PostgreSQL history')
        snapshot = offsets('tracking-proof-history', 'shipper.location-updated')
        assert snapshot is not None and snapshot[1] >= 7, snapshot
        wait_for(lambda: consumed('tracking-proof-history', 'shipper.location-updated', snapshot[1]), 'original history committed through snapshot after crash rebalance', seconds=90)
        points_before = sql('SELECT count(*) FROM shipper_location_history')
        receipts_before = sql('SELECT count(*) FROM location_history_receipts')
        a2, base_a2, port_a2 = start('a-restart', 'tracking-proof-replay')
        wait_for(lambda: consumed('tracking-proof-replay', 'shipper.location-updated', snapshot[1]), 'fresh history group replay committed through snapshot')
        assert sql('SELECT count(*) FROM shipper_location_history') == points_before
        assert sql('SELECT count(*) FROM location_history_receipts') == receipts_before

        publish('shipper.identity.upserted', identity)
        wait_for(lambda: consumed('tracking-shipper-identity-v1', 'shipper.identity.upserted', 2), 'identity replay committed Kafka offset')
        assert sql('SELECT count(*) FROM shipper_identity_inbox_receipts') == '1'
        status, history = request(base_a2, '/internal/tracking/location-history/deliveries/9002', token=admin, internal=SECRET)
        assert status == 200 and len(history) >= 1, (status, history)
        assert request(base_a2, '/internal/tracking/location-history/deliveries/9002', token=customer, internal=SECRET)[0] == 403
        assert request(base_a2, '/internal/tracking/location-history/deliveries/9002', token=admin, internal='wrong')[0] == 403
        fresh, _ = connect(port_a2, shipper)
        location(fresh, 10.84); received(10.84)
        assert request(base_a2, '/api/tracking/internal/shippers/7002/offline', 'POST', internal='wrong')[0] == 403
        assert request(base_a2, '/api/tracking/internal/shippers/7002/offline', 'POST', internal=SECRET)[0] == 200
        received(10.84, False)
        terminal = {**assignment, 'eventId': str(uuid.uuid4()), 'timestamp': int(time.time() * 1000), 'status': 'AVAILABLE'}
        publish('shipper.status-change', terminal)
        wait_for(lambda: redis_command('GET', 'tracking:shipper:assignment-terminal:7002') == str(terminal['timestamp']), 'terminal projection')
        publish('shipper.status-change', assignment)
        for label in ['b', 'a-restart']:
            wait_for(lambda: consumed(prefix + '-' + label, 'shipper.status-change', 3), label + ' terminal replay committed Kafka offset')
        assert redis_command('EXISTS', 'tracking:shipper:active-delivery:7002') == '0'
        dump = subprocess.run(['docker', 'exec', kafka, 'kafka-console-consumer', '--bootstrap-server', 'localhost:9092', '--topic', 'shipper.location-updated', '--from-beginning', '--timeout-ms', '5000'], text=True, capture_output=True)
        events = [json.loads(line) for line in dump.stdout.splitlines() if line.startswith('{')]
        assert {'WEBSOCKET', 'APPLICATION', 'OFFLINE_TOMBSTONE'} <= {event['source'] for event in events}, events
        assert all(event['shipperId'] == 7002 and event.get('latitude') != 10.99 for event in events), events
        assert len({event['eventId'] for event in events}) == len(events)
        assert all(secret == SECRET for _, secret in Boundary.calls)
        try:
            leaked = forbidden.receive(lambda value: value.get('type') == 'location_update', seconds=1)
        except socket.timeout: pass
        else: raise AssertionError('Denied audience received location: ' + str(leaked))
        (directory / 'summary.json').write_text(json.dumps({'result': 'PASS', 'events': len(events), 'history': len(history), 'jvms': 3, 'hard_kill_recovered': True}, indent=2))
        print('PASS: packaged two-JVM JWKS/HTTP/WebSocket/Redis/Kafka/PostgreSQL, supersession, hard-kill expiry and restart/history proof.', flush=True)
    finally:
        for ws in sockets:
            try: ws.close()
            except OSError: pass
        for child in children:
            if child.poll() is None:
                child.terminate()
                try: child.wait(timeout=10)
                except subprocess.TimeoutExpired: child.kill(); child.wait(timeout=5)
        boundary.shutdown(); boundary.server_close()
        key.unlink(missing_ok=True)
        for name in reversed(names):
            subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__': main()
