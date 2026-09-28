package com.pharmacy.messaging;

/**
 * Nội dung message gửi lên channel "pharmacy-alerts".
 * JSON: { "type": "IMPORT", "message": "Đã nhập 100 hộp Panadol" }
 */
public record PharmacyAlert(String type, String message) {
}
