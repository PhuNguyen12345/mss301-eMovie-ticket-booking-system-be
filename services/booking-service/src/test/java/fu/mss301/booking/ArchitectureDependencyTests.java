package fu.mss301.booking;

import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureDependencyTests {
    private final Path source = Path.of("src/main/java/fu/mss301/booking");

    @Test
    void domainAndApplicationOnlyDependOnJavaAndInnerLayers() throws Exception {
        assertDependencies("domain", Set.of("java.", "fu.mss301.booking.domain."));
        assertDependencies("application", Set.of("java.", "fu.mss301.booking.domain.", "fu.mss301.booking.application."));
    }

    @Test
    void innerLayersDoNotUseFullyQualifiedFrameworkNames() throws Exception {
        for (var layer : List.of("domain", "application")) {
            assertNoReferences(layer, "org.springframework", "jakarta.", "lombok.", "org.slf4j", "fu.mss301.common",
                "fu.mss301.booking.infrastructure.", "fu.mss301.booking.presentation.");
        }
    }

    @Test
    void presentationCannotBypassUseCasesAndInfrastructureDoesNotImportPresentation() throws Exception {
        assertNoReferences("presentation", "fu.mss301.booking.infrastructure.", "BookingRepository", "PaymentRepository");
        assertNoReferences("infrastructure", "fu.mss301.booking.presentation.");
    }

    @Test
    void aggregatesHaveNoPublicConstructorOrSetters() {
        for (var aggregate : List.of(Booking.class, PaymentTransaction.class)) {
            assertEquals(0, aggregate.getConstructors().length, aggregate.getName());
            for (var method : aggregate.getMethods()) {
                assertFalse(method.getName().startsWith("set"), aggregate.getName() + "." + method.getName());
            }
        }
    }

    @Test
    void onlyAggregateRootsHaveRepositories() throws Exception {
        try (var files = Files.walk(source.resolve("domain"))) {
            var repositories = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith("Repository.java")).collect(Collectors.toSet());
            assertEquals(Set.of("BookingRepository.java", "PaymentRepository.java"), repositories);
        }
    }

    private void assertDependencies(String layer, Set<String> allowed) throws Exception {
        var imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");
        try (var files = Files.walk(source.resolve(layer))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = imports.matcher(Files.readString(file));
                while (matcher.find()) {
                    var reference = matcher.group(1);
                    assertTrue(allowed.stream().anyMatch(reference::startsWith), file + ": " + reference);
                }
            }
        }
    }

    private void assertNoReferences(String layer, String... forbidden) throws Exception {
        try (var files = Files.walk(source.resolve(layer))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var text = Files.readString(file);
                for (var reference : forbidden) assertFalse(text.contains(reference), file + ": " + reference);
            }
        }
    }
}
