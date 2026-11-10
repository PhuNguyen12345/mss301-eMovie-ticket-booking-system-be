# Dữ liệu demo

`seed_demo.py` thay dữ liệu test trên máy local bằng một bộ dữ liệu trông như rạp thật:

| Dữ liệu | Số lượng | Ghi chú |
| --- | --- | --- |
| Phim | 26 | 10 đang chiếu, 5 sắp chiếu, 11 đã ngừng chiếu. Có poster, mô tả tiếng Việt, thể loại, đạo diễn và diễn viên kèm tên vai |
| Rạp | 3 | Hà Nội, Sài Gòn, Đà Nẵng, mỗi rạp 2 phòng |
| Ghế | 576 | Mỗi phòng 8 hàng × 12 ghế. Hàng F–H là ghế VIP |
| Suất chiếu | ~240 | 7 ngày kể từ ngày mai, từ 09:00 đến khoảng 22:30 |
| Combo | 6 | Bắp, nước, Combo Solo, Combo Đôi, Combo Gia Đình, Nachos |
| Voucher | 12 | Mã dễ đọc dạng `EMOVIE-...`, hạn 60 ngày |

Giá vé:

| Khung giờ | Thứ 2 – Thứ 6 | Thứ 7, Chủ nhật |
| --- | --- | --- |
| Trước 17:00 | 75.000đ | 85.000đ |
| Từ 17:00 | 95.000đ | 105.000đ |

Ghế VIP cộng thêm 30.000đ so với giá trong bảng.

Script chỉ dùng thư viện chuẩn của Python 3. Mọi dữ liệu được tạo qua API gateway, nên đi qua đúng các quy tắc nghiệp vụ của từng service.

## Chạy

Chạy ở thư mục gốc repo, khi stack Docker đang chạy (`docker-compose up -d --wait`). Nên chờ khoảng 1 phút sau khi khởi động, để các service kịp tìm thấy nhau qua Eureka.

```bash
python3 tools/seed/seed_demo.py reset          # sao lưu cả 5 DB, in số dòng sẽ xóa, chưa xóa gì
python3 tools/seed/seed_demo.py reset --yes    # sao lưu rồi xóa dữ liệu test
python3 tools/seed/seed_demo.py seed           # tạo dữ liệu demo
```

`reset --yes` xóa:
- Toàn bộ phim, thể loại, người, suất chiếu.
- Toàn bộ booking, giao dịch, refund, combo, voucher.

`reset --yes` giữ lại:
- Tài khoản người dùng.
- Rạp và ghế. Cinema-service tự tạo lại 2 rạp `DEMO_*` mỗi khi khởi động, nên script đổi tên và mở rộng 2 rạp này chứ không xóa.

`seed` chạy lại nhiều lần được. Phim, combo, voucher và ghế đã có thì được bỏ qua. Suất chiếu bị trùng lịch phòng thì cũng bỏ qua.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `SEED_GATEWAY_URL` | `http://127.0.0.1:8080` | Địa chỉ API gateway |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | trống | Tài khoản admin dùng để seed |

Khi không đặt tài khoản admin, script tạo một admin tạm với mật khẩu ngẫu nhiên trong `user_db`, rồi xóa admin đó khi chạy xong, kể cả khi bị lỗi giữa chừng. Cách này cần lệnh `htpasswd`: macOS có sẵn, trên Linux cài gói `apache2-utils`.

## Poster

Mặc định, `posterUrl` trỏ thẳng tới ảnh trên `upload.wikimedia.org`.

Cờ `seed --upload-posters` tải ảnh về rồi upload vào Cloudinary của nhóm qua `PUT /api/movies/{id}/poster`, nên demo không phụ thuộc Wikimedia. Ảnh nào upload lỗi thì giữ link Wikimedia. Cờ này chỉ có tác dụng với phim tạo mới. Muốn áp dụng cho phim đã có, chạy `reset --yes` trước.

## Sao lưu và khôi phục

Mỗi lần chạy `reset`, script dump cả 5 DB vào `.seed-backups/<thời điểm>/<service>.sql`. Danh sách mã voucher demo được ghi ở `.seed-backups/vouchers.txt`. Thư mục này nằm trong `.gitignore`.

Khôi phục một DB, ví dụ booking:

```bash
docker-compose exec -T booking-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' \
  < .seed-backups/20261110-234809/booking-db.sql
```

## Nguồn dữ liệu phim

`movies.json` được soạn từ Wikipedia và Wikidata:
- Poster, thời lượng, ngày khởi chiếu, đạo diễn, diễn viên và tên vai lấy theo Wikidata. Ngày khởi chiếu ưu tiên ngày ở Việt Nam.
- Tên tiếng Việt lấy theo nhãn tiếng Việt trên Wikidata.
- Mô tả được viết riêng cho bộ dữ liệu này.
- Phân loại tuổi (P/K/T13/T16/T18) chỉ mang tính tham khảo.
- Phim sắp chiếu chưa có thời lượng chính thức, nên dùng thời lượng dự kiến và có ghi chú trong mô tả.

Poster thuộc về các hãng phim. Wikipedia chỉ cung cấp bản độ phân giải thấp theo quy định sử dụng hợp lý, vì vậy bộ dữ liệu này chỉ dùng cho demo học tập, không dùng cho sản phẩm thương mại.

Thêm phim: thêm một phần tử vào `movies.json` với các field như các phim có sẵn. `status` là `PRESENTING`, `WILL_PRESENT` hoặc `EXPIRED`. `posterUrl` phải là link HTTPS trực tiếp tới file ảnh JPEG hoặc PNG.
