package vn.viettel.khdn.billing_platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ReqCreateCustomerRecordDTO(
    @NotNull(message = "ID kỳ cước không được để trống")
    Long billingPeriodId,

    @NotBlank(message = "Mã khách hàng không được để trống")
    String customerCode,

    @NotBlank(message = "Tên khách hàng không được để trống")
    String customerName,

    String subscriberNumber,
    
    String phoneNumber,

    @NotNull(message = "Số tiền phải thu không được để trống")
    BigDecimal amountDue,

    String province,
    String ward,
    String hamlet,
    String street,
    String fullAddress,
    String serviceType,
    
    String assignedConsultantUsername
) {}
