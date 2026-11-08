package fu.mss301.booking.application;

import fu.mss301.booking.application.booking.ComboLine;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.application.exception.ComboUnavailableException;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.SeatSelection;
import fu.mss301.booking.domain.booking.TicketDetails;
import fu.mss301.booking.domain.combo.Combo;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.combo.ComboStatus;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplaceBookingCombosUseCaseTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-20T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final TicketDetails TICKET = new TicketDetails("Dune", null, "Rạp 1", "Phòng 1", NOW, NOW.plusHours(2));

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ComboRepository combos = mock(ComboRepository.class);
    private final Transactions transactions = new Transactions() {
        @Override
        public <T> T read(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T write(Supplier<T> work) {
            return work.get();
        }
    };
    private final ReplaceBookingCombosUseCase useCase =
        new ReplaceBookingCombosUseCase(bookings, payments, combos, transactions, events -> { }, CLOCK);
    private final List<ComboLine> oneCombo = List.of(new ComboLine(5, 2));
    private Booking booking;

    @BeforeEach
    void pendingBooking() {
        var held = Booking.hold(7, 3L, TICKET, List.of(new SeatSelection(1, null, new BigDecimal("90000"), "A", 1, null, null)), 8, NOW,
            Duration.ofMinutes(5));
        booking = Booking.restore(42, held.userId(), held.showtimeId(), held.bookingCode(), held.ticket(), held.bookingTime(),
            held.totalAmount(), held.discountAmount(), held.status(), held.expiredAt(), 0, held.createdAt(), held.updatedAt(),
            held.seats(), held.combos(), held.vouchers(), held.checkIns());
        when(bookings.findByIdForUpdate(42)).thenReturn(Optional.of(booking));
        when(bookings.save(any())).thenAnswer(call -> call.getArgument(0));
        when(payments.findPendingByBookingIdForUpdate(42)).thenReturn(List.of());
    }

    @Test
    void addsTheComboAtTheCurrentMenuPrice() {
        when(combos.findAllByIds(any())).thenReturn(List.of(combo(ComboStatus.ACTIVE)));
        var view = useCase.replace(7, 42, oneCombo);
        assertEquals(new BigDecimal("260000.00"), view.totalAmount());
        assertEquals("Bắp nước", view.combos().getFirst().name());
        assertEquals(new BigDecimal("85000.00"), view.combos().getFirst().price());
    }

    @Test
    void anOpenPaymentBlocksTheChangeEvenBeforeItsLinkExists() {
        var linkBeingCreated = PaymentTransaction.initiate(42, new BigDecimal("90000.00"), 4201L, NOW);
        when(payments.findPendingByBookingIdForUpdate(42)).thenReturn(List.of(linkBeingCreated));
        assertThrows(InvalidStateTransitionException.class, () -> useCase.replace(7, 42, oneCombo));
        verify(bookings, never()).save(any());
        assertEquals(new BigDecimal("90000.00"), booking.totalAmount());
    }

    @Test
    void comboNotOnSaleIsRejected() {
        when(combos.findAllByIds(any())).thenReturn(List.of(combo(ComboStatus.INACTIVE)));
        assertThrows(ComboUnavailableException.class, () -> useCase.replace(7, 42, oneCombo));
        verify(bookings, never()).save(any());
    }

    @Test
    void unknownComboIsRejected() {
        when(combos.findAllByIds(any())).thenReturn(List.of());
        assertThrows(ComboNotFoundException.class, () -> useCase.replace(7, 42, oneCombo));
    }

    @Test
    void someoneElsesBookingLooksMissing() {
        assertThrows(BookingNotFoundException.class, () -> useCase.replace(8, 42, oneCombo));
        verify(bookings, never()).save(any());
    }

    private static Combo combo(ComboStatus status) {
        return Combo.restore(5, "Bắp nước", null, new BigDecimal("85000.00"), status, NOW, NOW);
    }
}
