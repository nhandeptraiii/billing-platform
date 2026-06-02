package vn.viettel.khdn.billing_platform.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.viettel.khdn.billing_platform.model.BillingPeriod;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.enums.BillingPeriodStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.BillingRecordStatusEnum;
import vn.viettel.khdn.billing_platform.repository.BillingPeriodRepository;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;

/**
 * Bước 5 — Kiểm tra cảnh báo cuối ngày:
 * Tìm tất cả bản ghi DA_THANH_TOAN trong các kỳ OPEN → log cảnh báo.
 * (Frontend gọi GET /records/warnings để hiển thị danh sách)
 */
@Component
@EnableScheduling
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private final BillingPeriodRepository billingPeriodRepository;
    private final CustomerBillingRecordRepository recordRepository;

    public BillingScheduler(BillingPeriodRepository billingPeriodRepository,
                             CustomerBillingRecordRepository recordRepository) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.recordRepository = recordRepository;
    }

    /**
     * Chạy lúc 21:00 mỗi ngày (cron từ config).
     * Kiểm tra các bill đã in nhưng chưa gạch nợ.
     */
    @Scheduled(cron = "${billing.scheduler.end-of-day-cron}")
    public void checkUnmarkedBillsEndOfDay() {
        log.info("[BillingScheduler] Bắt đầu kiểm tra cuối ngày...");

        List<BillingPeriod> openPeriods = billingPeriodRepository.findAll()
            .stream()
            .filter(p -> p.getStatus() == BillingPeriodStatusEnum.OPEN)
            .toList();

        int totalWarnings = 0;

        for (BillingPeriod period : openPeriods) {
            List<CustomerBillingRecord> unprocessed = recordRepository
                .findByBillingPeriodIdAndStatus(period.getId(), BillingRecordStatusEnum.DA_THANH_TOAN);

            if (!unprocessed.isEmpty()) {
                totalWarnings += unprocessed.size();
                log.warn("[BillingScheduler] Kỳ {} ({}/{}): {} bản ghi ĐÃ THANH TOÁN nhưng CHƯA GẠCH NỢ",
                    period.getName(), period.getMonth(), period.getYear(), unprocessed.size());
            }
        }

        if (totalWarnings == 0) {
            log.info("[BillingScheduler] Không có cảnh báo. Tất cả bill đã in đều đã gạch nợ.");
        } else {
            log.warn("[BillingScheduler] Tổng cảnh báo cuối ngày: {} bản ghi cần xử lý.", totalWarnings);
        }
    }
}
