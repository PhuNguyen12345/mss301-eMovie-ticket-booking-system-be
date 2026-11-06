package fu.mss301.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.RefreshCredential;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.infrastructure.persistence.entity.PasswordResetOtp;
import fu.mss301.user.infrastructure.persistence.entity.PendingRegistration;
import fu.mss301.user.infrastructure.persistence.entity.RefreshToken;
import fu.mss301.user.infrastructure.persistence.entity.Role;
import fu.mss301.user.infrastructure.persistence.entity.User;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureDependencyTest {
    private static final Path SOURCE = Path.of("src/main/java/fu/mss301/user");

    @Test
    void domainHasNoFrameworkOrOuterLayerImports() throws IOException {
        assertImportsAvoid(SOURCE.resolve("domain"),
                "jakarta.", "org.springframework.", "org.hibernate.", "fu.mss301.common.",
                "fu.mss301.user.application.", "fu.mss301.user.infrastructure.");
    }

    @Test
    void applicationDependsOnlyOnDomainAndPorts() throws IOException {
        assertImportsAvoid(SOURCE.resolve("application"),
                "jakarta.", "org.springframework.", "org.hibernate.",
                "fu.mss301.common.", "fu.mss301.user.infrastructure.");
    }

    @Test
    void webDoesNotImportPersistenceOrOtherAdapters() throws IOException {
        assertImportsAvoid(SOURCE.resolve("infrastructure/web"),
                "jakarta.persistence.", "org.springframework.data.jpa.",
                "fu.mss301.user.infrastructure.persistence.",
                "fu.mss301.user.infrastructure.email.",
                "fu.mss301.user.infrastructure.messaging.consumer.");
    }

    @Test
    void persistenceDoesNotImportWeb() throws IOException {
        assertImportsAvoid(SOURCE.resolve("infrastructure/persistence"),
                "fu.mss301.user.infrastructure.web.");
    }

    @Test
    void persistenceEntitiesDoNotImplementDomainContracts() {
        assertFalse(fu.mss301.user.domain.model.User.class.isAssignableFrom(User.class));
        assertFalse(UserRole.class.isAssignableFrom(Role.class));
        assertFalse(Registration.class.isAssignableFrom(PendingRegistration.class));
        assertFalse(PasswordOtp.class.isAssignableFrom(PasswordResetOtp.class));
        assertFalse(RefreshCredential.class.isAssignableFrom(RefreshToken.class));
    }

    @Test
    void domainModelsAreConcreteTypesWithoutSnapshotVariants() throws Exception {
        try (Stream<Path> files = Files.list(SOURCE.resolve("domain/model"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                assertFalse(name.contains("Snapshot"), name);
                String className = name.substring(0, name.length() - ".java".length());
                assertFalse(Class.forName("fu.mss301.user.domain.model." + className).isInterface(), name);
            }
        }
    }

    private void assertImportsAvoid(Path directory, String... forbidden) throws IOException {
        assertTrue(Files.isDirectory(directory), directory.toString());
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import ")) continue;
                    for (String prefix : forbidden) {
                        assertFalse(line.startsWith("import " + prefix), file + ": " + line);
                    }
                }
            }
        }
    }
}
