package vn.viettel.khdn.billing_platform.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ImportResultDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqCreateCustomerRecordDTO;
import vn.viettel.khdn.billing_platform.model.dto.ReqPrintBillDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResBillDataDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResCustomerRecordDTO;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.service.CustomerRecordService;
import vn.viettel.khdn.billing_platform.service.ImportService;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/records")
public class CustomerRecordController {

    private final CustomerRecordService recordService;
    private final ImportService importService;
    private final UserRepository userRepository;

    public CustomerRecordController(CustomerRecordService recordService,
            ImportService importService,
            UserRepository userRepository) {
        this.recordService = recordService;
        this.importService = importService;
        this.userRepository = userRepository;
    }

    // Helper lấy user hiện tại
    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    /**
     * GET /records
     * MANAGER: xem tất cả | CONSULTANT: xem KH của mình
     * Query params: periodId, status, province, ward, hamlet, street, search, page,
     * size
     */
    @GetMapping
    public ResponseEntity<Page<ResCustomerRecordDTO>> getRecords(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "collectionStatus", required = false) CollectionStatusEnum collectionStatus,
            @RequestParam(value = "debtStatus", required = false) DebtStatusEnum debtStatus,
            @RequestParam(value = "assignedUserId", required = false) Long assignedUserId,
            @RequestParam(value = "billPrintedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billPrintedDate,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        User currentUser = getCurrentUser();
        Page<CustomerBillingRecord> records = recordService.search(
                currentUser, periodId, collectionStatus, debtStatus, assignedUserId,
                billPrintedDate, search,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(records.map(recordService::toDTO));
    }

    /**
     * GET /records/export
     * Xuất Excel dựa trên bộ lọc
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRecords(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "collectionStatus", required = false) CollectionStatusEnum collectionStatus,
            @RequestParam(value = "debtStatus", required = false) DebtStatusEnum debtStatus,
            @RequestParam(value = "assignedUserId", required = false) Long assignedUserId,
            @RequestParam(value = "billPrintedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billPrintedDate,
            @RequestParam(value = "search", required = false) String search) {

        User currentUser = getCurrentUser();
        byte[] data = recordService.exportExcel(currentUser, periodId, collectionStatus, debtStatus, assignedUserId,
                billPrintedDate, search);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=danh_sach_khach_hang.xlsx")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(data);
    }

    /**
     * POST /records
     * Thêm mới khách hàng thủ công
     */
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGER')")
    public ResponseEntity<ResCustomerRecordDTO> createRecord(
            @Valid @RequestBody ReqCreateCustomerRecordDTO req) {
        User currentUser = getCurrentUser();
        CustomerBillingRecord newRecord = recordService.createRecord(req, currentUser);
        return ResponseEntity.ok(recordService.toDTO(newRecord));
    }

    /** GET /records/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ResCustomerRecordDTO> getById(@PathVariable("id") Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(recordService.toDTO(recordService.getById(id, currentUser)));
    }

    /**
     * PATCH /records/{id}/print-bill
     * Thu tiền + in bill (hoặc xác nhận đã thanh toán): CHUA_THU → DA_THANH_TOAN
     */
    @PatchMapping("/{id}/print-bill")
    public ResponseEntity<ResCustomerRecordDTO> printBill(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReqPrintBillDTO req) {
        User currentUser = getCurrentUser();
        CustomerBillingRecord updated = recordService.printBill(id, req.collectedAmount(), currentUser);
        return ResponseEntity.ok(recordService.toDTO(updated));
    }

    /**
     * PATCH /records/{id}/mark-debt
     * Gạch nợ: DA_THANH_TOAN → DA_GACH_NO
     */
    @PatchMapping("/{id}/mark-debt")
    public ResponseEntity<ResCustomerRecordDTO> markDebt(@PathVariable("id") Long id) {
        User currentUser = getCurrentUser();
        CustomerBillingRecord updated = recordService.markDebt(id, currentUser);
        return ResponseEntity.ok(recordService.toDTO(updated));
    }

    /**
     * PATCH /records/bulk-mark-debt
     * Gạch nợ hàng loạt tất cả bản ghi trong kỳ (không phân biệt trạng thái thu)
     */
    @PatchMapping("/bulk-mark-debt")
    public ResponseEntity<java.util.Map<String, Object>> bulkMarkDebt(@RequestParam("periodId") Long periodId) {
        User currentUser = getCurrentUser();
        int count = recordService.markDebtByPeriod(periodId, currentUser);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Đã gạch nợ thành công " + count + " bản ghi trong kỳ",
                "updatedCount", count));
    }

    /**
     * PATCH /records/bulk-mark-debt/filter
     * Gạch nợ hàng loạt dựa trên bộ lọc
     */
    @PatchMapping("/bulk-mark-debt/filter")
    public ResponseEntity<java.util.Map<String, Object>> bulkMarkDebtWithFilter(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "collectionStatus", required = false) CollectionStatusEnum collectionStatus,
            @RequestParam(value = "debtStatus", required = false) DebtStatusEnum debtStatus,
            @RequestParam(value = "assignedUserId", required = false) Long assignedUserId,
            @RequestParam(value = "billPrintedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billPrintedDate,
            @RequestParam(value = "search", required = false) String search) {

        User currentUser = getCurrentUser();
        int count = recordService.bulkMarkDebtWithFilter(currentUser, periodId, collectionStatus, debtStatus,
                assignedUserId, billPrintedDate, search);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Đã gạch nợ thành công " + count + " bản ghi theo bộ lọc",
                "updatedCount", count));
    }

    /**
     * PATCH /records/bulk-pay/filter
     * Thanh toán hàng loạt dựa trên bộ lọc
     */
    @PatchMapping("/bulk-pay/filter")
    public ResponseEntity<java.util.Map<String, Object>> bulkPayWithFilter(
            @RequestParam(value = "periodId", required = false) Long periodId,
            @RequestParam(value = "collectionStatus", required = false) CollectionStatusEnum collectionStatus,
            @RequestParam(value = "debtStatus", required = false) DebtStatusEnum debtStatus,
            @RequestParam(value = "assignedUserId", required = false) Long assignedUserId,
            @RequestParam(value = "billPrintedDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billPrintedDate,
            @RequestParam(value = "search", required = false) String search) {

        User currentUser = getCurrentUser();
        int count = recordService.bulkPayWithFilter(currentUser, periodId, collectionStatus, debtStatus, assignedUserId,
                billPrintedDate, search);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Đã thanh toán thành công " + count + " bản ghi theo bộ lọc",
                "updatedCount", count));
    }

    /**
     * GET /records/{id}/bill-data
     * Dữ liệu đầy đủ để Mobile App render + in bill
     */
    @GetMapping("/{id}/bill-data")
    public ResponseEntity<ResBillDataDTO> getBillData(@PathVariable("id") Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(recordService.getBillData(id, currentUser));
    }

    /**
     * POST /records/import-reconciliation?periodId=...
     * Manager import file đối chiếu gạch nợ
     */
    @PostMapping("/import-reconciliation")
    @PreAuthorize("hasAuthority('MANAGER')")
    public ResponseEntity<ImportResultDTO> importReconciliation(
            @RequestParam("file") MultipartFile file,
            @RequestParam("periodId") Long periodId) {
        return ResponseEntity.ok(importService.importReconciliation(file, periodId));
    }

    @PreAuthorize("hasAuthority('MANAGER')")
    @GetMapping("/import-reconciliation/template")
    public ResponseEntity<byte[]> downloadReconciliationTemplate() {
        byte[] data = importService.generateReconciliationTemplate();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=mau_doi_chieu_viettel.xlsx")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(data);
    }

    /**
     * GET /records/warnings
     * Danh sách cảnh báo: DA_THANH_TOAN chưa gạch nợ + INCONSISTENT (Hỗ trợ phân
     * trang)
     */
    @GetMapping("/warnings")
    public ResponseEntity<Page<ResCustomerRecordDTO>> getWarnings(
            @RequestParam("periodId") Long periodId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), safeSize, Sort.by(Sort.Order.desc("updatedAt")));
        Page<CustomerBillingRecord> warnings = recordService.getWarnings(periodId, pageable);
        return ResponseEntity.ok(warnings.map(recordService::toDTO));
    }
}
