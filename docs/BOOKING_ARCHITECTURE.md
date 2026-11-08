# Booking-service: Clean Architecture

Booking-service theo cùng chuẩn với showtime-service: `domain` và `application` chỉ dùng Java, mọi chi tiết kỹ thuật nằm ở `infrastructure` và `presentation`. Tài liệu gốc của chuẩn này là [SHOWTIME_ARCHITECTURE.md](SHOWTIME_ARCHITECTURE.md). File này chỉ ghi những gì booking cần thêm hoặc làm khác.

API, schema và luồng nghiệp vụ xem ở [BOOKING_API.md](BOOKING_API.md).

## Cấu trúc

```text
fu.mss301.booking
  domain/
    booking/         Booking, BookingSeat, BookingCombo, BookingVoucher, VoucherPricing, BookingCheckIn,
                     CheckInWindow, BookingCode,
                     BookingRepository, event/
    combo/           Combo, ComboRepository
    voucher/         Voucher, VoucherClaim, VoucherTerms, VoucherCode, VoucherRepository
    payment/         PaymentTransaction, Refund, PaymentRepository, event/
    shared/          DomainEvent, DomainException, InvalidStateTransitionException
  application/
    booking/         Use case và view của booking
    combo/           Danh mục combo
    voucher/         Quản lý, nhận voucher và VoucherReservations
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
    qr/              Vẽ mã QR bằng ZXing
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

- Mapper chỉ cập nhật field có thể thay đổi. Dòng con có id được tìm lại đúng theo id, nên id của `booking_seats`, `refunds` và `booking_combos` được giữ nguyên.
- Dòng `booking_seats` và `refunds` không bao giờ bị xóa. Riêng `booking_combos` bị xóa khi combo được bỏ khỏi booking, vì đây là lựa chọn chưa thanh toán, không phải lịch sử.
- `version()` trên aggregate chỉ để adapter so sánh. Lệch version sẽ trả 409.
- `active_flag` là generated column, không được map vào entity.
- `save()` flush ngay, để lỗi `uq_booking_seats_active` được đổi thành `SeatAlreadyTakenException` rồi trả về 409.

## Voucher

`Voucher` là aggregate riêng, mỗi dòng là một mã. `VoucherClaim` là con của nó, tương ứng dòng `user_vouchers`, cho biết mã thuộc về ai. Mọi thay đổi đều khóa dòng `vouchers`, nên hai người nhận cùng một mã thì người sau đọc được mã đã có chủ. `uq_user_vouchers_voucher` là lớp chặn cuối ở DB.

`VoucherTerms` gom các điều kiện của voucher và tự kiểm tra lúc tạo. `claim` trả cùng một lỗi cho mọi trường hợp không dùng được, để không ai dò được mã thật.

### Áp voucher vào booking

`Voucher` và `Booking` là 2 aggregate, nên `ApplyVoucherUseCase` đổi chúng ở 2 transaction:

1. Khóa dòng voucher, `reserveFor(userId, bookingId)`, nhận lại `VoucherOffer` là bản sao điều kiện.
2. Khóa dòng booking, `applyVoucher(offer, seatId)`. Booking lưu bản sao vào `BookingVoucher`, nên tính tiền không đọc lại `Voucher`.
3. Bước 2 lỗi thì `VoucherReservations.settle` trả voucher, rồi ném lỗi gốc.

Toàn bộ quy tắc tính tiền nằm ở `VoucherPricing`, dùng chung cho áp mã, gỡ mã, đổi combo và gỡ mã hết hạn. Chế độ `strict` từ chối cả lần áp nếu một voucher không còn hợp lệ. Chế độ còn lại bỏ voucher đó và `Booking` phát `BookingVoucherRemoved`.

`VoucherReservations.settle(voucherId, bookingId)` đưa voucher về đúng trạng thái theo booking, chạy bao nhiêu lần cũng được:

| Booking | Voucher |
| --- | --- |
| `CONFIRMED` và còn voucher | `USED` |
| `PENDING_PAYMENT` và còn voucher | Giữ nguyên |
| Trường hợp khác | `AVAILABLE` nếu đúng booking này đang giữ |

`VoucherEventPolicy` gọi hàm này sau các event `BookingConfirmed`, `BookingExpired`, `BookingCancelled`, `BookingVoucherRemoved`. Job bảo trì gọi lại với voucher `RESERVED` lâu hơn `BOOKING_HOLD_TTL`, để event bị mất hoặc service dừng giữa 2 bước không làm kẹt voucher.

Đơn 0đ được `InitiatePaymentUseCase` xác nhận trong transaction chỉ ghi booking, không tạo `PaymentTransaction`.

### Voucher giá trị

`ValueVoucherIssuer` phát voucher giá trị và chỉ phát một lần cho mỗi nguồn. Gọi lại với cùng payment hoặc booking thì trả voucher đã có. Hai lần chạy cùng lúc thì `UNIQUE` ở DB chặn lần sau, và lần đó đọc lại voucher của lần trước.

Tiền về mà không xuất được vé, `PaymentSettlementService.settle` chạy 3 transaction:

1. Khóa booking, xác định không giữ payment này.
2. Phát voucher giá trị cho chủ booking.
3. Khóa payment, `compensateWithVoucher(voucherId)`: thêm refund `VOUCHER` đã `COMPLETED`, payment `REFUNDED`.

Dừng giữa bước 2 và 3 thì job đối soát chạy lại `settle` và dùng lại voucher đã phát.

Phần dư được phát trong `VoucherReservations.settle` **trước** khi đánh dấu voucher cũ `USED`. Nếu phát lỗi, voucher cũ vẫn `RESERVED` và job bảo trì thử lại.

## Check-in

`BookingCheckIn` là con của `Booking`, mỗi dòng là một lần quét. `Booking.checkIn` giữ mọi quy tắc: chỉ `CONFIRMED`, trong `CheckInWindow`, tổng số người không vượt số ghế. Use case khóa dòng booking theo mã, nên hai máy quét cùng lúc lần lượt chạy.

Giờ chiếu đọc từ bản sao vé. Booking cũ không có bản sao thì `CheckInUseCase` hỏi showtime-service trước khi mở transaction.

Mã QR vẽ qua port `QrCodeRenderer`, nên `application` không phụ thuộc ZXing.

## Suất chiếu bị hủy

`ShowtimeCancellationUseCase` hỏi showtime-service để chắc suất đã hủy, rồi gọi `Booking.cancelForShowtime` cho từng booking, mỗi booking một transaction. Booking chờ thanh toán phát `BookingCancelled`, nên các policy có sẵn đóng link PayOS và trả voucher. Booking đã thanh toán phát `BookingShowtimeCancelled`.

`ShowtimeCompensationPolicy` gọi `ShowtimeCompensationService.compensate`. `ShowtimeCompensation` trong domain tính số tiền, service chạy 4 bước, mỗi bước một transaction:

1. Trả voucher đã dùng.
2. Phát voucher, mỗi `VoucherSourceReason` một cái.
3. Ghi refund cho payment.
4. `markCompensated`.

Bước nào cũng lặp lại được, nên job bảo trì chạy lại booking chưa có `compensated_at`. `VoucherReservations.settle` bỏ qua voucher của booking `SHOWTIME_CANCELLED`, để chỉ bước 1 quyết định voucher đó.

## Combo

`Combo` là aggregate riêng, `Booking` chỉ giữ `comboId` và bản sao đơn giá. `PUT /api/bookings/{id}/combos` chỉ ghi vào `Booking`, còn `Combo` chỉ được đọc, nên vẫn giữ quy tắc 1 transaction ghi 1 aggregate.

Quy tắc "không sửa combo khi có payment `PENDING`" nằm ở `ReplaceBookingCombosUseCase` vì cần dữ liệu của 2 aggregate. Use case này khóa dòng booking trước, cùng thứ tự với `InitiatePaymentUseCase`, nên sửa combo và tạo link không thể chen vào nhau để ra link sai số tiền.

## Thông tin vé

Vé đã bán là bản sao cố định. `HoldSeatsUseCase` đọc phim, rạp, phòng và loại ghế từ các service khác, ngoài transaction, rồi lưu vào `TicketDetails`, `BookingSeat` và `BookingCombo`.

- Đường đọc không gọi upstream. Không thêm lời gọi Feign vào `BookingQueryService`, vì `GET /api/bookings/me` và việc xem vé khi service khác lỗi đều dựa vào điều này.
- Kiểm tra ghế trước khi đọc thông tin vé, để request không hợp lệ không tốn thêm lời gọi.
- `Booking.ticket()` có thể `null` với booking cũ, còn booking mới luôn có.
- Khi suất chiếu đổi giờ, vé không tự cập nhật. Khách xử lý theo nghiệp vụ ở issue #20.

## Logging

`application` dùng `java.lang.System.Logger` để không phụ thuộc thư viện ngoài. Log đi qua JUL tới Logback nên vẫn có cùng định dạng.

Luôn dùng dạng `Supplier`, không dùng placeholder:

```java
log.log(Level.WARNING, () -> "Could not expire booking " + bookingId + ": " + exception.getMessage());
```

Placeholder `{0}` đi qua `MessageFormat`, nên số bị format theo locale, ví dụ `120001` có thể thành `120,001`. Còn `{}` của SLF4J sẽ được in nguyên văn.
