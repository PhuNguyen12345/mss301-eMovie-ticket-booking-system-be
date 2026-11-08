package fu.mss301.booking.domain.voucher;

/** What a voucher means to its owner on a given day. */
public enum VoucherAvailability {
    AVAILABLE,
    RESERVED,
    USED,
    EXPIRED,
    INACTIVE
}
