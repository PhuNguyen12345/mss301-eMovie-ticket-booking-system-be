package fu.mss301.booking.domain.voucher;

/** Why the system issued a voucher for a booking. A booking gets at most one voucher for each reason. */
public enum VoucherSourceReason {
    /** What a value voucher on the booking did not use. */
    REMAINDER,
    /** Money paid for a booking whose showtime was cancelled, apart from its combos. */
    SHOWTIME_REFUND,
    /** Money paid for the combos of a booking whose showtime was cancelled. */
    SHOWTIME_COMBO_REFUND,
    /** A free ticket for a showtime cancelled at short notice. */
    APOLOGY_TICKET,
    /** A combo for a showtime cancelled at short notice. */
    APOLOGY_COMBO
}
