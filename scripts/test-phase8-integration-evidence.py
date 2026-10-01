import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "evidence", Path(__file__).with_name("verify-phase8-integration-evidence.py")
)
evidence = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(evidence)


class IntegrationEvidenceTest(unittest.TestCase):
    def report(self, content):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "TEST-example.xml"
        path.write_text(content, encoding="utf-8")
        return path

    def test_executed_successful_suite_passes(self):
        path = self.report('<testsuite tests="1" failures="0" errors="0" skipped="0">'
                           '<testcase name="race"/></testsuite>')
        self.assertEqual(evidence.check_report(path), [])

    def test_skipped_test_fails_even_when_summary_omits_skip(self):
        path = self.report('<testsuite tests="1"><testcase><skipped/></testcase></testsuite>')
        self.assertTrue(any("skipped" in error for error in evidence.check_report(path)))

    def test_failure_and_error_are_rejected(self):
        for outcome in ("failure", "error"):
            with self.subTest(outcome=outcome):
                path = self.report('<testsuite tests="1"><testcase><' + outcome +
                                   '/></testcase></testsuite>')
                self.assertTrue(evidence.check_report(path))

    def test_empty_suite_cannot_pass(self):
        self.assertTrue(evidence.check_report(self.report('<testsuite tests="0"/>')))

    def test_summary_without_testcases_cannot_pass(self):
        self.assertTrue(evidence.check_report(self.report('<testsuite tests="1"/>')))

    def test_missing_and_malformed_reports_fail(self):
        self.assertTrue(evidence.check_report(Path('/nonexistent/TEST-example.xml')))
        self.assertTrue(evidence.check_report(self.report('not XML')))

    def test_summary_skip_and_failure_counts_cannot_be_ignored(self):
        for attribute in ("failures", "errors", "skipped"):
            with self.subTest(attribute=attribute):
                path = self.report('<testsuite tests="1" ' + attribute +
                                   '="1"><testcase/></testsuite>')
                self.assertTrue(evidence.check_report(path))

    def test_inventory_requires_every_mandatory_suite(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = evidence.verify(Path(directory))
            self.assertEqual(len(errors), 10)
            self.assertTrue(all("missing report" in error for error in errors))


if __name__ == '__main__':
    unittest.main()
