package com.delivery.search.application;

import com.delivery.search.application.api.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class DefaultSearchQueryUseCaseTest {
    @Test void observerFailureAndFatalReaderErrorKeepTheirOriginalBoundaries() {
        RuntimeException readFailure = new IllegalStateException("read failed");
        RuntimeException observerFailure = new IllegalArgumentException("observer failed");
        var observed = new DefaultSearchQueryUseCase<>(() -> query -> { throw readFailure; },
                "Dish", failure -> { assertSame(readFailure, failure); throw observerFailure; });
        assertSame(observerFailure, assertThrows(RuntimeException.class,
                () -> observed.search(new SearchQuery("q"))));
        AssertionError fatal = new AssertionError("fatal");
        var fatalReader = new DefaultSearchQueryUseCase<>(() -> query -> { throw fatal; },
                "Restaurant", failure -> fail("fatal errors must not be observed"));
        assertSame(fatal, assertThrows(AssertionError.class, () -> fatalReader.search(new SearchQuery("q"))));
    }
    @Test void successfulAndNullResultsStayUnchangedWithoutNewQueryPolicy() {
        for (Object result : new Object[]{new Object(), null}) {
            SearchQuery input = new SearchQuery(" q ");
            var useCase = new DefaultSearchQueryUseCase<>(() -> query -> {
                assertSame(input, query); return result;
            }, "Restaurant", failure -> fail("unexpected failure"));
            assertSame(result, useCase.search(input));
        }
    }
    @Test void unavailableFailureAndResolutionExceptionMaintainOrderingAndCause() {
        for (String resource : new String[]{"Restaurant", "Dish"}) {
            var failures = new ArrayList<RuntimeException>();
            var missing = new DefaultSearchQueryUseCase<>(() -> null, resource, failures::add);
            var unavailable = assertThrows(SearchUnavailableException.class, () -> missing.search(new SearchQuery("q")));
            assertEquals(resource + " search repository is unavailable", unavailable.getMessage());
            assertNull(unavailable.getCause()); assertTrue(failures.isEmpty());
            RuntimeException original = new IllegalStateException("backend details");
            var broken = new DefaultSearchQueryUseCase<>(() -> query -> { throw original; }, resource, failures::add);
            var failure = assertThrows(SearchUnavailableException.class, () -> broken.search(new SearchQuery("q")));
            assertEquals(resource + " search failed", failure.getMessage());
            assertSame(original, failure.getCause()); assertEquals(java.util.List.of(original), failures);
            failures.clear();
            var resolution = new DefaultSearchQueryUseCase<>(() -> { throw original; }, resource, failures::add);
            assertSame(original, assertThrows(RuntimeException.class, () -> resolution.search(new SearchQuery("q"))));
            assertTrue(failures.isEmpty());
        }
    }
}
