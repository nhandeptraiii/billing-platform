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
import vn.viettel.khdn.billing_platform.model.dto.ReqPrintBillDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResBillDataDTO;
import vn.viettel.khdn.billing_platform.model.dto.ResCustomerRecordDTO;
import vn.viettel.khdn.billing_platform.model.enums.BillingRecordStatusEnum;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.service.CustomerRecordService;
import vn.viettel.khdn.billing_platform.service.ImportService;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

import java.util.List;

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
        String email = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    /**
     * GET /records
     * MANAGER: xem tất cả | CONSULTANT: xem KH của mình
     * Query params: periodId, status, province, ward, hamlet, street, search, page, size
     */
    @GetMapping
    public ResponseEntity<Page<ResCustomerRecordDTO>> getRecords(
            @RequestParam(required = false) Long periodId,
            @RequestParam(required = false) BillingRecordStatusEnum status,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String hamlet,
            @RequestParam(required = false) String street,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User currentUser = getCurrentUser();
        Page<CustomerBillingRecord> records = recordService.search(
            currentUser, periodId, status,
            province, ward, hamlet, street, search,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(records.map(recordService::toDTO));
    }

    /** GET /records/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ResCustomerRecordDTO> getById(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(recordService.toDTO(recordService.getById(id, currentUser)));
    }

    /**
     * PATCH /records/{id}/print-bill
     * Thu tiền + in bill: CHUA_THU → DA_IN_BILL
     */
    @PatchMapping("/{id}/print-bill")
    public ResponseEntity<ResCustomerRecordDTO> printBill(
            @PathVariable Long id,
            @Valid @RequestBody ReqPrintBillDTO req) {
        User currentUser = getCurrentUser();
        CustomerBillingRecord updated = recordService.printBill(id, req.collectedAmount(), currentUser);
        return ResponseEntity.ok(recordService.toDTO(updated));
    }

    /**
     * PATCH /records/{id}/mark-debt
     * Gạch nợ: DA_IN_BILL → DA_GACH_NO
     */
    @PatchMapping("/{id}/mark-debt")
    public ResponseEntity<ResCustomerRecordDTO> markDebt(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        CustomerBillingRecord updated = recordService.markDebt(id, currentUser);
        return ResponseEntity.ok(recordService.toDTO(updated));
    }

    /**
     * GET /records/{id}/bill-data
     * Dữ liệu đầy đủ để Mobile App render + in bill
     */
    @GetMapping("/{id}/bill-data")
    public ResponseEntity<ResBillDataDTO> getBillData(@PathVariable Long id) {
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
            @RequestParam Long periodId) {
        return ResponseEntity.ok(importService.importReconciliation(file, periodId));
    }

    /**
     * GET /records/warnings?periodId=...
     * Danh sách cảnh báo: DA_IN_BILL chưa gạch nợ + INCONSISTENT
     */
    @GetMapping("/warnings")
    public ResponseEntity<List<ResCustomerRecordDTO>> getWarnings(
            @RequestParam Long periodId) {
        List<CustomerBillingRecord> warnings = recordService.getWarnings(periodId);
        return ResponseEntity.ok(warnings.stream().map(recordService::toDTO).toList());
    }
}
