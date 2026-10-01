import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("coverage_report", Path(__file__).with_name("report-phase8-coverage.py"))
report = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(report)


class CoverageReportTest(unittest.TestCase):
    def test_relocated_core_report_cannot_be_omitted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, "routing/domain", covered=84, gate="0.85")
            pom = root / "routing/domain/pom.xml"
            pom.write_text(pom.read_text().replace(
                '<properties>', '<artifactId>routing-domain</artifactId><properties>'))
            for name in report.SERVICES:
                self.fixture(root, f"{name}-service")
            rows, errors = report.collect(root)
            self.assertEqual(len(rows), 7)
            self.assertIn("routing/domain: LINE below configured gate 0.85", errors)

    def test_scope_partition_keeps_unknown_business_and_reports_support_separately(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text('<report>'
                            '<package name="com/delivery/demo/service"><counter type="LINE" covered="8" missed="2"/></package>'
                            '<package name="com/delivery/demo/unfamiliar"><counter type="LINE" covered="1" missed="9"/></package>'
                            '<package name="com/delivery/demo/dto"><counter type="LINE" covered="10" missed="0"/></package>'
                            '<package name="com/delivery/demo/config"><counter type="LINE" covered="3" missed="1"/></package>'
                            '<package name="db/migration"><counter type="LINE" covered="4" missed="2"/></package>'
                            '<counter type="LINE" covered="26" missed="14"/></report>')
            scopes = report.read_scope_counters(path)
            self.assertEqual(scopes["business"]["counters"]["LINE"], {"covered": 9, "missed": 11})
            self.assertEqual(scopes["business"]["percent"]["LINE"], 45.0)
            self.assertEqual(scopes["model_contracts"]["percent"]["LINE"], 100.0)
            self.assertEqual(scopes["migrations"]["packages"], ["db/migration"])

    def test_scope_partition_rejects_missing_package_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text('<report><counter type="LINE" covered="10" missed="0"/></report>')
            with self.assertRaises(ValueError):
                report.read_scope_counters(path)

    def read(self, content):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text(content, encoding="utf-8")
            return report.read_counters(path)

    def test_uses_bundle_counters_without_double_counting_packages(self):
        result = self.read('<report><package><counter type="LINE" covered="8" missed="2"/></package>'
                           '<counter type="LINE" covered="8" missed="2"/>'
                           '<counter type="BRANCH" covered="3" missed="1"/></report>')
        self.assertEqual(result["LINE"], {"covered": 8, "missed": 2})
        self.assertEqual(report.percentage(result["LINE"]), 80.0)

    def test_zero_branches_are_not_fabricated_as_full_coverage(self):
        result = self.read('<report><counter type="LINE" covered="2" missed="0"/></report>')
        self.assertIsNone(report.percentage(result["BRANCH"]))

    def test_report_without_executable_lines_fails(self):
        with self.assertRaises(ValueError):
            self.read('<report/>')

    def test_exact_gate_rejects_percentage_that_rounds_up(self):
        self.assertFalse(report.meets_gate({"covered": 84999, "missed": 15001}, "0.85"))
        self.assertTrue(report.meets_gate({"covered": 85, "missed": 15}, "0.85"))

    def test_missing_report_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError):
                report.read_counters(Path(directory) / "absent.xml")

    def fixture(self, root, module, covered=90, gate=None):
        directory = root / module
        directory.mkdir(parents=True)
        properties = "" if gate is None else (
            "<properties><delivery.coverage.line.minimum>" + gate +
            "</delivery.coverage.line.minimum><delivery.coverage.branch.minimum>" + gate +
            "</delivery.coverage.branch.minimum></properties>")
        (directory / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">' + properties + '</project>')
        xml = directory / "target/site/jacoco/jacoco.xml"
        xml.parent.mkdir(parents=True)
        xml.write_text('<report><package name="com/delivery/demo/service"><counter type="LINE" covered="' + str(covered) +
                       '" missed="' + str(100-covered) +
                       '"/><counter type="BRANCH" covered="90" missed="10"/></package><counter type="LINE" covered="' + str(covered) +
                       '" missed="' + str(100-covered) +
                       '"/><counter type="BRANCH" covered="90" missed="10"/></report>')

    def test_inventory_requires_all_services_and_rejects_weakened_core_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, "modules/demo/demo-domain", gate="0.80")
            _, errors = report.collect(root)
            self.assertEqual(len(errors), 8)
            self.assertTrue(any("weakened LINE gate" in error for error in errors))
            self.assertTrue(any("simulator-service" in error for error in errors))

    def test_core_below_gate_fails_while_service_baseline_has_no_new_threshold(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, "modules/demo/demo-domain", covered=84, gate="0.85")
            for service in report.SERVICES:
                self.fixture(root, service + "-service", covered=20)
            rows, errors = report.collect(root)
            self.assertEqual(len(rows), 7)
            self.assertEqual(errors, ["modules/demo/demo-domain: LINE below configured gate 0.85"])


if __name__ == "__main__":
    unittest.main()
