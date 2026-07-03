package vn.viettel.khdn.billing_platform.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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

    private boolean hasEnoughCollectedAmount(CustomerBillingRecord record) {
        BigDecimal collectedAmount = record.getCollectedAmount();
        if (collectedAmount == null) return false;
        BigDecimal amountDue = record.getAmountDue() != null ? record.getAmountDue() : BigDecimal.ZERO;
        return collectedAmount.compareTo(amountDue) >= 0;
    }

    private boolean canBulkMarkDebt(CustomerBillingRecord record, User currentUser, Long regionId) {
        if (currentUser.getRole() == RoleEnum.ADMIN) return true;
        if (currentUser.getRole() == RoleEnum.MANAGER) {
            return record.getRegion() != null && regionId != null && regionId.equals(record.getRegion().getId());
        }
        return record.getAssignedConsultant() != null
            && record.getAssignedConsultant().getId().equals(currentUser.getId());
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
                                               LocalDate billPrintedDate,
                                               String subscriberNumber, String customerName, String fullAddress, String search, Pageable pageable) {
        Instant startOfDay = null;
        Instant endOfDay = null;
        if (billPrintedDate != null) {
            startOfDay = billPrintedDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            endOfDay = billPrintedDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }

        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);

        if (currentUser.getRole() == RoleEnum.MANAGER || currentUser.getRole() == RoleEnum.ADMIN) {
            return recordRepository.searchAll(
                periodId, regionId, collectionStatus, debtStatus, assignedUserId,
                startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search, pageable);
        } else {
            return recordRepository.searchByConsultant(
                currentUser.getId(), periodId, collectionStatus, debtStatus,
                startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search, pageable);
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
        } else if (currentUser.getRole() == RoleEnum.MANAGER) {
            if (record.getRegion() == null || currentUser.getRegion() == null || !record.getRegion().getId().equals(currentUser.getRegion().getId())) {
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
        record.setRegion(currentUser.getRole() == RoleEnum.ADMIN ? null : currentUser.getRegion());

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
        if (!hasEnoughCollectedAmount(record)) {
            throw new IllegalStateException("Không thể gạch nợ vì số tiền đã thu nhỏ hơn tổng cước.");
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
        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        List<CustomerBillingRecord> records = recordRepository.findAllByBillingPeriodId(periodId);
        List<CustomerBillingRecord> changedRecords = new ArrayList<>();
        Instant now = Instant.now();

        for (CustomerBillingRecord record : records) {
            if (!canBulkMarkDebt(record, currentUser, regionId)) continue;
            if (record.getDebtStatus() == DebtStatusEnum.DA_GACH_NO) continue;
            if (!hasEnoughCollectedAmount(record)) continue;

            record.setDebtStatus(DebtStatusEnum.DA_GACH_NO);
            record.setDebtMarkedBy(currentUser);
            record.setDebtMarkedAt(now);
            record.setSyncWarning(SyncWarningEnum.NONE);
            record.setSyncWarningNote(null);
            changedRecords.add(record);
        }

        if (!changedRecords.isEmpty()) {
            recordRepository.saveAll(changedRecords);
        }
        return changedRecords.size();
    }

    @org.springframework.transaction.annotation.Transactional
    public int bulkMarkDebtWithFilter(User currentUser, Long periodId, CollectionStatusEnum collectionStatus,
                                      DebtStatusEnum debtStatus, Long assignedUserId,
                                      LocalDate billPrintedDate, String subscriberNumber, String customerName, String fullAddress, String search) {
        Instant startOfDay = null;
        Instant endOfDay = null;
        if (billPrintedDate != null) {
            startOfDay = billPrintedDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            endOfDay = billPrintedDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }

        List<Long> ids;
        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        if (currentUser.getRole() == RoleEnum.MANAGER || currentUser.getRole() == RoleEnum.ADMIN) {
            ids = recordRepository.findAllIdsAll(periodId, regionId, collectionStatus, debtStatus, assignedUserId, startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search);
        } else {
            ids = recordRepository.findAllIdsByConsultant(currentUser.getId(), periodId, collectionStatus, debtStatus, startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search);
        }

        if (ids.isEmpty()) return 0;

        List<CustomerBillingRecord> records = recordRepository.findAllById(ids);
        List<CustomerBillingRecord> changedRecords = new ArrayList<>();
        Instant now = Instant.now();
        for (CustomerBillingRecord record : records) {
            if (!canBulkMarkDebt(record, currentUser, regionId)) continue;
            if (!hasEnoughCollectedAmount(record)) continue;
            if (record.getDebtStatus() != DebtStatusEnum.DA_GACH_NO) {
                record.setDebtStatus(DebtStatusEnum.DA_GACH_NO);
                record.setDebtMarkedBy(currentUser);
                record.setDebtMarkedAt(now);
                record.setSyncWarning(SyncWarningEnum.NONE);
                record.setSyncWarningNote(null);
                changedRecords.add(record);
            }
        }
        if (!changedRecords.isEmpty()) {
            recordRepository.saveAll(changedRecords);
        }
        return changedRecords.size();
    }

    @org.springframework.transaction.annotation.Transactional
    public int bulkPayWithFilter(User currentUser, Long periodId, CollectionStatusEnum collectionStatus,
                                 DebtStatusEnum debtStatus, Long assignedUserId,
                                 LocalDate billPrintedDate, String subscriberNumber, String customerName, String fullAddress, String search) {
        Instant startOfDay = null;
        Instant endOfDay = null;
        if (billPrintedDate != null) {
            startOfDay = billPrintedDate.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
            endOfDay = billPrintedDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        }

        List<Long> ids;
        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        if (currentUser.getRole() == RoleEnum.MANAGER || currentUser.getRole() == RoleEnum.ADMIN) {
            ids = recordRepository.findAllIdsAll(periodId, regionId, collectionStatus, debtStatus, assignedUserId, startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search);
        } else {
            ids = recordRepository.findAllIdsByConsultant(currentUser.getId(), periodId, collectionStatus, debtStatus, startOfDay, endOfDay, subscriberNumber, customerName, fullAddress, search);
        }

        if (ids.isEmpty()) return 0;

        List<CustomerBillingRecord> records = recordRepository.findAllById(ids);
        int updatedCount = 0;
        Instant now = Instant.now();
        for (CustomerBillingRecord record : records) {
            if (record.getCollectionStatus() != CollectionStatusEnum.DA_THANH_TOAN) {
                record.setCollectionStatus(CollectionStatusEnum.DA_THANH_TOAN);
                record.setCollectedAmount(record.getAmountDue()); // Thu bằng số phải thu
                record.setCollectedBy(currentUser);
                record.setCollectedAt(now);
                record.setBillPrintedAt(now);
                updatedCount++;
            }
        }
        recordRepository.saveAll(records);
        return updatedCount;
    }

    /**
     * Lấy danh sách cảnh báo:
     * - DA_THANH_TOAN + CHUA_GACH_NO: đã thu tiền nhưng chưa gạch nợ Viettel
     * - INCONSISTENT / COLLECTED_NOT_MARKED từ import đối chiếu
     */
    public Page<CustomerBillingRecord> getWarnings(Long periodId, User currentUser, Pageable pageable) {
        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        return recordRepository.findWarningsByPeriod(periodId, regionId, pageable);
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

        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        StoreConfig store;
        if (regionId != null) {
            store = storeConfigRepository.findByRegionId(regionId).orElse(new StoreConfig());
        } else {
            store = storeConfigRepository.findFirstByOrderByIdAsc().orElse(new StoreConfig());
        }

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
            r.getFullAddress(),
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

    public byte[] exportExcel(User currentUser, Long periodId, CollectionStatusEnum collectionStatus,
                              DebtStatusEnum debtStatus, Long assignedUserId, LocalDate billPrintedDate, String subscriberNumber, String customerName, String fullAddress, String search) {
        // Lấy tất cả records dựa theo bộ lọc (không phân trang) bằng cách gọi search với size max
        Page<CustomerBillingRecord> pageResult = search(currentUser, periodId, collectionStatus, debtStatus,
            assignedUserId, billPrintedDate, subscriberNumber, customerName, fullAddress, search, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE));
        
        List<CustomerBillingRecord> records = pageResult.getContent();
        
        try (Workbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sách khách hàng");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Mã KH", "Tên KH", "Số điện thoại", "Số TB", "Tiền phải thu", "Tiền thực thu", "Ngày in bill", "Trạng thái thu", "Trạng thái gạch nợ", "Nhân viên"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

            int rowIdx = 1;
            for (CustomerBillingRecord r : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getCustomerCode() != null ? r.getCustomerCode() : "");
                row.createCell(1).setCellValue(r.getCustomerName() != null ? r.getCustomerName() : "");
                row.createCell(2).setCellValue(r.getPhoneNumber() != null ? r.getPhoneNumber() : "");
                row.createCell(3).setCellValue(r.getSubscriberNumber() != null ? r.getSubscriberNumber() : "");
                row.createCell(4).setCellValue(r.getAmountDue() != null ? r.getAmountDue().doubleValue() : 0);
                row.createCell(5).setCellValue(r.getCollectedAmount() != null ? r.getCollectedAmount().doubleValue() : 0);
                
                String printedDate = r.getBillPrintedAt() != null ? formatter.format(r.getBillPrintedAt()) : "";
                row.createCell(6).setCellValue(printedDate);
                
                row.createCell(7).setCellValue(r.getCollectionStatus() != null ? r.getCollectionStatus().name() : "");
                row.createCell(8).setCellValue(r.getDebtStatus() != null ? r.getDebtStatus().name() : "");
                
                String consultant = r.getAssignedConsultant() != null ? r.getAssignedConsultant().getFullName() : "";
                row.createCell(9).setCellValue(consultant);
            }
            
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file Excel: " + e.getMessage(), e);
        }
    }
}
