package com.delivery.promotion.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ModuleDependencyTest {
    @Test void productionSourceDependsOnlyOnJdkAndPromotionContracts() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.startsWith("import ")) {
                        assertThat(line).as(file.toString()).matches("import (java\\..*|com\\.delivery\\.promotion\\.(domain|application).*);");
                    }
                }
            }
        }
    }
}
