package vn.viettel.khdn.billing_platform.model.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResDashboardOverviewDTO {
    private Long totalRecordsImported;
    private Long totalCollectedRecords;
    private BigDecimal totalExpectedAmount;
    private BigDecimal totalCollectedAmount;
    private Double progressPercentage;
}
