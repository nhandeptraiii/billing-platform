package vn.viettel.khdn.billing_platform.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO dữ liệu in bill — trả về đầy đủ thông tin cho Mobile App render bill.
 */
public record ResBillDataDTO(
    // Thông tin cửa hàng
    String storeName,
    String storeAddress,
    String hotline,
    String qrImageUrl,  // URL đầy đủ đến ảnh QR (null nếu không có)
    String adsText,

    // Thông tin khách hàng
    String customerCode,
    String customerName,
    String subscriberNumber,
    String fullAddress,

    // Kỳ thanh toán
    String billingPeriodName,

    // Thông tin thu tiền
    BigDecimal amountDue,
    BigDecimal collectedAmount,
    Instant collectedAt,        // Ngày thu
    String collectedByName      // Người thu
) {}
