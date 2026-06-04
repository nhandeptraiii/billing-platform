package vn.viettel.khdn.billing_platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;

public interface CustomerBillingRecordRepository extends JpaRepository<CustomerBillingRecord, Long> {

    // Tìm theo mã KH + kỳ (dùng khi import đối chiếu)
    Optional<CustomerBillingRecord> findByCustomerCodeAndBillingPeriodId(
            String customerCode, Long billingPeriodId);

    // Tìm theo số TB + kỳ (backup key khi import đối chiếu)
    Optional<CustomerBillingRecord> findBySubscriberNumberAndBillingPeriodId(
            String subscriberNumber, Long billingPeriodId);

    // Scheduler cuối ngày: tìm bản ghi DA_THANH_TOAN nhưng chưa gạch nợ
    List<CustomerBillingRecord> findByBillingPeriodIdAndCollectionStatusAndDebtStatus(
            Long periodId, CollectionStatusEnum collectionStatus, DebtStatusEnum debtStatus);

    // Danh sách cảnh báo đồng bộ (TH import đối chiếu)
    List<CustomerBillingRecord> findByBillingPeriodIdAndSyncWarning(
            Long periodId, SyncWarningEnum syncWarning);

    // Cảnh báo: DA_THANH_TOAN chưa gạch nợ + INCONSISTENT (dùng cho warnings API)
    @Query("""
        SELECT r FROM CustomerBillingRecord r
        WHERE r.billingPeriod.id = :periodId
          AND (
            (r.collectionStatus = 'DA_THANH_TOAN' AND r.debtStatus = 'CHUA_GACH_NO')
            OR r.syncWarning = 'INCONSISTENT'
            OR r.syncWarning = 'COLLECTED_NOT_MARKED'
          )
        """)
    Page<CustomerBillingRecord> findWarningsByPeriod(
            @Param("periodId") Long periodId,
            Pageable pageable);


    // Tìm kiếm full-text + filter đa chiều (MANAGER xem tất cả)
    @Query("""
        SELECT r FROM CustomerBillingRecord r
        LEFT JOIN r.assignedConsultant c
        WHERE (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:collectionStatus IS NULL OR r.collectionStatus = :collectionStatus)
          AND (:debtStatus IS NULL OR r.debtStatus = :debtStatus)
          AND (:assignedUserId IS NULL OR c.id = :assignedUserId)
          AND (:startOfDay IS NULL OR r.billPrintedAt >= :startOfDay)
          AND (:endOfDay IS NULL OR r.billPrintedAt < :endOfDay)
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    Page<CustomerBillingRecord> searchAll(
            @Param("periodId") Long periodId,
            @Param("collectionStatus") CollectionStatusEnum collectionStatus,
            @Param("debtStatus") DebtStatusEnum debtStatus,
            @Param("assignedUserId") Long assignedUserId,
            @Param("startOfDay") java.time.Instant startOfDay,
            @Param("endOfDay") java.time.Instant endOfDay,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
        SELECT r.id FROM CustomerBillingRecord r
        LEFT JOIN r.assignedConsultant c
        WHERE (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:collectionStatus IS NULL OR r.collectionStatus = :collectionStatus)
          AND (:debtStatus IS NULL OR r.debtStatus = :debtStatus)
          AND (:assignedUserId IS NULL OR c.id = :assignedUserId)
          AND (:startOfDay IS NULL OR r.billPrintedAt >= :startOfDay)
          AND (:endOfDay IS NULL OR r.billPrintedAt < :endOfDay)
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    List<Long> findAllIdsAll(
            @Param("periodId") Long periodId,
            @Param("collectionStatus") CollectionStatusEnum collectionStatus,
            @Param("debtStatus") DebtStatusEnum debtStatus,
            @Param("assignedUserId") Long assignedUserId,
            @Param("startOfDay") java.time.Instant startOfDay,
            @Param("endOfDay") java.time.Instant endOfDay,
            @Param("search") String search);

    // CONSULTANT chỉ thấy KH của mình
    @Query("""
        SELECT r FROM CustomerBillingRecord r
        WHERE r.assignedConsultant.id = :consultantId
          AND (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:collectionStatus IS NULL OR r.collectionStatus = :collectionStatus)
          AND (:debtStatus IS NULL OR r.debtStatus = :debtStatus)
          AND (:startOfDay IS NULL OR r.billPrintedAt >= :startOfDay)
          AND (:endOfDay IS NULL OR r.billPrintedAt < :endOfDay)
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    Page<CustomerBillingRecord> searchByConsultant(
            @Param("consultantId") Long consultantId,
            @Param("periodId") Long periodId,
            @Param("collectionStatus") CollectionStatusEnum collectionStatus,
            @Param("debtStatus") DebtStatusEnum debtStatus,
            @Param("startOfDay") java.time.Instant startOfDay,
            @Param("endOfDay") java.time.Instant endOfDay,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
        SELECT r.id FROM CustomerBillingRecord r
        WHERE r.assignedConsultant.id = :consultantId
          AND (:periodId IS NULL OR r.billingPeriod.id = :periodId)
          AND (:collectionStatus IS NULL OR r.collectionStatus = :collectionStatus)
          AND (:debtStatus IS NULL OR r.debtStatus = :debtStatus)
          AND (:startOfDay IS NULL OR r.billPrintedAt >= :startOfDay)
          AND (:endOfDay IS NULL OR r.billPrintedAt < :endOfDay)
          AND (:search IS NULL OR
               LOWER(r.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               r.customerCode LIKE CONCAT('%', :search, '%') OR
               r.subscriberNumber LIKE CONCAT('%', :search, '%') OR
               r.phoneNumber LIKE CONCAT('%', :search, '%'))
        """)
    List<Long> findAllIdsByConsultant(
            @Param("consultantId") Long consultantId,
            @Param("periodId") Long periodId,
            @Param("collectionStatus") CollectionStatusEnum collectionStatus,
            @Param("debtStatus") DebtStatusEnum debtStatus,
            @Param("startOfDay") java.time.Instant startOfDay,
            @Param("endOfDay") java.time.Instant endOfDay,
            @Param("search") String search);

    // Thống kê tiến độ theo kỳ
    @Query("""
        SELECT r.collectionStatus, r.debtStatus, COUNT(r), SUM(r.amountDue), SUM(r.collectedAmount)
        FROM CustomerBillingRecord r
        WHERE r.billingPeriod.id = :periodId
        GROUP BY r.collectionStatus, r.debtStatus
        """)
    List<Object[]> getProgressByPeriod(@Param("periodId") Long periodId);

    // Thống kê theo tư vấn viên trong kỳ (kèm chỉ tiêu)
    @Query("""
        SELECT r.assignedConsultant.id, r.assignedConsultant.fullName,
               COUNT(r), SUM(r.amountDue),
               SUM(CASE WHEN r.collectionStatus = 'DA_THANH_TOAN' THEN 1 ELSE 0 END),
               SUM(CASE WHEN r.collectionStatus = 'DA_THANH_TOAN' THEN r.collectedAmount ELSE 0 END)
        FROM CustomerBillingRecord r
        WHERE r.billingPeriod.id = :periodId
        GROUP BY r.assignedConsultant.id, r.assignedConsultant.fullName
        """)
    List<Object[]> getConsultantPerformanceWithTarget(@Param("periodId") Long periodId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE CustomerBillingRecord r
        SET r.debtStatus = 'DA_GACH_NO',
            r.debtMarkedBy = :user,
            r.debtMarkedAt = :now,
            r.syncWarning = 'NONE',
            r.syncWarningNote = NULL
        WHERE r.billingPeriod.id = :periodId
          AND r.debtStatus != 'DA_GACH_NO'
        """)
    int markAllDebtByPeriodId(@Param("periodId") Long periodId, @Param("user") vn.viettel.khdn.billing_platform.model.User user, @Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE CustomerBillingRecord r
        SET r.debtStatus = 'DA_GACH_NO',
            r.debtMarkedBy = :user,
            r.debtMarkedAt = :now,
            r.syncWarning = 'NONE',
            r.syncWarningNote = NULL
        WHERE r.billingPeriod.id = :periodId
          AND r.assignedConsultant.id = :consultantId
          AND r.debtStatus != 'DA_GACH_NO'
        """)
    int markAllDebtByPeriodIdAndConsultant(@Param("periodId") Long periodId, @Param("user") vn.viettel.khdn.billing_platform.model.User user, @Param("now") java.time.Instant now, @Param("consultantId") Long consultantId);

    // Thống kê giờ in bill đầu tiên và số lượng thu trong ngày của các tư vấn viên
    @Query("""
        SELECT r.assignedConsultant.id, r.assignedConsultant.fullName,
               MIN(r.billPrintedAt),
               COUNT(r)
        FROM CustomerBillingRecord r
        WHERE r.collectedAt >= :startOfDay AND r.collectedAt < :endOfDay
          AND r.collectionStatus = 'DA_THANH_TOAN'
        GROUP BY r.assignedConsultant.id, r.assignedConsultant.fullName
        """)
    List<Object[]> getConsultantDailyStats(@Param("startOfDay") java.time.Instant startOfDay, @Param("endOfDay") java.time.Instant endOfDay);
}
