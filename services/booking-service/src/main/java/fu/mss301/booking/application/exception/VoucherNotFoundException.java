package fu.mss301.booking.application.exception;

public class VoucherNotFoundException extends RuntimeException {
    public VoucherNotFoundException() {
        super("Voucher not found");
    }
}
