package fu.mss301.booking.application.booking;

import fu.mss301.booking.application.exception.BookingNotFoundException;
import fu.mss301.booking.application.port.QrCodeRenderer;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.booking.BookingStatus;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

/** The QR code a customer shows at the door. It holds the booking code, which staff scan to check in. */
public class TicketQrUseCase {
    static final int SIZE = 300;
    private final BookingRepository bookings;
    private final QrCodeRenderer renderer;
    private final Transactions transactions;

    public TicketQrUseCase(BookingRepository bookings, QrCodeRenderer renderer, Transactions transactions) {
        this.bookings = bookings;
        this.renderer = renderer;
        this.transactions = transactions;
    }

    /** Owners see their own ticket, admins every ticket; anyone else gets 404 so ids are not probed. */
    public byte[] png(Integer requesterId, boolean admin, Integer bookingId) {
        String code = transactions.read(() -> {
            var booking = bookings.findById(bookingId).filter(b -> admin || b.isOwnedBy(requesterId))
                .orElseThrow(BookingNotFoundException::new);
            if (booking.status() != BookingStatus.CONFIRMED) {
                throw new InvalidStateTransitionException("Only a paid booking has a ticket QR code");
            }
            return booking.bookingCode().value();
        });
        return renderer.png(code, SIZE);
    }
}
