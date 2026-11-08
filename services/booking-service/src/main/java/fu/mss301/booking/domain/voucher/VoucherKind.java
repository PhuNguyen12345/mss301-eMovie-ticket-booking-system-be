package fu.mss301.booking.domain.voucher;

/** What a voucher applies to. VALUE vouchers are issued by the system and never created by an admin. */
public enum VoucherKind {
    TICKET_EXCHANGE,
    TICKET_DISCOUNT,
    COMBO_EXCHANGE,
    COMBO_DISCOUNT,
    VALUE;

    public boolean isExchange() {
        return this == TICKET_EXCHANGE || this == COMBO_EXCHANGE;
    }

    public boolean isDiscount() {
        return this == TICKET_DISCOUNT || this == COMBO_DISCOUNT;
    }

    public boolean appliesToTickets() {
        return this == TICKET_EXCHANGE || this == TICKET_DISCOUNT;
    }
}
