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
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.service.CustomerRecordService;
import vn.viettel.khdn.billing_platform.service.DashboardService;
import vn.viettel.khdn.billing_platform.model.dto.ResCustomerRecordDTO;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CustomerBillingRecordRepository recordRepository;
    private final CustomerRecordService recordService;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<ResDashboardOverviewDTO> getOverview(@RequestParam("periodId") Long periodId) {
        return ResponseEntity.ok(dashboardService.getDashboardOverview(periodId));
    }

    @GetMapping("/consultants")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ResConsultantPerformanceDTO>> getConsultantPerformance(@RequestParam("periodId") Long periodId) {
        return ResponseEntity.ok(dashboardService.getConsultantPerformance(periodId));
    }

    @GetMapping("/warnings")
    @PreAuthorize("hasAnyAuthority('MANAGER', 'ADMIN')")
    public ResponseEntity<Page<ResCustomerRecordDTO>> getWarnings(
            @RequestParam("periodId") Long periodId,
            Pageable pageable) {
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
