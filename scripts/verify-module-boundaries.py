#!/usr/bin/env python3
"""Verify opt-in module boundaries without adding an architecture dependency."""

from __future__ import annotations

import argparse
import re
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
FORBIDDEN_CORE_GROUPS = (
    "org.springframework",
    "jakarta.persistence",
    "org.hibernate",
    "org.apache.kafka",
    "io.lettuce",
    "redis.clients",
)
FORBIDDEN_CORE_IMPORTS = (
    "org.springframework.",
    "jakarta.persistence.",
    "org.hibernate.",
    "org.apache.kafka.",
    "io.lettuce.",
    "redis.clients.",
)
CORE_SUFFIXES = ("-domain", "-application-api", "-application")


def child_text(node: ET.Element, path: str, default: str = "") -> str:
    found = node.find(path, NS)
    return default if found is None or found.text is None else found.text.strip()


def parse_pom(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def core_kind(artifact_id: str) -> tuple[str, str] | None:
    for suffix in CORE_SUFFIXES:
        if artifact_id.endswith(suffix):
            return artifact_id[: -len(suffix)], suffix[1:]
    return None


def verify_core_module(pom_path: Path) -> list[str]:
    errors: list[str] = []
    project = parse_pom(pom_path)
    artifact_id = child_text(project, "m:artifactId")
    kind = core_kind(artifact_id)
    if kind is None:
        return errors
    service, layer = kind

    parent = (
        child_text(project, "m:parent/m:groupId"),
        child_text(project, "m:parent/m:artifactId"),
    )
    if parent != ("com.delivery", "delivery-build-parent"):
        errors.append(f"{pom_path}: core module must inherit delivery-build-parent")

    if layer in {"domain", "application"}:
        for prop in ("line", "branch"):
            actual = child_text(
                project, f"m:properties/m:delivery.coverage.{prop}.minimum"
            )
            if actual not in {"0.85", "0.850", "85%"}:
                errors.append(
                    f"{pom_path}: {layer} must enforce 85% {prop} coverage"
                )

    allowed_delivery = {
        "domain": set(),
        "application-api": {f"{service}-domain"},
        "application": {f"{service}-domain", f"{service}-application-api"},
    }[layer]
    for dependency in project.findall("m:dependencies/m:dependency", NS):
        if child_text(dependency, "m:scope", "compile") == "test":
            continue
        group_id = child_text(dependency, "m:groupId")
        dependency_artifact = child_text(dependency, "m:artifactId")
        if group_id.startswith(FORBIDDEN_CORE_GROUPS):
            errors.append(
                f"{pom_path}: {layer} has forbidden production dependency "
                f"{group_id}:{dependency_artifact}"
            )
        if group_id == "com.delivery" and dependency_artifact not in allowed_delivery:
            errors.append(
                f"{pom_path}: {layer} cannot depend on {dependency_artifact}"
            )

    source_root = pom_path.parent / "src" / "main" / "java"
    for source in source_root.rglob("*.java") if source_root.exists() else ():
        text = source.read_text(encoding="utf-8")
        if layer == "application-api":
            declaration = re.search(r"public\s+(interface|class|record|enum)\s+", text)
            method_body = re.search(
                r"\b(?:default|static|private)\b[^;{}]*\([^;{}]*\)\s*\{",
                text,
                re.DOTALL,
            )
            if declaration is None or declaration.group(1) != "interface" or method_body:
                errors.append(
                    f"{source}: application-api must contain behavior-free interfaces only"
                )
        for forbidden in FORBIDDEN_CORE_IMPORTS:
            if f"import {forbidden}" in text:
                errors.append(f"{source}: forbidden core import {forbidden}")
    return errors


def verify_build_parent(root: Path) -> list[str]:
    path = root / "platform" / "delivery-build-parent" / "pom.xml"
    if not path.exists():
        return [f"{path}: missing build parent"]
    project = parse_pom(path)
    plugin = next(
        (
            item
            for item in project.findall("m:build/m:plugins/m:plugin", NS)
            if child_text(item, "m:artifactId") == "jacoco-maven-plugin"
        ),
        None,
    )
    if plugin is None:
        return [f"{path}: JaCoCo plugin is not active"]
    goals = {
        goal.text.strip()
        for goal in plugin.findall("m:executions/m:execution/m:goals/m:goal", NS)
        if goal.text
    }
    if not {"prepare-agent", "report", "check"}.issubset(goals):
        return [f"{path}: JaCoCo prepare-agent/report/check must all be active"]
    return []


def verify_contracts(root: Path) -> list[str]:
    errors: list[str] = []
    candidates = list(root.glob("*-contracts/pom.xml"))
    candidates += list((root / "contracts").glob("*/pom.xml")) if (root / "contracts").exists() else []
    for pom_path in candidates:
        project = parse_pom(pom_path)
        for dependency in project.findall("m:dependencies/m:dependency", NS):
            if child_text(dependency, "m:scope", "compile") == "test":
                continue
            group_id = child_text(dependency, "m:groupId")
            if group_id.startswith(("org.springframework", "org.apache.kafka", "org.hibernate")):
                errors.append(f"{pom_path}: contract has runtime framework dependency {group_id}")
    return errors


def verify_cross_service_imports(root: Path) -> list[str]:
    errors: list[str] = []
    pattern = re.compile(r"^import com\.delivery\.([a-z0-9_]+_service)\.", re.MULTILINE)
    for service_dir in root.glob("*-service"):
        own_package = service_dir.name.replace("-", "_")
        source_root = service_dir / "src" / "main" / "java"
        for source in source_root.rglob("*.java") if source_root.exists() else ():
            for imported_service in pattern.findall(source.read_text(encoding="utf-8")):
                if imported_service != own_package:
                    errors.append(
                        f"{source}: imports internals from {imported_service}"
                    )
    return errors


def verify(root: Path) -> list[str]:
    errors = verify_build_parent(root)
    errors.extend(verify_contracts(root))
    errors.extend(verify_cross_service_imports(root))
    modules_root = root / "modules"
    if modules_root.exists():
        for pom_path in modules_root.glob("*/*/pom.xml"):
            errors.extend(verify_core_module(pom_path))
    return errors


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="delivery-boundary-test.") as temp:
        root = Path(temp)
        module = root / "modules" / "restaurant" / "restaurant-domain"
        source_root = module / "src" / "main" / "java"
        source_root.mkdir(parents=True)
        (module / "pom.xml").write_text(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <parent><groupId>com.delivery</groupId><artifactId>delivery-build-parent</artifactId><version>1</version></parent>
            <artifactId>restaurant-domain</artifactId>
            <dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId></dependency></dependencies>
            </project>""",
            encoding="utf-8",
        )
        errors = verify_core_module(module / "pom.xml")
        assert any("85% line" in error for error in errors), errors
        assert any("forbidden production dependency" in error for error in errors), errors

        api = root / "modules" / "restaurant" / "restaurant-application-api"
        api_source = api / "src" / "main" / "java" / "RestaurantUseCase.java"
        api_source.parent.mkdir(parents=True)
        (api / "pom.xml").write_text(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion>
            <parent><groupId>com.delivery</groupId><artifactId>delivery-build-parent</artifactId><version>1</version></parent>
            <artifactId>restaurant-application-api</artifactId>
            <dependencies><dependency><groupId>com.delivery</groupId><artifactId>order-domain</artifactId></dependency></dependencies>
            </project>""",
            encoding="utf-8",
        )
        api_source.write_text(
            """public interface RestaurantUseCase {
            static void hiddenBehavior() { throw new UnsupportedOperationException(); }
            }""",
            encoding="utf-8",
        )
        errors = verify_core_module(api / "pom.xml")
        assert any("cannot depend on order-domain" in error for error in errors), errors
        assert any("behavior-free interfaces" in error for error in errors), errors

        contract = root / "order-contracts"
        contract.mkdir()
        (contract / "pom.xml").write_text(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
            <modelVersion>4.0.0</modelVersion><artifactId>order-contracts</artifactId>
            <dependencies><dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId></dependency></dependencies>
            </project>""",
            encoding="utf-8",
        )
        assert any("runtime framework dependency" in error for error in verify_contracts(root))

        service_source = (
            root / "order-service" / "src" / "main" / "java" / "OrderLeak.java"
        )
        service_source.parent.mkdir(parents=True)
        service_source.write_text(
            "import com.delivery.restaurant_service.repository.RestaurantRepository;",
            encoding="utf-8",
        )
        assert any("imports internals" in error for error in verify_cross_service_imports(root))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    errors = verify(args.root.resolve())
    if errors:
        print("Module boundary verification failed:")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Module boundary verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
