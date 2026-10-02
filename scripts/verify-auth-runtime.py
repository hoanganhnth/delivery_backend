#!/usr/bin/env python3
"""Exercise the packaged Auth JAR and recovery against an owned PostgreSQL fixture."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
HTTP = build_opener(ProxyHandler({}))
SECRET = "auth-runtime-proof-only"


def request(base, path, method="GET", body=None, secret=None):
    headers = {"Content-Type": "application/json"}
    if secret is not None:
        headers["Internal-Token"] = secret
    payload = None if body is None else json.dumps(body).encode()
    try:
        response = HTTP.open(Request(base + path, data=payload, headers=headers, method=method), timeout=10)
    except HTTPError as error:
        response = error
    with response:
        content = response.read().decode()
        return response.status, json.loads(content) if content else None


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def run_jar(jar, directory, port, key, pub, attempt, handle):
    log = directory / f"auth-{attempt}.log"
    environment = {name: value for name, value in os.environ.items()
                   if not name.startswith(("SPRING_", "EUREKA_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
    args = ["java", "-jar", str(jar), "--server.address=127.0.0.1", "--server.port=0",
            "--management.server.address=127.0.0.1", "--management.server.port=0",
            "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
            f"--spring.datasource.url=jdbc:postgresql://127.0.0.1:{port}/authproof",
            "--spring.datasource.username=authproof", "--spring.datasource.password=fixture-only",
            f"--jwt.private-key.path={key}", f"--jwt.public-key.path={pub}",
            f"--app.internal.secret={SECRET}", "--app.identity.events.enabled=true",
            "--app.identity.public-registration-enabled=true",
            "--app.identity.registration.canary-percentage=100",
            "--app.identity.outbox.relay-enabled=false", "--spring.kafka.listener.auto-startup=false",
            "--management.health.kafka.enabled=false", "--management.health.mail.enabled=false",
            "--spring.mail.properties.mail.smtp.connectiontimeout=500",
            "--spring.mail.properties.mail.smtp.timeout=500"]
    with log.open("w") as output:
        child = subprocess.Popen(args, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, env=environment)
        try:
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                content = log.read_text()
                ports = re.findall(r"Tomcat started on port (\d+)", content)
                if len(ports) >= 2:
                    break
                if child.poll() is not None:
                    raise RuntimeError("Auth startup failed:\n" + content[-5000:])
                time.sleep(0.25)
            else:
                raise RuntimeError("Auth startup timed out:\n" + log.read_text()[-5000:])
            base = f"http://127.0.0.1:{ports[0]}"
            management = f"http://127.0.0.1:{ports[1]}"
            readiness = request(management, "/actuator/health/readiness")
            assert readiness[0] == 200, (readiness, log.read_text()[-5000:])
            status, jwks = request(base, "/.well-known/jwks.json")
            assert status == 200 and jwks["keys"][0]["kid"] == "auth-key-1", (status, jwks)
            assert request(base, "/api/auth/sessions")[0] == 401
            assert request(base, "/api/auth/internal/principals/1")[0] == 403
            assert request(base, "/api/auth/internal/principals/1", secret="wrong")[0] == 403
            principal = request(base, "/api/auth/internal/principals/1", secret=SECRET)
            assert principal[0] == (200 if attempt else 404), principal
            reset = {"email": "unknown@example.test"}
            assert request(base, "/api/auth/forgot-password", "POST", reset)[0] == 202
            assert request(base, "/api/auth/email-verification/request", "POST", reset)[0] == 202
            if attempt:
                status, recovery = request(base, f"/api/auth/registrations/{handle}")
                assert status == 200 and recovery["data"]["principalId"] > 0, (status, recovery)
                return handle
            status, registration = request(base, "/api/auth/register", "POST", {
                "email": "runtime-auth@example.test", "password": "Password1!", "role": "USER"})
            assert status == 200, (status, registration)
            data = registration["data"]
            assert data["authId"] > 0 and data["registrationHandle"], data
            assert request(base, f"/api/auth/internal/principals/{data['authId']}", secret=SECRET)[0] == 200
            return data["registrationHandle"]
        finally:
            child.terminate()
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=5)


def main():
    jars = list((ROOT / "auth/boot/target").glob("*.jar"))
    if len(jars) != 1:
        raise RuntimeError("Build exactly one auth/boot executable JAR first")
    name = "auth-runtime-proof-" + uuid.uuid4().hex[:12]
    created = False
    try:
        docker("run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=fixture-only",
               "-e", "POSTGRES_USER=authproof", "-e", "POSTGRES_DB=authproof",
               "-p", "127.0.0.1::5432", "postgres:16-alpine")
        created = True
        port = docker("port", name, "5432/tcp").rsplit(":", 1)[1]
        for _ in range(120):
            if subprocess.run(["docker", "exec", name, "pg_isready", "-U", "authproof", "-d", "authproof"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError("Fixture PostgreSQL did not become ready")
        with tempfile.TemporaryDirectory(prefix="auth-runtime-") as temporary:
            directory = Path(temporary)
            key, pub = directory / "key.pem", directory / "public.pem"
            subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                            "-out", str(key)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-out", str(pub)],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            handle = None
            for attempt in range(2):
                handle = run_jar(jars[0], directory, port, key, pub, attempt, handle)
    finally:
        if created:
            docker("rm", "-f", name)
    print("PASS: packaged Auth JAR, HTTP policy, PostgreSQL registration and restart recovery")


if __name__ == "__main__":
    main()
