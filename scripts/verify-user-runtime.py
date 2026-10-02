#!/usr/bin/env python3
"""Prove packaged User HTTP behavior with isolated PostgreSQL and signed Auth handoff.

Only disposable fixture credentials, owned containers and child JVMs are used.
Kafka transport has a separate Kafka/PostgreSQL integration proof.
"""
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
HTTP = build_opener(ProxyHandler({}))
SECRET = "user-runtime-proof-only"


def encoded(value):
    if isinstance(value, dict): value = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


class Boundary(BaseHTTPRequestHandler):
    jwks = {}

    def respond(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.respond(self.jwks if self.path == "/.well-known/jwks.json" else {},
                     200 if self.path == "/.well-known/jwks.json" else 404)

    def log_message(self, *_): pass


def request(base, path, method="GET", token=None, body=None, internal=False):
    headers = {"Content-Type": "application/json"}
    if internal: headers["Internal-Token"] = SECRET
    if token: headers["Authorization"] = "Bearer " + token
    req = Request(base + path, method=method, headers=headers,
                  data=None if body is None else json.dumps(body).encode())
    try: response = HTTP.open(req, timeout=10)
    except HTTPError as error: response = error
    with response:
        text = response.read().decode()
        return response.status, json.loads(text) if text else None


def docker(*args): return subprocess.check_output(["docker", *args], text=True).strip()


def stop(child):
    child.terminate()
    try: child.wait(timeout=10)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait(timeout=5)


def main():
    jars = list((ROOT / "user/boot/target").glob("*.jar"))
    if len(jars) != 1: raise RuntimeError("Build exactly one user/boot executable JAR first")
    name = "user-runtime-proof-" + uuid.uuid4().hex[:12]
    boundary = ThreadingHTTPServer(("127.0.0.1", 0), Boundary)
    worker = threading.Thread(target=boundary.serve_forever, daemon=True)
    worker.start()
    created = False
    try:
        docker("run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=fixture-only",
               "-e", "POSTGRES_USER=userproof", "-e", "POSTGRES_DB=userproof",
               "-p", "127.0.0.1::5432", "postgres:16-alpine")
        created = True
        port = docker("port", name, "5432/tcp").rsplit(":", 1)[1]
        for _ in range(120):
            if subprocess.run(["docker", "exec", name, "pg_isready", "-U", "userproof", "-d", "userproof"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0: break
            time.sleep(0.25)
        else: raise RuntimeError("Fixture PostgreSQL did not become ready")
        with tempfile.TemporaryDirectory(prefix="user-runtime-") as directory:
            key = Path(directory) / "fixture-key.pem"
            subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                            "-pkeyopt", "rsa_keygen_pubexp:65537", "-out", str(key)],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            modulus = subprocess.check_output(["openssl", "rsa", "-in", str(key), "-noout", "-modulus"], text=True).strip().split("=", 1)[1]
            Boundary.jwks = {"keys": [{"kty": "RSA", "kid": "runtime-proof", "use": "sig", "alg": "RS256",
                                       "n": encoded(bytes.fromhex(modulus)), "e": "AQAB"}]}

            def token(principal, user, role, kind="access", email="fixture@example.test"):
                now = int(time.time())
                claims = {"iss": "delivery-auth", "aud": "delivery-api" if kind == "access" else "delivery-user-registration", "iat": now, "exp": now + 600,
                          "sub": str(user), "principal_id": principal, "legacy_user_id": user,
                          "identity_claims_version": 1, "email": email, "roles": [role], "role": role, "token_type": kind}
                unsigned = encoded({"alg": "RS256", "kid": "runtime-proof", "typ": "JWT"}) + "." + encoded(claims)
                signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", str(key)], input=unsigned.encode())
                return unsigned + "." + encoded(signature)

            owner, admin, other = token(971, 1971, "USER"), token(972, 1972, "ADMIN"), token(973, 1973, "USER")
            provision = token(971, 1971, "USER", "provisioning", "runtime-owner@example.test")
            environment = {k: v for k, v in os.environ.items()
                           if not k.startswith(("SPRING_", "EUREKA_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
            for attempt in range(2):
                log = Path(directory) / f"user-{attempt}.log"
                with log.open("w") as output:
                    child = subprocess.Popen([
                        "java", "-jar", str(jars[0]), "--server.address=127.0.0.1", "--server.port=0",
                        "--management.server.address=127.0.0.1", "--management.server.port=0",
                        f"--spring.config.import=optional:configtree:{directory}/",
                        "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                        f"--spring.datasource.url=jdbc:postgresql://127.0.0.1:{port}/userproof",
                        "--spring.datasource.username=userproof", "--spring.datasource.password=fixture-only",
                        f"--app.auth.jwks-uri=http://127.0.0.1:{boundary.server_port}/.well-known/jwks.json",
                        "--app.jwt.issuer=delivery-auth", "--app.jwt.audience=delivery-api", f"--app.internal.secret={SECRET}",
                        "--app.identity.events.enabled=false", "--app.identity.outbox.relay-enabled=false",
                    ], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, env=environment)
                    try:
                        deadline = time.monotonic() + 90
                        while time.monotonic() < deadline:
                            text = log.read_text()
                            ports = re.findall(r"Tomcat started on port (\d+)", text)
                            if len(ports) >= 2: break
                            if child.poll() is not None: raise RuntimeError("User startup failed:\n" + text[-4000:])
                            time.sleep(0.25)
                        else: raise RuntimeError("User startup timed out:\n" + log.read_text()[-4000:])
                        base = f"http://127.0.0.1:{ports[0]}"
                        assert request(f"http://127.0.0.1:{ports[1]}", "/actuator/health/readiness")[0] == 200
                        if attempt:
                            status, body = request(base, "/api/users", token=owner)
                            assert status == 200 and body["data"]["fullName"] == "Updated User", (status, body)
                            assert request(base, f"/api/addresses/{first_id}", token=owner)[1]["data"]["isDefault"] is True
                            continue
                        assert request(base, "/api/users")[0] == 401
                        assert request(base, "/api/users", token=owner[:-10] + "AAAAAAAAAA")[0] == 401
                        assert request(base, "/api/users", token=provision)[0] == 401
                        registration = {"provisioningToken": provision, "fullName": "Runtime User", "phone": "0900000000",
                                        "avatarUrl": "fixture-avatar", "address": "fixture-address"}
                        status, body = request(base, "/api/users/registrations", "POST", body=registration)
                        assert status == 200 and body["status"] == 1, (status, body)
                        user_id = body["data"]["id"]
                        assert body["data"]["principalId"] == 971 and body["data"]["authId"] == 971
                        assert body["data"]["email"] == "runtime-owner@example.test"
                        replay = request(base, "/api/users/registrations", "POST", body=registration)
                        assert replay[0] == 200 and replay[1]["data"]["id"] == user_id, replay
                        mismatch = {**registration, "provisioningToken": token(971, 1971, "USER", "provisioning", "attacker@example.test")}
                        assert request(base, "/api/users/registrations", "POST", body=mismatch)[0] == 409
                        assert request(base, "/api/users", "POST", body={"authId": 980, "principalId": 981,
                                       "email": "divergent@example.test", "role": "USER"}, internal=True)[0] == 400
                        assert request(base, "/api/users", "POST", body={"authId": 980, "principalId": 980,
                                       "email": "runtime-owner@example.test", "role": "USER"}, internal=True)[0] == 409
                        other_registration = {"provisioningToken": token(973, 1973, "USER", "provisioning", "runtime-other@example.test")}
                        assert request(base, "/api/users/registrations", "POST", body=other_registration)[0] == 200
                        status, body = request(base, "/api/users", "PUT", owner, {"fullName": "Updated User"})
                        assert status == 200 and body["data"]["fullName"] == "Updated User", (status, body)
                        # Existing full replacement semantics are preserved.
                        assert body["data"]["phone"] is None and body["data"]["avatarUrl"] is None
                        address = {"label": "Home", "recipientName": "Runtime User", "phoneNumber": "0900000000",
                                   "addressLine": "Fixture Street", "ward": "Ward", "district": "District", "city": "City", "isDefault": True}
                        path = f"/api/addresses/users/{user_id}/addresses"
                        assert request(base, path, "POST", other, address)[0] == 403
                        status, body = request(base, path, "POST", owner, address)
                        assert status == 200, (status, body)
                        first_id = body["data"]["id"]
                        status, body = request(base, path, "POST", owner, {**address, "label": "Office"})
                        assert status == 200, (status, body)
                        second_id = body["data"]["id"]
                        assert request(base, f"/api/addresses/{first_id}", token=owner)[1]["data"]["isDefault"] is False
                        assert request(base, f"/api/addresses/{second_id}", token=other)[0] == 403
                        assert request(base, f"/api/addresses/{second_id}", token=admin)[0] == 200
                        patch = {key: value for key, value in address.items() if key != "isDefault"}
                        updated = request(base, f"/api/addresses/{second_id}", "PUT", owner, patch)
                        assert updated[0] == 200 and updated[1]["data"]["isDefault"] is True, updated
                        assert request(base, f"/api/addresses/{second_id}", "DELETE", other)[0] == 403
                        assert request(base, f"/api/addresses/{second_id}", "DELETE", owner)[0] == 200
                        assert request(base, f"/api/addresses/{first_id}", token=owner)[1]["data"]["isDefault"] is True
                        block_path = f"/api/internal/users/{user_id}/block-status"
                        block = {"blocked": True, "adminId": 1972, "reason": "fixture review"}
                        assert request(base, block_path, "POST", body=block)[0] == 403
                        assert request(base, block_path, "POST", body=block, internal=True)[0] == 200
                        def query(statement):
                            return docker("exec", name, "psql", "-U", "userproof", "-d", "userproof", "-At", "-c", statement)
                        before = query(f"select is_blocked,is_active,blocked_at,block_reason from users where id={user_id}")
                        assert before.startswith("t|f|") and before.endswith("|fixture review"), before
                        assert request(base, block_path, "POST", body=block, internal=True)[0] == 200
                        assert query(f"select is_blocked,is_active,blocked_at,block_reason from users where id={user_id}") == before
                        assert request(base, block_path, "POST", body={"blocked": False, "adminId": 1972}, internal=True)[0] == 200
                        assert query(f"select is_blocked,is_active,blocked_at,block_reason from users where id={user_id}") == "f|t||"
                        assert query("select count(*) from identity_outbox_events where aggregate_id=971") == "1"
                    except Exception:
                        print(log.read_text()[-5000:])
                        raise
                    finally: stop(child)
        print("User packaged JAR PASSED: PostgreSQL migrations/restart, real provisioning and access RS256/JWKS, identity binding/replay/outbox, profile replacement, address ownership/default transitions and idempotent block/unblock.")
    finally:
        if created: docker("rm", "-f", name)
        boundary.shutdown()
        boundary.server_close()
        worker.join(timeout=5)


if __name__ == "__main__": main()
