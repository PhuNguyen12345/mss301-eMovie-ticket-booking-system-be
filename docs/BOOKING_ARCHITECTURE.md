# Booking-service: Clean Architecture

Booking-service theo cùng chuẩn với showtime-service: `domain` và `application` chỉ dùng Java, mọi chi tiết kỹ thuật nằm ở `infrastructure` và `presentation`. Tài liệu gốc của chuẩn này là [SHOWTIME_ARCHITECTURE.md](SHOWTIME_ARCHITECTURE.md). File này chỉ ghi những gì booking cần thêm hoặc làm khác.

API, schema và luồng nghiệp vụ xem ở [BOOKING_API.md](BOOKING_API.md).

## Cấu trúc

```text
fu.mss301.booking
  domain/
    booking/         Booking, BookingSeat, BookingCode, BookingRepository, event/
    payment/         PaymentTransaction, Refund, PaymentRepository, event/
    shared/          DomainEvent, DomainException, InvalidStateTransitionException
  application/
    booking/         Use case và view của booking
    payment/         Use case và view của thanh toán, hoàn tiền
    policy/          Phản ứng với domain event
    port/            Transactions, DomainEvents, BookingQueries, ShowtimeCatalog,
                     SeatLayoutProvider, PaymentGateway
    exception/       Lỗi của tầng application, không biết HTTP
  infrastructure/
    persistence/     JPA entity, mapper, Spring Data repository, adapter
    event/           SpringDomainEvents, DomainEventListener
    client/          Feign client và adapter
    payos/           Adapter PayOS SDK
    config/          SpringTransactions, UseCaseConfig
    scheduling/      Job hết hạn và đối soát
  presentation/      Controller, request DTO, đọc user từ header, map lỗi sang HTTP
```

`ArchitectureDependencyTests` kiểm tra các quy tắc phụ thuộc này mỗi lần chạy test.

## Khác với showtime

| Điểm | Showtime | Booking | Lý do |
| --- | --- | --- | --- |
| Domain event | Không có | Có | Payment và Booking là 2 aggregate, phải cập nhật ở 2 transaction |
| Transaction | Mặc định REQUIRED, READ_COMMITTED | Luôn REQUIRES_NEW, isolation mặc định | Policy chạy sau commit, xem bên dưới |
| Package | `domain/model`, `application/usecase` | Theo aggregate: `domain/booking`, `domain/payment` | Booking có 2 aggregate |
| Quan hệ JPA | Không dùng, con lấy bằng query riêng | Giữ `@OneToMany`, `@ManyToOne` | Giữ nguyên các truy vấn JPQL sẵn có |
| Lock | Chỉ row lock | Row lock và `@Version` | Nhiều luồng cùng đổi một booking: webhook, job hết hạn, người dùng |

## Domain event

Luồng của một event:

1. Aggregate gom event vào list riêng khi đổi trạng thái, ví dụ `confirm()`.
2. Use case lưu aggregate rồi gọi `events.publish(booking.pullEvents())`.
3. `SpringDomainEvents` chuyển event cho Spring.
4. Sau khi transaction commit, `DomainEventListener` gọi policy ở `application/policy`.
5. Policy cập nhật aggregate kia trong transaction mới.

Khi thêm một chỗ phát event, phải giữ đúng 3 quy tắc sau. Vi phạm quy tắc 1 hoặc 3 sẽ mất event mà không có lỗi nào được báo:

```java
return transactions.write(() -> {
    booking.cancelBy(userId, LocalDateTime.now(clock));
    var saved = bookings.save(booking);
    events.publish(booking.pullEvents()); // không phải saved.pullEvents()
    return BookingView.of(saved, null);
});
```

1. **Luôn publish sau khi đổi trạng thái.** Bài test trong `DomainEventPublishingTests` phải có cho mỗi chỗ phát event mới.
2. **Publish trong transaction.** Ngoài transaction, `SpringDomainEvents` sẽ ném `IllegalStateException`.
3. **Pull từ object truyền vào `save()`.** `save()` trả về object mới, không mang theo event.

## Transaction

`Transactions` luôn mở transaction mới. Listener `AFTER_COMMIT` chạy khi transaction cũ vẫn còn gắn với thread, nên nếu dùng REQUIRED thì policy sẽ nhập vào transaction đã kết thúc và thay đổi không được commit.

Hệ quả là không được gọi `transactions.read` hay `write` lồng nhau, vì mỗi lần gọi sẽ chiếm thêm một kết nối DB.

Repository adapter yêu cầu có transaction (`MANDATORY`) vì mapper phải đọc các collection lazy. Khi cần gọi PayOS hoặc Feign sau khi đọc DB, hãy lấy dữ liệu trong `transactions.read` trước, rồi gọi bên ngoài. Xem `ReconcilePaymentUseCase.reconcileBooking`.

## Lưu aggregate

- Mapper chỉ cập nhật field có thể thay đổi. Dòng con có id được tìm lại đúng theo id và không bao giờ bị xóa, nên id của `booking_seats` và `refunds` được giữ nguyên.
- `version()` trên aggregate chỉ để adapter so sánh. Lệch version sẽ trả 409.
- `active_flag` là generated column, không được map vào entity.
- `save()` flush ngay, để lỗi `uq_booking_seats_active` được đổi thành `SeatAlreadyTakenException` rồi trả về 409.

## Logging

`application` dùng `java.lang.System.Logger` để không phụ thuộc thư viện ngoài. Log đi qua JUL tới Logback nên vẫn có cùng định dạng.

Luôn dùng dạng `Supplier`, không dùng placeholder:

```java
log.log(Level.WARNING, () -> "Could not expire booking " + bookingId + ": " + exception.getMessage());
```

Placeholder `{0}` đi qua `MessageFormat`, nên số bị format theo locale, ví dụ `120001` có thể thành `120,001`. Còn `{}` của SLF4J sẽ được in nguyên văn.
