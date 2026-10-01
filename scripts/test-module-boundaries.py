"""Regression fixtures for implementation coupling across service boundaries."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location(
    "boundaries", Path(__file__).with_name("verify-module-boundaries.py")
)
boundaries = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(boundaries)


class ServiceBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def source(self, service, content):
        source = self.root / service / "src/main/java/Boundary.java"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(content, encoding="utf-8")

    def pom(self, content):
        path = self.root / "order-service/pom.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            '<groupId>com.delivery</groupId><artifactId>order-service</artifactId>'
            + content + '</project>', encoding="utf-8"
        )

    def test_static_import_of_foreign_implementation_is_rejected(self):
        self.source("order-service", "  import static com.delivery.restaurant_service.Policy.check;")
        self.assertEqual(len(boundaries.verify_cross_service_imports(self.root)), 1)

    def test_simulator_implementation_import_is_rejected(self):
        self.source("order-service", "import com.delivery.simulator.service.SimulationService;")
        self.assertEqual(len(boundaries.verify_cross_service_imports(self.root)), 1)

    def test_simulator_can_import_its_own_implementation(self):
        self.source("simulator-service", "import static com.delivery.simulator.service.Policy.check;")
        self.assertEqual(boundaries.verify_cross_service_imports(self.root), [])

    def test_contract_and_own_imports_are_allowed(self):
        self.source("order-service", "import com.delivery.order_service.Order;\n"
                    "import com.delivery.restaurant.contracts.RestaurantSnapshot;")
        self.assertEqual(boundaries.verify_cross_service_imports(self.root), [])

    def test_direct_service_dependency_is_rejected(self):
        self.pom('<dependencies><dependency><groupId>com.delivery</groupId>'
                 '<artifactId>restaurant-service</artifactId></dependency></dependencies>')
        self.assertEqual(len(boundaries.verify_cross_service_dependencies(self.root)), 1)

    def test_profile_service_dependency_is_rejected(self):
        self.pom('<profiles><profile><id>production</id><dependencies><dependency>'
                 '<groupId>com.delivery</groupId><artifactId>restaurant-service</artifactId>'
                 '</dependency></dependencies></profile></profiles>')
        self.assertEqual(len(boundaries.verify_cross_service_dependencies(self.root)), 1)

    def test_test_and_contract_dependencies_are_allowed(self):
        self.pom('<dependencies><dependency><groupId>com.delivery</groupId>'
                 '<artifactId>restaurant-service</artifactId><scope>test</scope></dependency>'
                 '<dependency><groupId>com.delivery</groupId>'
                 '<artifactId>restaurant-contracts</artifactId></dependency></dependencies>')
        self.assertEqual(boundaries.verify_cross_service_dependencies(self.root), [])

    def test_dependency_management_does_not_add_a_service_link(self):
        self.pom('<dependencyManagement><dependencies><dependency>'
                 '<groupId>com.delivery</groupId><artifactId>restaurant-service</artifactId>'
                 '</dependency></dependencies></dependencyManagement>')
        self.assertEqual(boundaries.verify_cross_service_dependencies(self.root), [])

    def test_contract_cannot_depend_on_persistence_or_redis(self):
        for group in ("jakarta.persistence", "io.lettuce", "redis.clients"):
            with self.subTest(group=group):
                contract = self.root / "order-contracts/pom.xml"
                contract.parent.mkdir(exist_ok=True)
                contract.write_text(
                    '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                    '<dependencies><dependency><groupId>' + group + '</groupId>'
                    '<artifactId>runtime</artifactId></dependency></dependencies></project>',
                    encoding="utf-8"
                )
                self.assertEqual(len(boundaries.verify_contracts(self.root)), 1)

    def test_core_static_framework_import_is_rejected(self):
        module = self.root / "modules/order/order-domain"
        source = module / "src/main/java/Order.java"
        source.parent.mkdir(parents=True)
        source.write_text("import static org.springframework.util.Assert.notNull;", encoding="utf-8")
        (module / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            '<parent><groupId>com.delivery</groupId><artifactId>delivery-build-parent</artifactId>'
            '</parent><artifactId>order-domain</artifactId><properties>'
            '<delivery.coverage.line.minimum>0.85</delivery.coverage.line.minimum>'
            '<delivery.coverage.branch.minimum>0.85</delivery.coverage.branch.minimum>'
            '</properties></project>', encoding="utf-8"
        )
        errors = boundaries.verify_core_module(module / "pom.xml")
        self.assertEqual(len(errors), 1)
        self.assertIn("forbidden core import", errors[0])


if __name__ == "__main__":
    unittest.main()
