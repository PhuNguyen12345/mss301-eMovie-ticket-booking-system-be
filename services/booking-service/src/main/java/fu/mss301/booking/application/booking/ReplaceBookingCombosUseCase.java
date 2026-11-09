package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.application.exception.ComboUnavailableException;
import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.ComboSelection;
import fu.mss301.booking.domain.combo.Combo;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Replaces the combos of a pending booking, which changes the amount it has to pay. */
public class ReplaceBookingCombosUseCase {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final ComboRepository combos;
    private final Transactions transactions;
    private final DomainEvents events;
    private final Clock clock;

    public ReplaceBookingCombosUseCase(BookingRepository bookings, PaymentRepository payments, ComboRepository combos,
                                       Transactions transactions, DomainEvents events, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.combos = combos;
        this.transactions = transactions;
        this.events = events;
        this.clock = clock;
    }

    public BookingView replace(Integer userId, Integer bookingId, List<ComboLine> lines) {
        if (lines == null) throw new DomainException("Combo list is required");
        return transactions.write(() -> {
            // The booking row lock serializes this with InitiatePaymentUseCase, which opens payments.
            var booking = bookings.findByIdForUpdate(bookingId).filter(b -> b.isOwnedBy(userId))
                .orElseThrow(BookingNotFoundException::new);
            // An open PayOS link carries the old amount, and a different paid amount would be rejected.
            if (!payments.findPendingByBookingIdForUpdate(bookingId).isEmpty()) {
                throw new InvalidStateTransitionException("Combos cannot be changed after a payment link was created");
            }
            booking.replaceCombos(select(lines), LocalDateTime.now(clock));
            var saved = bookings.save(booking);
            // Combo vouchers that no longer apply were taken off and are given back after commit.
            events.publish(booking.pullEvents());
            return BookingView.of(saved, null);
        });
    }

    private List<ComboSelection> select(List<ComboLine> lines) {
        Map<Integer, Combo> menu = combos.findAllByIds(lines.stream().map(ComboLine::comboId).distinct().toList())
            .stream().collect(Collectors.toMap(Combo::id, Function.identity()));
        return lines.stream().map(line -> {
            Combo combo = menu.get(line.comboId());
            if (combo == null) throw new ComboNotFoundException();
            if (!combo.isOnSale()) throw new ComboUnavailableException(combo.id());
            return new ComboSelection(combo.id(), combo.name(), line.quantity(), combo.price());
        }).toList();
    }
}
