package vn.viettel.khdn.billing_platform.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;

/**
 * Entity trung tâm: Bản ghi thu cước của một khách hàng trong một kỳ.
 * Lưu snapshot thông tin KH từ file import — không cần bảng Customer riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "customer_billing_records", indexes = {
    @Index(name = "idx_period_id", columnList = "billing_period_id"),
    @Index(name = "idx_assigned_consultant", columnList = "assigned_consultant_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_customer_code", columnList = "customerCode"),
    @Index(name = "idx_subscriber_number", columnList = "subscriberNumber")
})
public class CustomerBillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Kỳ thanh toán ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_period_id", nullable = false)
    private BillingPeriod billingPeriod;

    // ---- Thông tin khách hàng (snapshot từ file import) ----
    @Column(length = 50)
    private String customerCode;        // Mã KH

    @Column(length = 200)
    private String customerName;        // Tên KH

    @Column(length = 50)
    private String subscriberNumber;    // Số TB / account

    @Column(length = 20)
    private String phoneNumber;         // Số điện thoại

    @Column(precision = 15, scale = 0)
    private BigDecimal amountDue;       // Số tiền phải thu

    // ---- Địa chỉ (phục vụ filter theo khu vực) ----
    @Column(length = 100)
    private String province;            // Tỉnh/Thành phố

    @Column(length = 100)
    private String ward;                // Xã/Phường

    @Column(length = 100)
    private String hamlet;              // Ấp/Khu vực

    @Column(length = 200)
    private String street;              // Tuyến đường

    @Column(length = 500)
    private String fullAddress;         // Địa chỉ đầy đủ

    @Column(length = 200)
    private String serviceType;         // Loại dịch vụ (VD: CƯỚC VIỄN THÔNG, KỲ HÒA ĐƠN)

    @Column(length = 1000)
    private String adsContent;          // Nội dung quảng cáo


    // ---- Phân công ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_consultant_id")
    private User assignedConsultant;    // Tư vấn viên phụ trách

    // ---- Trạng thái thu tiền (người thu cước) ----
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private CollectionStatusEnum collectionStatus = CollectionStatusEnum.CHUA_THU;

    // ---- Trạng thái gạch nợ (hệ thống Viettel) ----
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DebtStatusEnum debtStatus = DebtStatusEnum.CHUA_GACH_NO;

    // ---- Thông tin thu tiền (điền khi bấm "In bill") ----
    @Column(precision = 15, scale = 0)
    private BigDecimal collectedAmount; // Số tiền thực thu

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by_id")
    private User collectedBy;           // Người thu tiền

    private Instant collectedAt;        // Thời điểm thu tiền

    private Instant billPrintedAt;      // Thời điểm in bill

    // ---- Thông tin gạch nợ (điền khi bấm "Đã gạch nợ") ----
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_marked_by_id")
    private User debtMarkedBy;          // Người gạch nợ

    private Instant debtMarkedAt;       // Thời điểm gạch nợ

    // ---- Cảnh báo đồng bộ (kết quả import đối chiếu) ----
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SyncWarningEnum syncWarning = SyncWarningEnum.NONE;

    @Column(length = 500)
    private String syncWarningNote;     // Ghi chú cảnh báo chi tiết

    // ---- Audit ----
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void handleBeforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
