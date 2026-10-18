package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.entity.PendingRegistration;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {
    Optional<PendingRegistration> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.id = :id")
    Optional<PendingRegistration> findByIdForUpdate(@Param("id") String id);

    boolean existsByIdAndOtpHashAndConfirmedAtIsNullAndExpiresAtAfter(
            String id, String otpHash, LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update PendingRegistration p
            set p.otpDeliveryStatus = :status, p.updatedAt = :now, p.version = p.version + 1
            where p.id = :id and p.otpHash = :otpHash and p.confirmedAt is null
              and p.otpDeliveryStatus = :pending
            """)
    int updateDeliveryStatus(@Param("id") String id, @Param("otpHash") String otpHash,
                             @Param("status") OtpDeliveryStatus status,
                             @Param("pending") OtpDeliveryStatus pending,
                             @Param("now") LocalDateTime now);
}
