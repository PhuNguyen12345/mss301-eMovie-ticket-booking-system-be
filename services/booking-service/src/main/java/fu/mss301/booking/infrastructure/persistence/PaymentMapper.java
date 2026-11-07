package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.payment.PaymentTransaction;
import fu.mss301.booking.domain.payment.Refund;

import java.util.function.Function;
import java.util.stream.Collectors;

final class PaymentMapper {
    private PaymentMapper() {}

    static PaymentTransaction toDomain(PaymentEntity entity) {
        return PaymentTransaction.restore(entity.getId(), entity.getBookingId(), entity.getPaymentMethod(),
            entity.getProvider(), entity.getOrderCode(), entity.getPaymentLinkId(), entity.getProviderTransactionId(),
            entity.getCheckoutUrl(), entity.getAmount(), entity.getStatus(), entity.getPaidAt(), entity.getVersion(),
            entity.getCreatedAt(), entity.getUpdatedAt(), entity.getRefunds().stream()
                .map(refund -> Refund.restore(refund.getId(), refund.getAmount(), refund.getReason(),
                    refund.getStatus(), refund.getRefundedAt(), refund.getCreatedAt(), refund.getUpdatedAt()))
                .toList());
    }

    static PaymentEntity newEntity(PaymentTransaction source) {
        var entity = new PaymentEntity();
        entity.setBookingId(source.bookingId());
        entity.setPaymentMethod(source.paymentMethod());
        entity.setProvider(source.provider());
        entity.setOrderCode(source.orderCode());
        entity.setAmount(source.amount());
        entity.setCreatedAt(source.createdAt());
        update(source, entity);
        return entity;
    }

    /** Copies only what can change. Existing refund rows keep their id; rows are never deleted. */
    static void update(PaymentTransaction source, PaymentEntity target) {
        target.setPaymentLinkId(source.paymentLinkId());
        target.setProviderTransactionId(source.providerTransactionId());
        target.setCheckoutUrl(source.checkoutUrl());
        target.setStatus(source.status());
        target.setPaidAt(source.paidAt());
        target.setUpdatedAt(source.updatedAt());
        var rows = target.getRefunds().stream().collect(Collectors.toMap(RefundEntity::getId, Function.identity()));
        for (Refund refund : source.refunds()) {
            if (refund.id() == null) {
                target.getRefunds().add(newRefund(refund, target));
            } else {
                var row = rows.get(refund.id());
                row.setStatus(refund.status());
                row.setRefundedAt(refund.refundedAt());
                row.setUpdatedAt(refund.updatedAt());
            }
        }
    }

    private static RefundEntity newRefund(Refund refund, PaymentEntity payment) {
        var row = new RefundEntity();
        row.setPayment(payment);
        row.setAmount(refund.amount());
        row.setReason(refund.reason());
        row.setStatus(refund.status());
        row.setRefundedAt(refund.refundedAt());
        row.setCreatedAt(refund.createdAt());
        row.setUpdatedAt(refund.updatedAt());
        return row;
    }
}
