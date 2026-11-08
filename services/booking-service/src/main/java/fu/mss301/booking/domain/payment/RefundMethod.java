package fu.mss301.booking.domain.payment;

/** How money is given back: by a staff transfer, or as a value voucher the customer can spend. */
public enum RefundMethod {
    MANUAL,
    VOUCHER
}
