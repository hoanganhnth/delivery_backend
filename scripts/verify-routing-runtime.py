#!/usr/bin/env python3
"""Exercise the packaged Routing service through HTTP with a local provider."""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
SECRET = "routing-runtime-proof-only"
HTTP = build_opener(ProxyHandler({}))


class Provider(BaseHTTPRequestHandler):
    unavailable = False
    paths = []

    def do_GET(self):
        self.paths.append(self.path)
        if self.unavailable:
            self.send_response(503)
            self.end_headers()
            return
        if self.path.startswith('/directions-matrix/'):
            value = {"durations": [[60, 120]], "distances": [[450, 900]]}
        else:
            value = {"routes": [{"duration": 120, "distance": 900,
                                 "geometry": {"type": "LineString", "coordinates": []}}]}
        payload = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_):
        pass


def request(url, body=None, token=SECRET):
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["Internal-Token"] = token
    payload = None if body is None else json.dumps(body).encode()
    try:
        with HTTP.open(Request(url, data=payload, headers=headers), timeout=5) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        return error.code, None


def main():
    jars = list((ROOT / "routing/boot/target").glob("*.jar"))
    if len(jars) != 1:
        raise RuntimeError("Build exactly one routing/boot executable JAR first")
    provider = ThreadingHTTPServer(("127.0.0.1", 0), Provider)
    worker = threading.Thread(target=provider.serve_forever, daemon=True)
    worker.start()
    try:
        with tempfile.TemporaryDirectory(prefix="routing-runtime-") as directory:
            log = Path(directory) / "routing.log"
            environment = {key: value for key, value in os.environ.items()
                           if not key.startswith(("SPRING_", "EUREKA_", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"))}
            with log.open("w") as output:
                child = subprocess.Popen([
                    "java", "-jar", str(jars[0]), "--server.address=127.0.0.1", "--server.port=0",
                    "--management.server.address=127.0.0.1", "--management.server.port=0",
                    f"--spring.config.import=optional:configtree:{directory}/",
                    "--spring.cloud.config.enabled=false", "--eureka.client.enabled=false",
                    f"--app.internal.secret={SECRET}", "--platform.secrets.internal-secret-required=true",
                    "--routing.mapbox-token=local-provider-fixture",
                    # Avoid measuring cold JVM/WebClient initialization in this
                    # contract proof. The production timeout remains unchanged.
                    "--routing.provider-timeout-ms=5000",
                    f"--routing.mapbox-base-url=http://127.0.0.1:{provider.server_port}",
                ], stdout=output, stderr=subprocess.STDOUT, cwd=ROOT, env=environment)
                try:
                    deadline = time.monotonic() + 90
                    while time.monotonic() < deadline:
                        text = log.read_text()
                        ports = re.findall(r"Tomcat started on port (\d+)", text)
                        if len(ports) >= 2:
                            break
                        if child.poll() is not None:
                            raise RuntimeError("Routing exited during startup:\n" + text[-4000:])
                        time.sleep(0.25)
                    else:
                        raise RuntimeError("Routing startup timed out:\n" + log.read_text()[-4000:])
                    base = f"http://127.0.0.1:{ports[0]}/internal/routing/v1"
                    status, health = request(f"http://127.0.0.1:{ports[1]}/actuator/health/readiness")
                    assert status == 200 and health["status"] == "UP", (status, health)
                    origin, destination = {"lat": 10.76, "lng": 106.66}, {"lat": 10.78, "lng": 106.68}
                    bodies = {
                        "route": {"profile": "driving", "origin": origin, "destination": destination,
                                  "includeGeometry": True},
                        "matrix": {"profile": "driving", "origin": origin, "destinations": [
                            {"id": "a", "coordinate": destination}, {"id": "b", "coordinate": origin}]},
                        "eta-window": {"origin": origin, "destination": destination, "prepMinutes": 15},
                    }
                    for endpoint, body in bodies.items():
                        for token in (None, "wrong-token"):
                            assert request(f"{base}/{endpoint}", body, token)[0] == 403, endpoint
                    status, route = request(f"{base}/route", bodies["route"])
                    assert status == 200 and route["source"] == "MAPBOX_DIRECTIONS", (route, Provider.paths)
                    assert (route["durationSeconds"], route["distanceMeters"]) == (120, 900), route
                    assert json.loads(route["geometry"])["type"] == "LineString", route
                    status, matrix = request(f"{base}/matrix", bodies["matrix"])
                    assert status == 200 and [(row["id"], row["durationSeconds"], row["source"])
                        for row in matrix["results"]] == [("a", 60, "MAPBOX_MATRIX"), ("b", 120, "MAPBOX_MATRIX")], matrix
                    status, eta = request(f"{base}/eta-window", bodies["eta-window"])
                    assert status == 200 and eta == {"minMinutes": 17, "maxMinutes": 27,
                                                    "source": "MAPBOX_DIRECTIONS"}, eta
                    assert any('/directions-matrix/v1/mapbox/driving/' in path for path in Provider.paths)
                    Provider.unavailable = True
                    for endpoint, body in bodies.items():
                        status, result = request(f"{base}/{endpoint}", body)
                        assert status == 200, (endpoint, status)
                        rows = result["results"] if endpoint == "matrix" else [result]
                        assert all(row["source"] == "GEODESIC_FALLBACK" for row in rows), result
                    print("Routing packaged JAR PASSED: readiness, all 3 HTTP contracts, missing/wrong "
                          "credentials, Mapbox adapter fixture and provider-failure fallback.")
                finally:
                    child.terminate()
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=5)
    finally:
        provider.shutdown()
        provider.server_close()
        worker.join(timeout=5)


if __name__ == "__main__":
    main()
