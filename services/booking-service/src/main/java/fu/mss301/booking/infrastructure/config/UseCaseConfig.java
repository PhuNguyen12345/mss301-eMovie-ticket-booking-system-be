package fu.mss301.booking.infrastructure.config;

import fu.mss301.booking.application.booking.BookingQueryService;
import fu.mss301.booking.application.booking.CancelBookingUseCase;
import fu.mss301.booking.application.booking.ExpireOverdueBookingsUseCase;
import fu.mss301.booking.application.booking.HoldPolicy;
import fu.mss301.booking.application.booking.HoldSeatsUseCase;
import fu.mss301.booking.application.booking.ReplaceBookingCombosUseCase;
import fu.mss301.booking.application.booking.SeatMapQuery;
import fu.mss301.booking.application.combo.ComboService;
import fu.mss301.booking.application.payment.HandlePayosWebhookUseCase;
import fu.mss301.booking.application.payment.InitiatePaymentUseCase;
import fu.mss301.booking.application.payment.PaymentSettlementService;
import fu.mss301.booking.application.payment.ReconcilePaymentUseCase;
import fu.mss301.booking.application.payment.RefundAdminUseCase;
import fu.mss301.booking.application.policy.BookingEventPolicy;
import fu.mss301.booking.application.policy.PaymentEventPolicy;
import fu.mss301.booking.application.port.BookingQueries;
import fu.mss301.booking.application.port.DomainEvents;
import fu.mss301.booking.application.port.PaymentGateway;
import fu.mss301.booking.application.port.SeatLayoutProvider;
import fu.mss301.booking.application.port.ShowtimeCatalog;
import fu.mss301.booking.application.port.Transactions;
import fu.mss301.booking.domain.booking.BookingRepository;
import fu.mss301.booking.domain.combo.ComboRepository;
import fu.mss301.booking.domain.payment.PaymentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Application classes stay free of Spring, so their beans are declared here. */
@Configuration
public class UseCaseConfig {
    @Bean
    HoldSeatsUseCase holdSeatsUseCase(ShowtimeCatalog showtimes, SeatLayoutProvider seatLayout,
                                      BookingRepository bookings, HoldPolicy policy, Transactions transactions,
                                      Clock clock) {
        return new HoldSeatsUseCase(showtimes, seatLayout, bookings, policy, transactions, clock);
    }

    @Bean
    SeatMapQuery seatMapQuery(ShowtimeCatalog showtimes, SeatLayoutProvider seatLayout, BookingQueries queries,
                              Transactions transactions) {
        return new SeatMapQuery(showtimes, seatLayout, queries, transactions);
    }

    @Bean
    BookingQueryService bookingQueryService(BookingRepository bookings, BookingQueries queries,
                                            PaymentRepository payments, Transactions transactions) {
        return new BookingQueryService(bookings, queries, payments, transactions);
    }

    @Bean
    CancelBookingUseCase cancelBookingUseCase(BookingRepository bookings, Transactions transactions, DomainEvents events,
                                              Clock clock) {
        return new CancelBookingUseCase(bookings, transactions, events, clock);
    }

    @Bean
    ExpireOverdueBookingsUseCase expireOverdueBookingsUseCase(BookingRepository bookings,
                                                              ReconcilePaymentUseCase reconciliation,
                                                              Transactions transactions, DomainEvents events,
                                                              Clock clock) {
        return new ExpireOverdueBookingsUseCase(bookings, reconciliation, transactions, events, clock);
    }

    @Bean
    InitiatePaymentUseCase initiatePaymentUseCase(BookingRepository bookings, PaymentRepository payments,
                                                  PaymentGateway gateway, Transactions transactions, Clock clock) {
        return new InitiatePaymentUseCase(bookings, payments, gateway, transactions, clock);
    }

    @Bean
    PaymentSettlementService paymentSettlementService(PaymentRepository payments, BookingRepository bookings,
                                                      Transactions transactions, DomainEvents events, Clock clock) {
        return new PaymentSettlementService(payments, bookings, transactions, events, clock);
    }

    @Bean
    HandlePayosWebhookUseCase handlePayosWebhookUseCase(PaymentGateway gateway, PaymentSettlementService settlement) {
        return new HandlePayosWebhookUseCase(gateway, settlement);
    }

    @Bean
    ReconcilePaymentUseCase reconcilePaymentUseCase(PaymentRepository payments, PaymentGateway gateway,
                                                    PaymentSettlementService settlement,
                                                    BookingQueryService bookingQueries, Transactions transactions) {
        return new ReconcilePaymentUseCase(payments, gateway, settlement, bookingQueries, transactions);
    }

    @Bean
    BookingEventPolicy bookingEventPolicy(PaymentRepository payments, PaymentGateway gateway, Transactions transactions,
                                          Clock clock) {
        return new BookingEventPolicy(payments, gateway, transactions, clock);
    }

    @Bean
    PaymentEventPolicy paymentEventPolicy(PaymentSettlementService settlement) {
        return new PaymentEventPolicy(settlement);
    }

    @Bean
    ReplaceBookingCombosUseCase replaceBookingCombosUseCase(BookingRepository bookings, PaymentRepository payments,
                                                            ComboRepository combos, Transactions transactions,
                                                            Clock clock) {
        return new ReplaceBookingCombosUseCase(bookings, payments, combos, transactions, clock);
    }

    @Bean
    ComboService comboService(ComboRepository combos, Transactions transactions, Clock clock) {
        return new ComboService(combos, transactions, clock);
    }

    @Bean
    RefundAdminUseCase refundAdminUseCase(PaymentRepository payments, Transactions transactions, Clock clock) {
        return new RefundAdminUseCase(payments, transactions, clock);
    }
}
