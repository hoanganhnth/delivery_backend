#!/usr/bin/env python3
"""Prove packaged Shipper HTTP behavior with isolated PostgreSQL, JWKS and Tracking.

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
SECRET = "shipper-runtime-proof-only"


def encoded(value):
    if isinstance(value, dict): value = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


class Boundary(BaseHTTPRequestHandler):
    jwks = {}
    unavailable = False
    offline = []

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

    def do_POST(self):
        assert self.headers.get("Internal-Token") == SECRET
        self.offline.append(self.path)
        self.respond({"status": 1, "data": None}, 503 if self.unavailable else 200)

    def log_message(self, *_): pass


def request(base, path, method="GET", token=None, body=None):
    headers = {"Content-Type": "application/json"}
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
    jars = list((ROOT / "shipper/boot/target").glob("*.jar"))
    if len(jars) != 1: raise RuntimeError("Build exactly one shipper/boot executable JAR first")
    name = "shipper-runtime-proof-" + uuid.uuid4().hex[:12]
    boundary = ThreadingHTTPServer(("127.0.0.1", 0), Boundary)
    worker = threading.Thread(target=boundary.serve_forever, daemon=True)
    worker.start()
    created = False
    try:
        docker("run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=fixture-only",
               "-e", "POSTGRES_USER=shipperproof", "-e", "POSTGRES_DB=shipperproof",
               "-p", "127.0.0.1::5432", "postgres:16-alpine")
        created = True
        port = docker("port", name, "5432/tcp").rsplit(":", 1)[1]
        for _ in range(120):
            if subprocess.run(["docker", "exec", name, "pg_isready", "-U", "shipperproof", "-d", "shipperproof"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0: break
            time.sleep(0.25)
        else: raise RuntimeError("Fixture PostgreSQL did not become ready")
        with tempfile.TemporaryDirectory(prefix="shipper-runtime-") as directory:
            key = Path(directory) / "fixture-key.pem"
            subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                            "-pkeyopt", "rsa_keygen_pubexp:65537", "-out", str(key)],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            modulus = subprocess.check_output(["openssl", "rsa", "-in", str(key), "-noout", "-modulus"], text=True).strip().split("=", 1)[1]
            Boundary.jwks = {"keys": [{"kty": "RSA", "kid": "runtime-proof", "use": "sig", "alg": "RS256",
                                       "n": encoded(bytes.fromhex(modulus)), "e": "AQAB"}]}

            def token(principal, user, role):
                now = int(time.time())
                claims = {"iss": "delivery-auth", "aud": "delivery-api", "iat": now, "exp": now + 600,
                          "sub": str(user), "principal_id": principal, "legacy_user_id": user,
                          "identity_claims_version": 1, "email": "fixture@example.test", "roles": [role], "token_type": "access"}
                unsigned = encoded({"alg": "RS256", "kid": "runtime-proof", "typ": "JWT"}) + "." + encoded(claims)
                signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", str(key)], input=unsigned.encode())
                return unsigned + "." + encoded(signature)

            owner, admin, customer = token(971, 1971, "SHIPPER"), token(972, 1972, "ADMIN"), token(973, 1973, "CUSTOMER")
            environment = {k: v for k, v in os.environ.items()
                           if not k.startswith(("SPRING_", "EUREKA_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
            for attempt in range(2):
                log = Path(directory) / f"shipper-{attempt}.log"
                with log.open("w") as output:
                    child = subprocess.Popen([
                        "java", "-jar", str(jars[0]), "--server.address=127.0.0.1", "--server.port=0",
                        "--management.server.address=127.0.0.1", "--management.server.port=0",
                        f"--spring.config.import=optional:configtree:{directory}/",
                        "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                        f"--spring.datasource.url=jdbc:postgresql://127.0.0.1:{port}/shipperproof",
                        "--spring.datasource.username=shipperproof", "--spring.datasource.password=fixture-only",
                        f"--app.auth.jwks-uri=http://127.0.0.1:{boundary.server_port}/.well-known/jwks.json",
                        "--app.jwt.issuer=delivery-auth", "--app.jwt.audience=delivery-api", f"--app.internal.secret={SECRET}",
                        f"--app.shipper.tracking-service.url=http://127.0.0.1:{boundary.server_port}",
                        "--app.identity.events.enabled=false", "--app.shipper.identity-outbox.relay-enabled=false",
                    ], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, env=environment)
                    try:
                        deadline = time.monotonic() + 90
                        while time.monotonic() < deadline:
                            text = log.read_text()
                            ports = re.findall(r"Tomcat started on port (\d+)", text)
                            if len(ports) >= 2: break
                            if child.poll() is not None: raise RuntimeError("Shipper startup failed:\n" + text[-4000:])
                            time.sleep(0.25)
                        else: raise RuntimeError("Shipper startup timed out:\n" + log.read_text()[-4000:])
                        base = f"http://127.0.0.1:{ports[0]}"
                        assert request(f"http://127.0.0.1:{ports[1]}", "/actuator/health/readiness")[0] == 200
                        if attempt:
                            status, body = request(base, "/api/shippers/my-profile", token=owner)
                            assert status == 200 and body["data"]["driverImage"] == "updated-driver", (status, body)
                            continue
                        assert request(base, "/api/shippers/my-profile")[0] == 401
                        assert request(base, "/api/shippers/my-profile", token=owner[:-10] + "AAAAAAAAAA")[0] == 401
                        assert request(base, "/api/shippers/my-profile", token=customer)[0] == 403
                        profile = {"fullName": "Runtime Shipper", "vehicleType": "BIKE", "licenseNumber": "RUNTIME-LICENSE",
                                   "idCard": "RUNTIME-CARD", "phone": "0900000000", "licensePlate": "TEST-1",
                                   "driverImage": "fixture-driver", "idCardFrontImage": "fixture-front",
                                   "idCardBackImage": "fixture-back", "licenseImage": "fixture-license"}
                        status, body = request(base, "/api/shippers", "POST", owner, profile)
                        assert status == 200 and body["status"] == 1, (status, body)
                        data, shipper_id = body["data"], body["data"]["id"]
                        assert data["principalId"] == 971 and data["userId"] == 1971
                        assert all(data[field] == profile[field] for field in ["driverImage", "idCardFrontImage", "idCardBackImage", "licenseImage"]), data
                        assert data["createdAt"] and data["updatedAt"]
                        assert request(base, "/api/shippers", "POST", owner, profile)[0] == 400
                        status, body = request(base, "/api/shippers", "PUT", owner,
                                               {"driverImage": "updated-driver", "licenseNumber": profile["licenseNumber"], "idCard": profile["idCard"]})
                        assert status == 200 and body["data"]["driverImage"] == "updated-driver", (status, body)
                        assert body["data"]["idCardFrontImage"] == "fixture-front"
                        assert request(base, f"/api/shippers/{shipper_id}", token=owner)[0] == 403
                        assert request(base, f"/api/shippers/{shipper_id}", token=admin)[0] == 200
                        assert request(base, "/api/shippers?page=0&size=10", token=admin)[0] == 200
                        assert request(base, "/api/shippers/online-status?isOnline=true", "PATCH", owner)[1]["data"]["isOnline"] is True
                        Boundary.unavailable = True
                        assert request(base, "/api/shippers/online-status?isOnline=false", "PATCH", owner)[0] == 500
                        assert request(base, "/api/shippers/my-profile", token=owner)[1]["data"]["isOnline"] is True
                        Boundary.unavailable = False
                        offline = request(base, "/api/shippers/online-status?isOnline=false", "PATCH", owner)
                        assert offline[0] == 200 and offline[1]["data"]["isOnline"] is False, offline
                        assert Boundary.offline[-1] == f"/api/tracking/internal/shippers/{shipper_id}/offline"
                        result = docker("exec", name, "psql", "-U", "shipperproof", "-d", "shipperproof", "-At", "-c",
                                        f"select count(*) from shipper_identity_outbox_events where aggregate_id={shipper_id} and event_key='971'")
                        assert result == "1", result
                        docker("exec", name, "psql", "-U", "shipperproof", "-d", "shipperproof", "-At", "-c",
                               f"insert into shipper_ratings(shipper_id,customer_id,order_id,rating,comment,created_at) values ({shipper_id},1,5001,5,'fixture',current_timestamp)")
                        ratings = request(base, "/api/shippers/me/ratings", token=owner)
                        assert ratings[0] == 200 and ratings[1]["data"][0]["rating"] == 5, ratings
                        assert request(base, "/api/shippers/me/ratings", token=customer)[0] == 403
                    except Exception:
                        print(log.read_text()[-5000:])
                        raise
                    finally: stop(child)
        print("Shipper packaged JAR PASSED: PostgreSQL migrations/restart, real RS256/JWKS authentication, principal ownership, profile/images/timestamps, admin/ratings reads, Tracking availability/failure and identity outbox.")
    finally:
        if created: docker("rm", "-f", name)
        boundary.shutdown()
        boundary.server_close()
        worker.join(timeout=5)


if __name__ == "__main__": main()
