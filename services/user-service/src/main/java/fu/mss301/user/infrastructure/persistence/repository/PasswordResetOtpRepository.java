package fu.mss301.user.infrastructure.persistence.repository;

import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.infrastructure.persistence.entity.PasswordResetOtp;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Integer> {
    Optional<PasswordResetOtp> findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(
            Integer userId, PasswordOtpPurpose purpose);

    boolean existsByIdAndOtpHashAndUsedAtIsNullAndExpiresAtAfter(
            Integer id, String otpHash, LocalDateTime now);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update PasswordResetOtp o
            set o.otpDeliveryStatus = :status, o.version = o.version + 1
            where o.id = :id and o.otpHash = :otpHash and o.usedAt is null
              and o.otpDeliveryStatus = :pending
            """)
    int updateDeliveryStatus(@Param("id") Integer id, @Param("otpHash") String otpHash,
                             @Param("status") OtpDeliveryStatus status,
                             @Param("pending") OtpDeliveryStatus pending);
}
