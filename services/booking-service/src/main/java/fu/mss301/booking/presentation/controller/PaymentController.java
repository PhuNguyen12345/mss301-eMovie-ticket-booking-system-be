package fu.mss301.booking.presentation.controller;

import fu.mss301.booking.application.booking.BookingView;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.payment.RefundAdminUseCase;
import fu.mss301.booking.application.payment.RefundView;
import fu.mss301.booking.domain.payment.RefundStatus;
import fu.mss301.booking.presentation.security.AuthenticatedUser;
import fu.mss301.booking.presentation.security.CurrentUser;
import fu.mss301.common.dto.ApiResponse;
import fu.mss301.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final HandlePayosWebhookUseCase webhook;
    private final ReconcilePaymentUseCase reconcile;
    private final RefundAdminUseCase refunds;

    public PaymentController(HandlePayosWebhookUseCase webhook, ReconcilePaymentUseCase reconcile,
                             RefundAdminUseCase refunds) {
        this.webhook = webhook;
        this.reconcile = reconcile;
        this.refunds = refunds;
    }

    /** Called by PayOS (public at the gateway); trust comes from the checksum signature only. */
    @PostMapping("/payos/webhook")
    public ApiResponse<Void> payosWebhook(@RequestBody Map<String, Object> body) {
        webhook.handle(body);
        return ApiResponse.success(null, "Webhook processed");
    }

    /** Front-end calls this after returning from the PayOS page instead of trusting the return URL params. */
    @PostMapping("/bookings/{bookingId}/sync")
    public ApiResponse<BookingView> sync(@CurrentUser AuthenticatedUser user, @PathVariable("bookingId") Integer bookingId) {
        return ApiResponse.success(reconcile.sync(user.id(), bookingId), "Payment status synchronized");
    }

    @GetMapping("/refunds")
    public ApiResponse<List<RefundView>> listRefunds(@CurrentUser AuthenticatedUser user,
                                                     @RequestParam(name = "status", defaultValue = "PENDING") RefundStatus status) {
        requireAdmin(user);
        return ApiResponse.success(refunds.list(status), "Refunds retrieved successfully");
    }

    @PostMapping("/refunds/{id}/complete")
    public ApiResponse<RefundView> completeRefund(@CurrentUser AuthenticatedUser user, @PathVariable("id") Integer id) {
        requireAdmin(user);
        return ApiResponse.success(refunds.complete(id), "Refund marked as completed");
    }

    private static void requireAdmin(AuthenticatedUser user) {
        if (!user.isAdmin()) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
