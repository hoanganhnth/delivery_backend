#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/delivery-build-foundation.XXXXXX")"
trap 'rm -rf "${TEMP_DIR}"' EXIT

current_effective="${TEMP_DIR}/identity-current-effective.xml"
baseline_effective="${TEMP_DIR}/identity-baseline-effective.xml"
unmigrated_effective="${TEMP_DIR}/notification-effective.xml"

mvn -B -f "${ROOT_DIR}/scripts/fixtures/build-foundation/identity-contracts-before-parent.xml" \
  help:effective-pom \
  -Doutput="${baseline_effective}"
mvn -B -f "${ROOT_DIR}/pom.xml" -pl platform/delivery-platform-bom -am \
  -DskipTests install
mvn -B -f "${ROOT_DIR}/identity-contracts/pom.xml" help:effective-pom \
  -Doutput="${current_effective}"
mvn -B -f "${ROOT_DIR}/notification/boot/pom.xml" help:effective-pom \
  -Doutput="${unmigrated_effective}"

python3 - "${baseline_effective}" "${current_effective}" "${unmigrated_effective}" <<'PY'
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def root(path):
    return ET.parse(path).getroot()

def text(node, name, default=""):
    found = node.find(f"m:{name}", NS)
    return default if found is None or found.text is None else found.text.strip()

def dependencies(project):
    result = []
    for dependency in project.findall("m:dependencies/m:dependency", NS):
        result.append(tuple(text(dependency, field, default) for field, default in (
            ("groupId", ""), ("artifactId", ""), ("version", ""),
            ("type", "jar"), ("classifier", ""), ("scope", "compile"),
            ("optional", "false"),
        )))
    return sorted(result)

def plugins(project, path):
    return {
        (text(plugin, "groupId", "org.apache.maven.plugins"), text(plugin, "artifactId")): plugin
        for plugin in project.findall(path, NS)
    }

baseline = root(sys.argv[1])
current = root(sys.argv[2])
unmigrated = root(sys.argv[3])

if dependencies(baseline) != dependencies(current):
    raise SystemExit("identity-contracts effective dependencies changed from baseline")

baseline_managed = plugins(baseline, "m:build/m:pluginManagement/m:plugins/m:plugin")
current_managed = plugins(current, "m:build/m:pluginManagement/m:plugins/m:plugin")
for key in (("org.apache.maven.plugins", "maven-surefire-plugin"),
            ("org.springframework.boot", "spring-boot-maven-plugin")):
    if key not in baseline_managed or key not in current_managed:
        raise SystemExit(f"managed plugin missing from effective POM: {key}")
    if text(baseline_managed[key], "version") != text(current_managed[key], "version"):
        raise SystemExit(f"managed plugin version changed from baseline: {key}")

active = plugins(current, "m:build/m:plugins/m:plugin")
jacoco_key = ("org.jacoco", "jacoco-maven-plugin")
if jacoco_key not in active or text(active[jacoco_key], "version") != "0.8.13":
    raise SystemExit("JaCoCo 0.8.13 is not active in identity-contracts")
goals = {
    goal.text.strip()
    for goal in active[jacoco_key].findall("m:executions/m:execution/m:goals/m:goal", NS)
    if goal.text
}
if not {"prepare-agent", "report", "check"}.issubset(goals):
    raise SystemExit("JaCoCo prepare-agent, report and check executions are not all active")
if ("org.springframework.boot", "spring-boot-maven-plugin") in active:
    raise SystemExit("library effective POM activates spring-boot-maven-plugin")

unmigrated_active = plugins(unmigrated, "m:build/m:plugins/m:plugin")
if jacoco_key in unmigrated_active:
    raise SystemExit("unmigrated notification-service unexpectedly gained JaCoCo")
PY

mvn -B -f "${ROOT_DIR}/pom.xml" -pl identity-contracts -am clean verify

mvn -B -f "${ROOT_DIR}/pom.xml" \
  -pl platform/delivery-platform-bom,platform/delivery-build-parent -am \
  -DskipTests install

fixture_dir="${TEMP_DIR}/coverage-fixture"
mkdir -p "${fixture_dir}/src/main/java/com/delivery/buildfixture" \
  "${fixture_dir}/src/test/java/com/delivery/buildfixture"

cat > "${fixture_dir}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.delivery</groupId>
        <artifactId>delivery-build-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <artifactId>delivery-build-coverage-fixture</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF

cat > "${fixture_dir}/src/main/java/com/delivery/buildfixture/ExecutedFixture.java" <<'EOF'
package com.delivery.buildfixture;

public final class ExecutedFixture {
    private ExecutedFixture() {}

    public static int answer() {
        return 42;
    }
}
EOF

cat > "${fixture_dir}/src/main/java/com/delivery/buildfixture/UnexecutedFixture.java" <<'EOF'
package com.delivery.buildfixture;

public final class UnexecutedFixture {
    private UnexecutedFixture() {}

    public static int neverCalled() {
        return -1;
    }
}
EOF

cat > "${fixture_dir}/src/test/java/com/delivery/buildfixture/ExecutedFixtureTest.java" <<'EOF'
package com.delivery.buildfixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExecutedFixtureTest {
    @Test
    void executesOneProductionClass() {
        assertEquals(42, ExecutedFixture.answer());
    }
}
EOF

mvn -B -f "${fixture_dir}/pom.xml" clean verify

python3 - "${fixture_dir}/target/site/jacoco/jacoco.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

report = ET.parse(sys.argv[1]).getroot()
target = next((node for node in report.findall(".//class")
               if node.get("name") == "com/delivery/buildfixture/UnexecutedFixture"), None)
if target is None:
    raise SystemExit("JaCoCo XML omitted the unexecuted fixture class")
instruction = next((counter for counter in target.findall("counter")
                    if counter.get("type") == "INSTRUCTION"), None)
if instruction is None or int(instruction.get("missed", "0")) == 0:
    raise SystemExit("unexecuted fixture does not have missed instructions")
PY

fixture_jar="${fixture_dir}/target/delivery-build-coverage-fixture-1.0.0-SNAPSHOT.jar"
if jar tf "${fixture_jar}" | rg -q '^BOOT-INF/'; then
  echo "Library fixture was repackaged as a Spring Boot executable JAR." >&2
  exit 1
fi
if [[ -e "${fixture_jar}.original" ]]; then
  echo "Spring Boot repackage left an unexpected original library JAR." >&2
  exit 1
fi

threshold_fixture="${TEMP_DIR}/coverage-threshold-fixture"
mkdir -p "${threshold_fixture}"
cp -R "${fixture_dir}/src" "${threshold_fixture}/src"
cat > "${threshold_fixture}/pom.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.delivery</groupId>
        <artifactId>delivery-build-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <artifactId>delivery-build-coverage-threshold-fixture</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <properties>
        <delivery.coverage.line.minimum>0.85</delivery.coverage.line.minimum>
        <delivery.coverage.branch.minimum>0.85</delivery.coverage.branch.minimum>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
EOF
if mvn -B -f "${threshold_fixture}/pom.xml" clean verify \
    > "${TEMP_DIR}/coverage-threshold.log" 2>&1; then
  echo "JaCoCo accepted fixture coverage below the required 85%." >&2
  exit 1
fi
if ! rg -Fq 'Coverage checks have not been met' "${TEMP_DIR}/coverage-threshold.log"; then
  echo "Coverage threshold fixture failed for an unexpected reason." >&2
  sed -n '1,200p' "${TEMP_DIR}/coverage-threshold.log" >&2
  exit 1
fi

echo "Build foundation verification passed."
