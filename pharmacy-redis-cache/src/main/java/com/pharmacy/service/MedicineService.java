package com.pharmacy.service;

import com.pharmacy.entity.Medicine;
import com.pharmacy.messaging.AlertPublisher;
import com.pharmacy.messaging.PharmacyAlert;
import com.pharmacy.repository.MedicineRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MedicineService {

    private static final Logger log = LoggerFactory.getLogger(MedicineService.class);
    private final MedicineRepository repository;
    private final AlertPublisher alertPublisher;
    private final RedissonClient redissonClient;

    private static final long LOCK_WAIT_SECONDS = 3;   // chờ tối đa 3s để lấy lock
    private static final long LOCK_LEASE_SECONDS = 5;  // giữ lock tối đa 5s rồi tự nhả

    public MedicineService(MedicineRepository repository, AlertPublisher alertPublisher,
                           RedissonClient redissonClient) {
        this.repository = repository;
        this.alertPublisher = alertPublisher;
        this.redissonClient = redissonClient;
    }

    /**
     * Bán 1 hộp thuốc, có Distributed Lock.
     * Chỉ thread nào lấy được lock "lock:medicine:{id}" mới được kiểm tra tồn kho và trừ số lượng.
     * Thread còn lại phải chờ; khi vào được thì tồn kho đã = 0 -> "Sản phẩm đã hết hàng!".
     *
     * KHÔNG đặt @Transactional ở hàm này: nếu có, transaction chỉ commit SAU khi lock đã nhả,
     * thread kia vào đọc DB vẫn thấy tồn kho cũ -> vẫn bán âm kho.
     */
    @CacheEvict(value = "medicines", key = "#id")   // tồn kho đổi -> xoá cache chi tiết thuốc
    public String sellMedicine(Long id) {
        RLock lock = redissonClient.getLock("lock:medicine:" + id);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return "Hệ thống đang bận, vui lòng thử lại!";
            }
            log.info("[{}] Đã lấy được lock:medicine:{}", Thread.currentThread().getName(), id);

            // ---- Vùng găng: chỉ 1 thread tại 1 thời điểm ----
            Medicine m = repository.findById(id).orElseThrow(() -> new MedicineNotFoundException(id));
            if (m.getStock() == null || m.getStock() <= 0) {
                return "Sản phẩm đã hết hàng!";
            }
            simulatePaymentProcessing();              // mô phỏng thời gian xử lý thanh toán
            m.setStock(m.getStock() - 1);
            repository.save(m);                       // commit ngay, trước khi nhả lock
            return "Thanh toán thành công thuốc: " + m.getName();
            // --------------------------------------------------
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Giao dịch bị gián đoạn!";
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[{}] Đã nhả lock:medicine:{}", Thread.currentThread().getName(), id);
            }
        }
    }

    /** Phiên bản KHÔNG có lock – chỉ để so sánh, sẽ thấy cả 2 người đều mua được (âm kho). */
    @CacheEvict(value = "medicines", key = "#id")
    public String sellMedicineWithoutLock(Long id) {
        Medicine m = repository.findById(id).orElseThrow(() -> new MedicineNotFoundException(id));
        if (m.getStock() == null || m.getStock() <= 0) {
            return "Sản phẩm đã hết hàng!";
        }
        try { simulatePaymentProcessing(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        m.setStock(m.getStock() - 1);
        repository.save(m);
        return "Thanh toán thành công thuốc: " + m.getName() + " (tồn kho còn " + m.getStock() + ")";
    }

    private void simulatePaymentProcessing() throws InterruptedException {
        Thread.sleep(200);
    }

    /**
     * @Cacheable: tìm key "medicines::<id>" trong Redis.
     *  - CÓ   -> trả luôn từ Redis, KHÔNG chạy thân hàm (không có SQL).
     *  - CHƯA -> chạy thân hàm (query DB) rồi lưu vào Redis với TTL 60 phút.
     */
    @Cacheable(value = "medicines", key = "#id")
    public Medicine getMedicineById(Long id) {
        log.info(">>> Cache MISS - truy vấn DATABASE cho thuốc id = {}", id);
        return repository.findById(id).orElseThrow(() -> new MedicineNotFoundException(id));
    }

    /**
     * Cập nhật thông tin thuốc (bao gồm giá).
     * @CacheEvict: sau khi hàm chạy thành công, xoá key "medicines::<medicine.id>" khỏi Redis
     * -> lần GET tiếp theo sẽ bị Cache MISS và nạp lại dữ liệu (giá mới) từ DB.
     */
    // Không đặt @Transactional ở đây: repository.save() tự commit trước khi cache bị xoá,
    // tránh trường hợp request khác nạp lại giá cũ vào cache trước khi DB commit.
    @CacheEvict(value = "medicines", key = "#medicine.id")
    public Medicine updateMedicine(Medicine medicine) {
        Medicine m = repository.findById(medicine.getId())
                .orElseThrow(() -> new MedicineNotFoundException(medicine.getId()));
        if (medicine.getName() != null)             m.setName(medicine.getName());
        if (medicine.getActiveIngredient() != null) m.setActiveIngredient(medicine.getActiveIngredient());
        if (medicine.getDosageForm() != null)       m.setDosageForm(medicine.getDosageForm());
        if (medicine.getManufacturer() != null)     m.setManufacturer(medicine.getManufacturer());
        if (medicine.getPrice() != null)            m.setPrice(medicine.getPrice());
        if (medicine.getStock() != null)            m.setStock(medicine.getStock());
        Medicine saved = repository.save(m);
        log.info(">>> Đã cập nhật thuốc id = {} (giá mới: {}) -> XOÁ cache medicines::{}",
                saved.getId(), saved.getPrice(), saved.getId());
        return saved;
    }

    /**
     * Nhập thêm hàng: cộng tồn kho, xoá cache (tồn kho đã đổi),
     * rồi PUBLISH thông báo tới tất cả Dashboard đang mở.
     */
    @CacheEvict(value = "medicines", key = "#id")
    public Medicine importStock(Long id, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng nhập phải > 0");
        Medicine m = repository.findById(id).orElseThrow(() -> new MedicineNotFoundException(id));
        m.setStock((m.getStock() == null ? 0 : m.getStock()) + quantity);
        Medicine saved = repository.save(m);   // nhập hàng thành công

        alertPublisher.publish(new PharmacyAlert("IMPORT",
                "Đã nhập " + quantity + " hộp " + saved.getName()));
        return saved;
    }

    /** Xoá thuốc thì xoá luôn key trong cache. */
    @CacheEvict(value = "medicines", key = "#id")
    public void deleteMedicine(Long id) {
        repository.deleteById(id);
    }

    public Medicine create(Medicine m) {
        return repository.save(m);
    }
}
