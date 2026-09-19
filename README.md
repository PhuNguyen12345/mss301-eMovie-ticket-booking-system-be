# E-Movie Ticket Booking System (Backend)

Hệ thống đặt vé xem phim trực tuyến được xây dựng dựa trên kiến trúc **Microservices**, ứng dụng các công nghệ hiện đại nhằm đảm bảo tính mở rộng, hiệu năng cao và giải quyết triệt để bài toán tranh chấp dữ liệu (Concurrency) khi đặt ghế.

## 🚀 Công nghệ sử dụng

* **Framework:** Spring Boot 3.x, Spring Cloud (Gateway, Netflix Eureka)
* **Database:** MySQL (Database-per-service)
* **Cache & Distributed Lock:** Redis
* **Inter-service Communication:** Spring Cloud OpenFeign (Synchronous) & `@Async` (Asynchronous)
* **3rd Party Integrations:**
* **Media Storage:** Cloudinary (Quản lý hình ảnh, trailer)
* **Payment Gateway:** SePay / PayOS (Thanh toán tự động qua Webhook)



## 📂 Cấu trúc dự án (Project Skeleton)

Dự án được thiết kế theo mô hình multi-module Maven, phân tách rõ ràng tầng hạ tầng (Platform) và tầng nghiệp vụ (Application).

```text
e-movie-ticket-booking-system/
├── .github/                # Cấu hình CI/CD workflows (VD: GitHub Actions)
├── api-gateway/            # Cổng giao tiếp duy nhất (Routing & JWT Security Filter)
├── discovery-server/       # Máy chủ đăng ký dịch vụ (Netflix Eureka Server)
├── docs/                   # Tài liệu dự án, API Contracts, Sơ đồ kiến trúc
├── services/               # Khối chứa các Microservices nghiệp vụ cốt lõi
│   ├── booking-service/    # Xử lý đặt vé, giữ ghế (Redis Lock) và Webhook thanh toán
│   ├── cinema-service/     # Quản lý hạ tầng tĩnh: Rạp, Phòng chiếu, Sơ đồ ghế
│   ├── movie-service/      # Quản lý danh mục phim (tích hợp Cloudinary)
│   ├── showtime-service/   # Xếp lịch chiếu, kiểm tra xung đột thời gian
│   └── user-service/       # Cấp phát JWT, xác thực tài khoản, lịch sử mua vé
├── pom.xml                 # Maven Parent POM quản lý dependency chung cho toàn dự án
└── mvnw / mvnw.cmd         # Script hỗ trợ chạy Maven không cần cài đặt môi trường

```

## 🧩 Chi tiết các Modules

### 1. Hạ tầng kiến trúc (Platform Layer)

* **API Gateway (`api-gateway`):** Điểm chạm duy nhất của toàn bộ hệ thống. Nhiệm vụ chính là định tuyến (Routing) request tới các service bên dưới và đóng vai trò "người kiểm duyệt", giải mã JWT, trích xuất thông tin người dùng và chặn các truy cập trái phép ngay từ vòng ngoài.
* **Service Discovery (`discovery-server`):** Hoạt động như một "danh bạ điện thoại". Các service nghiệp vụ sẽ tự động đăng ký địa chỉ IP tại đây, giúp Gateway và OpenFeign có thể tự động tìm thấy nhau mà không cần cấu hình cứng (hardcode) URL.

### 2. Nghiệp vụ cốt lõi (Application Layer)

* **Movie Service:** Chịu trách nhiệm quản lý toàn bộ thông tin phim (Tên, Thể loại, Độ tuổi). Tải và lưu trữ poster/trailer trực tiếp thông qua API của Cloudinary.
* **Cinema Service:** Nắm giữ cấu trúc vật lý tĩnh của hệ thống. Quản lý dữ liệu về các Cụm rạp, Phòng chiếu, và trạng thái phần cứng của sơ đồ ghế (Ví dụ: ghế VIP, ghế đôi, ghế đang bảo trì).
* **Showtime Service:** Kết hợp dữ liệu từ Movie và Cinema để tạo ra các suất chiếu cụ thể. Service này thực hiện các logic kiểm tra phức tạp để đảm bảo không có sự xung đột về thời gian và phòng chiếu.
* **Booking Service:** "Trái tim" của hệ thống phân tán. Chịu trách nhiệm gọi chéo OpenFeign để tổng hợp dữ liệu, sử dụng **Redis** để thiết lập khóa phân tán (Distributed Lock) chống trùng ghế và đếm ngược thời gian (TTL). Nó cũng tích hợp API PayOS/SePay để xử lý Webhook thanh toán và chạy luồng `@Async` gửi email xác nhận.
* **User Service:** Đảm nhận việc cấp phát "chứng minh thư" (JWT) khi người dùng đăng nhập. Lưu trữ thông tin cá nhân và hứng các sự kiện từ Booking Service để cập nhật lịch sử đặt vé của khách hàng.

## ⚙️ Hướng dẫn khởi chạy cục bộ

*(Phần này sẽ được bổ sung chi tiết sau khi chốt cấu hình `application.yml` cho từng môi trường)*

1. Cài đặt và khởi chạy **MySQL** và **Redis** trên máy cá nhân hoặc qua Docker.
2. Build toàn bộ dự án từ thư mục gốc: `./mvnw clean install`
3. Khởi chạy các service theo thứ tự bắt buộc:
* Chạy `discovery-server` đầu tiên.
* Chạy `api-gateway`.
* Chạy các service trong thư mục `services/` theo thứ tự tùy ý.