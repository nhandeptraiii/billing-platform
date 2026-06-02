package vn.viettel.khdn.billing_platform.model.enums;

public enum BillingRecordStatusEnum {
    CHUA_THU,       // Chưa thu tiền
    DA_THANH_TOAN,  // Đã thu tiền (in bill hoặc xác nhận thanh toán), chưa gạch nợ trên hệ thống Viettel
    DA_GACH_NO      // Đã gạch nợ trên hệ thống Viettel
}
