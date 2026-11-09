package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.payment.PaymentHistoryView;
import fu.mss301.booking.application.payment.PaymentSearch;
import fu.mss301.booking.application.port.PaymentQueries;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaPaymentQueries implements PaymentQueries {
    private final PaymentJpaRepository payments;
    private final BookingJpaRepository bookings;

    JpaPaymentQueries(PaymentJpaRepository payments, BookingJpaRepository bookings) {
        this.payments = payments;
        this.bookings = bookings;
    }

    @Override
    public BookingPage<PaymentHistoryView> search(PaymentSearch search, int page, int size) {
        var result = payments.findAll(matching(search),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        // One query for the booking codes of the whole page.
        var bookingIds = result.getContent().stream().map(PaymentEntity::getBookingId).collect(Collectors.toSet());
        Map<Integer, BookingReference> references = bookingIds.isEmpty() ? Map.of()
            : bookings.findReferences(bookingIds).stream()
                .collect(Collectors.toMap(BookingReference::id, Function.identity()));
        var views = result.getContent().stream().map(entity -> {
            var reference = references.get(entity.getBookingId());
            return PaymentHistoryView.of(PaymentMapper.toDomain(entity),
                reference == null ? null : reference.bookingCode(), reference == null ? null : reference.userId());
        }).toList();
        return new BookingPage<>(views, result.getNumber(), result.getSize(), result.getTotalElements(),
            result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private static Specification<PaymentEntity> matching(PaymentSearch search) {
        return (root, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            if (search.status() != null) conditions.add(builder.equal(root.get("status"), search.status()));
            if (search.bookingId() != null) conditions.add(builder.equal(root.get("bookingId"), search.bookingId()));
            if (search.orderCode() != null) conditions.add(builder.equal(root.get("orderCode"), search.orderCode()));
            if (search.from() != null) {
                conditions.add(builder.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), search.from()));
            }
            if (search.until() != null) {
                conditions.add(builder.lessThan(root.<LocalDateTime>get("createdAt"), search.until()));
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }
}
