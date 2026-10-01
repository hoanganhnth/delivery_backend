import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "runtime_boundaries", Path(__file__).with_name("verify-runtime-dependency-boundaries.py")
)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class RuntimeDependencyBoundariesTest(unittest.TestCase):
    def test_relocated_boot_and_core_still_require_graph_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for layer, artifact in (("boot", "routing-service"), ("domain", "routing-domain")):
                pom = root / "routing" / layer / "pom.xml"
                pom.parent.mkdir(parents=True)
                pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">'
                               f'<artifactId>{artifact}</artifactId></project>')
            inventory = audit.inventory(root)
            self.assertEqual({(artifact, layer) for _, artifact, layer in inventory},
                             {("routing-service", "service"), ("routing-domain", "domain")})
            self.assertTrue(all(audit.check_graph(path, artifact, layer)
                                for path, artifact, layer in inventory))

    def graph(self, value):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "tree.tgf"
        path.write_text(value, encoding="utf-8")
        return path

    def test_service_rejects_transitive_foreign_service(self):
        path = self.graph("1 com.delivery:order-service:jar:1\n"
                          "2 com.delivery:shared-adapter:jar:1:compile\n"
                          "3 com.delivery:restaurant-service:jar:1:runtime\n#\n1 2 compile\n2 3 runtime\n")
        errors = audit.check_graph(path, "order-service", "service")
        self.assertEqual(len(errors), 1)
        self.assertIn("restaurant-service", errors[0])

    def test_service_contract_and_framework_dependencies_are_allowed(self):
        path = self.graph("1 com.delivery:order-service:jar:1\n"
                          "2 com.delivery:restaurant-contracts:jar:1:compile\n"
                          "3 org.springframework:spring-context:jar:1:compile\n#\n1 2 compile\n1 3 compile\n")
        self.assertEqual(audit.check_graph(path, "order-service", "service"), [])

    def test_core_rejects_framework_hidden_behind_allowed_domain(self):
        path = self.graph("1 com.delivery:order-application:jar:1\n"
                          "2 com.delivery:order-domain:jar:1:compile\n"
                          "3 org.springframework:spring-context:jar:1:compile\n#\n1 2 compile\n2 3 compile\n")
        self.assertTrue(audit.check_graph(path, "order-application", "application"))

    def test_core_rejects_foreign_domain_transitively(self):
        path = self.graph("1 com.delivery:order-application:jar:1\n"
                          "2 com.delivery:order-domain:jar:1:compile\n"
                          "3 com.delivery:restaurant-domain:jar:1:compile\n#\n1 2 compile\n2 3 compile\n")
        self.assertTrue(audit.check_graph(path, "order-application", "application"))

    def test_contract_rejects_runtime_database_dependency(self):
        path = self.graph("1 com.delivery:order-contracts:jar:1\n"
                          "2 jakarta.persistence:jakarta.persistence-api:jar:1:compile\n#\n1 2 compile\n")
        self.assertTrue(audit.check_graph(path, "order-contracts", "contract"))

    def test_missing_malformed_and_wrong_root_fail(self):
        self.assertTrue(audit.check_graph(Path('/nonexistent/tree.tgf'), "order-service", "service"))
        for graph in ("", "not a graph", "1 com.delivery:other-service:jar:1\n#\n",
                      "1 com.delivery:order-service:jar:1\n#\n1 2 compile\n"):
            with self.subTest(graph=graph):
                self.assertTrue(audit.check_graph(self.graph(graph), "order-service", "service"))

    def test_empty_dependency_graph_for_a_core_module_is_valid(self):
        path = self.graph("1 com.delivery:order-domain:jar:1\n#\n")
        self.assertEqual(audit.check_graph(path, "order-domain", "domain"), [])


if __name__ == "__main__":
    unittest.main()
