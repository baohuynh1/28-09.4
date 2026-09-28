# Pharmacy – Redis Cache cho chi tiết thuốc

- `@Cacheable(value="medicines", key="#id")` – lấy chi tiết thuốc
- `@CacheEvict(value="medicines", key="#medicine.id")` – cập nhật thuốc / giá → xoá cache ngay
- TTL cache `medicines` = **60 phút** (`app.cache.medicines-ttl=60m`)
- Key trong Redis: `medicines::<id>`, value dạng JSON

## Chạy
```bash
docker compose up -d          # PostgreSQL + Redis
mvn spring-boot:run           # lần đầu tự sinh 5.000 thuốc mẫu
redis-cli FLUSHALL            # làm sạch cache trước khi thực hành
```

## Thực hành 4 bước

**Bước 1 – Lần đầu (Cache MISS, chậm):**
```bash
curl -i http://localhost:8080/api/medicines/1
```
Log: `Cache MISS ...` + `Hibernate: select ... from medicines ... where m1_0.id=?`

**Bước 2 – Gọi lại (Cache HIT, cực nhanh):**
```bash
curl -i http://localhost:8080/api/medicines/1
redis-cli KEYS "*"                 # -> "medicines::1"
redis-cli TTL  "medicines::1"      # -> khoảng 3600 (giây)
```
Log: `Cache entry for key '1' found in cache(s) [medicines]`, **không có SQL**, `X-Response-Time-ms` < 10.

**Bước 3 – Sửa giá thuốc ID=1 (xoá cache):**
```bash
curl -X PATCH http://localhost:8080/api/medicines/1/price \
     -H "Content-Type: application/json" -d '{"price": 99000}'
# hoặc: curl -X PUT http://localhost:8080/api/medicines/1 -H "Content-Type: application/json" -d '{"price": 99000}'

redis-cli KEYS "*"                 # -> (empty array): key medicines::1 đã biến mất
```
Log: `Invalidating cache key [1] for operation ... CacheEvictOperation` và `Đã cập nhật thuốc id = 1 (giá mới: 99000) -> XOÁ cache medicines::1`

**Bước 4 – Gọi lại (Cache MISS, nạp giá mới):**
```bash
curl http://localhost:8080/api/medicines/1     # "price": 99000
redis-cli KEYS "*"                             # -> "medicines::1" xuất hiện lại
```
Log: SQL `select ... from medicines` **xuất hiện lại** → hệ thống nhận ra cache cũ đã bị xoá và nạp dữ liệu mới nhất từ DB.

## Đổi sang MySQL
Trong `pom.xml` thay driver `postgresql` bằng `mysql-connector-j`, đổi `spring.datasource.url`.

---

# Lesson 03 – Redis Pub/Sub: thông báo nhập hàng

| Thành phần | File | Vai trò |
|---|---|---|
| Message | `messaging/PharmacyAlert.java` | `{ "type": "IMPORT", "message": "..." }` |
| Producer | `messaging/AlertPublisher.java` | `ObjectMapper` → JSON → `PUBLISH pharmacy-alerts` |
| Consumer | `messaging/AlertSubscriber.java` | Nhận message → `ObjectMapper` → in ra Console |
| Cấu hình | `config/RedisPubSubConfig.java` | `RedisMessageListenerContainer` subscribe channel |

## Thực hành
Mở 3 terminal để mô phỏng nhiều Dashboard chi nhánh:

```bash
# Terminal 1 – Dashboard chi nhánh 1 (đồng thời là server nhận API)
mvn spring-boot:run

# Terminal 2 – Dashboard chi nhánh 2 (instance thứ 2, cổng khác)
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

# Terminal 3 – theo dõi trực tiếp bằng redis-cli
redis-cli SUBSCRIBE pharmacy-alerts
```

Gọi API nhập hàng (thuốc id=1 là Panadol trong dữ liệu mẫu):
```bash
curl -X POST http://localhost:8080/api/medicines/1/import \
     -H "Content-Type: application/json" -d '{"quantity": 100}'
```

Kết quả: **cả 3 terminal** nhận ngay lập tức:
```
[PUBLISHER] Đã gửi lên 'pharmacy-alerts': {"type":"IMPORT","message":"Đã nhập 100 hộp Panadol"} -> 3 subscriber nhận được
==================================================
[DASHBOARD] Nhận thông báo từ channel: pharmacy-alerts
  Loại    : IMPORT
  Nội dung: Đã nhập 100 hộp Panadol
==================================================
```

Có thể gửi thử thủ công không cần API:
```bash
redis-cli PUBLISH pharmacy-alerts '{"type":"IMPORT","message":"Đã nhập 100 hộp Panadol"}'
```

## Chứng minh Fire-and-forget
1. Tắt hết các subscriber, gọi API nhập hàng → log `-> 0 subscriber nhận được`.
2. Bật lại subscriber → **không nhận được** message cũ.
3. `redis-cli KEYS "*"` → không có key nào chứa message: Redis không lưu lại.

> Lưu ý: DB đã có dữ liệu từ các bài trước thì seeder không chạy lại, id=1 có thể không phải Panadol.
> Muốn dữ liệu mới: `docker compose down -v && docker compose up -d`.

---

# Distributed Lock với Redisson: chống bán âm kho

- `RedissonConfig`: tạo `RedissonClient` dùng chung Redis.
- `MedicineService.sellMedicine(id)`: `RLock` key `lock:medicine:{id}`, `tryLock(3, 5, SECONDS)`
  (chờ tối đa 3s, giữ lock tối đa 5s). Lấy được lock mới kiểm tra tồn kho và trừ số lượng.
- `PUT /api/medicines/sell/{id}`: 2 thread (2 nhân viên) mua cùng lúc.
- `PUT /api/medicines/sell-no-lock/{id}`: bản không lock để so sánh.

## Thực hành
```bash
# 1. Đặt tồn kho thuốc id=1 còn đúng 1 hộp
curl -X PUT http://localhost:8080/api/medicines/1 -H "Content-Type: application/json" -d '{"stock": 1}'

# 2. Hai nhân viên cùng mua (CÓ lock)
curl -X PUT http://localhost:8080/api/medicines/sell/1
```
Console:
```
[nhan-vien-2] Đã lấy được lock:medicine:1
Hibernate: select ... / update medicines set ... stock=? ...
[nhan-vien-2] Đã nhả lock:medicine:1
Người dùng 2 : Thanh toán thành công thuốc: Panadol
[nhan-vien-1] Đã lấy được lock:medicine:1
Hibernate: select ...
Người dùng 1 : Sản phẩm đã hết hàng!
```
(Người thắng có thể là 1 hoặc 2, nhưng luôn chỉ có **1 người** mua được.)

## So sánh khi KHÔNG có lock
```bash
curl -X PUT http://localhost:8080/api/medicines/1 -H "Content-Type: application/json" -d '{"stock": 1}'
curl -X PUT http://localhost:8080/api/medicines/sell-no-lock/1
```
→ Cả 2 người đều "Thanh toán thành công": đã bán 2 hộp trong khi kho chỉ có 1.

## Xem lock trong Redis (trong lúc đang bán)
```bash
redis-cli KEYS "lock:*"          # lock:medicine:1
redis-cli TTL lock:medicine:1    # <= 5 giây (lease time)
```
