package vn.viettel.khdn.billing_platform.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.BillingPeriod;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ImportResultDTO;
import vn.viettel.khdn.billing_platform.model.enums.BillingPeriodStatusEnum;
import vn.viettel.khdn.billing_platform.repository.BillingPeriodRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.service.ImportService;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;

import java.util.List;

@RestController
@RequestMapping("/billing-periods")
public class BillingPeriodController {

    private final BillingPeriodRepository billingPeriodRepository;
    private final ImportService importService;
    private final UserRepository userRepository;

    public BillingPeriodController(BillingPeriodRepository billingPeriodRepository,
                                   ImportService importService,
                                   UserRepository userRepository) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.importService = importService;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
            .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    /** GET /billing-periods — Danh sách tất cả kỳ (Hỗ trợ phân trang) */
    @GetMapping
    public ResponseEntity<Page<BillingPeriod>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            safeSize,
            Sort.by(Sort.Order.desc("year"), Sort.Order.desc("month"))
        );
        return ResponseEntity.ok(billingPeriodRepository.findAll(pageable));
    }

    /** GET /billing-periods/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<BillingPeriod> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(
            billingPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kỳ ID: " + id)));
    }

    /**
     * POST /billing-periods/import — Import đầu kỳ (Manager only)
     * File: mau_import_dau_ky.xlsx
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('MANAGER')")
    public ResponseEntity<ImportResultDTO> importStartOfPeriod(
            @RequestParam("file") MultipartFile file) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(importService.importStartOfPeriod(file, currentUser));
    }

    /** PATCH /billing-periods/{id}/close — Đóng kỳ (Manager only) */
    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAuthority('MANAGER')")
    public ResponseEntity<BillingPeriod> closePeriod(@PathVariable("id") Long id) {
        BillingPeriod period = billingPeriodRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kỳ ID: " + id));

        if (period.getStatus() == BillingPeriodStatusEnum.CLOSED) {
            throw new IllegalStateException("Kỳ này đã được đóng");
        }
        period.setStatus(BillingPeriodStatusEnum.CLOSED);
        return ResponseEntity.ok(billingPeriodRepository.save(period));
    }
}
