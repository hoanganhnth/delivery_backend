#!/usr/bin/env python3
"""Prove packaged Restaurant HTTP behavior with isolated PostgreSQL, JWKS.

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
SECRET = "restaurant-runtime-proof-only"


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


def request(base, path, method="GET", token=None, body=None, internal=None):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = "Bearer " + token
    if internal is not None: headers["Internal-Token"] = internal
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
    jars = list((ROOT / "restaurant/boot/target").glob("*.jar"))
    if len(jars) != 1: raise RuntimeError("Build exactly one restaurant/boot executable JAR first")
    name = "restaurant-runtime-proof-" + uuid.uuid4().hex[:12]
    boundary = ThreadingHTTPServer(("127.0.0.1", 0), Boundary)
    worker = threading.Thread(target=boundary.serve_forever, daemon=True)
    worker.start()
    created = False
    try:
        docker("run", "--rm", "-d", "--name", name, "-e", "POSTGRES_PASSWORD=fixture-only",
               "-e", "POSTGRES_USER=restaurantproof", "-e", "POSTGRES_DB=restaurantproof",
               "-p", "127.0.0.1::5432", "postgres:16-alpine")
        created = True
        port = docker("port", name, "5432/tcp").rsplit(":", 1)[1]
        def sql(query):
            return docker("exec", name, "psql", "-U", "restaurantproof", "-d", "restaurantproof", "-At", "-c", query)
        for _ in range(120):
            if subprocess.run(["docker", "exec", name, "pg_isready", "-U", "restaurantproof", "-d", "restaurantproof"],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0: break
            time.sleep(0.25)
        else: raise RuntimeError("Fixture PostgreSQL did not become ready")
        with tempfile.TemporaryDirectory(prefix="restaurant-runtime-") as directory:
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

            owner, admin, customer = token(971, 1971, "SHOP_OWNER"), token(972, 1972, "ADMIN"), token(973, 1973, "CUSTOMER")
            foreign = token(974, 1974, "SHOP_OWNER")
            environment = {k: v for k, v in os.environ.items()
                           if not k.startswith(("SPRING_", "EUREKA_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
            for attempt in range(2):
                log = Path(directory) / f"restaurant-{attempt}.log"
                with log.open("w") as output:
                    child = subprocess.Popen([
                        "java", "-jar", str(jars[0]), "--server.address=127.0.0.1", "--server.port=0",
                        "--management.server.address=127.0.0.1", "--management.server.port=0",
                        f"--spring.config.import=optional:configtree:{directory}/",
                        "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                        f"--spring.datasource.url=jdbc:postgresql://127.0.0.1:{port}/restaurantproof",
                        "--spring.datasource.username=restaurantproof", "--spring.datasource.password=fixture-only",
                        f"--app.auth.jwks-uri=http://127.0.0.1:{boundary.server_port}/.well-known/jwks.json",
                        "--app.jwt.issuer=delivery-auth", "--app.jwt.audience=delivery-api", f"--app.internal.secret={SECRET}",
                        "--app.outbox.relay-enabled=false", "--spring.kafka.listener.auto-startup=false",
                        "--spring.kafka.admin.auto-create=false", "--app.restaurant.inventory-consumer-enabled=false",
                        "--app.restaurant.inventory-enabled=true", "--app.restaurant.serviceability-enabled=true",
                        "--app.identity.principal-ownership.enforced=true",
                        "--management.otlp.tracing.export.enabled=false",
                    ], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, env=environment)
                    try:
                        deadline = time.monotonic() + 90
                        while time.monotonic() < deadline:
                            text = log.read_text()
                            ports = re.findall(r"Tomcat started on port (\d+)", text)
                            if len(ports) >= 2: break
                            if child.poll() is not None: raise RuntimeError("Restaurant startup failed:\n" + text[-4000:])
                            time.sleep(0.25)
                        else: raise RuntimeError("Restaurant startup timed out:\n" + log.read_text()[-4000:])
                        base = f"http://127.0.0.1:{ports[0]}"
                        assert request(f"http://127.0.0.1:{ports[1]}", "/actuator/health/readiness")[0] == 200
                        if attempt:
                            status, body = request(base, "/api/restaurants/my-restaurants", token=owner)
                            assert status == 200 and any(row["id"] == restaurant_id for row in body["data"]), (status, body)
                            assert request(base, f"/api/menu-items/{menu_id}/inventory", token=owner)[1]["data"]["onHandQuantity"] == 5
                            assert sql("select count(*) from catalog_lifecycle_audits") == "4"
                            continue
                        restaurant = {"name": "Runtime Restaurant", "address": "Canonical runtime address", "phone": "0900000000",
                                      "addressLat": 10.75, "addressLng": 106.65, "defaultPrepTimeMinutes": 30}
                        assert request(base, "/api/restaurants", "POST", body=restaurant)[0] == 401
                        assert request(base, "/api/restaurants", "POST", owner[:-10] + "AAAAAAAAAA", restaurant)[0] == 401
                        assert request(base, "/api/restaurants", "POST", customer, restaurant)[0] == 403
                        status, body = request(base, "/api/restaurants", "POST", owner, restaurant)
                        assert status == 200 and body["status"] == 1, (status, body)
                        restaurant_id = body["data"]["id"]
                        assert body["data"]["lifecycleStatus"] == "ACTIVE" and body["data"]["version"] == 0
                        assert sql(f"select owner_principal_id || ':' || creator_id from restaurant where id={restaurant_id}") == "971:1971"
                        assert request(base, "/api/restaurants/my-restaurants", token=foreign)[1]["data"] == []
                        status, body = request(base, "/api/menu-items", "POST", owner,
                                               {"restaurantId": restaurant_id, "name": "Canonical meal", "price": 42.5})
                        assert status == 200 and body["data"]["price"] == 42.5, (status, body)
                        menu_id = body["data"]["id"]
                        zone_path = f"/api/restaurants/{restaurant_id}/serviceability-zones"
                        polygon = {"type": "Polygon", "coordinates": [[[106.6,10.7],[106.7,10.7],[106.7,10.8],[106.6,10.8],[106.6,10.7]]]}
                        zone = {"name": "Coverage", "polygonGeoJson": json.dumps(polygon), "priority": 0, "active": True}
                        assert request(base, zone_path, "POST", foreign, zone)[0] == 403
                        status, body = request(base, zone_path, "POST", owner, zone)
                        assert status == 200 and body["data"]["revision"] == 0, (status, body)
                        inventory_path = f"/api/menu-items/{menu_id}/inventory"
                        assert request(base, inventory_path, "PUT", foreign, {"onHandQuantity": 5})[0] == 403
                        status, body = request(base, inventory_path, "PUT", owner, {"onHandQuantity": 5})
                        assert status == 200 and body["data"]["availableQuantity"] == 5, (status, body)
                        checkout = {"restaurantId": restaurant_id, "deliveryLat": 10.75, "deliveryLng": 106.65,
                                    "items": [{"menuItemId": menu_id, "quantity": 2, "menuItemName": "Forged", "price": 0.01}]}
                        validation_path = "/api/restaurants/validate/order"
                        assert request(base, validation_path, "POST", body=checkout)[0] == 403
                        assert request(base, validation_path, "POST", body=checkout, internal="wrong")[0] == 403
                        status, body = request(base, validation_path, "POST", body=checkout, internal=SECRET)
                        assert status == 200 and body["status"] == 1 and body["data"]["calculatedTotal"] == 85, (status, body)
                        assert body["data"]["itemValidations"][0]["menuItemName"] == "Canonical meal"
                        assert body["data"]["restaurantInfo"]["serviceable"] is True
                        checkout["deliveryLat"] = 11
                        status, body = request(base, validation_path, "POST", body=checkout, internal=SECRET)
                        assert status == 200 and body["status"] == 0 and body["data"]["errors"][0]["errorCode"] == "OUTSIDE_ACTIVE_ZONES", (status, body)
                        checkout["deliveryLat"] = 10.75
                        reservation_id = str(uuid.uuid4())
                        reserve_path = "/api/menu-items/internal/inventory/reservations"
                        reservation = {"reservationId": reservation_id, "orderId": 99501, "userId": 300, "userPrincipalId": 400,
                                       "restaurantId": restaurant_id, "items": [{"menuItemId": menu_id, "quantity": 2}]}
                        # Match the actual Order client: Internal-Token, without a user's Bearer token.
                        status, body = request(base, reserve_path, "POST", body=reservation, internal=SECRET)
                        assert status == 200 and body["data"]["state"] == "RESERVED", (status, body)
                        assert request(base, reserve_path, "POST", body=reservation, internal="wrong")[0] == 403
                        transition_path = reserve_path + "/" + reservation_id
                        status, body = request(base, transition_path + "/commit?orderId=99501", "POST", internal=SECRET)
                        assert status == 200 and body["data"]["state"] == "COMMITTED", (status, body)
                        assert request(base, inventory_path, token=owner)[1]["data"]["onHandQuantity"] == 3
                        status, body = request(base, transition_path + "/release?orderId=99501", "POST", internal=SECRET)
                        assert status == 200 and body["data"]["state"] == "RELEASED", (status, body)
                        assert request(base, inventory_path, token=owner)[1]["data"]["onHandQuantity"] == 5
                        lifecycle_path = f"/api/restaurants/{restaurant_id}/lifecycle"
                        assert request(base, lifecycle_path, "PATCH", foreign, {"targetStatus": "PAUSED", "expectedVersion": 0})[0] == 403
                        status, body = request(base, lifecycle_path, "PATCH", owner, {"targetStatus": "PAUSED", "expectedVersion": 0})
                        assert status == 200 and body["data"]["version"] == 1, (status, body)
                        assert request(base, lifecycle_path, "PATCH", owner, {"targetStatus": "ACTIVE", "expectedVersion": 0})[0] == 409
                        assert request(base, validation_path, "POST", body=checkout, internal=SECRET)[1]["status"] == 0
                        assert request(base, lifecycle_path, "PATCH", owner, {"targetStatus": "ACTIVE", "expectedVersion": 1})[0] == 200
                        assert request(base, f"/api/restaurants/{restaurant_id}", "DELETE", owner)[0] == 200
                        status, body = request(base, f"/api/restaurants/{restaurant_id}")
                        assert status == 200 and body["data"]["lifecycleStatus"] == "ARCHIVED", (status, body)
                        assert all(row["id"] != restaurant_id for row in request(base, "/api/restaurants")[1]["data"])
                        assert request(base, validation_path, "POST", body=checkout, internal=SECRET)[1]["status"] == 0
                        assert request(base, lifecycle_path, "PATCH", owner, {"targetStatus": "PAUSED", "expectedVersion": 3})[0] == 400
                        status, body = request(base, lifecycle_path, "PATCH", admin, {"targetStatus": "PAUSED", "expectedVersion": 3})
                        assert status == 200 and body["data"]["version"] == 4, (status, body)
                        assert sql("select count(*) from catalog_lifecycle_audits") == "4"
                        assert sql("select count(*) from restaurant_outbox_events") == "6"
                    except Exception:
                        print(log.read_text()[-5000:])
                        raise
                    finally: stop(child)
        print("Restaurant packaged JAR PASSED: PostgreSQL migrations/restart, RS256/JWKS authentication, owner/admin authorization, canonical checkout, serviceability, inventory reserve/commit/compensation and lifecycle audit/outbox.")
    finally:
        if created: docker("rm", "-f", name)
        boundary.shutdown()
        boundary.server_close()
        worker.join(timeout=5)


if __name__ == "__main__": main()
