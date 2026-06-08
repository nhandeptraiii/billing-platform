package vn.viettel.khdn.billing_platform.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantPerformanceDTO;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResDashboardOverviewDTO;
import vn.viettel.khdn.billing_platform.repository.BillingPeriodRepository;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.service.CustomerRecordService;
import vn.viettel.khdn.billing_platform.service.DashboardService;
import vn.viettel.khdn.billing_platform.repository.UserRepository;
import vn.viettel.khdn.billing_platform.util.SecurityUtil;
import vn.viettel.khdn.billing_platform.model.User;
import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.dto.ResCustomerRecordDTO;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CustomerBillingRecordRepository recordRepository;
    private final CustomerRecordService recordService;
    private final BillingPeriodRepository periodRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String username = SecurityUtil.getCurrentUserLogin()
                .orElseThrow(() -> new EntityNotFoundException("Chưa đăng nhập"));
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng"));
    }

    private Long resolvePeriodId(Integer month, Integer year) {
        if (month == null || year == null) {
            java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            if (month == null) month = now.getMonthValue();
            if (year == null) year = now.getYear();
        }
        return periodRepository.findByMonthAndYear(month, year)
            .map(vn.viettel.khdn.billing_platform.model.BillingPeriod::getId)
            .orElse(null);
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN', 'CONSULTANT')")
    public ResponseEntity<ResDashboardOverviewDTO> getOverview(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        Long periodId = resolvePeriodId(month, year);
        if (periodId == null) {
            return ResponseEntity.ok(new ResDashboardOverviewDTO(0L, 0L, 0L, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 0.0, 0.0));
        }
        return ResponseEntity.ok(dashboardService.getDashboardOverview(periodId, getCurrentUser()));
    }

    @GetMapping("/consultants")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ResConsultantPerformanceDTO>> getConsultantPerformance(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        Long periodId = resolvePeriodId(month, year);
        if (periodId == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(dashboardService.getConsultantPerformance(periodId));
    }

    @GetMapping("/consultants/export")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportConsultantPerformance(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year) {
        Long periodId = resolvePeriodId(month, year);
        if (periodId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        byte[] data = dashboardService.exportConsultantPerformance(periodId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=bao_cao_tien_do.xlsx")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(data);
    }

    @GetMapping("/warnings")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<Page<ResCustomerRecordDTO>> getWarnings(
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "year", required = false) Integer year,
            Pageable pageable) {
        Long periodId = resolvePeriodId(month, year);
        if (periodId == null) {
            return ResponseEntity.ok(Page.empty(pageable));
        }
        Page<CustomerBillingRecord> page = recordRepository.findWarningsByPeriod(periodId, pageable);
        return ResponseEntity.ok(page.map(recordService::toDTO));
    }

    @GetMapping("/daily-stats")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<List<vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantDailyStatsDTO>> getDailyStats(
            @RequestParam(value = "date", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        if (date == null) {
            date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        }
        return ResponseEntity.ok(dashboardService.getDailyStats(date));
    }
}
