package fu.mss301.booking.domain.voucher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository {
    Voucher save(Voucher voucher);

    Optional<Voucher> findById(Integer id);

    Optional<Voucher> findByIdForUpdate(Integer id);

    Optional<Voucher> findByCodeForUpdate(String code);

    /** Vouchers a booking has held since before {@code before}, oldest first. */
    List<Integer> findReservedSince(LocalDateTime before, int limit);
}
