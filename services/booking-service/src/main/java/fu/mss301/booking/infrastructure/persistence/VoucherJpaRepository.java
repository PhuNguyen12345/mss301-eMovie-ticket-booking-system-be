package fu.mss301.booking.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface VoucherJpaRepository extends JpaRepository<VoucherEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoucherEntity v where v.id = :id")
    Optional<VoucherEntity> lockById(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoucherEntity v where v.code = :code")
    Optional<VoucherEntity> lockByCode(@Param("code") String code);

    /** The claim is fetched here, otherwise a page would run one extra query per voucher. */
    @EntityGraph(attributePaths = "claim")
    @Query(value = "select v from VoucherEntity v", countQuery = "select count(v) from VoucherEntity v")
    Page<VoucherEntity> findPage(Pageable pageable);

    @Query("select v from VoucherEntity v join fetch v.claim c where c.userId = :userId order by v.id desc")
    List<VoucherEntity> findOwnedBy(@Param("userId") Integer userId);
}
