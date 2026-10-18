package fu.mss301.user.domain.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.mss301.user.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

class EmailTest {
    @Test
    void normalizesAddressForLookupAndPersistence() {
        assertEquals("person@example.com", Email.of(" Person@Example.com ").value());
        assertEquals(Email.of("person@example.com"), Email.of(" PERSON@EXAMPLE.COM "));
    }

    @Test
    void rejectsMissingAndMalformedAddresses() {
        for (String value : new String[] {"", "person", "@example.com", "person@", "a b@example.com"}) {
            assertThrows(DomainException.class, () -> Email.of(value));
        }
        assertThrows(DomainException.class, () -> Email.of(null));
    }
}
