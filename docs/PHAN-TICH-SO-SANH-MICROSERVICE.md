# PHÂN TÍCH KIẾN TRÚC MICROSERVICES: MOVIE TICKET BOOKING SYSTEM

## 1. Tổng quan bài toán

* **Mục tiêu:** Xây dựng hệ thống đặt vé xem phim trực tuyến cho một rạp chiếu phim duy nhất.
* **Chức năng người dùng:** 
  * Xem danh sách phim.
  * Xem lịch chiếu.
  * Chọn suất chiếu.
  * Chọn ghế ngồi.
  * Thực hiện đặt vé.
* **Các chức năng chính của hệ thống:**
  * Quản lý thông tin phim.
  * Quản lý các phòng chiếu trong rạp.
  * Quản lý lịch chiếu.
  * Quản lý trạng thái ghế theo từng suất chiếu.
  * Đặt vé và lưu thông tin đặt vé.
  * Quản lý tài khoản người dùng và lịch sử đặt vé.
* **Vấn đề kỹ thuật cốt lõi:** 
  * Đảm bảo tính chính xác của trạng thái ghế theo thời gian thực (Concurrency - kiểm soát đồng thời).
  * Tránh trường hợp một ghế được đặt bởi nhiều người cùng lúc.

---

## 2. Xác định các Microservices

Hệ thống được chia thành 5 microservices chính dựa trên nguyên tắc **Business Capability** (mỗi service chịu trách nhiệm cho một nhóm nghiệp vụ tương đối độc lập):

* **Movie Service:** Quản lý thông tin phim.
* **Cinema Service:** Quản lý phòng chiếu và cấu trúc ghế của rạp.
* **Showtime Service:** Quản lý lịch chiếu.
* **Booking Service:** Quản lý quá trình đặt vé và trạng thái ghế.
* **User Service:** Quản lý tài khoản người dùng và lịch sử đặt vé.

---

## 3. Vai trò của từng Microservice

### 3.1. Movie Service
* **Vai trò:** Chịu trách nhiệm quản lý toàn bộ thông tin liên quan đến các bộ phim (không trực tiếp quản lý phòng chiếu hoặc lịch chiếu).
* **Dữ liệu quản lý:**
  * Movie ID
  * Tên phim
  * Mô tả, Thể loại, Đạo diễn, Diễn viên
  * Thời lượng, Ngày phát hành
  * Poster, Trailer
  * Độ tuổi giới hạn
  * Trạng thái phim: `EXPIRED`, `PRESENTING`, `WILL_PRESENT`
* **Chức năng chính:**
  * Thêm, cập nhật, xóa hoặc ngừng hiển thị phim.
  * Xem danh sách và chi tiết phim.
  * Tìm kiếm và lọc phim.
  * Phân biệt phim đang chiếu, sắp chiếu hoặc ngừng chiếu.

### 3.2. Cinema Service
* **Vai trò:** Quản lý phòng chiếu và cấu trúc ghế bên trong rạp (chỉ phục vụ một rạp duy nhất).
* **Dữ liệu quản lý:**
  * *Phòng chiếu:* Screen ID, Tên/số phòng, Sức chứa, Loại phòng, Trạng thái hoạt động.
  * *Ghế ngồi:* Seat ID, Hàng ghế, Số ghế, Loại ghế (Standard, VIP, Couple), Trạng thái vật lý.
* **Chức năng chính:**
  * Quản lý danh sách, thêm/cập nhật phòng chiếu và sức chứa.
  * Quản lý sơ đồ và phân loại ghế.
  * Đánh dấu ghế tạm ngừng sử dụng (bảo trì).
  * Cung cấp thông tin phòng chiếu và ghế vật lý cho Showtime Service và Booking Service.
* **Lưu ý nghiệp vụ:** 
  * Cinema Service chỉ quản lý thông tin vật lý của ghế.
  * Các trạng thái đặt vé (`AVAILABLE`, `HELD`, `BOOKED`) thuộc nghiệp vụ của Booking Service quản lý theo từng suất chiếu.

### 3.3. Showtime Service
* **Vai trò:** Quản lý lịch chiếu phim tại các phòng chiếu.
* **Công thức xác định:** `Showtime = Movie + Screen + Start Time`
* **Dữ liệu quản lý:**
  * Showtime ID
  * Movie ID
  * Screen ID
  * Thời gian bắt đầu và kết thúc
  * Giá vé cơ bản
  * Trạng thái suất chiếu
* **Chức năng chính:**
  * Tạo, cập nhật, hủy suất chiếu.
  * Xem danh sách suất chiếu theo phim, theo ngày, theo phòng.
  * Kiểm tra xung đột lịch chiếu (tránh hai suất chiếu dùng chung phòng cùng thời điểm).

### 3.4. Booking Service
* **Vai trò:** Xử lý nghiệp vụ đặt vé, quản lý trạng thái ghế theo từng suất chiếu và xử lý thanh toán. Là service quan trọng nhất với bài toán Concurrency.
* **Dữ liệu quản lý:**
  * Booking ID, User ID, Showtime ID, Danh sách Seat ID
  * Tổng tiền, Thời gian tạo, Thời gian hết hạn giữ ghế (`ExpiredAt`)
  * Payment Transaction ID, Phương thức thanh toán, Trạng thái thanh toán, Trạng thái booking
* **Các trạng thái Booking:**
  * `PENDING_PAYMENT`: Đã tạo booking, đang giữ ghế chờ thanh toán (TTL 2–3 phút).
  * `CONFIRMED`: Thanh toán thành công, booking được xác nhận.
  * `CANCELLED`: Hủy bởi người dùng hoặc hệ thống.
  * `EXPIRED`: Hết hạn giữ ghế do chưa thanh toán.
* **Các trạng thái Thanh toán (Quản lý riêng biệt với Booking):**
  * `PENDING`: Chưa hoàn thành thanh toán.
  * `SUCCESS`: Thanh toán thành công.
  * `FAILED`: Thanh toán thất bại.
  * `REFUNDED`: Đã hoàn tiền.
* **Các trạng thái Ghế (`Showtime ID + Seat ID`):**
  * `AVAILABLE`: Chưa được đặt, có thể chọn.
  * `HELD`: Đang được giữ tạm thời trong 2–3 phút.
  * `BOOKED`: Đã thanh toán và đặt chính thức.
* **Chức năng chính:**
  * Lấy danh sách & kiểm tra trạng thái ghế theo suất chiếu.
  * Tạm giữ ghế, tạo booking, tính tổng tiền.
  * Khởi tạo và xử lý kết quả thanh toán.
  * Xác nhận booking hoặc giải phóng ghế (khi hủy/hết hạn).
* **Cơ chế xử lý Concurrency (Đồng thời):**
  * Tránh Race Condition khi nhiều người cùng chọn một ghế.
  * Phương pháp áp dụng: Database Transaction, Unique Constraint, Optimistic/Pessimistic Locking, Atomic Update, Distributed Lock (Redis).
  * *Ví dụ Atomic Update:* `UPDATE showtime_seat SET status = 'HELD' WHERE status = 'AVAILABLE'` (Chỉ thành công nếu ghế còn trống).
* **Idempotency & Xử lý thanh toán trễ:**
  * Xác minh kết quả từ Payment Gateway thông qua Callback/Webhook (không tin tưởng trực tiếp từ frontend).
  * Sử dụng Payment Transaction ID / Idempotency Key để tránh xử lý trùng lặp.
  * Trường hợp Payment `SUCCESS` nhưng Booking đã `EXPIRED` -> Kích hoạt quy trình Hoàn tiền (`REFUND`).

### 3.5. User Service
* **Vai trò:** Quản lý tài khoản người dùng trong hệ thống.
* **Dữ liệu quản lý:**
  * User ID, Họ tên, Email, Số điện thoại, Password Hash, Role (`ADMIN`, `CUSTOMER`), Trạng thái tài khoản.
* **Chức năng chính:**
  * Đăng ký, đăng nhập.
  * Quản lý thông tin cá nhân và đổi mật khẩu (xác minh OTP qua email).
  * Phối hợp với Booking Service để cung cấp dữ liệu lịch sử đặt vé.

---

## 4. Quan hệ và Giao tiếp giữa các Microservices

* **Sơ đồ liên kết:**
  * `Movie Service` <--feign-- `Showtime Service`
  * `Cinema Service` <--feign-- `Showtime Service`
  * `Cinema Service` <--feign-- `Booking Service`
  * `Showtime Service` <--feign-- `Booking Service`
  * `User Service` <--feign-- `Booking Service`
* **Sự kết hợp nghiệp vụ:**
  * **Showtime Service:** Liên kết `Movie + Screen + Time`
  * **Booking Service:** Liên kết `User + Showtime + Seat`

---

## 5. Quyền sở hữu dữ liệu (Database per Service)

Mỗi service sở hữu cơ sở dữ liệu riêng, không truy cập trực tiếp database của nhau:

* **Movie Service:** Movie Database
* **Cinema Service:** Screen / Seat Database
* **Showtime Service:** Showtime Database
* **Booking Service:** Booking / ShowtimeSeat Database
* **User Service:** User Database

*Giao tiếp giữa các service thực hiện qua REST API hoặc Message Broker.*

---

## 6. Quy trình đặt vé (Luồng nghiệp vụ)

1. **User** chọn phim -> **Movie Service** cung cấp thông tin.
2. **User** chọn suất chiếu -> **Showtime Service** trả về danh sách lịch chiếu.
3. **User** bắt đầu đặt ghế -> **Booking Service** lấy cấu trúc ghế từ **Cinema Service**.
4. **Booking Service** kết hợp trạng thái ghế thực tế theo suất chiếu.
5. **Booking Service** thực hiện `Hold Seat` (chuyển sang `HELD`, gán TTL 2-3 phút) và tạo Booking `PENDING_PAYMENT`.
6. **User** thực hiện thanh toán qua Payment Gateway:
   * **Thành công:** Payment = `SUCCESS` -> Booking = `CONFIRMED` -> Seat = `BOOKED`.
   * **Thất bại / Hết giờ (Timeout):** Booking = `EXPIRED` -> Seat = `AVAILABLE` (Giải phóng ghế).

---

## 7. So sánh Monolithic và Microservices

* **Cấu trúc:** Monolithic gộp chung một ứng dụng; Microservices chia thành nhiều service nhỏ.
* **Triển khai (Deployment):** Monolithic deploy toàn bộ; Microservices deploy độc lập từng service.
* **Database:** Monolithic dùng chung database; Microservices áp dụng Database per service.
* **Khả năng mở rộng (Scale):** Monolithic scale toàn bộ; Microservices scale riêng service bị tải cao (vd: Booking Service).
* **Độ phức tạp:** Monolithic đơn giản ban đầu; Microservices phức tạp hơn về giao tiếp mạng và distributed transaction.
* **Cách ly lỗi (Fault Isolation):** Monolithic kém (lỗi một module có thể kéo sập hệ thống); Microservices tốt hơn.

---

## 8. Lý do lựa chọn Microservices cho hệ thống

* **Phân chia nghiệp vụ rõ ràng:** Giảm sự phụ thuộc lẫn nhau giữa các module.
* **Phát triển & triển khai độc lập:** Nâng cấp Booking Service mà không ảnh hưởng Movie Service.
* **Scale linh hoạt theo tải:** Tăng instance cho Booking Service & Showtime Service vào các khung giờ cao điểm mở bán vé phim Hot.
* **Tối ưu bài toán Concurrency:** Tách biệt Booking Service giúp áp dụng các giải pháp Caching/Locking nâng cao mà không làm ảnh hưởng đến các tác vụ CRUD đơn giản khác.

---

## 9. Một số trade-off của Microservices và Giải pháp

Microservices mang lại nhiều lợi ích nhưng đồng thời làm hệ thống phức tạp hơn. Dưới đây là các thách thức chính cùng giải pháp xử lý ngắn gọn:

* **Giao tiếp giữa các service (Inter-service Communication):**
  * **Thách thức:** Phức tạp trong việc chọn lựa và quản lý các cơ chế truyền nhận dữ liệu.
  * **Giải pháp ngắn:** Dùng Message Broker (RabbitMQ, Kafka) cho xử lý bất đồng bộ theo dạng sự kiện (Event-driven).

* **Service Discovery (Phát hiện dịch vụ):**
  * **Thách thức:** Khó theo dõi và quản lý địa chỉ IP/Port thay đổi linh hoạt khi scale nhiều instance.
  * **Giải pháp ngắn:** Sử dụng Service Registry tự động như Consul, Eureka hoặc tận dụng DNS native của Kubernetes / API Gateway.

* **Network Failure (Lỗi kết nối mạng):**
  * **Thách thức:** Độ trễ mạng và nguy cơ rớt mạng khiến chuỗi gọi API bị gián đoạn.
  * **Giải pháp ngắn:** Đặt Timeout hợp lý, cấu hình cơ chế Retry tự động và thiết lập Fallback response khi dịch vụ đích gặp sự cố.

* **Distributed Logging (Ghi log phân tán):**
  * **Thách thức:** Log nằm rải rác ở từng service/container làm khó khăn cho việc truy vết lỗi.
  * **Giải pháp ngắn:** Tập trung hóa log bằng bộ công cụ ELK Stack (Elasticsearch, Logstash, Kibana) hoặc PLG (Promtail, Loki, Grafana) kết hợp gắn Correlation ID vào mỗi request.

* **Distributed Tracing (Truy vết phân tán):**
  * **Thách thức:** Khó theo dõi luồng đi và đo lường độ trễ của một request đi qua nhiều microservices.
  * **Giải pháp ngắn:** Sử dụng OpenTelemetry kết hợp với Jaeger hoặc Zipkin để gắn Trace ID theo dõi toàn bộ vòng đời của request.

* **Consistency giữa nhiều database (Tính nhất quán dữ liệu):**
  * **Thách thức:** Không thể dùng ACID Transaction truyền thống khi mỗi service quản lý database riêng.
  * **Giải pháp ngắn:** Áp dụng mô hình Saga Pattern (Choreography hoặc Orchestration) kết hợp với Outbox Pattern để đạt được tính nhất quán cuối cùng (Eventual Consistency).

* **Retry & Timeout:**
  * **Thách thức:** Việc Retry không kiểm soát dễ gây ra hiện tượng bão request (Cascading Failure) làm sập hệ thống.
  * **Giải pháp ngắn:** Cấu hình Max Retry, kết hợp thời gian chờ ngẫu nhiên tăng dần (Exponential Backoff with Jitter).

* **Circuit Breaker (Cầu chì ngắt mạch):**
  * **Thách thức:** Một service bị chậm/sập có thể kéo theo các service gọi đến nó cũng bị nghẽn tài nguyên.
  * **Giải pháp ngắn:** Tích hợp thư viện Resilience4j hoặc sử dụng Service Mesh (Istio) để tự động ngắt kết nối tạm thời khi tỷ lệ lỗi vượt ngưỡng cho phép.

* **Authentication giữa các service (Xác thực nội bộ):**
  * **Thách thức:** Đảm bảo an toàn và phân quyền khi các service tự do gọi lẫn nhau.
  * **Giải pháp ngắn:** Cấu hình mTLS (Mutual TLS) trong Service Mesh để mã hóa đường truyền và sử dụng JWT/OAuth2 Scopes để xác thực quyền giữa các service.

---

## 10. Kết luận

* **Tổng quan kiến trúc:** Hệ thống đặt vé xem phim trực tuyến dành cho một rạp duy nhất được phân chia tối ưu thành 5 microservices độc lập: Movie Service, Cinema Service, Showtime Service, Booking Service và User Service.
* **Phân định rõ trách nhiệm về quản lý ghế:**
  * **Cinema Service:** Quản lý phòng chiếu và cấu trúc vật lý của ghế (sơ đồ ghế, loại ghế, sức chứa).
  * **Booking Service:** Quản lý trạng thái hoạt động thực tế của ghế (AVAILABLE, HELD, BOOKED) gắn liền với từng suất chiếu cụ thể.
* **Công thức định danh nghiệp vụ chính:**
  * **Showtime Service:** `Movie + Screen + Time`
  * **Booking Service:** `User + Showtime + Seat`
* **Tính tối ưu và hiệu quả:**
  * Thiết kế đáp ứng chính xác phạm vi một rạp duy nhất, loại bỏ sự phức tạp dư thừa từ các tính năng quản lý chuỗi/chi nhánh không cần thiết.
  * Giải quyết hiệu quả bài toán xử lý đồng thời (concurrency), đảm bảo tính toàn vẹn dữ liệu ghế theo thời gian thực và cho phép mở rộng (scale) độc lập dịch vụ đặt vé khi tải cao.