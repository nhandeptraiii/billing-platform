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

    // =========================================================================
    // LUỒNG 1: IMPORT ĐẦU KỲ — mau_import_dau_ky.xlsx
    // =========================================================================
    /**
     * Cấu trúc file mau_import_dau_ky.xlsx (header dòng 1, data từ dòng 2):
     *
     * Cột A (0): STT               — bỏ qua
     * Cột B (1): Mã Khách Hàng     — customerCode (*)
     * Cột C (2): Tên Khách Hàng    — customerName (*)
     * Cột D (3): Số TB/Account      — subscriberNumber (*)
     * Cột E (4): SĐT Liên Hệ      — phoneNumber
     * Cột F (5): Địa Chỉ           — fullAddress
     * Cột G (6): Tổng Cước (VNĐ)   — amountDue (*)
     * Cột H (7): Hình Thức TT      — bỏ qua (chỉ gợi ý)
     * Cột I (8): Username Nhân Viên — assignedConsultant (*)
     * Cột J (9): Username Quản Lý  — bỏ qua
     * Cột K (10): Kỳ Thanh Toán    — định dạng MM/YYYY, VD: 05/2026 (*)
     * Cột L (11): Loại Dịch Vụ     — serviceType (*)
     * Cột M (12): Nội Dung QC      — adsContent
     *
     * Trạng thái sau import: CHUA_THU, collectedAmount = 0
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
                if (row == null || isRowEmpty(row)) continue;

                try {
                    // Col 0: STT — bỏ qua
                    String customerCode    = getCellString(row, 1);   // Mã KH
                    String customerName    = getCellString(row, 2);   // Tên KH
                    String subscriberNum   = getCellString(row, 3);   // Số TB
                    String phoneNumber     = getCellString(row, 4);   // SĐT
                    String fullAddress     = getCellString(row, 5);   // Địa chỉ
                    BigDecimal amount      = getCellBigDecimal(row, 6); // Tổng cước
                    // Col 7: Hình thức TT — bỏ qua
                    String consultantEmail = getCellString(row, 8);   // Email nhân viên
                    // Col 9: Username quản lý — bỏ qua
                    String billingPeriodRaw = getCellString(row, 10); // Kỳ TT: MM/YYYY
                    String serviceType      = getCellString(row, 11); // Loại dịch vụ
                    // Col 12: Nội dung QC — bỏ qua

                    // Validate bắt buộc
                    if (customerCode.isBlank() || subscriberNum.isBlank()) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Mã KH (cột B) và Số TB (cột D) không được để trống"));
                        continue;
                    }
                    if (billingPeriodRaw.isBlank()) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Kỳ Thanh Toán (cột K) không được để trống, định dạng MM/YYYY"));
                        continue;
                    }

                    // Parse kỳ thanh toán "MM/YYYY"
                    int month, year;
                    try {
                        String[] parts = billingPeriodRaw.split("/");
                        month = Integer.parseInt(parts[0].trim());
                        year  = Integer.parseInt(parts[1].trim());
                    } catch (Exception e) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Kỳ Thanh Toán '" + billingPeriodRaw + "' sai định dạng, yêu cầu MM/YYYY (VD: 05/2026)"));
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
                        consultant = userRepository.findByEmail(consultantEmail).orElse(null);
                        if (consultant == null) {
                            errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                                "Không tìm thấy nhân viên với email: " + consultantEmail
                                + ". Hãy kiểm tra lại email trong cột I."));
                            continue;
                        }
                    }

                    // Tạo record mới
                    CustomerBillingRecord record = new CustomerBillingRecord();
                    record.setBillingPeriod(period);
                    record.setCustomerCode(customerCode);
                    record.setCustomerName(customerName);
                    record.setSubscriberNumber(subscriberNum);
                    record.setPhoneNumber(phoneNumber);
                    record.setFullAddress(fullAddress);
                    record.setAmountDue(amount != null ? amount : BigDecimal.ZERO);
                    record.setServiceType(serviceType.isBlank() ? null : serviceType);
                    record.setAssignedConsultant(consultant);
                    record.setStatus(BillingRecordStatusEnum.CHUA_THU);
                    record.setSyncWarning(SyncWarningEnum.NONE);
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

    // =========================================================================
    // LUỒNG 2: IMPORT ĐỐI CHIẾU GẠCH NỢ — Báo cáo xuất từ hệ thống Viettel
    // =========================================================================
    /**
     * Cấu trúc file báo cáo từ Viettel (Book2.xlsx):
     *
     * Dòng 1-6: Tiêu đề báo cáo (bỏ qua — tìm động đến khi gặp cột "STT")
     * Dòng 7:   Header: STT | Chi nhánh | Ban cước | Tổ thu | Số HĐ | Số TB | Tiền trả | Số lũy kế | Còn nợ | HT thu | HT TT | Item no | Mã HĐ
     * Dòng 8+:  Dữ liệu
     *
     * Key đối chiếu:
     *   Cột F (5): Số TB — subscriberNumber → tìm record trong hệ thống
     *
     * Xác định "đã gạch nợ trên Viettel":
     *   Cột J (9): HT thu — chứa "Gach no" hoặc "Gạch nợ" → fileIsMarked = true
     *
     * Ma trận đối chiếu (4 trường hợp):
     * ┌──────────────┬──────────────────┬─────────────────────────────────────────────────────┐
     * │ Trạng thái HT│ File Viettel     │ Kết quả                                             │
     * ├──────────────┼──────────────────┼─────────────────────────────────────────────────────┤
     * │ DA_GACH_NO   │ Đã gạch nợ       │ ✅ OK — bỏ qua                                      │
     * │ DA_IN_BILL   │ Đã gạch nợ       │ ✅ Auto cập nhật → DA_GACH_NO (file Viettel là chuẩn)│
     * │ CHUA_THU     │ Đã gạch nợ       │ ✅ Auto cập nhật → DA_GACH_NO (gạch mà chưa thu)    │
     * │ DA_GACH_NO   │ Chưa gạch nợ     │ ⚠️ INCONSISTENT — HT ghi gạch nhưng Viettel chưa   │
     * │ DA_IN_BILL   │ Chưa gạch nợ     │ ⚠️ COLLECTED_NOT_MARKED — đã thu chưa gạch Viettel  │
     * │ CHUA_THU     │ Chưa gạch nợ     │ — Bỏ qua                                            │
     * └──────────────┴──────────────────┴─────────────────────────────────────────────────────┘
     */
    public ImportResultDTO importReconciliation(MultipartFile file, Long periodId) {
        List<ImportResultDTO.ImportErrorRow> errors = new ArrayList<>();
        int autoUpdatedCount = 0;
        int validCount       = 0;
        int warningCount     = 0;

        // Validate kỳ tồn tại
        billingPeriodRepository.findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kỳ thanh toán ID: " + periodId));

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            // Tìm động dòng header thực sự (dòng có cột đầu = "STT")
            int dataStartRow = findDataStartRow(sheet, lastRow);
            if (dataStartRow < 0) {
                throw new IllegalArgumentException(
                    "Không tìm thấy dòng header trong file. "
                    + "File phải có dòng tiêu đề cột với ô đầu là 'STT'.");
            }

            for (int i = dataStartRow; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    // Cột F (index 5): Số TB — key đối chiếu chính
                    String subscriberNum = getCellString(row, 5);
                    // Cột J (index 9): HT thu — xác định đã gạch nợ hay chưa
                    String htThu         = getCellString(row, 9).toLowerCase().trim();

                    if (subscriberNum.isBlank()) continue; // Bỏ qua dòng không có số TB

                    boolean fileIsMarked = htThu.contains("gach no")
                                       || htThu.contains("gạch nợ");

                    // Tìm record trong hệ thống theo Số TB + kỳ
                    CustomerBillingRecord record = recordRepository
                        .findBySubscriberNumberAndBillingPeriodId(subscriberNum, periodId)
                        .orElse(null);

                    if (record == null) {
                        errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                            "Không tìm thấy KH có Số TB '" + subscriberNum
                            + "' trong kỳ ID=" + periodId
                            + ". KH có thể chưa được import đầu kỳ."));
                        continue;
                    }

                    BillingRecordStatusEnum sysStatus = record.getStatus();

                    if (fileIsMarked) {
                        if (sysStatus == BillingRecordStatusEnum.DA_GACH_NO) {
                            // TH1: Cả 2 đều gạch nợ → OK
                            validCount++;

                        } else {
                            // TH2 & TH3: File đã gạch nợ, HT chưa → Auto update (file Viettel là chuẩn)
                            record.setStatus(BillingRecordStatusEnum.DA_GACH_NO);
                            record.setDebtMarkedAt(Instant.now());
                            record.setSyncWarning(SyncWarningEnum.NONE);
                            record.setSyncWarningNote(null);
                            recordRepository.save(record);
                            autoUpdatedCount++;
                        }

                    } else {
                        // File CHƯA gạch nợ
                        if (sysStatus == BillingRecordStatusEnum.DA_GACH_NO) {
                            // TH4: HT gạch nợ nhưng Viettel chưa → INCONSISTENT
                            record.setSyncWarning(SyncWarningEnum.INCONSISTENT);
                            record.setSyncWarningNote(
                                "Hệ thống ghi 'Đã gạch nợ' nhưng báo cáo Viettel chưa ghi nhận. "
                                + "Số TB: " + subscriberNum + ". Cần kiểm tra lại trên hệ thống Viettel.");
                            recordRepository.save(record);
                            warningCount++;

                        } else if (sysStatus == BillingRecordStatusEnum.DA_IN_BILL) {
                            // TH5: Đã thu/in bill nhưng chưa gạch trên Viettel → COLLECTED_NOT_MARKED
                            record.setSyncWarning(SyncWarningEnum.COLLECTED_NOT_MARKED);
                            record.setSyncWarningNote(
                                "Đã thu tiền và in bill nhưng chưa gạch nợ trên hệ thống Viettel. "
                                + "Số TB: " + subscriberNum + ". Nhân viên cần vào Viettel để gạch nợ.");
                            recordRepository.save(record);
                            warningCount++;

                        }
                        // TH6: CHUA_THU + file chưa gạch → bỏ qua (bình thường)
                    }

                } catch (Exception e) {
                    errors.add(new ImportResultDTO.ImportErrorRow(i + 1,
                        "Lỗi xử lý dòng: " + e.getMessage()));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể đọc file Excel: " + e.getMessage());
        }

        int total = autoUpdatedCount + validCount + warningCount + errors.size();
        return new ImportResultDTO(total, validCount + autoUpdatedCount, errors.size(),
                                   autoUpdatedCount, warningCount, null, errors);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Tìm dòng bắt đầu dữ liệu (dòng ngay sau header "STT").
     * File Viettel có nhiều dòng tiêu đề trước khi vào header cột thực sự.
     * Phương thức quét từ trên xuống để tìm dòng có ô đầu tiên = "STT".
     *
     * @return index của dòng DATA đầu tiên (sau header), hoặc -1 nếu không tìm thấy
     */
    private int findDataStartRow(Sheet sheet, int lastRow) {
        for (int i = 0; i <= Math.min(lastRow, 20); i++) { // Quét tối đa 20 dòng đầu
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String firstCell = getCellString(row, 0).trim().toUpperCase();
            if (firstCell.equals("STT")) {
                return i + 1; // Dòng tiếp theo là dữ liệu
            }
        }
        return -1;
    }

    /** Kiểm tra dòng có rỗng hoàn toàn không */
    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !getCellString(row, c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                // Tránh số thực không cần thiết: 220000.0 → "220000"
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
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
                try { yield new BigDecimal(cell.getStringCellValue().replaceAll("[^\\d.]", "").trim()); }
                catch (Exception e) { yield BigDecimal.ZERO; }
            }
            default -> BigDecimal.ZERO;
        };
    }
}
