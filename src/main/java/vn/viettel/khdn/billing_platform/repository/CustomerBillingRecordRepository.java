package vn.viettel.khdn.billing_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.enums.BillingRecordStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;

public interface CustomerBillingRecordRepository extends JpaRepository<CustomerBillingRecord, Long> {

    // Tìm theo mã KH + kỳ (dùng khi import đối chiếu)
    Optional<CustomerBillingRecord> findByCustomerCodeAndBillingPeriodId(
            String customerCode, Long billingPeriodId);

    // Tìm theo số TB + kỳ (backup key khi import đối chiếu)
    Optional<CustomerBillingRecord> findBySubscriberNumberAndBillingPeriodId(
            String subscriberNumber, Long billingPeriodId);

    // Danh sách cảnh báo DA_IN_BILL chưa gạch nợ (dùng cho cuối ngày + warnings API)
    List<CustomerBillingRecord> findByBillingPeriodIdAndStatus(
            Long periodId, BillingRecordStatusEnum status);

    // Danh sách cảnh báo đồng bộ (TH3 import đối chiếu)
    List<CustomerBillingRecord> findByBillingPeriodIdAndSyncWarning(
            Long periodId, SyncWarningEnum syncWarning);

    // Tìm kiếm full-text + filter đa chiều (MANAGER xem tất cả)
    @Query("""
        SELECT r FROM CustomerBillingRecord r
        WHERE (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:status IS NULL OR r.status = :status)
          AND (:assignedUserId IS NULL OR r.assignedConsultant.id = :assignedUserId)
          AND (:province IS NULL OR LOWER(r.province) LIKE LOWER(CONCAT('%', :province, '%')))
          AND (:ward IS NULL OR LOWER(r.ward) LIKE LOWER(CONCAT('%', :ward, '%')))
          AND (:hamlet IS NULL OR LOWER(r.hamlet) LIKE LOWER(CONCAT('%', :hamlet, '%')))
          AND (:street IS NULL OR LOWER(r.street) LIKE LOWER(CONCAT('%', :street, '%')))
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    Page<CustomerBillingRecord> searchAll(
            @Param("periodId") Long periodId,
            @Param("status") BillingRecordStatusEnum status,
            @Param("assignedUserId") Long assignedUserId,
            @Param("province") String province,
            @Param("ward") String ward,
            @Param("hamlet") String hamlet,
            @Param("street") String street,
            @Param("search") String search,
            Pageable pageable);

    // CONSULTANT chỉ thấy KH của mình
    @Query("""
        SELECT r FROM CustomerBillingRecord r
        WHERE r.assignedConsultant.id = :consultantId
          AND (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:status IS NULL OR r.status = :status)
          AND (:province IS NULL OR LOWER(r.province) LIKE LOWER(CONCAT('%', :province, '%')))
          AND (:ward IS NULL OR LOWER(r.ward) LIKE LOWER(CONCAT('%', :ward, '%')))
          AND (:hamlet IS NULL OR LOWER(r.hamlet) LIKE LOWER(CONCAT('%', :hamlet, '%')))
          AND (:street IS NULL OR LOWER(r.street) LIKE LOWER(CONCAT('%', :street, '%')))
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    Page<CustomerBillingRecord> searchByConsultant(
            @Param("consultantId") Long consultantId,
            @Param("periodId") Long periodId,
            @Param("status") BillingRecordStatusEnum status,
            @Param("province") String province,
            @Param("ward") String ward,
            @Param("hamlet") String hamlet,
            @Param("street") String street,
            @Param("search") String search,
            Pageable pageable);

    // Thống kê tiến độ theo kỳ
    @Query("""
        SELECT r.status, COUNT(r), SUM(r.collectedAmount)
        FROM CustomerBillingRecord r
        WHERE r.billingPeriod.id = :periodId
        GROUP BY r.status
        """)
    List<Object[]> getProgressByPeriod(@Param("periodId") Long periodId);

    // Thống kê theo tư vấn viên trong kỳ
    @Query("""
        SELECT r.assignedConsultant.id, r.assignedConsultant.fullName,
               COUNT(r), SUM(r.collectedAmount)
        FROM CustomerBillingRecord r
        WHERE r.billingPeriod.id = :periodId
          AND r.status IN ('DA_IN_BILL', 'DA_GACH_NO')
        GROUP BY r.assignedConsultant.id, r.assignedConsultant.fullName
        """)
    List<Object[]> getConsultantPerformance(@Param("periodId") Long periodId);
}
