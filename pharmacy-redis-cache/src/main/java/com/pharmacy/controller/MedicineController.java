package com.pharmacy.controller;

import com.pharmacy.entity.Medicine;
import com.pharmacy.service.MedicineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@RestController
@RequestMapping("/api/medicines")
public class MedicineController {

    private static final Logger log = LoggerFactory.getLogger(MedicineController.class);
    private final MedicineService service;

    public MedicineController(MedicineService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Medicine> getById(@PathVariable Long id) {
        long start = System.nanoTime();
        Medicine m = service.getMedicineById(id);
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        log.info("GET /api/medicines/{} xử lý trong {} ms", id, String.format("%.2f", ms));
        return ResponseEntity.ok()
                .header("X-Response-Time-ms", String.format("%.2f", ms))
                .body(m);
    }

    @PostMapping
    public Medicine create(@RequestBody Medicine m) {
        return service.create(m);
    }

    /** Cập nhật thông tin thuốc (chỉ các trường gửi lên). */
    @PutMapping("/{id}")
    public Medicine update(@PathVariable Long id, @RequestBody Medicine m) {
        m.setId(id);                       // để #medicine.id = id trên URL
        return service.updateMedicine(m);
    }

    /** Sửa riêng giá: PATCH /api/medicines/1/price  body: {"price": 99000} */
    @PatchMapping("/{id}/price")
    public Medicine updatePrice(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        Medicine m = new Medicine();
        m.setId(id);
        m.setPrice(body.get("price"));
        return service.updateMedicine(m);
    }

    /** Nhập hàng: POST /api/medicines/1/import  body: {"quantity": 100} */
    @PostMapping("/{id}/import")
    public Medicine importStock(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return service.importStock(id, body.get("quantity"));
    }

    /**
     * Mô phỏng 2 nhân viên cùng bấm "Thanh toán" sản phẩm cuối cùng.
     * PUT /api/medicines/sell/1
     */
    @PutMapping("/sell/{id}")
    public String sellMedicine(@PathVariable Long id) {
        runTwoBuyers(() -> service.sellMedicine(id));
        return "Đã gửi 2 yêu cầu mua song song, xem kết quả trong Console";
    }

    /** So sánh: không có lock -> cả 2 đều thành công, tồn kho âm. PUT /api/medicines/sell-no-lock/1 */
    @PutMapping("/sell-no-lock/{id}")
    public String sellMedicineNoLock(@PathVariable Long id) {
        runTwoBuyers(() -> service.sellMedicineWithoutLock(id));
        return "Đã gửi 2 yêu cầu mua song song (KHÔNG lock), xem kết quả trong Console";
    }

    private void runTwoBuyers(java.util.function.Supplier<String> sell) {
        // Latch giúp 2 thread xuất phát đúng cùng một thời điểm
        CountDownLatch startSignal = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            await(startSignal);
            String rsBuy = sell.get();
            System.out.println("Người dùng 1 : " + rsBuy);
        }, "nhan-vien-1");

        Thread thread2 = new Thread(() -> {
            await(startSignal);
            String rsBuy = sell.get();
            System.out.println("Người dùng 2 : " + rsBuy);
        }, "nhan-vien-2");

        thread1.start();
        thread2.start();
        startSignal.countDown();   // "bấm Thanh toán" cùng lúc
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteMedicine(id);
        return ResponseEntity.noContent().build();
    }
}
