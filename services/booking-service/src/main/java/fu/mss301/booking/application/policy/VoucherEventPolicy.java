package fu.mss301.booking.application.policy;

import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.voucher.VoucherReservations;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingVoucher;
import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.booking.event.BookingVoucherRemoved;

import java.lang.System.Logger.Level;
import java.util.List;

/** Booking changed -> use up or give back its vouchers, each in its own transaction. */
public class VoucherEventPolicy {
    private static final System.Logger log = System.getLogger(VoucherEventPolicy.class.getName());
    private final BookingRepository bookings;
    private final VoucherReservations reservations;
    private final Transactions transactions;

    public VoucherEventPolicy(BookingRepository bookings, VoucherReservations reservations, Transactions transactions) {
        this.bookings = bookings;
        this.reservations = reservations;
        this.transactions = transactions;
    }

    public void on(BookingConfirmed event) {
        settleAll(event.bookingId());
    }

    public void on(BookingExpired event) {
        settleAll(event.bookingId());
    }

    public void on(BookingCancelled event) {
        settleAll(event.bookingId());
    }

    public void on(BookingVoucherRemoved event) {
        settle(event.voucherId(), event.bookingId());
    }

    private void settleAll(Integer bookingId) {
        List<Integer> voucherIds = transactions.read(() -> bookings.findById(bookingId)
            .map(booking -> booking.vouchers().stream().map(BookingVoucher::voucherId).toList())
            .orElse(List.of()));
        voucherIds.forEach(voucherId -> settle(voucherId, bookingId));
    }

    /** A failure is left to the maintenance job, which checks held vouchers again. */
    private void settle(Integer voucherId, Integer bookingId) {
        try {
            reservations.settle(voucherId, bookingId);
        } catch (RuntimeException exception) {
            log.log(Level.ERROR, () -> "Settling voucher " + voucherId + " of booking " + bookingId + " failed", exception);
        }
    }
}
