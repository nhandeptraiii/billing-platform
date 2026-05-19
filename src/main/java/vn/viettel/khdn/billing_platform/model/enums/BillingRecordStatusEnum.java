package vn.viettel.khdn.billing_platform.model.enums;

public enum BillingRecordStatusEnum {
    CHUA_THU,   // Chưa thu tiền
    DA_IN_BILL, // Đã thu tiền + in bill, chưa cập nhật hệ thống Viettel
    DA_GACH_NO  // Đã gạch nợ trên hệ thống Viettel
}
