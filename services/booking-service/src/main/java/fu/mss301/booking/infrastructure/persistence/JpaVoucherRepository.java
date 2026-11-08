package fu.mss301.booking.infrastructure.persistence;

import fu.mss301.booking.application.booking.BookingPage;
import fu.mss301.booking.application.port.VoucherQueries;
import fu.mss301.booking.domain.voucher.Voucher;
import fu.mss301.booking.domain.voucher.VoucherCodeTakenException;
import fu.mss301.booking.domain.voucher.VoucherRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaVoucherRepository implements VoucherRepository, VoucherQueries {
    private final VoucherJpaRepository jpa;

    JpaVoucherRepository(VoucherJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Voucher save(Voucher voucher) {
        VoucherEntity entity;
        if (voucher.id() == null) {
            entity = new VoucherEntity();
        } else {
            entity = jpa.findById(voucher.id()).orElseThrow();
            if (!Objects.equals(entity.getVersion(), voucher.version())) {
                throw new ObjectOptimisticLockingFailureException(VoucherEntity.class, voucher.id());
            }
        }
        VoucherMapper.update(voucher, entity);
        try {
            return VoucherMapper.toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            // uq_vouchers_code, or uq_vouchers_source_* when two runs issue the same value voucher at once. Every
            // other write holds a row lock.
            throw new VoucherCodeTakenException();
        }
    }

    @Override
    public Optional<Voucher> findById(Integer id) {
        return jpa.findById(id).map(VoucherMapper::toDomain);
    }

    @Override
    public Optional<Voucher> findByIdForUpdate(Integer id) {
        return jpa.lockById(id).map(VoucherMapper::toDomain);
    }

    @Override
    public Optional<Voucher> findByCodeForUpdate(String code) {
        return jpa.lockByCode(code).map(VoucherMapper::toDomain);
    }

    @Override
    public Optional<Voucher> findBySourcePaymentId(Integer paymentId) {
        return jpa.findBySourcePaymentId(paymentId).map(VoucherMapper::toDomain);
    }

    @Override
    public Optional<Voucher> findBySourceBookingId(Integer bookingId) {
        return jpa.findBySourceBookingId(bookingId).map(VoucherMapper::toDomain);
    }

    @Override
    public List<Integer> findReservedSince(LocalDateTime before, int limit) {
        return jpa.findReservedSince(before, PageRequest.of(0, limit));
    }

    @Override
    public BookingPage<Voucher> findAll(int page, int size) {
        var result = jpa.findPage(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return new BookingPage<>(result.getContent().stream().map(VoucherMapper::toDomain).toList(), result.getNumber(),
            result.getSize(), result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    public List<Voucher> findOwnedBy(Integer userId) {
        return jpa.findOwnedBy(userId).stream().map(VoucherMapper::toDomain).toList();
    }
}
