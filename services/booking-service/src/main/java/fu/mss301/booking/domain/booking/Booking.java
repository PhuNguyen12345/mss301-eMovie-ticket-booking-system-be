package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.booking.event.BookingCancelled;
import fu.mss301.booking.domain.booking.event.BookingConfirmed;
import fu.mss301.booking.domain.booking.event.BookingExpired;
import fu.mss301.booking.domain.booking.event.BookingVoucherRemoved;
import fu.mss301.booking.domain.shared.DomainEvent;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.shared.InvalidStateTransitionException;
import fu.mss301.booking.domain.voucher.VoucherKind;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregate root for one customer's seat reservation on one showtime.
 * <p>
 * State machine: {@code PENDING_PAYMENT -> CONFIRMED | CANCELLED | EXPIRED}. Seats are HELD while the
 * booking waits for payment, BOOKED once confirmed and RELEASED when it is cancelled or expires.
 */
public final class Booking {
    public static final int MAX_COMBO_QUANTITY = 20;
    private static final BigDecimal MAX_TOTAL = new BigDecimal("99999999.99");

    private final Integer id;
    private final Integer userId;
    private final Long showtimeId;
    private final BookingCode bookingCode;
    private final TicketDetails ticket;
    private final LocalDateTime bookingTime;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BookingStatus status;
    private final LocalDateTime expiredAt;
    private final Integer version;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<BookingSeat> seats;
    private final List<BookingCombo> combos;
    private final List<BookingVoucher> vouchers;
    private final List<DomainEvent> events = new ArrayList<>();

    private Booking(Integer id, Integer userId, Long showtimeId, BookingCode bookingCode, TicketDetails ticket,
                    LocalDateTime bookingTime,
                    BigDecimal totalAmount, BigDecimal discountAmount, BookingStatus status, LocalDateTime expiredAt,
                    Integer version, LocalDateTime createdAt, LocalDateTime updatedAt, List<BookingSeat> seats,
                    List<BookingCombo> combos, List<BookingVoucher> vouchers) {
        this.id = id;
        this.userId = userId;
        this.showtimeId = showtimeId;
        this.bookingCode = bookingCode;
        this.ticket = ticket;
        this.bookingTime = bookingTime;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.status = status;
        this.expiredAt = expiredAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.seats = new ArrayList<>(seats);
        this.combos = new ArrayList<>(combos);
        this.vouchers = new ArrayList<>(vouchers);
    }

    public static Booking hold(Integer userId, Long showtimeId, TicketDetails ticket, List<SeatSelection> selections,
                               int maxSeats,
                               LocalDateTime now, Duration holdTtl) {
        if (userId == null || userId <= 0) throw new DomainException("User id must be positive");
        if (showtimeId == null || showtimeId <= 0) throw new DomainException("Showtime id must be positive");
        if (ticket == null) throw new DomainException("Ticket details are required");
        if (selections == null || selections.isEmpty()) throw new DomainException("At least one seat is required");
        if (selections.size() > maxSeats) throw new DomainException("A booking can hold at most " + maxSeats + " seats");
        if (holdTtl == null || holdTtl.isNegative() || holdTtl.isZero()) throw new DomainException("Hold TTL must be positive");
        var seen = new HashSet<Integer>();
        for (SeatSelection selection : selections) {
            if (!seen.add(selection.seatId())) throw new DomainException("Seat " + selection.seatId() + " is selected twice");
        }

        var seats = selections.stream().map(selection -> BookingSeat.hold(showtimeId, selection, now)).toList();
        BigDecimal total = selections.stream().map(SeatSelection::price)
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new Booking(null, userId, showtimeId, BookingCode.generate(now), ticket, now, total,
            BigDecimal.ZERO.setScale(2), BookingStatus.PENDING_PAYMENT, now.plus(holdTtl), null, now, now, seats,
            List.of(), List.of());
    }

    /**
     * Rebuilds a stored booking; performs no business validation because old bookings must always load.
     *
     * @param ticket null for bookings made before ticket details were stored
     */
    public static Booking restore(Integer id, Integer userId, Long showtimeId, BookingCode bookingCode,
                                  TicketDetails ticket, LocalDateTime bookingTime, BigDecimal totalAmount,
                                  BigDecimal discountAmount, BookingStatus status, LocalDateTime expiredAt,
                                  Integer version, LocalDateTime createdAt, LocalDateTime updatedAt,
                                  List<BookingSeat> seats, List<BookingCombo> combos, List<BookingVoucher> vouchers) {
        return new Booking(id, userId, showtimeId, bookingCode, ticket, bookingTime, totalAmount, discountAmount, status,
            expiredAt, version, createdAt, updatedAt, seats, combos, vouchers);
    }

    /** Payment succeeded: the held seats become booked. Allowed until the hold is actually released. */
    public void confirm(LocalDateTime now) {
        requirePending("confirm");
        status = BookingStatus.CONFIRMED;
        seats.forEach(seat -> seat.book(now));
        updatedAt = now;
        events.add(new BookingConfirmed(id));
    }

    public void cancelBy(Integer requesterId, LocalDateTime now) {
        if (!isOwnedBy(requesterId)) throw new InvalidStateTransitionException("Only the booking owner can cancel it");
        requirePending("cancel");
        status = BookingStatus.CANCELLED;
        releaseSeats(now);
        events.add(new BookingCancelled(id));
    }

    public void expire(LocalDateTime now) {
        requirePending("expire");
        if (now.isBefore(expiredAt)) throw new InvalidStateTransitionException("Booking hold has not expired yet");
        status = BookingStatus.EXPIRED;
        releaseSeats(now);
        events.add(new BookingExpired(id));
    }

    /**
     * Replaces all combos and recalculates the total. Combos kept from the previous list keep their line and
     * take the new quantity and price. Combo vouchers that no longer apply are removed. A rejected change leaves
     * the booking untouched.
     */
    public void replaceCombos(List<ComboSelection> selections, LocalDateTime now) {
        requireEditable("change combos of", now);
        var existing = combos.stream().collect(Collectors.toMap(BookingCombo::comboId, Function.identity()));
        var seen = new HashSet<Integer>();
        var replaced = new ArrayList<BookingCombo>();
        for (ComboSelection selection : selections) {
            if (!seen.add(selection.comboId())) throw new DomainException("Combo " + selection.comboId() + " is selected twice");
            if (selection.quantity() == null || selection.quantity() < 1 || selection.quantity() > MAX_COMBO_QUANTITY) {
                throw new DomainException("Combo quantity must be between 1 and " + MAX_COMBO_QUANTITY);
            }
            var current = existing.get(selection.comboId());
            replaced.add(current == null ? BookingCombo.add(selection) : current.replacedBy(selection));
        }
        replaced.sort(Comparator.comparing(BookingCombo::comboId));

        if (subtotalWith(replaced).compareTo(MAX_TOTAL) > 0) throw new DomainException("Booking total is too large");
        var pricing = VoucherPricing.price(seats, replaced, vouchers, false);

        combos.clear();
        combos.addAll(replaced);
        applyPricing(pricing, now);
    }

    /**
     * Applies a voucher that was reserved for this booking. The voucher and every voucher already on the booking
     * must still apply afterwards, otherwise nothing changes.
     *
     * @param seatId the seat a ticket exchange voucher pays for; null for every other kind
     */
    public void applyVoucher(VoucherOffer offer, Integer seatId, LocalDateTime now) {
        requireEditable("apply a voucher to", now);
        if (offer == null) throw new DomainException("Voucher is required");
        if (hasVoucher(offer.voucherId())) throw new InvalidStateTransitionException("This voucher is already applied");
        if (now.toLocalDate().isAfter(offer.expiresOn())) throw new DomainException("This voucher has expired");
        boolean exchangesSeat = offer.kind() == VoucherKind.TICKET_EXCHANGE;
        if (exchangesSeat && seatId == null) throw new DomainException("Choose the seat this voucher pays for");
        if (!exchangesSeat && seatId != null) throw new DomainException("Only a ticket exchange voucher takes a seat");

        var candidates = new ArrayList<>(vouchers);
        candidates.add(BookingVoucher.apply(offer, seatId, now));
        var pricing = VoucherPricing.price(seats, combos, candidates, true);
        vouchers.add(candidates.getLast());
        applyPricing(pricing, now);
    }

    /** The customer takes a voucher off; it becomes usable again once this change is saved. */
    public void removeVoucher(Integer voucherId, LocalDateTime now) {
        requireEditable("remove a voucher from", now);
        if (!hasVoucher(voucherId)) throw new DomainException("Voucher " + voucherId + " is not applied to this booking");
        var left = vouchers.stream().filter(voucher -> !voucher.voucherId().equals(voucherId)).toList();
        var pricing = VoucherPricing.price(seats, combos, left, false);
        vouchers.removeIf(voucher -> voucher.voucherId().equals(voucherId));
        events.add(new BookingVoucherRemoved(id, voucherId));
        applyPricing(pricing, now);
    }

    /**
     * Takes off vouchers whose last day has passed, so the price goes back up. Checked before a payment link is
     * created; a link that is already open keeps its price.
     *
     * @return true when a voucher was removed
     */
    public boolean removeExpiredVouchers(LocalDateTime now) {
        requirePending("remove vouchers from");
        var left = vouchers.stream().filter(voucher -> !voucher.isExpiredOn(now.toLocalDate())).toList();
        if (left.size() == vouchers.size()) return false;
        var pricing = VoucherPricing.price(seats, combos, left, false);
        vouchers.stream().filter(voucher -> !left.contains(voucher))
            .forEach(voucher -> events.add(new BookingVoucherRemoved(id, voucher.voucherId())));
        vouchers.retainAll(left);
        applyPricing(pricing, now);
        return true;
    }

    public boolean hasVoucher(Integer voucherId) {
        return vouchers.stream().anyMatch(voucher -> voucher.voucherId().equals(voucherId));
    }

    public boolean hasExpiredVoucherOn(LocalDate today) {
        return vouchers.stream().anyMatch(voucher -> voucher.isExpiredOn(today));
    }

    /** Seats plus combos, before vouchers. */
    public BigDecimal subtotal() {
        return subtotalWith(combos).setScale(2);
    }

    /** A new payment may only be started while the seat hold is still valid. */
    public boolean isPayableAt(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT && now.isBefore(expiredAt);
    }

    public boolean isOwnedBy(Integer requesterId) {
        return userId.equals(requesterId);
    }

    /** Returns the events raised since the last call and forgets them, so each is published once. */
    public List<DomainEvent> pullEvents() {
        var pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public Integer id() { return id; }
    public Integer userId() { return userId; }
    public Long showtimeId() { return showtimeId; }
    public BookingCode bookingCode() { return bookingCode; }
    public TicketDetails ticket() { return ticket; }
    public LocalDateTime bookingTime() { return bookingTime; }
    public BigDecimal totalAmount() { return totalAmount; }
    public BigDecimal discountAmount() { return discountAmount; }
    public BookingStatus status() { return status; }
    public LocalDateTime expiredAt() { return expiredAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public List<BookingSeat> seats() { return Collections.unmodifiableList(seats); }
    public List<BookingCombo> combos() { return Collections.unmodifiableList(combos); }
    public List<BookingVoucher> vouchers() { return Collections.unmodifiableList(vouchers); }

    /** Optimistic-lock token owned by persistence; the domain never interprets it. */
    public Integer version() { return version; }

    private void releaseSeats(LocalDateTime now) {
        seats.forEach(seat -> seat.release(now));
        updatedAt = now;
    }

    /** Drops the vouchers pricing left out, then sets every amount from the same result. */
    private void applyPricing(VoucherPricing.Result pricing, LocalDateTime now) {
        pricing.dropped().forEach(voucher -> events.add(new BookingVoucherRemoved(id, voucher.voucherId())));
        vouchers.retainAll(pricing.kept());
        vouchers.forEach(voucher -> voucher.priceAt(pricing.amounts().get(voucher)));
        discountAmount = pricing.discount().setScale(2);
        totalAmount = subtotal().subtract(discountAmount).setScale(2);
        updatedAt = now;
    }

    private BigDecimal subtotalWith(List<BookingCombo> lines) {
        BigDecimal total = seats.stream().map(BookingSeat::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        return lines.stream().map(BookingCombo::lineTotal).reduce(total, BigDecimal::add);
    }

    private void requireEditable(String action, LocalDateTime now) {
        requirePending(action);
        if (!now.isBefore(expiredAt)) throw new InvalidStateTransitionException("Booking seat hold has expired");
    }

    private void requirePending(String action) {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Cannot " + action + " a booking in status " + status);
        }
    }
}
