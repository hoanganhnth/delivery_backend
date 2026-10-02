#!/usr/bin/env python3
"""Exercise the packaged BFF against isolated PostgreSQL and local HTTP fixtures.

Uses only a temporary Docker container and child JVM owned by this invocation.
Requires Docker and JDK 17; does not use real credentials or external providers.
"""
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
ORIGIN = "https://bff.test"


class Gateway(BaseHTTPRequestHandler):
    calls = []
    generation = 0

    def respond(self, value, status=200):
        payload = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
                if not size:
                    while self.rfile.readline().strip(): pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            payload = b"".join(chunks)
        else:
            payload = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        body = json.loads(payload)
        self.calls.append((self.path, body))
        if self.path == "/api/auth/login" and body.get("password") == "wrong":
            self.respond({"status": 0, "message": "upstream detail must not leak"}, 401)
            return
        if self.path == "/api/auth/logout":
            assert body["refreshToken"] == "fixture-refresh-1", body
            self.respond({"status": 1})
            return
        if self.path == "/api/auth/refresh-token":
            assert body["refreshToken"] == "fixture-refresh-0", body
            type(self).generation += 1
        elif self.path == "/api/auth/login":
            assert body["deviceType"] == "WEB" and body["deviceId"].startswith("web-bff-"), body
        else:
            self.respond({}, 404)
            return
        self.respond({"status": 1, "data": {
            "accessToken": f"fixture-access-{self.generation}",
            "refreshToken": f"fixture-refresh-{self.generation}",
            "authId": 21, "email": "owner@example.test", "role": "SHOP_OWNER"}})

    def do_GET(self):
        self.calls.append((self.path, dict(self.headers)))
        assert self.headers.get("Authorization") == "Bearer fixture-access-0", dict(self.headers)
        assert self.headers.get("Cookie") is None, dict(self.headers)
        self.respond({"status": 1, "data": [21]})

    def log_message(self, *_):
        pass


def request(base, path, method="GET", body=None, cookie=None, csrf=None, origin=None, extra=None):
    headers = {"Content-Type": "application/json"}
    if cookie: headers["Cookie"] = cookie
    if csrf: headers["X-CSRF-Token"] = csrf
    if origin: headers["Origin"] = origin
    headers.update(extra or {})
    req = Request(base + path, method=method, headers=headers,
                  data=None if body is None else json.dumps(body).encode())
    try:
        response = HTTP.open(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        text = response.read().decode()
        return response.status, response.headers, json.loads(text) if text else None


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def stop(child):
    child.terminate()
    try: child.wait(timeout=10)
    except subprocess.TimeoutExpired:
        child.kill()
        child.wait(timeout=5)


def main():
    jars = list((ROOT / "web-bff/boot/target").glob("*.jar"))
    if len(jars) != 1: raise RuntimeError("Build exactly one web-bff/boot executable JAR first")
    name = "bff-runtime-proof-" + uuid.uuid4().hex[:12]
    gateway = ThreadingHTTPServer(("127.0.0.1", 0), Gateway)
    worker = threading.Thread(target=gateway.serve_forever, daemon=True)
    worker.start()
    created = False
    try:
        docker("run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=fixture-only",
               "-e", "POSTGRES_USER=bffproof", "-e", "POSTGRES_DB=bffproof",
               "-p", "127.0.0.1::5432", "postgres:16-alpine")
        created = True
        port = docker("port", name, "5432/tcp").rsplit(":", 1)[1]
        for _ in range(120):
            ready = subprocess.run(["docker", "exec", name, "pg_isready", "-U", "bffproof", "-d", "bffproof"],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0: break
            time.sleep(0.25)
        else: raise RuntimeError("Fixture PostgreSQL did not become ready")
        with tempfile.TemporaryDirectory(prefix="bff-runtime-") as directory:
            environment = {key: value for key, value in os.environ.items()
                           if not key.startswith(("SPRING_", "EUREKA_", "WEB_BFF_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
            for attempt in range(2):
                log = Path(directory) / f"bff-{attempt}.log"
                with log.open("w") as output:
                    child = subprocess.Popen([
                        "java", "-jar", str(jars[0]), "--server.address=127.0.0.1", "--server.port=0",
                        "--management.server.address=127.0.0.1", "--management.server.port=0",
                        f"--spring.config.import=optional:configtree:{directory}/",
                        "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                        f"--spring.datasource.url=jdbc:postgresql://127.0.0.1:{port}/bffproof",
                        "--spring.datasource.username=bffproof", "--spring.datasource.password=fixture-only",
                        "--web-bff.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "--web-bff.encryption-key-version=runtime-test", f"--web-bff.allowed-origins={ORIGIN}",
                        f"--web-bff.gateway-base-url=http://127.0.0.1:{gateway.server_port}",
                    ], stdout=output, stderr=subprocess.STDOUT, cwd=ROOT, env=environment)
                    try:
                        deadline = time.monotonic() + 90
                        while time.monotonic() < deadline:
                            text = log.read_text()
                            ports = re.findall(r"Tomcat started on port (\d+)", text)
                            if len(ports) >= 2: break
                            if child.poll() is not None: raise RuntimeError("BFF exited during startup:\n" + text[-4000:])
                            time.sleep(0.25)
                        else: raise RuntimeError("BFF startup timed out:\n" + log.read_text()[-4000:])
                        base = f"http://127.0.0.1:{ports[0]}"
                        health = request(f"http://127.0.0.1:{ports[1]}", "/actuator/health/readiness")
                        assert health[0] == 200 and health[2]["status"] == "UP", health
                        if attempt:
                            assert request(base, "/bff/session", cookie=cookie)[2]["authenticated"] is False
                            continue  # Existing schema/history must survive a second production startup.
                        assert request(base, "/bff/session/login", "POST", {})[0] == 403
                        credentials = {"email": "owner@example.test", "password": "fixture-password", "role": "SHOP_OWNER"}
                        rejected = request(base, "/bff/session/login", "POST", {**credentials, "password": "wrong"}, origin=ORIGIN)
                        assert rejected[0] == 401 and rejected[2]["error"]["code"] == "AUTHENTICATION_REJECTED", rejected
                        assert "upstream detail" not in json.dumps(rejected[2])
                        status, headers, body = request(base, "/bff/session/login", "POST", credentials, origin=ORIGIN)
                        assert status == 200 and body["authenticated"] is True, (status, body)
                        assert headers["Cache-Control"] == "no-store"
                        assert "fixture-access" not in json.dumps(body) and "fixture-refresh" not in json.dumps(body)
                        session_cookie = next(value for value in headers.get_all("Set-Cookie") if value.startswith("__Host-delivery-session="))
                        assert all(flag in session_cookie for flag in ["HttpOnly", "Secure", "SameSite=Lax", "Path=/"])
                        cookie, csrf = session_cookie.split(";", 1)[0], body["csrfToken"]
                        stored = docker("exec", name, "psql", "-U", "bffproof", "-d", "bffproof", "-At", "-c",
                                        "select count(*) from web_sessions where access_token_cipher like 'runtime-test.%' and refresh_token_cipher like 'runtime-test.%' and generation=1")
                        assert stored == "1", stored
                        assert request(base, "/bff/session", cookie=cookie)[2]["authenticated"] is True
                        proxy = request(base, "/bff/api/users?page=1", cookie=cookie, extra={"Authorization": "Bearer browser-controlled"})
                        assert proxy[0] == 200 and proxy[2]["data"] == [21], proxy
                        before = len(Gateway.calls)
                        assert request(base, "/bff/api/auth/login", "POST", {}, cookie, csrf, ORIGIN)[0] == 404
                        assert request(base, "/bff/api/orders", "POST", {}, cookie, "wrong", ORIGIN)[0] == 401
                        assert len(Gateway.calls) == before
                        assert request(base, "/bff/session/refresh", "POST", {}, cookie, "wrong", ORIGIN)[0] == 401
                        refreshed = request(base, "/bff/session/refresh", "POST", {}, cookie, csrf, ORIGIN)
                        assert refreshed[0] == 200 and refreshed[2]["sessionVersion"] == 2, refreshed
                        logout = request(base, "/bff/session/logout", "POST", {}, cookie, csrf, ORIGIN)
                        assert logout[0] == 204 and not logout[1].get_all("Set-Cookie"), logout
                        assert request(base, "/bff/session", cookie=cookie)[2]["authenticated"] is False
                    except Exception:
                        print(log.read_text()[-5000:])
                        raise
                    finally:
                        stop(child)
        print("Web BFF packaged JAR PASSED: PostgreSQL migration/restart, readiness, actual Auth/proxy HTTP adapters, encrypted sessions, origin/CSRF, login/refresh/logout.")
    finally:
        if created: docker("rm", "-f", name)
        gateway.shutdown()
        gateway.server_close()
        worker.join(timeout=5)


if __name__ == "__main__":
    main()
