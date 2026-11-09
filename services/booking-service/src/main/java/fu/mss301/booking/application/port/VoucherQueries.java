package fu.mss301.booking.application.port;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.domain.voucher.Voucher;

import java.util.List;

/** Read-only voucher lists that are not aggregate persistence. */
public interface VoucherQueries {
    /** Every voucher, newest first. */
    BookingPage<Voucher> findAll(int page, int size);

    /** The vouchers a user owns, newest first. */
    List<Voucher> findOwnedBy(Integer userId);
}
