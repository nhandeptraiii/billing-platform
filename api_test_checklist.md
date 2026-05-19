# Billing Platform — Danh sách Test API

> **Base URL:** `http://localhost:8080`  
> **Auth:** Bearer Token (JWT) — lấy từ `/login`  
> **Format ngày:** `dd/MM/yyyy HH:mm`

---

## Thiết lập trước khi test

| Bước | Việc cần làm |
|------|--------------|
| 1 | Chạy ứng dụng (`./gradlew bootRun`) |
| 2 | Kiểm tra DB `billing_platform` đã được tạo tự động |
| 3 | `AdminInitializer` tự seed tài khoản MANAGER mặc định khi startup |
| 4 | Chuẩn bị file Excel mẫu để test import |

> **Tài khoản mặc định (seed bởi AdminInitializer):** xem log console khi khởi động lần đầu.

---

## Module 1 — Authentication (`/login`, `/logout`, `/account`)

### TC-AUTH-01: Đăng nhập thành công
- **Method:** `POST /login`
- **Role yêu cầu:** Không cần token
- **Body:**
```json
{
  "email": "admin@billing.vn",
  "password": "123456"
}
```
- **Kết quả mong đợi:** `200 OK` — trả về `{ accessToken, user: { id, email, fullName, role } }`
- [ ] Pass

---

### TC-AUTH-02: Đăng nhập sai mật khẩu
- **Method:** `POST /login`
- **Body:** email đúng, password sai
- **Kết quả mong đợi:** `401 Unauthorized` — message lỗi tiếng Việt
- [ ] Pass

---

### TC-AUTH-03: Đăng nhập email không tồn tại
- **Method:** `POST /login`
- **Body:** email không có trong hệ thống
- **Kết quả mong đợi:** `401 Unauthorized`
- [ ] Pass

---

### TC-AUTH-04: Lấy thông tin tài khoản hiện tại
- **Method:** `GET /account`
- **Header:** `Authorization: Bearer <token>`
- **Kết quả mong đợi:** `200 OK` — trả về thông tin User đang đăng nhập
- [ ] Pass

---

### TC-AUTH-05: Đăng xuất
- **Method:** `POST /logout`
- **Header:** `Authorization: Bearer <token>`
- **Kết quả mong đợi:** `200 OK`
- [ ] Pass

---

### TC-AUTH-06: Dùng token sau khi đăng xuất
- **Điều kiện:** Đã logout token ở TC-AUTH-05
- **Method:** `GET /account` với token cũ
- **Kết quả mong đợi:** `401 Unauthorized` — token bị blacklist
- [ ] Pass

---

### TC-AUTH-07: Gọi API không có token
- **Method:** `GET /records`
- **Header:** Không có Authorization
- **Kết quả mong đợi:** `401 Unauthorized` với JSON response (không redirect)
- [ ] Pass

---

## Module 2 — User Management (`/users`)

> **Lưu ý:** Toàn bộ module yêu cầu role **MANAGER**

### TC-USER-01: Lấy danh sách user
- **Method:** `GET /users`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — danh sách User `[]`
- [ ] Pass

---

### TC-USER-02: Lấy user theo ID
- **Method:** `GET /users/{id}`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — chi tiết 1 User
- [ ] Pass

---

### TC-USER-03: Tạo tài khoản CONSULTANT
- **Method:** `POST /users`
- **Role:** MANAGER
- **Body:**
```json
{
  "email": "consultant1@billing.vn",
  "password": "123456",
  "fullName": "Nguyễn Văn A",
  "role": "CONSULTANT"
}
```
- **Kết quả mong đợi:** `201 Created` — trả về User mới
- [ ] Pass

---

### TC-USER-04: Tạo user với email đã tồn tại
- **Method:** `POST /users`
- **Body:** email trùng với TC-USER-03
- **Kết quả mong đợi:** `400 Bad Request` — message trùng email
- [ ] Pass

---

### TC-USER-05: Cập nhật thông tin user
- **Method:** `PUT /users/{id}`
- **Role:** MANAGER
- **Body:**
```json
{
  "email": "consultant1@billing.vn",
  "password": "newpass123",
  "fullName": "Nguyễn Văn A (Updated)",
  "role": "CONSULTANT"
}
```
- **Kết quả mong đợi:** `200 OK` — thông tin đã cập nhật
- [ ] Pass

---

### TC-USER-06: Vô hiệu hoá tài khoản (disable)
- **Method:** `PATCH /users/{id}/status?status=false`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — `enabled: false`
- [ ] Pass

---

### TC-USER-07: Kích hoạt lại tài khoản
- **Method:** `PATCH /users/{id}/status?status=true`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — `enabled: true`
- [ ] Pass

---

### TC-USER-08: Xoá user
- **Method:** `DELETE /users/{id}`
- **Role:** MANAGER
- **Kết quả mong đợi:** `204 No Content`
- [ ] Pass

---

### TC-USER-09: CONSULTANT gọi `/users` bị từ chối
- **Method:** `GET /users`
- **Role:** CONSULTANT (token của CONSULTANT)
- **Kết quả mong đợi:** `403 Forbidden`
- [ ] Pass

---

## Module 3 — Billing Period (`/billing-periods`)

### TC-BP-01: Lấy danh sách kỳ cước
- **Method:** `GET /billing-periods`
- **Role:** Bất kỳ (đã đăng nhập)
- **Kết quả mong đợi:** `200 OK` — danh sách `BillingPeriod[]`
- [ ] Pass

---

### TC-BP-02: Lấy kỳ cước theo ID
- **Method:** `GET /billing-periods/{id}`
- **Role:** Bất kỳ
- **Kết quả mong đợi:** `200 OK` — chi tiết kỳ cước
- [ ] Pass

---

### TC-BP-03: Lấy kỳ cước ID không tồn tại
- **Method:** `GET /billing-periods/9999`
- **Kết quả mong đợi:** `404 Not Found`
- [ ] Pass

---

### TC-BP-04: Import đầu kỳ (file Excel hợp lệ)
- **Method:** `POST /billing-periods/import`
- **Role:** MANAGER
- **Form-data:** `file=<file .xlsx>`
- **Kết quả mong đợi:** `200 OK` — `{ successCount, failCount, errors[] }`
- [ ] Pass

---

### TC-BP-05: Import file không phải Excel
- **Method:** `POST /billing-periods/import`
- **Role:** MANAGER
- **Form-data:** `file=<file .csv hoặc .pdf>`
- **Kết quả mong đợi:** `400 Bad Request`
- [ ] Pass

---

### TC-BP-06: CONSULTANT import bị từ chối
- **Method:** `POST /billing-periods/import`
- **Role:** CONSULTANT
- **Kết quả mong đợi:** `403 Forbidden`
- [ ] Pass

---

### TC-BP-07: Đóng kỳ cước (close)
- **Method:** `PATCH /billing-periods/{id}/close`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — `status: "CLOSED"`
- [ ] Pass

---

### TC-BP-08: Đóng kỳ đã đóng rồi
- **Method:** `PATCH /billing-periods/{id}/close` (kỳ đã CLOSED)
- **Kết quả mong đợi:** `400 Bad Request` — "Kỳ này đã được đóng"
- [ ] Pass

---

## Module 4 — Customer Records (`/records`)

### TC-CR-01: Lấy danh sách records (MANAGER — xem tất cả)
- **Method:** `GET /records`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — Page object với danh sách tất cả records
- [ ] Pass

---

### TC-CR-02: Lấy danh sách records (CONSULTANT — chỉ xem của mình)
- **Method:** `GET /records`
- **Role:** CONSULTANT
- **Kết quả mong đợi:** `200 OK` — chỉ trả về records được giao cho CONSULTANT đó
- [ ] Pass

---

### TC-CR-03: Filter theo periodId
- **Method:** `GET /records?periodId={id}`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — chỉ records thuộc kỳ đó
- [ ] Pass

---

### TC-CR-04: Filter theo status
- **Method:** `GET /records?status=CHUA_THU`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — chỉ records chưa thu
- [ ] Pass

---

### TC-CR-05: Filter theo địa chỉ (province, ward, hamlet, street)
- **Method:** `GET /records?province=Hà Nội&ward=Phường X`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — records đúng địa chỉ
- [ ] Pass

---

### TC-CR-06: Tìm kiếm tự do (search)
- **Method:** `GET /records?search=Nguyễn Văn`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — records khớp tên/SĐT/địa chỉ
- [ ] Pass

---

### TC-CR-07: Phân trang
- **Method:** `GET /records?page=0&size=5`
- **Role:** MANAGER
- **Kết quả mong đợi:** `200 OK` — `totalElements, totalPages, content[5 items]`
- [ ] Pass

---

### TC-CR-08: Lấy chi tiết 1 record
- **Method:** `GET /records/{id}`
- **Role:** Bất kỳ (đã đăng nhập)
- **Kết quả mong đợi:** `200 OK` — chi tiết `ResCustomerRecordDTO`
- [ ] Pass

---

### TC-CR-09: CONSULTANT lấy record không thuộc mình
- **Method:** `GET /records/{id}` — id thuộc consultant khác
- **Role:** CONSULTANT
- **Kết quả mong đợi:** `403 Forbidden` hoặc `404 Not Found`
- [ ] Pass

---

### TC-CR-10: Thu tiền và in bill (CHUA_THU → DA_IN_BILL)
- **Method:** `PATCH /records/{id}/print-bill`
- **Role:** CONSULTANT (hoặc MANAGER)
- **Body:**
```json
{
  "collectedAmount": 250000
}
```
- **Kết quả mong đợi:** `200 OK` — record chuyển `status: "DA_IN_BILL"`, lưu `collectedAmount`
- [ ] Pass

---

### TC-CR-11: In bill record đã in rồi
- **Method:** `PATCH /records/{id}/print-bill` (record đã DA_IN_BILL)
- **Kết quả mong đợi:** `400 Bad Request` — "Record không ở trạng thái hợp lệ"
- [ ] Pass

---

### TC-CR-12: Gạch nợ (DA_IN_BILL → DA_GACH_NO)
- **Method:** `PATCH /records/{id}/mark-debt`
- **Role:** Bất kỳ (đã đăng nhập)
- **Kết quả mong đợi:** `200 OK` — `status: "DA_GACH_NO"`
- [ ] Pass

---

### TC-CR-13: Gạch nợ record chưa in bill
- **Method:** `PATCH /records/{id}/mark-debt` (record còn CHUA_THU)
- **Kết quả mong đợi:** `400 Bad Request`
- [ ] Pass

---

### TC-CR-14: Lấy dữ liệu in bill (bill-data)
- **Method:** `GET /records/{id}/bill-data`
- **Role:** Bất kỳ
- **Kết quả mong đợi:** `200 OK` — `ResBillDataDTO` với đầy đủ thông tin KH + store config
- [ ] Pass

---

### TC-CR-15: Import đối chiếu gạch nợ
- **Method:** `POST /records/import-reconciliation?periodId={id}`
- **Role:** MANAGER
- **Form-data:** `file=<file đối chiếu .xlsx>`
- **Kết quả mong đợi:** `200 OK` — `{ successCount, failCount }`
- [ ] Pass

---

### TC-CR-16: CONSULTANT gọi import-reconciliation bị từ chối
- **Method:** `POST /records/import-reconciliation?periodId=1`
- **Role:** CONSULTANT
- **Kết quả mong đợi:** `403 Forbidden`
- [ ] Pass

---

### TC-CR-17: Lấy danh sách cảnh báo
- **Method:** `GET /records/warnings?periodId={id}`
- **Role:** Bất kỳ
- **Kết quả mong đợi:** `200 OK` — danh sách records có `DA_IN_BILL` chưa gạch nợ hoặc `INCONSISTENT`
- [ ] Pass

---

## Module 5 — Store Config (`/store-config`)

### TC-SC-01: Lấy cấu hình hiện tại
- **Method:** `GET /store-config`
- **Role:** Bất kỳ (đã đăng nhập)
- **Kết quả mong đợi:** `200 OK` — `StoreConfig` object (hoặc object rỗng nếu chưa cấu hình)
- [ ] Pass

---

### TC-SC-02: Cập nhật cấu hình (MANAGER)
- **Method:** `PUT /store-config`
- **Role:** MANAGER
- **Body:**
```json
{
  "storeName": "Điểm thu cước Viettel Q1",
  "address": "123 Nguyễn Trãi, Q1, TP.HCM",
  "hotline": "1800 8000",
  "adsText": "Viettel - Theo dòng lịch sử"
}
```
- **Kết quả mong đợi:** `200 OK` — config đã lưu với `updatedBy`
- [ ] Pass

---

### TC-SC-03: CONSULTANT cập nhật config bị từ chối
- **Method:** `PUT /store-config`
- **Role:** CONSULTANT
- **Kết quả mong đợi:** `403 Forbidden`
- [ ] Pass

---

### TC-SC-04: Upload ảnh QR (MANAGER)
- **Method:** `POST /store-config/qr`
- **Role:** MANAGER
- **Form-data:** `file=<file ảnh .png hoặc .jpg, ≤ 5MB>`
- **Kết quả mong đợi:** `200 OK` — config có `qrImagePath` chứa tên file
- [ ] Pass

---

### TC-SC-05: Upload ảnh QR vượt 5MB
- **Method:** `POST /store-config/qr`
- **Role:** MANAGER
- **Form-data:** `file=<file > 5MB>`
- **Kết quả mong đợi:** `400 Bad Request` — lỗi multipart size exceeded
- [ ] Pass

---

### TC-SC-06: Truy cập file ảnh đã upload (public)
- **Method:** `GET /uploads/{filename}`
- **Role:** Không cần token
- **Kết quả mong đợi:** `200 OK` — trả về file ảnh (byte stream)
- [ ] Pass

---

## Tóm tắt

| Module | Số TC | Đã pass | Còn lại |
|--------|-------|---------|---------|
| Auth | 7 | 0 | 7 |
| User Management | 9 | 0 | 9 |
| Billing Period | 8 | 0 | 8 |
| Customer Records | 17 | 0 | 17 |
| Store Config | 6 | 0 | 6 |
| **Tổng** | **47** | **0** | **47** |

---

## Thứ tự test đề xuất

```
1. TC-AUTH-01  → lấy token MANAGER
2. TC-USER-03  → tạo CONSULTANT
3. TC-AUTH-01  → login bằng CONSULTANT → lấy token CONSULTANT
4. TC-BP-04    → import đầu kỳ (dùng token MANAGER)
5. TC-CR-01 ~ TC-CR-17  → test toàn bộ luồng nghiệp vụ
6. TC-SC-02, TC-SC-04   → cấu hình store
7. Các TC kiểm tra bảo mật (403, 401)
```
