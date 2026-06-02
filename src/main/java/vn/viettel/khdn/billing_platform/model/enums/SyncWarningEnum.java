package vn.viettel.khdn.billing_platform.model.enums;

public enum SyncWarningEnum {
    NONE,                 // Không có cảnh báo
    INCONSISTENT,         // Hệ thống ghi DA_GACH_NO nhưng file Viettel CHƯA ghi nhận → NV bấm nhầm?
    COLLECTED_NOT_MARKED  // Đã thu (DA_THANH_TOAN) nhưng file Viettel chưa gạch nợ → NV chưa gạch nợ trên Viettel
}
