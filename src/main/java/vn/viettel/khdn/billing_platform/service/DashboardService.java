package vn.viettel.khdn.billing_platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantPerformanceDTO;
import vn.viettel.khdn.billing_platform.model.dto.dashboard.ResDashboardOverviewDTO;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.RoleEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Font;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CustomerBillingRecordRepository repository;

    public ResDashboardOverviewDTO getDashboardOverview(Long periodId, User currentUser) {
        List<Object[]> stats;
        if (currentUser.getRole() == RoleEnum.MANAGER || currentUser.getRole() == RoleEnum.ADMIN) {
            Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
            stats = repository.getProgressByPeriod(periodId, regionId);
        } else {
            stats = repository.getProgressByPeriodAndConsultant(periodId, currentUser.getId());
        }
        
        long totalRecords = 0L;
        long collectedRecords = 0L;
        long markedDebtRecords = 0L;
        BigDecimal expectedAmount = BigDecimal.ZERO;
        BigDecimal collectedAmount = BigDecimal.ZERO;

        for (Object[] row : stats) {
            CollectionStatusEnum collectionStatus = (CollectionStatusEnum) row[0];
            DebtStatusEnum debtStatus = (DebtStatusEnum) row[1];
            Long count = ((Number) row[2]).longValue();
            BigDecimal amtDue = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal colAmt = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

            totalRecords += count;
            expectedAmount = expectedAmount.add(amtDue);

            // Tính theo Phương án A: Đã in bill HOẶC Đã gạch nợ (chỉ tính 1 lần mỗi nhóm, chống double count)
            if (CollectionStatusEnum.DA_THANH_TOAN == collectionStatus || DebtStatusEnum.DA_GACH_NO == debtStatus) {
                collectedRecords += count;
                if (colAmt != null && colAmt.compareTo(BigDecimal.ZERO) > 0) {
                    collectedAmount = collectedAmount.add(colAmt);
                } else {
                    collectedAmount = collectedAmount.add(amtDue);
                }
            }

            if (DebtStatusEnum.DA_GACH_NO == debtStatus) {
                markedDebtRecords += count;
            }
        }

        Double amountProgressPercentage = 0.0;
        Double recordsProgressPercentage = 0.0;
        
        if (expectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            amountProgressPercentage = collectedAmount.divide(expectedAmount, 4, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .doubleValue();
        } 
        
        if (totalRecords > 0) {
            recordsProgressPercentage = (double) collectedRecords / totalRecords * 100;
        }

        return new ResDashboardOverviewDTO(
                totalRecords,
                collectedRecords,
                markedDebtRecords,
                expectedAmount,
                collectedAmount,
                amountProgressPercentage,
                recordsProgressPercentage
        );
    }

    public List<ResConsultantPerformanceDTO> getConsultantPerformance(Long periodId, User currentUser) {
        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        List<Object[]> data = repository.getConsultantPerformanceWithTarget(periodId, regionId);
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

    public List<vn.viettel.khdn.billing_platform.model.dto.dashboard.ResConsultantDailyStatsDTO> getDailyStats(java.time.LocalDate date, User currentUser) {
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        java.time.Instant startOfDay = date.atStartOfDay(zoneId).toInstant();
        java.time.Instant endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        Long regionId = currentUser.getRole() == RoleEnum.ADMIN ? null : (currentUser.getRegion() != null ? currentUser.getRegion().getId() : null);
        List<Object[]> data = repository.getConsultantDailyStats(startOfDay, endOfDay, regionId);
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

    public byte[] exportConsultantPerformance(Long periodId, User currentUser) {
        List<ResConsultantPerformanceDTO> records = getConsultantPerformance(periodId, currentUser);

        try (Workbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Báo cáo tiến độ");
            if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet) {
                ((org.apache.poi.xssf.streaming.SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            }

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Row 0: Merged headers
            Row row0 = sheet.createRow(0);
            row0.setHeight((short) 500);
            org.apache.poi.ss.usermodel.Cell cellDoanhThu = row0.createCell(2);
            cellDoanhThu.setCellValue("Doanh thu cước");
            cellDoanhThu.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 2, 5));

            org.apache.poi.ss.usermodel.Cell cellKhachHang = row0.createCell(6);
            cellKhachHang.setCellValue("Số lượng khách hàng");
            cellKhachHang.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 6, 9));

            // Row 1: Sub-headers
            Row row1 = sheet.createRow(1);
            String[] headers = {
                    "S", "Tên nhân viên", 
                    "Đã thu lũy kế", "Tổng cước phải", "Tồn đầu kỳ", "% Hoàn thành",
                    "Đã thu lũy kế", "Tổng KH phải thu", "Tồn đầu kỳ", "% Hoàn thành"
            };
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = row1.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 2;
            int stt = 1;
            for (ResConsultantPerformanceDTO r : records) {
                Row row = sheet.createRow(rowIdx++);
                
                row.createCell(0).setCellValue(stt++);
                row.createCell(1).setCellValue(r.getConsultantName() != null ? r.getConsultantName() : "");
                
                // Doanh thu
                BigDecimal collectedAmount = r.getCollectedAmount() != null ? r.getCollectedAmount() : BigDecimal.ZERO;
                BigDecimal targetAmount = r.getTargetAmount() != null ? r.getTargetAmount() : BigDecimal.ZERO;
                BigDecimal remainAmount = targetAmount.subtract(collectedAmount);
                if (remainAmount.compareTo(BigDecimal.ZERO) < 0) remainAmount = BigDecimal.ZERO;
                
                double amountPercent = 0.0;
                if (targetAmount.compareTo(BigDecimal.ZERO) > 0) {
                    amountPercent = collectedAmount.divide(targetAmount, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue();
                }

                row.createCell(2).setCellValue(collectedAmount.doubleValue());
                row.createCell(3).setCellValue(targetAmount.doubleValue());
                row.createCell(4).setCellValue(remainAmount.doubleValue());
                row.createCell(5).setCellValue(String.format("%.2f %%", amountPercent));

                // Khách hàng
                long collectedRecords = r.getCollectedRecords() != null ? r.getCollectedRecords() : 0L;
                long targetRecords = r.getTargetRecords() != null ? r.getTargetRecords() : 0L;
                long remainRecords = targetRecords - collectedRecords;
                if (remainRecords < 0) remainRecords = 0L;

                double recordsPercent = 0.0;
                if (targetRecords > 0) {
                    recordsPercent = (double) collectedRecords / targetRecords * 100;
                }

                row.createCell(6).setCellValue(collectedRecords);
                row.createCell(7).setCellValue(targetRecords);
                row.createCell(8).setCellValue(remainRecords);
                row.createCell(9).setCellValue(String.format("%.2f %%", recordsPercent));
            }

            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file Excel: " + e.getMessage(), e);
        }
    }
}
