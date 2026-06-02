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

        record.setStatus(BillingRecordStatusEnum.CHUA_THU);
        record.setSyncWarning(SyncWarningEnum.NONE);

        return recordRepository.save(record);
    }

    /**
     * Bước 2: Người thu thu tiền → In bill (hoặc xác nhận đã thanh toán)
     * Status: CHUA_THU → DA_THANH_TOAN
     * Hành động "In bill" và "Đã thanh toán" được gộp thành 1 bước.
     */
    public CustomerBillingRecord printBill(Long id, java.math.BigDecimal collectedAmount,
                                            User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getStatus() != BillingRecordStatusEnum.CHUA_THU) {
            throw new IllegalStateException(
                "Chỉ có thể thu tiền khi trạng thái là CHƯA THU. Trạng thái hiện tại: "
                + record.getStatus());
        }

        record.setStatus(BillingRecordStatusEnum.DA_THANH_TOAN);
        record.setCollectedAmount(collectedAmount);
        record.setCollectedBy(currentUser);
        record.setCollectedAt(Instant.now());
        record.setBillPrintedAt(Instant.now());

        return recordRepository.save(record);
    }

    /**
     * Bước 3: Người thu xác nhận đã gạch nợ trên hệ thống Viettel (thủ công)
     * Status: DA_THANH_TOAN → DA_GACH_NO
     * Lưu ý: phải đã thanh toán trước mới được gạch nợ.
     */
    public CustomerBillingRecord markDebt(Long id, User currentUser) {
        CustomerBillingRecord record = getById(id, currentUser);

        if (record.getStatus() == BillingRecordStatusEnum.CHUA_THU) {
            throw new IllegalStateException(
                "Không thể gạch nợ khi chưa thu tiền. Vui lòng thu tiền (in bill) trước.");
        }
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
     * Bước 5: Lấy danh sách cảnh báo (DA_THANH_TOAN chưa gạch nợ + INCONSISTENT)
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
            record.getServiceType(),
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
