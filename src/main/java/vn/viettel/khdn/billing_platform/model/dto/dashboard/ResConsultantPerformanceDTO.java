package vn.viettel.khdn.billing_platform.model.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResConsultantPerformanceDTO {
    private Long consultantId;
    private String consultantName;
    private Long targetRecords;
    private BigDecimal targetAmount;
    private Long collectedRecords;
    private BigDecimal collectedAmount;
}
