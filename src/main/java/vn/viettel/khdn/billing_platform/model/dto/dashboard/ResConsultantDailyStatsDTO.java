package vn.viettel.khdn.billing_platform.model.dto.dashboard;

import java.time.Instant;

public record ResConsultantDailyStatsDTO(
        Long consultantId,
        String consultantName,
        Instant firstBillPrintedAt,
        Long collectedCount
) {
}
