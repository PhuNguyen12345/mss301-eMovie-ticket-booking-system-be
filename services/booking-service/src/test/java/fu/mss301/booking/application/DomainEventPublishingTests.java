package fu.mss301.booking.application;

import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.payment.PaymentSettlementService;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.Booking;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.SeatSelection;
import fu.mss301.booking.domain.booking.TicketDetails;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.event.PaymentSucceeded;
import fu.mss301.booking.domain.shared.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Each of the four places that raise an event must publish it exactly once, inside the transaction, and
 * from the aggregate passed to save. The repository mocks return a different object, as the real adapter does.
 */
class DomainEventPublishingTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-20T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final TicketDetails TICKET = new TicketDetails("Dune", null, "Rạp 1", "Phòng 1", NOW, NOW.plusHours(2));

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEvents events = batch -> {
        assertEquals(true, transactions.active, "events must be published inside the transaction");
        published.addAll(batch);
    };

    @BeforeEach
    void savedCopiesCarryNoEvents() {
        when(bookings.save(any())).thenAnswer(call -> pending(LocalDateTime.MIN));
        when(payments.save(any())).thenAnswer(call -> pendingPayment());
    }

    @Test
    void cancelPublishesBookingCancelled() {
        when(bookings.findByIdForUpdate(42)).thenReturn(Optional.of(pending(NOW.plusMinutes(5))));
        new CancelBookingUseCase(bookings, transactions, events, CLOCK).cancel(7, 42);
        assertEquals(List.of(new BookingCancelled(42)), published);
    }

    @Test
    void expiryPublishesBookingExpired() {
        when(bookings.findOverdueIds(any(), any(int.class))).thenReturn(List.of(42));
        when(bookings.findByIdForUpdate(42)).thenReturn(Optional.of(pending(NOW.minusMinutes(1))));
        var reconcile = mock(ReconcilePaymentUseCase.class);
        new ExpireOverdueBookingsUseCase(bookings, reconcile, transactions, events, CLOCK).expireOverdue(10);
        assertEquals(List.of(new BookingExpired(42)), published);
    }

    @Test
    void settlementPublishesBookingConfirmed() {
        when(bookings.findByIdForUpdate(42)).thenReturn(Optional.of(pending(NOW.plusMinutes(5))));
        new PaymentSettlementService(payments, bookings, transactions, events, null, CLOCK).settle(5, 42);
        assertEquals(List.of(new BookingConfirmed(42)), published);
    }

    @Test
    void recordingSuccessPublishesPaymentSucceededOnlyTheFirstTime() {
        var payment = pendingPayment();
        when(payments.findByOrderCodeForUpdate(4201L)).thenReturn(Optional.of(payment));
        var settlement = new PaymentSettlementService(payments, bookings, transactions, events, null, CLOCK);
        settlement.recordSuccess(4201L, 90000L, "FT1", NOW);
        settlement.recordSuccess(4201L, 90000L, "FT1", NOW);
        assertEquals(List.of(new PaymentSucceeded(5, 42)), published);
        assertFalse(transactions.active);
    }

    private static Booking pending(LocalDateTime expiredAt) {
        var held = Booking.hold(7, 3L, TICKET, List.of(new SeatSelection(1, null, new BigDecimal("90000"), "A", 1, null, null)), 8,
            NOW.minusMinutes(10), Duration.ofMinutes(5));
        return Booking.restore(42, held.userId(), held.showtimeId(), held.bookingCode(), held.ticket(), held.bookingTime(),
            held.totalAmount(), held.discountAmount(), held.status(), expiredAt, 0, held.createdAt(), held.updatedAt(), held.seats(),
            held.combos(), held.vouchers());
    }

    private static PaymentTransaction pendingPayment() {
        var payment = PaymentTransaction.initiate(42, new BigDecimal("90000.00"), 4201L, NOW);
        return PaymentTransaction.restore(5, payment.bookingId(), payment.paymentMethod(), payment.provider(),
            payment.orderCode(), null, null, null, payment.amount(), payment.status(), null, 0, NOW, NOW, List.of());
    }

    private static final class RecordingTransactions implements Transactions {
        private boolean active;

        @Override
        public <T> T read(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T write(Supplier<T> work) {
            active = true;
            try {
                return work.get();
            } finally {
                active = false;
            }
        }
    }
}
