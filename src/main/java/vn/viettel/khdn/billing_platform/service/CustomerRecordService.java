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
import vn.viettel.khdn.billing_platform.model.enums.BillingRecordStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.repository.StoreConfigRepository;

@Service
public class CustomerRecordService {

    private final CustomerBillingRecordRepository recordRepository;
    private final StoreConfigRepository storeConfigRepository;

    public CustomerRecordService(CustomerBillingRecordRepository recordRepository,
                                 StoreConfigRepository storeConfigRepository) {
        this.recordRepository = recordRepository;
        this.storeConfigRepository = storeConfigRepository;
    }

    /**
     * Tìm kiếm bản ghi có role-based access control:
     * - MANAGER: thấy tất cả
     * - CONSULTANT: chỉ thấy KH của mình
     */
    public Page<CustomerBillingRecord> search(User currentUser,
                                               Long periodId,
                                               BillingRecordStatusEnum status,
                                               String province, String ward,
                                               String hamlet, String street,
                                               String search, Pageable pageable) {
        if (currentUser.getRole() == RoleEnum.MANAGER) {
            return recordRepository.searchAll(
                periodId, status, null,
                province, ward, hamlet, street, search, pageable);
        } else {
            return recordRepository.searchByConsultant(
                currentUser.getId(), periodId, status,
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

    /**
     * Bước 2: Tư vấn viên thu tiền → In bill
     * Status: CHUA_THU → DA_IN_BILL
     */
    public CustomerBillingRecord printBill(Long id, java.math.BigDecimal collectedAmount,
                                            User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getStatus() != BillingRecordStatusEnum.CHUA_THU) {
            throw new IllegalStateException(
                "Chỉ có thể in bill khi trạng thái là CHƯA THU. Trạng thái hiện tại: "
                + record.getStatus());
        }

        record.setStatus(BillingRecordStatusEnum.DA_IN_BILL);
        record.setCollectedAmount(collectedAmount);
        record.setCollectedBy(currentUser);
        record.setCollectedAt(Instant.now());
        record.setBillPrintedAt(Instant.now());

        return recordRepository.save(record);
    }

    /**
     * Bước 3: Tư vấn viên xác nhận đã gạch nợ trên hệ thống Viettel (thủ công)
     * Status: CHUA_THU hoặc DA_IN_BILL → DA_GACH_NO
     * Lưu ý: có thể bấm trực tiếp mà không cần qua bước in bill
     */
    public CustomerBillingRecord markDebt(Long id, User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getStatus() == BillingRecordStatusEnum.DA_GACH_NO) {
            throw new IllegalStateException("Bản ghi này đã được gạch nợ rồi.");
        }

        record.setStatus(BillingRecordStatusEnum.DA_GACH_NO);
        record.setDebtMarkedBy(currentUser);
        record.setDebtMarkedAt(Instant.now());
        // Xóa cảnh báo nếu có
        record.setSyncWarning(SyncWarningEnum.NONE);
        record.setSyncWarningNote(null);

        return recordRepository.save(record);
    }


    /**
     * Bước 5: Lấy danh sách cảnh báo (DA_IN_BILL chưa gạch nợ + INCONSISTENT)
     */
    public Page<CustomerBillingRecord> getWarnings(Long periodId, Pageable pageable) {
        return recordRepository.findWarningsByPeriod(periodId, pageable);
    }

    /**
     * Lấy dữ liệu đầy đủ để Mobile App render bill in.
     */
    public ResBillDataDTO getBillData(Long id, User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getStatus() == BillingRecordStatusEnum.CHUA_THU) {
            throw new IllegalStateException("Chưa thu tiền, không thể lấy dữ liệu in bill");
        }

        StoreConfig store = storeConfigRepository.findFirstByOrderByIdAsc()
            .orElse(new StoreConfig()); // Trả về config rỗng nếu chưa cấu hình

        String qrUrl = store.getQrImagePath() != null
            ? "/uploads/" + store.getQrImagePath()
            : null;

        return new ResBillDataDTO(
            store.getStoreName(),
            store.getAddress(),
            store.getHotline(),
            qrUrl,
            store.getAdsText(),
            record.getCustomerCode(),
            record.getCustomerName(),
            record.getSubscriberNumber(),
            record.getFullAddress(),
            record.getBillingPeriod() != null ? record.getBillingPeriod().getName() : "",
            record.getAmountDue(),
            record.getCollectedAmount(),
            record.getCollectedAt(),
            record.getCollectedBy() != null ? record.getCollectedBy().getFullName() : ""
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
            r.getAssignedConsultant() != null ? r.getAssignedConsultant().getId() : null,
            r.getAssignedConsultant() != null ? r.getAssignedConsultant().getFullName() : null,
            r.getStatus(),
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
