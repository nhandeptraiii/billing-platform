package vn.viettel.khdn.billing_platform.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;

public record ResCustomerRecordDTO(
    Long id,
    Long billingPeriodId,
    String billingPeriodName,

    // Thông tin KH
    String customerCode,
    String customerName,
    String subscriberNumber,
    String phoneNumber,
    BigDecimal amountDue,

    // Địa chỉ
    String province,
    String ward,
    String hamlet,
    String street,
    String fullAddress,
    
    // Dịch vụ và QC
    String serviceType,
    String adsContent,

    // Phân công
    Long assignedConsultantId,
    String assignedConsultantName,

    // Trạng thái
    CollectionStatusEnum collectionStatus,   // Trạng thái thu tiền
    DebtStatusEnum debtStatus,               // Trạng thái gạch nợ Viettel

    // Thu tiền
    BigDecimal collectedAmount,
    String collectedByName,
    Instant collectedAt,
    Instant billPrintedAt,

    // Gạch nợ
    String debtMarkedByName,
    Instant debtMarkedAt,

    // Cảnh báo
    SyncWarningEnum syncWarning,
    String syncWarningNote,

    Instant createdAt,
    Instant updatedAt
) {}
