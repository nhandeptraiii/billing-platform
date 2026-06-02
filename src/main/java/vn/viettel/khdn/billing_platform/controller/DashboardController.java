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
import vn.viettel.khdn.billing_platform.service.DashboardService;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CustomerBillingRecordRepository recordRepository;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ResDashboardOverviewDTO> getOverview(@RequestParam Long periodId) {
        return ResponseEntity.ok(dashboardService.getDashboardOverview(periodId));
    }

    @GetMapping("/consultants")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ResConsultantPerformanceDTO>> getConsultantPerformance(@RequestParam Long periodId) {
        return ResponseEntity.ok(dashboardService.getConsultantPerformance(periodId));
    }

    @GetMapping("/warnings")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Page<CustomerBillingRecord>> getWarnings(
            @RequestParam Long periodId,
            Pageable pageable) {
        // Có thể map sang DTO nếu cần thiết, tạm thời trả về Page<Entity> 
        // giống với các API warning khác hoặc Frontend tự xử lý fields.
        return ResponseEntity.ok(recordRepository.findWarningsByPeriod(periodId, pageable));
    }
}
