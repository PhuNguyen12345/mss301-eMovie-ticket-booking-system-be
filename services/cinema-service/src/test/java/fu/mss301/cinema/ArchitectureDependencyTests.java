package fu.mss301.cinema;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ArchitectureDependencyTests {
    private static final Path SOURCE = Path.of("src/main/java/fu/mss301/cinema");

    @Test
    void coreHasNoFrameworkOrAdapterDependencies() throws IOException {
        for (String layer : new String[] {"domain", "application"}) {
            reject(layer, "org.springframework.", "jakarta.", "org.hibernate.",
                    "fu.mss301.cinema.infrastructure.");
        }
        reject("domain", "fu.mss301.cinema.application.", "fu.mss301.common.");
    }

    @Test
    void webCannotBypassApplicationIntoPersistenceOrStorage() throws IOException {
        reject("infrastructure/web", "fu.mss301.cinema.infrastructure.persistence.",
                "fu.mss301.cinema.infrastructure.storage.", "jakarta.persistence.",
                "org.springframework.data.jpa.");
        reject("infrastructure/persistence", "fu.mss301.cinema.infrastructure.web.");
    }

    private void reject(String directory, String... forbidden) throws IOException {
        Path root = SOURCE.resolve(directory);
        assertTrue(Files.isDirectory(root), root.toString());
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String prefix : forbidden) {
                    assertFalse(source.contains(prefix), file + " depends on " + prefix);
                }
            }
        }
    }
}
