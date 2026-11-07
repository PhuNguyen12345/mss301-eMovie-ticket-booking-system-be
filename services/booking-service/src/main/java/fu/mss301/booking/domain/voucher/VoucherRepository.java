package fu.mss301.booking.domain.voucher;

import java.util.Optional;

public interface VoucherRepository {
    Voucher save(Voucher voucher);

    Optional<Voucher> findById(Integer id);

    Optional<Voucher> findByIdForUpdate(Integer id);

    Optional<Voucher> findByCodeForUpdate(String code);
}
