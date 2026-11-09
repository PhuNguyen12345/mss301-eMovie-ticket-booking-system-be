package fu.mss301.booking.application.voucher;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.exception.ComboNotFoundException;
import fu.mss301.booking.application.exception.VoucherNotFoundException;
import fu.mss301.booking.application.port.CinemaCatalog;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.application.port.VoucherQueries;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.shared.DomainException;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherCode;
import fu.mss301.booking.domain.voucher.VoucherRepository;
import fu.mss301.booking.domain.voucher.VoucherTerms;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** Admins manage vouchers and give them to customers; customers see theirs and claim codes. */
public class VoucherService {
    private static final int MAX_PAGE_SIZE = 100;

    private final VoucherRepository vouchers;
    private final VoucherQueries queries;
    private final ComboRepository combos;
    private final CinemaCatalog cinemas;
    private final Transactions transactions;
    private final Clock clock;

    public VoucherService(VoucherRepository vouchers, VoucherQueries queries, ComboRepository combos,
                          CinemaCatalog cinemas, Transactions transactions, Clock clock) {
        this.vouchers = vouchers;
        this.queries = queries;
        this.combos = combos;
        this.cinemas = cinemas;
        this.transactions = transactions;
        this.clock = clock;
    }

    public BookingPage<VoucherView> list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException("Page must not be negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return transactions.read(() -> queries.findAll(page, size).map(VoucherView::of));
    }

    public VoucherView get(Integer id) {
        return transactions.read(() -> vouchers.findById(id).map(VoucherView::of)
            .orElseThrow(VoucherNotFoundException::new));
    }

    public List<MyVoucherView> mine(Integer userId) {
        LocalDate today = LocalDate.now(clock);
        return transactions.read(() -> queries.findOwnedBy(userId).stream()
            .map(voucher -> MyVoucherView.of(voucher, today)).toList());
    }

    public VoucherView create(VoucherCommand command) {
        VoucherCode code = command.code() == null || command.code().isBlank()
            ? VoucherCode.generate() : VoucherCode.of(command.code());
        VoucherTerms terms = validated(command);
        return transactions.write(() -> {
            requireCombo(terms);
            return VoucherView.of(vouchers.save(Voucher.create(code, terms, LocalDateTime.now(clock))));
        });
    }

    public VoucherView update(Integer id, VoucherCommand command) {
        VoucherTerms terms = validated(command);
        return transactions.write(() -> {
            requireCombo(terms);
            return change(id, voucher -> voucher.update(terms, LocalDateTime.now(clock)));
        });
    }

    public VoucherView activate(Integer id) {
        return transactions.write(() -> change(id, voucher -> voucher.activate(LocalDateTime.now(clock))));
    }

    public VoucherView deactivate(Integer id) {
        return transactions.write(() -> change(id, voucher -> voucher.deactivate(LocalDateTime.now(clock))));
    }

    public VoucherView assign(Integer id, Integer userId) {
        return transactions.write(() -> change(id, voucher -> voucher.assignTo(userId, LocalDateTime.now(clock))));
    }

    /** A used, inactive or unknown code all answer the same, so guessing reveals nothing about real codes. */
    public MyVoucherView claim(Integer userId, String code) {
        return transactions.write(() -> {
            LocalDateTime now = LocalDateTime.now(clock);
            var voucher = vouchers.findByCodeForUpdate(VoucherCode.normalize(code))
                .filter(found -> found.isClaimableOn(now.toLocalDate()))
                .orElseThrow(VoucherNotFoundException::new);
            voucher.assignTo(userId, now);
            return MyVoucherView.of(vouchers.save(voucher), now.toLocalDate());
        });
    }

    private VoucherView change(Integer id, Consumer<Voucher> change) {
        var voucher = vouchers.findByIdForUpdate(id).orElseThrow(VoucherNotFoundException::new);
        change.accept(voucher);
        return VoucherView.of(vouchers.save(voucher));
    }

    /** Asks cinema-service outside any transaction, and only when a seat type was given. */
    private VoucherTerms validated(VoucherCommand command) {
        VoucherTerms terms = command.terms();
        if (terms.seatTypeId() != null && !cinemas.seatTypeNames().containsKey(terms.seatTypeId())) {
            throw new DomainException("Seat type " + terms.seatTypeId() + " does not exist");
        }
        return terms;
    }

    private void requireCombo(VoucherTerms terms) {
        if (terms.comboId() != null && combos.findAllByIds(List.of(terms.comboId())).isEmpty()) {
            throw new ComboNotFoundException();
        }
    }
}
