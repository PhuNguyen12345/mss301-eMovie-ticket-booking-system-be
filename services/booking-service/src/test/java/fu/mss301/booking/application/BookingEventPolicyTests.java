package fu.mss301.booking.application;

import fu.mss301.booking.application.exception.PaymentGatewayException;
import fu.mss301.booking.application.policy.BookingEventPolicy;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.PaymentGateway.LinkState;
import fu.mss301.booking.application.port.PaymentGateway.LinkStatus;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.payment.PaymentStatus;
import fu.mss301.booking.domain.payment.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingEventPolicyTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-20T18:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
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
    private final BookingEventPolicy policy = new BookingEventPolicy(payments, gateway, transactions, CLOCK);
    private PaymentTransaction payment;

    @BeforeEach
    void pendingPaymentWithALink() {
        var initiated = PaymentTransaction.initiate(42, new BigDecimal("90000.00"), 4201L, NOW);
        payment = PaymentTransaction.restore(5, initiated.bookingId(), initiated.paymentMethod(), initiated.provider(),
            initiated.orderCode(), "link-1", null, "https://pay.payos.vn/web/link-1", initiated.amount(),
            initiated.status(), null, 0, NOW, NOW, List.of());
        when(payments.findPendingByBookingIdForUpdate(42)).thenReturn(List.of(payment));
    }

    @Test
    void anOpenLinkIsCancelled() {
        linkIs(LinkStatus.PENDING);
        policy.on(new BookingCancelled(42));
        verify(gateway).cancelLink(eq(4201L), anyString());
        assertEquals(PaymentStatus.FAILED, payment.status());
    }

    @ParameterizedTest
    @EnumSource(value = LinkStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void aClosedLinkIsNotCancelled(LinkStatus status) {
        linkIs(status);
        policy.on(new BookingExpired(42));
        verify(gateway, never()).cancelLink(anyLong(), anyString());
        assertEquals(PaymentStatus.FAILED, payment.status());
    }

    @Test
    void aFailedCancelNeverBreaksTheEventHandler() {
        linkIs(LinkStatus.PENDING);
        doThrow(new PaymentGatewayException("down", null)).when(gateway).cancelLink(anyLong(), any());
        assertDoesNotThrow(() -> policy.on(new BookingCancelled(42)));
    }

    @Test
    void anUnreadableLinkNeverBreaksTheEventHandler() {
        when(gateway.getLinkState(4201L)).thenThrow(new PaymentGatewayException("down", null));
        assertDoesNotThrow(() -> policy.on(new BookingExpired(42)));
        verify(gateway, never()).cancelLink(anyLong(), anyString());
        assertEquals(PaymentStatus.FAILED, payment.status());
    }

    private void linkIs(LinkStatus status) {
        when(gateway.getLinkState(4201L)).thenReturn(new LinkState(status, 0, null, null));
    }
}
