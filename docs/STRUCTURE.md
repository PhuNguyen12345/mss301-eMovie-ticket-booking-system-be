# Cấu trúc hệ thống (Project Structure)

Dự án được thiết kế theo mô hình Multi-module Maven. Ở mức độ kiến trúc tổng thể, hệ thống tuân theo chuẩn **Microservices**. Ở mức độ chi tiết bên trong mỗi service, hệ thống áp dụng kiến trúc 3 lớp (**MVC - Controller/Service/Repository**) để tối ưu hóa tốc độ phát triển và dễ dàng bảo trì.

## 1. Cấu trúc thư mục toàn cục

Sơ đồ dưới đây thể hiện kiến trúc tổng thể của toàn bộ hệ thống, bao gồm thư mục dùng chung (`common`) và cấu trúc mẫu bên trong một microservice tiêu biểu (`booking-service`).

```text
e-movie-ticket-booking-system/
├── .github/                # Cấu hình CI/CD (GitHub Actions)
├── api-gateway/            # Cổng định tuyến API & Trạm gác bảo mật (Xác thực JWT)
├── discovery-server/       # Máy chủ đăng ký dịch vụ (Netflix Eureka)
│
├── common/                 # Module chứa code dùng chung cho toàn bộ hệ thống
│   └── src/main/java/com/ecinema/common/
│       ├── dto/
│       │   └── ApiResponse.java            # Format chuẩn (code, message, data) cho mọi API
│       └── exception/
│           └── GlobalExceptionHandler.java # Bắt lỗi toàn cục (@RestControllerAdvice)
│
├── services/               # Thư mục chứa các Microservices nghiệp vụ
│   ├── booking-service/    # Quản lý đặt vé, giữ chỗ, thanh toán
│   │   └── src/main/java/com/ecinema/booking/
│   │       ├── BookingServiceApplication.java # Lớp khởi chạy Spring Boot
│   │       ├── config/       # Cấu hình đặc thù (Redis, Async, OpenFeign...)
│   │       ├── controller/   # Tầng giao diện API (REST Endpoints)
│   │       ├── dto/          # Đối tượng truyền tải dữ liệu (Request/Response)
│   │       ├── entity/       # Các lớp ánh xạ trực tiếp với bảng Database (JPA)
│   │       ├── repository/   # Tầng giao tiếp cơ sở dữ liệu (Spring Data JPA)
│   │       ├── client/       # (Tùy chọn) Interface OpenFeign gọi sang service khác
│   │       ├── service/      # Tầng xử lý logic nghiệp vụ cốt lõi (Business Logic)
│   │       ├── notification/ # (Tùy chọn) Tác vụ chạy ngầm (Gửi mail/thông báo)
│   │       └── exception/    # Các Exception đặc thù của service (VD: SeatBookedException)
│   │
│   ├── cinema-service/     # Quản lý rạp, phòng chiếu, sơ đồ ghế vật lý
│   ├── movie-service/      # Quản lý phim, danh mục
│   ├── showtime-service/   # Quản lý lịch chiếu, chống xung đột thời gian
│   └── user-service/       # Quản lý người dùng, cấp phát JWT, lịch sử
│
├── docs/                   # Tài liệu thiết kế, API Contracts, Sơ đồ kiến trúc
├── pom.xml                 # Maven Parent POM quản lý dependencies chung
└── README.md               # Tổng quan dự án

```

## 2. Quy chuẩn thiết kế (Coding Convention)

* **Tách biệt ranh giới (Domain Isolation):** Mỗi microservice sở hữu một cơ sở dữ liệu độc lập. Giao tiếp liên dịch vụ (Inter-service communication) bắt buộc thực hiện thông qua HTTP/OpenFeign tại tầng `client`.
* **Đồng bộ dữ liệu trả về:** Mọi API Endpoints từ tất cả các services đều phải bọc dữ liệu trả về bằng class `ApiResponse` từ module `common` để đảm bảo tính nhất quán cho phía Frontend.
* **Xử lý lỗi tập trung:** Mọi ngoại lệ (Exception) phát sinh trong quá trình xử lý nghiệp vụ sẽ được đẩy về `GlobalExceptionHandler` tại module `common` để định dạng lại thành HTTP Status Code và message tương ứng trước khi trả về cho client.
* **Quản lý tiến trình ngầm:** Các tác vụ không thuộc luồng giao dịch chính (ví dụ: gửi email xác nhận tại `notification`) phải được xử lý bất đồng bộ (Asynchronous) để đảm bảo thời gian phản hồi API tối ưu nhất.