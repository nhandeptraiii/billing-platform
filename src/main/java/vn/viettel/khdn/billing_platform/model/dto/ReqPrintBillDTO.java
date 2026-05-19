package vn.viettel.khdn.billing_platform.model.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReqPrintBillDTO(
    @NotNull(message = "Số tiền thu không được để trống")
    @Positive(message = "Số tiền thu phải lớn hơn 0")
    BigDecimal collectedAmount
) {}
