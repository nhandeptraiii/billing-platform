package vn.viettel.khdn.billing_platform.model.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO kết quả import (đầu kỳ hoặc đối chiếu gạch nợ).
 */
public record ImportResultDTO(
    int totalRows,
    int successCount,
    int failedCount,
    int updatedCount,     // Số dòng được auto-update (dùng cho import đối chiếu)
    int warningCount,     // Số dòng có cảnh báo không đồng bộ (TH3)
    BigDecimal totalAmount, // Tổng tiền (import đầu kỳ)
    List<ImportErrorRow> errors
) {
    public record ImportErrorRow(int rowNumber, String reason) {}
}
