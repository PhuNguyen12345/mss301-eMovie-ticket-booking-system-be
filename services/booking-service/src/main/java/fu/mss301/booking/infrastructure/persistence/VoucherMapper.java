package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherClaim;
import fu.mss301.booking.domain.voucher.VoucherCode;
import fu.mss301.booking.domain.voucher.VoucherTerms;

final class VoucherMapper {
    private VoucherMapper() {
    }

    static Voucher toDomain(VoucherEntity entity) {
        var terms = new VoucherTerms(entity.getKind(), entity.getDiscountType(), entity.getDiscountValue(),
            entity.getStartDate(), entity.getEndDate(), entity.getMinOrderAmount(), entity.getSeatTypeId(),
            entity.getComboId());
        var claim = entity.getClaim();
        return Voucher.restore(entity.getId(), new VoucherCode(entity.getCode()), terms, entity.getStatus(),
            entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt(), claim == null ? null : toDomain(claim));
    }

    static void update(Voucher voucher, VoucherEntity entity) {
        var terms = voucher.terms();
        entity.setCode(voucher.code().value());
        entity.setKind(terms.kind());
        entity.setDiscountType(terms.discountType());
        entity.setDiscountValue(terms.discountValue());
        entity.setStartDate(terms.startDate());
        entity.setEndDate(terms.endDate());
        entity.setMinOrderAmount(terms.minOrderAmount());
        entity.setSeatTypeId(terms.seatTypeId());
        entity.setComboId(terms.comboId());
        entity.setStatus(voucher.status());
        entity.setCreatedAt(voucher.createdAt());
        entity.setUpdatedAt(voucher.updatedAt());
        if (voucher.claim() != null) update(voucher.claim(), entity);
    }

    private static void update(VoucherClaim claim, VoucherEntity voucher) {
        var entity = voucher.getClaim();
        if (entity == null) {
            entity = new UserVoucherEntity();
            entity.setVoucher(voucher);
            voucher.setClaim(entity);
        }
        entity.setUserId(claim.userId());
        entity.setStatus(claim.status());
        entity.setUsedAt(claim.usedAt());
        entity.setCreatedAt(claim.createdAt());
        entity.setUpdatedAt(claim.updatedAt());
    }

    private static VoucherClaim toDomain(UserVoucherEntity entity) {
        return VoucherClaim.restore(entity.getId(), entity.getUserId(), entity.getStatus(), entity.getUsedAt(),
            entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
