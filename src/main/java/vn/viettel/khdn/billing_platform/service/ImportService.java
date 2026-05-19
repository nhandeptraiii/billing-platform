package vn.viettel.khdn.billing_platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.BillingPeriod;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ImportResultDTO;
import vn.viettel.khdn.billing_platform.model.enums.BillingRecordStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.SyncWarningEnum;
import vn.viettel.khdn.billing_platform.repository.BillingPeriodRepository;
import vn.viettel.khdn.billing_platform.repository.CustomerBillingRecordRepository;
import vn.viettel.khdn.billing_platform.repository.UserRepository;

@Service
public class ImportService {

    private final BillingPeriodRepository billingPeriodRepository;
    private final CustomerBillingRecordRepository recordRepository;
    private final UserRepository userRepository;

    public ImportService(BillingPeriodRepository billingPeriodRepository,
                         CustomerBillingRecordRepository recordRepository,
                         UserRepository userRepository) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
    }

    /**
     * Import đầu kỳ: mau_import_dau_ky.xlsx
     * Cột: Mã KH | Tên KH | Số TB | SĐT | Số tiền | Tỉnh/TP | Xã/Phường |
     *       Ấp/KV | Tuyến đường | Địa chỉ đầy đủ | Email tư vấn viên | Tháng | Năm
     */
    public ImportResultDTO importStartOfPeriod(MultipartFile file, User createdBy) {
        List<ImportResultDTO.ImportErrorRow> errors = new ArrayList<>();
        int successCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) { // Bỏ header row 0
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String customerCode    = getCellString(row, 0);
                    String customerName    = getCellString(row, 1);
                    String subscriberNum   = getCellString(row, 2);
                    String phoneNumber     = getCellString(row, 3);
                    BigDecimal amount      = getCellBigDecimal(row, 4);
                    String province        = getCellString(row, 5);
                    String ward            = getCellString(row, 6);
                    String hamlet          = getCellString(row, 7);
                    String street          = getCellString(row, 8);
                    String fullAddress     = getCellString(row, 9);
                    String consultantEmail = getCellString(row, 10);
                    int month              = (int) getCellNumeric(row, 11);
                    int year               = (int) getCellNumeric(row, 12);

                    // Validate bắt buộc
                    if (customerCode.isBlank() || subscriberNum.isBlank()) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Mã KH và Số TB không được để trống"));
                        continue;
                    }

                    // Tìm hoặc tạo BillingPeriod
                    BillingPeriod period = billingPeriodRepository
                        .findByMonthAndYear(month, year)
                        .orElseGet(() -> {
                            BillingPeriod bp = new BillingPeriod();
                            bp.setMonth(month);
                            bp.setYear(year);
                            bp.setCreatedBy(createdBy);
                            return billingPeriodRepository.save(bp);
                        });

                    // Tìm tư vấn viên theo email
                    User consultant = null;
                    if (!consultantEmail.isBlank()) {
                        consultant = userRepository.findByEmail(consultantEmail)
                            .orElse(null);
                        if (consultant == null) {
                            errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                                "Không tìm thấy tư vấn viên với email: " + consultantEmail));
                            continue;
                        }
                    }

                    // Tạo record
                    CustomerBillingRecord record = new CustomerBillingRecord();
                    record.setBillingPeriod(period);
                    record.setCustomerCode(customerCode);
                    record.setCustomerName(customerName);
                    record.setSubscriberNumber(subscriberNum);
                    record.setPhoneNumber(phoneNumber);
                    record.setAmountDue(amount);
                    record.setProvince(province);
                    record.setWard(ward);
                    record.setHamlet(hamlet);
                    record.setStreet(street);
                    record.setFullAddress(fullAddress);
                    record.setAssignedConsultant(consultant);
                    record.setStatus(BillingRecordStatusEnum.CHUA_THU);
                    recordRepository.save(record);

                    totalAmount = totalAmount.add(amount != null ? amount : BigDecimal.ZERO);
                    successCount++;

                } catch (Exception e) {
                    errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                        "Lỗi xử lý dòng: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể đọc file Excel: " + e.getMessage());
        }

        return new ImportResultDTO(
            successCount + errors.size(),
            successCount,
            errors.size(),
            0, 0,
            totalAmount,
            errors
        );
    }

    /**
     * Import đối chiếu gạch nợ: Mau_cap_nhat_trang_thai_da_thanh_toan.xls
     * Cột: Mã KH | Số TB | Trạng thái (DA_GACH_NO / CHUA_GACH_NO) | Tháng | Năm
     *
     * Quy tắc 3 trường hợp:
     * TH1: Hệ thống=CHUA_GACH_NO + File=DA_GACH_NO  → Auto update
     * TH2: Hệ thống=DA_GACH_NO   + File=DA_GACH_NO  → Hợp lệ
     * TH3: Hệ thống=DA_GACH_NO   + File=CHUA_GACH_NO → Cảnh báo INCONSISTENT
     */
    public ImportResultDTO importReconciliation(MultipartFile file, Long periodId) {
        List<ImportResultDTO.ImportErrorRow> errors = new ArrayList<>();
        int updatedCount = 0;
        int validCount = 0;
        int warningCount = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String customerCode  = getCellString(row, 0);
                    String subscriberNum = getCellString(row, 1);
                    String statusStr     = getCellString(row, 2).toUpperCase().trim();

                    boolean fileIsMarked = statusStr.contains("DA_GACH_NO")
                                       || statusStr.contains("ĐÃ GẠCH NỢ")
                                       || statusStr.equals("1")
                                       || statusStr.equals("TRUE");

                    // Tìm record
                    CustomerBillingRecord record = recordRepository
                        .findByCustomerCodeAndBillingPeriodId(customerCode, periodId)
                        .or(() -> recordRepository.findBySubscriberNumberAndBillingPeriodId(
                            subscriberNum, periodId))
                        .orElse(null);

                    if (record == null) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Không tìm thấy KH: " + customerCode + " / " + subscriberNum));
                        continue;
                    }

                    boolean systemIsMarked = record.getStatus() == BillingRecordStatusEnum.DA_GACH_NO;

                    if (!systemIsMarked && fileIsMarked) {
                        // TH1: Auto update
                        record.setStatus(BillingRecordStatusEnum.DA_GACH_NO);
                        record.setDebtMarkedAt(Instant.now());
                        record.setSyncWarning(SyncWarningEnum.NONE);
                        record.setSyncWarningNote(null);
                        recordRepository.save(record);
                        updatedCount++;

                    } else if (systemIsMarked && fileIsMarked) {
                        // TH2: Hợp lệ
                        validCount++;

                    } else if (systemIsMarked && !fileIsMarked) {
                        // TH3: Cảnh báo
                        record.setSyncWarning(SyncWarningEnum.INCONSISTENT);
                        record.setSyncWarningNote(
                            "Hệ thống đã gạch nợ nhưng file Viettel chưa ghi nhận. Cần kiểm tra lại.");
                        recordRepository.save(record);
                        warningCount++;
                    }
                    // TH: !systemIsMarked && !fileIsMarked → không làm gì

                } catch (Exception e) {
                    errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                        "Lỗi xử lý dòng: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể đọc file Excel: " + e.getMessage());
        }

        int total = updatedCount + validCount + warningCount + errors.size();
        return new ImportResultDTO(total, validCount + updatedCount, errors.size(),
                                   updatedCount, warningCount, null, errors);
    }

    // ---- Helpers ----
    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    private BigDecimal getCellBigDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> {
                try { yield new BigDecimal(cell.getStringCellValue().trim()); }
                catch (Exception e) { yield BigDecimal.ZERO; }
            }
            default -> BigDecimal.ZERO;
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> {
                try { yield Double.parseDouble(cell.getStringCellValue().trim()); }
                catch (Exception e) { yield 0; }
            }
            default -> 0;
        };
    }
}
