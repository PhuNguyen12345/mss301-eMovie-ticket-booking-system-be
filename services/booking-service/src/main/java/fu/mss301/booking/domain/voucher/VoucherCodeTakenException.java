package fu.mss301.booking.domain.voucher;

/** Saving a voucher lost the race for its code: another voucher already uses it. */
public class VoucherCodeTakenException extends RuntimeException {
    public VoucherCodeTakenException() {
        super("This voucher code is already in use");
    }
}
