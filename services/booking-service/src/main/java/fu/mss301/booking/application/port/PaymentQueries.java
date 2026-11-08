package fu.mss301.booking.application.port;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.payment.PaymentHistoryView;
import fu.mss301.booking.application.payment.PaymentSearch;

/** Read-only payment queries that are not aggregate persistence. */
public interface PaymentQueries {
    /** Payments matching every given filter, newest first. */
    BookingPage<PaymentHistoryView> search(PaymentSearch search, int page, int size);
}
