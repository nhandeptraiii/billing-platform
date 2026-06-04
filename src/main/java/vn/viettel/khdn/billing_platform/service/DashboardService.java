package vn.viettel.khdn.billing_platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantPerformanceDTO;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResDashboardOverviewDTO;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerBillingRecordRepository repository;

    public ResDashboardOverviewDTO getDashboardOverview(Long periodId) {
        List<Object[]> stats = repository.getProgressByPeriod(periodId);
        
        long totalRecords = 0L;
        long collectedRecords = 0L;
        BigDecimal expectedAmount = BigDecimal.ZERO;
        BigDecimal collectedAmount = BigDecimal.ZERO;

        for (Object[] row : stats) {
            CollectionStatusEnum collectionStatus = (CollectionStatusEnum) row[0];
            // row[1] is debtStatus
            Long count = ((Number) row[2]).longValue();
            BigDecimal amtDue = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal colAmt = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

            totalRecords += count;
            expectedAmount = expectedAmount.add(amtDue);
            collectedAmount = collectedAmount.add(colAmt);

            if (CollectionStatusEnum.DA_THANH_TOAN == collectionStatus) {
                collectedRecords += count;
            }
        }

        Double progressPercentage = 0.0;
        if (expectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            progressPercentage = collectedAmount.divide(expectedAmount, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .doubleValue();
        } else if (totalRecords > 0) {
            progressPercentage = (double) collectedRecords / totalRecords * 100;
        }

        return new ResDashboardOverviewDTO(
                totalRecords,
                collectedRecords,
                expectedAmount,
                collectedAmount,
                progressPercentage
        );
    }

    public List<ResConsultantPerformanceDTO> getConsultantPerformance(Long periodId) {
        List<Object[]> data = repository.getConsultantPerformanceWithTarget(periodId);
        List<ResConsultantPerformanceDTO> result = new ArrayList<>();
        
        for (Object[] row : data) {
            Long consultantId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String consultantName = (String) row[1];
            Long targetRecords = ((Number) row[2]).longValue();
            BigDecimal targetAmount = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            Long collectedRecords = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            BigDecimal collectedAmount = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;

            result.add(new ResConsultantPerformanceDTO(
                    consultantId,
                    consultantName,
                    targetRecords,
                    targetAmount,
                    collectedRecords,
                    collectedAmount
            ));
        }
        return result;
    }

    public List<vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantDailyStatsDTO> getDailyStats(java.time.LocalDate date) {
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.Instant startOfDay = date.atStartOfDay(zoneId).toInstant();
        java.time.Instant endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<Object[]> data = repository.getConsultantDailyStats(startOfDay, endOfDay);
        List<vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantDailyStatsDTO> result = new ArrayList<>();

        for (Object[] row : data) {
            Long consultantId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String consultantName = (String) row[1];
            java.time.Instant firstBillPrintedAt = (java.time.Instant) row[2];
            Long collectedCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            result.add(new vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantDailyStatsDTO(
                    consultantId,
                    consultantName,
                    firstBillPrintedAt,
                    collectedCount
            ));
        }
        return result;
    }
}
