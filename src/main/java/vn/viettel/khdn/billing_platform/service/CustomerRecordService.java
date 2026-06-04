package vn.viettel.khdn.billing_platform.service;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.StoreConfig;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ResBillDataDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResCustomerRecordDTO;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.repository.StoreConfigRepository;
import vn.viettel.khdn.billing_platform.repository.BillingPeriodRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.model.BillingPeriod;
import vn.viettel.khdn.billing_platform.model.dto.ReqCreateCustomerRecordDTO;

@Service
public class CustomerRecordService {

    private final CustomerBillingRecordRepository recordRepository;
    private final StoreConfigRepository storeConfigRepository;
    private final BillingPeriodRepository billingPeriodRepository;
    private final UserRepository userRepository;

    public CustomerRecordService(CustomerBillingRecordRepository recordRepository,
                                 StoreConfigRepository storeConfigRepository,
                                 BillingPeriodRepository billingPeriodRepository,
                                 UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.storeConfigRepository = storeConfigRepository;
        this.billingPeriodRepository = billingPeriodRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tìm kiếm bản ghi có role-based access control:
     * - MANAGER: thấy tất cả
     * - CONSULTANT: chỉ thấy KH của mình
     * Filter độc lập theo collectionStatus và debtStatus.
     */
    public Page<CustomerBillingRecord> search(User currentUser,
                                               Long periodId,
                                               CollectionStatusEnum collectionStatus,
                                               DebtStatusEnum debtStatus,
                                               Long assignedUserId,
                                               String province, String ward,
                                               String hamlet, String street,
                                               String search, Pageable pageable) {
        if (currentUser.getRole() == RoleEnum.MANAGER) {
            return recordRepository.searchAll(
                periodId, collectionStatus, debtStatus, assignedUserId,
                province, ward, hamlet, street, search, pageable);
        } else {
            return recordRepository.searchByConsultant(
                currentUser.getId(), periodId, collectionStatus, debtStatus,
                province, ward, hamlet, street, search, pageable);
        }
    }

    public CustomerBillingRecord getById(Long id, User currentUser) {
        CustomerBillingRecord record = recordRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản ghi ID: " + id));

        // CONSULTANT chỉ xem được KH của mình
        if (currentUser.getRole() == RoleEnum.CONSULTANT) {
            if (record.getAssignedConsultant() == null
                || !record.getAssignedConsultant().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Bạn không có quyền xem bản ghi này");
            }
        }
        return record;
    }

    public CustomerBillingRecord createRecord(ReqCreateCustomerRecordDTO req, User currentUser) {
        BillingPeriod period = billingPeriodRepository.findById(req.billingPeriodId())
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kỳ ID: " + req.billingPeriodId()));

        CustomerBillingRecord record = new CustomerBillingRecord();
        record.setBillingPeriod(period);
        record.setCustomerCode(req.customerCode());
        record.setCustomerName(req.customerName());
        record.setSubscriberNumber(req.subscriberNumber());
        record.setPhoneNumber(req.phoneNumber());
        record.setAmountDue(req.amountDue());
        record.setProvince(req.province());
        record.setWard(req.ward());
        record.setHamlet(req.hamlet());
        record.setStreet(req.street());
        record.setFullAddress(req.fullAddress());
        record.setServiceType(req.serviceType());

        if (req.assignedConsultantUsername() != null && !req.assignedConsultantUsername().trim().isEmpty()) {
            User consultant = userRepository.findByUsername(req.assignedConsultantUsername().trim())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy nhân viên: " + req.assignedConsultantUsername()));
            record.setAssignedConsultant(consultant);
        }

        record.setCollectionStatus(CollectionStatusEnum.CHUA_THU);
        record.setDebtStatus(DebtStatusEnum.CHUA_GACH_NO);
        record.setSyncWarning(SyncWarningEnum.NONE);

        return recordRepository.save(record);
    }

    /**
     * Nút "In bill / Đã thanh toán":
     * Người thu thu tiền trực tiếp → in bill.
     * Chỉ cập nhật collectionStatus, KHÔNG thay đổi debtStatus.
     * collectionStatus: CHUA_THU → DA_THANH_TOAN
     */
    public CustomerBillingRecord printBill(Long id, java.math.BigDecimal collectedAmount,
                                            User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getCollectionStatus() != CollectionStatusEnum.CHUA_THU) {
            throw new IllegalStateException(
                "Chỉ có thể thu tiền khi trạng thái thu là CHƯA THU. Trạng thái hiện tại: "
                + record.getCollectionStatus());
        }

        record.setCollectionStatus(CollectionStatusEnum.DA_THANH_TOAN);
        record.setCollectedAmount(collectedAmount);
        record.setCollectedBy(currentUser);
        record.setCollectedAt(Instant.now());
        record.setBillPrintedAt(Instant.now());

        return recordRepository.save(record);
    }

    /**
     * Nút "Đã gạch nợ":
     * Người thu xác nhận đã gạch nợ thủ công trên hệ thống Viettel.
     * Chỉ cập nhật debtStatus, KHÔNG thay đổi collectionStatus.
     * Điều kiện: phải đã thu tiền trước (collectionStatus = DA_THANH_TOAN).
     * debtStatus: CHUA_GACH_NO → DA_GACH_NO
     */
    public CustomerBillingRecord markDebt(Long id, User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getCollectionStatus() == CollectionStatusEnum.CHUA_THU) {
            throw new IllegalStateException(
                "Không thể gạch nợ khi chưa thu tiền. Vui lòng thu tiền (in bill) trước.");
        }
        if (record.getDebtStatus() == DebtStatusEnum.DA_GACH_NO) {
            throw new IllegalStateException("Bản ghi này đã được gạch nợ rồi.");
        }

        record.setDebtStatus(DebtStatusEnum.DA_GACH_NO);
        record.setDebtMarkedBy(currentUser);
        record.setDebtMarkedAt(Instant.now());
        // Xóa cảnh báo nếu có
        record.setSyncWarning(SyncWarningEnum.NONE);
        record.setSyncWarningNote(null);

        return recordRepository.save(record);
    }

    /**
     * Gạch nợ tất cả trong kỳ
     */
    @org.springframework.transaction.annotation.Transactional
    public int markDebtByPeriod(Long periodId, User currentUser) {
        if (currentUser.getRole() == RoleEnum.MANAGER) {
            return recordRepository.markAllDebtByPeriodId(periodId, currentUser, Instant.now());
        } else {
            return recordRepository.markAllDebtByPeriodIdAndConsultant(periodId, currentUser, Instant.now(), currentUser.getId());
        }
    }

    /**
     * Lấy danh sách cảnh báo:
     * - DA_THANH_TOAN + CHUA_GACH_NO: đã thu tiền nhưng chưa gạch nợ Viettel
     * - INCONSISTENT / COLLECTED_NOT_MARKED từ import đối chiếu
     */
    public Page<CustomerBillingRecord> getWarnings(Long periodId, Pageable pageable) {
        return recordRepository.findWarningsByPeriod(periodId, pageable);
    }

    /**
     * Lấy dữ liệu đầy đủ để Mobile App render bill in.
     * Chỉ lấy được khi đã thu tiền (collectionStatus = DA_THANH_TOAN).
     */
    public ResBillDataDTO getBillData(Long id, User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getCollectionStatus() == CollectionStatusEnum.CHUA_THU) {
            throw new IllegalStateException("Chưa thu tiền, không thể lấy dữ liệu in bill");
        }

        StoreConfig store = storeConfigRepository.findFirstByOrderByIdAsc()
            .orElse(new StoreConfig()); // Trả về config rỗng nếu chưa cấu hình

        return new ResBillDataDTO(
            store.getStoreName(),
            store.getAddress(),
            store.getHotline(),
            record.getCustomerCode(),
            record.getCustomerName(),
            record.getSubscriberNumber(),
            record.getFullAddress(),
            record.getBillingPeriod() != null ? record.getBillingPeriod().getName() : "",
            record.getServiceType(),
            record.getAdsContent(),
            record.getAmountDue(),
            record.getCollectedAmount(),
            record.getCollectedAt(),
            record.getCollectedBy() != null ? record.getCollectedBy().getFullName() : "",
            record.getBillPrintedAt()
        );
    }

    /** Map entity → DTO */
    public ResCustomerRecordDTO toDTO(CustomerBillingRecord r) {
        return new ResCustomerRecordDTO(
            r.getId(),
            r.getBillingPeriod() != null ? r.getBillingPeriod().getId() : null,
            r.getBillingPeriod() != null ? r.getBillingPeriod().getName() : null,
            r.getCustomerCode(), r.getCustomerName(), r.getSubscriberNumber(),
            r.getPhoneNumber(), r.getAmountDue(),
            r.getProvince(), r.getWard(), r.getHamlet(), r.getStreet(), r.getFullAddress(),
            r.getServiceType(), r.getAdsContent(),
            r.getAssignedConsultant() != null ? r.getAssignedConsultant().getId() : null,
            r.getAssignedConsultant() != null ? r.getAssignedConsultant().getFullName() : null,
            r.getCollectionStatus(),
            r.getDebtStatus(),
            r.getCollectedAmount(),
            r.getCollectedBy() != null ? r.getCollectedBy().getFullName() : null,
            r.getCollectedAt(), r.getBillPrintedAt(),
            r.getDebtMarkedBy() != null ? r.getDebtMarkedBy().getFullName() : null,
            r.getDebtMarkedAt(),
            r.getSyncWarning(), r.getSyncWarningNote(),
            r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
