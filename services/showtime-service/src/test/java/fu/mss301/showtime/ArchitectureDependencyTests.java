package fu.mss301.showtime;

import fu.mss301.showtime.domain.model.Showtime;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureDependencyTests {
    private final Path source = Path.of("src/main/java/fu/mss301/showtime");

    @Test
    void domainAndApplicationOnlyDependOnJavaAndInnerLayers() throws Exception {
        assertDependencies("domain", Set.of("java.", "fu.mss301.showtime.domain."));
        assertDependencies("application", Set.of("java.", "fu.mss301.showtime.domain.", "fu.mss301.showtime.application."));
    }

    @Test
    void webCannotBypassUseCasesAndOtherAdaptersDoNotImportWeb() throws Exception {
        Path web = source.resolve("infrastructure/web");
        assertTrue(Files.isDirectory(web));
        try (var files = Files.walk(source.resolve("infrastructure"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                if (file.startsWith(web)) {
                    assertFalse(Pattern.compile("fu\\.mss301\\.showtime\\.infrastructure\\.(?!web\\.)").matcher(text).find(), file.toString());
                    assertFalse(text.contains("showtime.domain.repository."), file.toString());
                    assertFalse(text.contains("BookingRepository") || text.contains("PaymentRepository"), file.toString());
                } else assertFalse(text.contains("fu.mss301.showtime.infrastructure.web."), file.toString());
            }
        }
    }

    @Test
    void aggregateHasNoPublicConstructorOrSettersAndNoChildRepository() throws Exception {
        assertEquals(0, Showtime.class.getConstructors().length);
        for (var method : Showtime.class.getMethods()) assertFalse(method.getName().startsWith("set"));
        try (var files = Files.list(source.resolve("domain/repository"))) {
            assertEquals(Set.of("ShowtimeRepository.java"), files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    private void assertDependencies(String layer, Set<String> allowed) throws Exception {
        var imports = Pattern.compile("(?m)^import\\s+(?:static\\s+)?([^;]+);");
        try (var files = Files.walk(source.resolve(layer))) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
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
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var text = Files.readString(file);
                for (var reference : forbidden) assertFalse(text.contains(reference), file + ": " + reference);
            }
        }
    }
}
