package fu.mss301.booking.application.payment;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.port.PaymentQueries;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.payment.PaymentStatus;

import java.time.LocalDate;

/** Every payment, refunded ones included, for admins to reconcile with PayOS. Nothing here changes a payment. */
public class PaymentHistoryQuery {
    public static final int MAX_PAGE_SIZE = 100;
    private final PaymentQueries queries;
    private final Transactions transactions;

    public PaymentHistoryQuery(PaymentQueries queries, Transactions transactions) {
        this.queries = queries;
        this.transactions = transactions;
    }

    /** @param from first day a payment was created, included; @param to last day, included */
    public BookingPage<PaymentHistoryView> search(PaymentStatus status, Integer bookingId, Long orderCode,
                                                  LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        var search = new PaymentSearch(status, bookingId, orderCode, from == null ? null : from.atStartOfDay(),
            to == null ? null : to.plusDays(1).atStartOfDay());
        return transactions.read(() -> queries.search(search, page, size));
    }
}
