package fu.mss301.booking.domain.combo;

import fu.mss301.booking.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ComboTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 20, 18, 0);

    @Test
    void newComboIsOnSale() {
        Combo combo = Combo.create(" Bắp nước ", "1 bắp, 1 nước", new BigDecimal("85000"), NOW);
        assertEquals("Bắp nước", combo.name());
        assertEquals(new BigDecimal("85000.00"), combo.price());
        assertTrue(combo.isOnSale());
    }

    @Test
    void deactivateAndActivateChangeWhatIsOnSale() {
        Combo combo = Combo.create("Bắp nước", null, new BigDecimal("85000"), NOW);
        combo.deactivate(NOW.plusDays(1));
        assertFalse(combo.isOnSale());
        assertEquals(ComboStatus.INACTIVE, combo.status());
        assertEquals(NOW.plusDays(1), combo.updatedAt());
        combo.activate(NOW.plusDays(2));
        assertTrue(combo.isOnSale());
    }

    @Test
    void updateReplacesDetails() {
        Combo combo = Combo.create("Bắp nước", null, new BigDecimal("85000"), NOW);
        combo.update("Bắp lớn", "Size L", new BigDecimal("95000.00"), NOW.plusDays(1));
        assertEquals("Bắp lớn", combo.name());
        assertEquals("Size L", combo.description());
        assertEquals(new BigDecimal("95000.00"), combo.price());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "85000.50", "100000000"})
    void invalidPricesAreRejected(String price) {
        assertThrows(DomainException.class, () -> Combo.create("Bắp nước", null, new BigDecimal(price), NOW));
    }

    @Test
    void missingPriceAndInvalidTextAreRejected() {
        assertThrows(DomainException.class, () -> Combo.create("Bắp nước", null, null, NOW));
        assertThrows(DomainException.class, () -> Combo.create("  ", null, BigDecimal.TEN, NOW));
        assertThrows(DomainException.class, () -> Combo.create("x".repeat(101), null, BigDecimal.TEN, NOW));
        assertThrows(DomainException.class, () -> Combo.create("Bắp", "x".repeat(256), BigDecimal.TEN, NOW));
    }

    @Test
    void aFailedUpdateLeavesTheComboUnchanged() {
        Combo combo = Combo.create("Bắp nước", null, new BigDecimal("85000"), NOW);
        assertThrows(DomainException.class, () -> combo.update("Khác", null, new BigDecimal("-1"), NOW));
        assertEquals("Bắp nước", combo.name());
    }
}
