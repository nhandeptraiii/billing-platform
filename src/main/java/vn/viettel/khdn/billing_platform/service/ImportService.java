package vn.viettel.khdn.billing_platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.github.pjfanning.xlsx.StreamingReader;

import jakarta.persistence.EntityNotFoundException;
import vn.viettel.khdn.billing_platform.model.BillingPeriod;
import vn.viettel.khdn.billing_platform.model.CustomerBillingRecord;
import vn.viettel.khdn.billing_platform.model.User;
import vn.viettel.khdn.billing_platform.model.dto.ImportResultDTO;
import vn.viettel.khdn.billing_platform.model.enums.CollectionStatusEnum;
import vn.viettel.khdn.billing_platform.model.enums.DebtStatusEnum;
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

    private String normalizeSubscriberNumber(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.trim();
    }

    private record ReconciliationKey(String customerCode, String subscriberNumber) {}

    private static class ReconciliationGroup {
        private final int firstRowNumber;
        private int rowCount = 0;
        private boolean hasRemainingDebtValue = false;
        private boolean hasRemainingDebt = false;
        private boolean hasPositivePayment = false;

        private ReconciliationGroup(int firstRowNumber) {
            this.firstRowNumber = firstRowNumber;
        }

        private void addRow(BigDecimal paidAmount, String remainingDebtRaw, BigDecimal remainingDebt) {
            rowCount++;
            if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                hasPositivePayment = true;
            }
            if (remainingDebtRaw != null && !remainingDebtRaw.isBlank()) {
                hasRemainingDebtValue = true;
            }
            if (remainingDebt != null && remainingDebt.compareTo(BigDecimal.ZERO) > 0) {
                hasRemainingDebt = true;
            }
        }

        private boolean isCleared() {
            return hasPositivePayment && hasRemainingDebtValue && !hasRemainingDebt;
        }
    }

    // =========================================================================
    // TEMPLATE GENERATION
    // =========================================================================

    public byte[] generateStartOfPeriodTemplate() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Import Dau Ky");
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "STT", "Mã Khách Hàng", "Tên Khách Hàng", "Số TB/Account", "SĐT Liên Hệ", 
                "Địa Chỉ", "Tổng Cước (VNĐ)", "Hình Thức TT", "Username Nhân Viên", 
                "Username Quản Lý", "Kỳ Thanh Toán", "Loại Dịch Vụ", "Nội Dung QC"
            };
            
            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // Sample Row
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue(1);
            sampleRow.createCell(1).setCellValue("KH001");
            sampleRow.createCell(2).setCellValue("Nguyen Van A");
            sampleRow.createCell(3).setCellValue("0901234567");
            sampleRow.createCell(4).setCellValue("0901234567");
            sampleRow.createCell(5).setCellValue("Q1, HCM");
            sampleRow.createCell(6).setCellValue(150000);
            sampleRow.createCell(7).setCellValue("Tien mat");
            sampleRow.createCell(8).setCellValue("nhanvien01");
            sampleRow.createCell(9).setCellValue("quanly01");
            sampleRow.createCell(10).setCellValue("05/2026");
            sampleRow.createCell(11).setCellValue("Internet");
            sampleRow.createCell(12).setCellValue("Mừng ngày Giải Phóng giảm giá 20%");

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file mẫu", e);
        }
    }

    public byte[] generateReconciliationTemplate() {
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Bao Cao Viettel");
            
            // Viettel report has some title rows
            sheet.createRow(0).createCell(0).setCellValue("TỔNG CÔNG TY VIỄN THÔNG VIETTEL");
            sheet.createRow(1).createCell(0).setCellValue("BÁO CÁO ĐỐI CHIẾU THU CƯỚC");
            
            // Actual headers at row 6 (index 6)
            Row headerRow = sheet.createRow(6);
            String[] headers = {
                "STT", "Chi nhánh", "Ban cước", "Tổ thu", "Số HĐ", "Số TB", 
                "Tiền trả", "Số lũy kế", "Còn nợ", "HT thu", "HT TT", "Item no", "Mã HĐ"
            };
            
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            // Sample Row
            Row sampleRow = sheet.createRow(7);
            sampleRow.createCell(0).setCellValue(1);
            sampleRow.createCell(5).setCellValue("0901234567"); // Số TB (Cột F)
            sampleRow.createCell(6).setCellValue(150000);       // Tiền trả
            sampleRow.createCell(9).setCellValue("Gạch nợ");    // HT thu (Cột J)

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo file mẫu", e);
        }
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
     */
    public ImportResultDTO importStartOfPeriod(MultipartFile file, User createdBy) {
        List<ImportResultDTO.ImportErrorRow> errors = new ArrayList<>();
        int successCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        List<CustomerBillingRecord> batchRecords = new ArrayList<>();
        int BATCH_SIZE = 500;

        try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Bỏ header
                if (isRowEmpty(row)) continue;
                
                int currentRowNum = row.getRowNum() + 1; // 1-indexed for logging

                try {
                    String customerCode    = getCellString(row, 1);
                    String customerName    = getCellString(row, 2);
                    String subscriberNum   = getCellString(row, 3);
                    String phoneNumber     = getCellString(row, 4);
                    String fullAddress     = getCellString(row, 5);
                    BigDecimal amount      = getCellBigDecimal(row, 6);
                    String consultantUsername = getCellString(row, 8);
                    String billingPeriodRaw = getCellString(row, 10);
                    String serviceType      = getCellString(row, 11);
                    String adsContent       = getCellString(row, 12);

                    if (customerCode.isBlank() || subscriberNum.isBlank()) {
                        errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                            "Mã KH (cột B) và Số TB (cột D) không được để trống"));
                        continue;
                    }
                    if (billingPeriodRaw.isBlank()) {
                        errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                            "Kỳ Thanh Toán (cột K) không được để trống, định dạng MM/YYYY"));
                        continue;
                    }

                    int month, year;
                    try {
                        String[] parts = billingPeriodRaw.split("/");
                        month = Integer.parseInt(parts[0].trim());
                        year  = Integer.parseInt(parts[1].trim());
                    } catch (Exception e) {
                        errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                            "Kỳ Thanh Toán '" + billingPeriodRaw + "' sai định dạng, yêu cầu MM/YYYY (VD: 05/2026)"));
                        continue;
                    }

                    BillingPeriod period = billingPeriodRepository
                        .findByMonthAndYear(month, year)
                        .orElseGet(() -> {
                            BillingPeriod bp = new BillingPeriod();
                            bp.setMonth(month);
                            bp.setYear(year);
                            bp.setCreatedBy(createdBy);
                            return billingPeriodRepository.save(bp);
                        });

                    User consultant = null;
                    if (!consultantUsername.isBlank()) {
                        consultant = userRepository.findByUsername(consultantUsername).orElse(null);
                        if (consultant == null) {
                            errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                                "Không tìm thấy nhân viên với username: " + consultantUsername));
                            continue;
                        }
                        // Kiểm tra tư vấn viên phải thuộc cùng cụm (region) với Manager đang import
                        boolean sameRegion = createdBy.getRegion() != null
                            && consultant.getRegion() != null
                            && createdBy.getRegion().getId().equals(consultant.getRegion().getId());
                        if (!sameRegion) {
                            errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                                "Nhân viên '" + consultantUsername + "' không thuộc cụm của bạn, không thể import"));
                            continue;
                        }
                    }

                    String managerUsername = getCellString(row, 9);
                    if (consultant != null && !managerUsername.isBlank()) {
                        User manager = userRepository.findByUsername(managerUsername).orElse(null);
                        if (manager != null) {
                            if (consultant.getManager() == null || !consultant.getManager().getId().equals(manager.getId())) {
                                consultant.setManager(manager);
                                userRepository.save(consultant);
                            }
                        }
                    }

                    CustomerBillingRecord record = new CustomerBillingRecord();
                    record.setBillingPeriod(period);
                    record.setCustomerCode(customerCode);
                    record.setCustomerName(customerName);
                    record.setSubscriberNumber(subscriberNum);
                    record.setPhoneNumber(phoneNumber);
                    record.setFullAddress(fullAddress);
                    record.setAmountDue(amount != null ? amount : BigDecimal.ZERO);
                    record.setServiceType(serviceType.isBlank() ? null : serviceType);
                    record.setAdsContent(adsContent.isBlank() ? null : adsContent);
                    record.setAssignedConsultant(consultant);
                    record.setRegion(createdBy.getRegion());
                    record.setCollectionStatus(CollectionStatusEnum.CHUA_THU);
                    record.setDebtStatus(DebtStatusEnum.CHUA_GACH_NO);
                    record.setSyncWarning(SyncWarningEnum.NONE);
                    
                    batchRecords.add(record);
                    totalAmount = totalAmount.add(amount != null ? amount : BigDecimal.ZERO);
                    successCount++;
                    
                    if (batchRecords.size() >= BATCH_SIZE) {
                        recordRepository.saveAll(batchRecords);
                        batchRecords.clear();
                    }

                } catch (Exception e) {
                    errors.add(new ImportResultDTO.ImportErrorRow(currentRowNum,
                        "Lỗi xử lý dòng: " + e.getMessage()));
                }
            }
            
            if (!batchRecords.isEmpty()) {
                recordRepository.saveAll(batchRecords);
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
     * Cấu trúc file RP2 (header thực tế ở dòng 7, data từ dòng 8):
     *
     * Cột A (0): STT
     * Cột B (1): Chi nhánh
     * Cột C (2): Ban cước
     * Cột D (3): Tổ thu
     * Cột E (4): Số hợp đồng       — thường là "xxxxx" (không dùng)
     * Cột F (5): Số TB              — số thuê bao
     * Cột G (6): Tiền trả           — numeric
     * Cột H (7): Số lũy kế          — numeric
     * Cột I (8): Còn nợ             — numeric
     * Cột J (9): HT thu             — "Gach no TPP" / "Kênh thương mại điện tử" / ...
     * Cột K (10): HT TT
     * Cột L (11): Item no
     * Cột M (12): Mã hợp đồng      — Mã KH Viettel (*) — dùng để match với customerCode
     *
     * CHIẾN LƯỢC XỬ LÝ (2-pass + chunked IN query):
     *   Pass 1: Stream toàn bộ file → Map<MãHĐ, isGachedNo> (chỉ lưu String+Boolean, ~1MB)
     *           + Map<MãHĐ, rowNumber> (để log lỗi)
     *   Pass 2: Chia Mã HĐ thành batch CHUNK_SIZE → SELECT IN (batch) → xử lý → save → next
     *           → Không N+1 (tránh chậm), không load all (tránh OOM)
     */
    public ImportResultDTO importReconciliation(MultipartFile file, Long periodId, User currentUser) {
        return importReconciliation(file, periodId, currentUser, false);
    }

    public ImportResultDTO importReconciliation(MultipartFile file, Long periodId, User currentUser, boolean dryRun) {
        List<ImportResultDTO.ImportErrorRow> errors = new ArrayList<>();
        int autoUpdatedCount = 0;
        int warningCount     = 0;

        billingPeriodRepository.findById(periodId)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy kỳ thanh toán ID: " + periodId));

        // ── PASS 1: Đọc toàn bộ file, gom theo Mã HĐ + Số TB ──────────────────
        // Một nhóm được coi là đã gạch nợ khi có tiền trả và không còn nợ.
        java.util.LinkedHashMap<ReconciliationKey, ReconciliationGroup> groups = new java.util.LinkedHashMap<>();
        // Lưu số dòng đầu tiên gặp mỗi cặp key để báo lỗi.
        boolean foundHeader = false;
        int totalInputRows = 0;

        try (Workbook workbook = StreamingReader.builder()
                .rowCacheSize(200)
                .bufferSize(65536)
                .open(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                if (row == null) continue;

                // Tìm header: dòng nào có cột A = "STT" (kiểm tra bằng getCellString
                // để tránh phụ thuộc vào cell type — StreamingReader trả về STRING cho shared string)
                if (!foundHeader) {
                    String firstCell = getCellString(row, 0).trim();
                    if (firstCell.equalsIgnoreCase("STT")) {
                        foundHeader = true;
                    }
                    continue;
                }

                // Lấy Mã hợp đồng (cột M, index 12) — đây là key duy nhất cần thiết
                // Không dùng isRowEmptyForReconciliation vì nó check cột A (STT) theo cell type,
                // dễ bị sai với StreamingReader khi STT là shared string (type='s')
                String contractCode = getCellString(row, 12).trim();
                if (contractCode.isBlank()) continue; // Dòng không có Mã HĐ → bỏ qua

                String normalizedCode = normalizeContractCode(contractCode);
                if (normalizedCode.isBlank()) continue;

                String subscriberNumber = normalizeSubscriberNumber(getCellString(row, 5));
                if (subscriberNumber.isBlank()) continue;

                totalInputRows++;
                ReconciliationKey key = new ReconciliationKey(normalizedCode, subscriberNumber);
                ReconciliationGroup group = groups.computeIfAbsent(key, k -> new ReconciliationGroup(row.getRowNum() + 1));
                group.addRow(getCellBigDecimal(row, 6), getCellString(row, 8), getCellBigDecimal(row, 8));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Không thể đọc file Excel: " + e.getMessage());
        }

        if (!foundHeader) {
            throw new IllegalArgumentException("Không tìm thấy dòng header có cột 'STT'.");
        }

        // ── PASS 2: Chunked IN query — xử lý từng batch 500 Mã HĐ ─────────────
        final int CHUNK_SIZE = 500;
        final int SAVE_BATCH = 500;
        List<CustomerBillingRecord> saveBuffer = new ArrayList<>();

        Long currentUserRegionId = (currentUser.getRegion() != null) ? currentUser.getRegion().getId() : null;
        boolean isManager = currentUser.getRole() == vn.viettel.khdn.billing_platform.model.enums.RoleEnum.MANAGER;

        List<String> allCodes = groups.keySet().stream()
            .map(ReconciliationKey::customerCode)
            .distinct()
            .toList();
        int failedRowCount = 0;

        for (int i = 0; i < allCodes.size(); i += CHUNK_SIZE) {
            List<String> chunk = allCodes.subList(i, Math.min(i + CHUNK_SIZE, allCodes.size()));
            java.util.Set<String> chunkCodes = new java.util.HashSet<>(chunk);

            // 1 câu SELECT IN cho cả batch → không N+1, không OOM
            List<CustomerBillingRecord> dbRecords =
                recordRepository.findAllByCustomerCodeInAndBillingPeriodId(chunk, periodId);

            // Group DB records theo cùng key với file RP2.
            java.util.Map<ReconciliationKey, List<CustomerBillingRecord>> byKey = new java.util.HashMap<>();
            for (CustomerBillingRecord r : dbRecords) {
                ReconciliationKey key = new ReconciliationKey(
                    normalizeContractCode(r.getCustomerCode()),
                    normalizeSubscriberNumber(r.getSubscriberNumber()));
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }

            // Xử lý từng cặp Mã HĐ + Số TB trong chunk.
            for (java.util.Map.Entry<ReconciliationKey, ReconciliationGroup> entry : groups.entrySet()) {
                ReconciliationKey key = entry.getKey();
                if (!chunkCodes.contains(key.customerCode())) continue;

                ReconciliationGroup group = entry.getValue();
                boolean fileIsCleared = group.isCleared();
                int rowNum = group.firstRowNumber;
                List<CustomerBillingRecord> records = byKey.getOrDefault(key, java.util.Collections.emptyList());

                if (records.isEmpty()) {
                    errors.add(new ImportResultDTO.ImportErrorRow(rowNum,
                        "Không tìm thấy KH có Mã hợp đồng '" + key.customerCode() +
                        "' và Số TB '" + key.subscriberNumber() + "' trong kỳ."));
                    failedRowCount += group.rowCount;
                    continue;
                }

                // Lọc ra các record mà user hiện tại có quyền cập nhật (nếu là MANAGER thì chỉ record thuộc cụm mình)
                List<CustomerBillingRecord> allowedRecords = new ArrayList<>();
                for (CustomerBillingRecord r : records) {
                    if (isManager && currentUserRegionId != null) {
                        if (r.getRegion() != null && currentUserRegionId.equals(r.getRegion().getId())) {
                            allowedRecords.add(r);
                        }
                    } else {
                        allowedRecords.add(r);
                    }
                }

                if (allowedRecords.isEmpty()) {
                    errors.add(new ImportResultDTO.ImportErrorRow(rowNum,
                        "KH có Mã hợp đồng '" + key.customerCode() + "' và Số TB '" +
                        key.subscriberNumber() + "' thuộc cụm khác, bạn không có quyền cập nhật."));
                    failedRowCount += group.rowCount;
                    continue;
                }

                for (CustomerBillingRecord record : allowedRecords) {
                    boolean updated = false;
                    if (fileIsCleared) {
                        if (record.getDebtStatus() != DebtStatusEnum.DA_GACH_NO) {
                            autoUpdatedCount++;
                            if (!dryRun) {
                                record.setDebtStatus(DebtStatusEnum.DA_GACH_NO);
                                record.setDebtMarkedAt(Instant.now());
                                record.setSyncWarning(SyncWarningEnum.NONE);
                                record.setSyncWarningNote(null);
                                updated = true;
                            }
                        }
                    } else {
                        if (record.getDebtStatus() == DebtStatusEnum.DA_GACH_NO) {
                            warningCount++;
                            if (!dryRun) {
                                record.setSyncWarning(SyncWarningEnum.INCONSISTENT);
                                record.setSyncWarningNote("Hệ thống ghi 'Đã gạch nợ' nhưng báo cáo Viettel chưa ghi nhận.");
                                updated = true;
                            }
                        } else if (record.getCollectionStatus() == CollectionStatusEnum.DA_THANH_TOAN) {
                            warningCount++;
                            if (!dryRun) {
                                record.setSyncWarning(SyncWarningEnum.COLLECTED_NOT_MARKED);
                                record.setSyncWarningNote("Đã thu tiền và in bill nhưng chưa gạch nợ trên hệ thống Viettel.");
                                updated = true;
                            }
                        }
                    }

                    if (updated && !dryRun) {
                        saveBuffer.add(record);
                        if (saveBuffer.size() >= SAVE_BATCH) {
                            recordRepository.saveAll(saveBuffer);
                            saveBuffer.clear();
                        }
                    }
                }
            }
            // Mỗi chunk xử lý xong → dbRecords được GC, không giữ RAM
        }

        // Flush phần còn lại
        if (!dryRun && !saveBuffer.isEmpty()) {
            recordRepository.saveAll(saveBuffer);
        }

        return new ImportResultDTO(totalInputRows, totalInputRows - failedRowCount, failedRowCount,
                                   autoUpdatedCount, warningCount, null, errors);
    }

    /**
     * Chuẩn hóa mã hợp đồng từ file RP2.
     * File Viettel lưu dạng số thực: 3.20224299E8 → "320224299"
     * Hoặc đã là chuỗi nguyên: "644976020" → "644976020"
     */
    private String normalizeContractCode(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            // Thử parse số khoa học (scientific notation) → long
            double d = Double.parseDouble(raw);
            return String.valueOf((long) d);
        } catch (NumberFormatException e) {
            // Không phải số → trả về nguyên
            return raw.trim();
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < 15; c++) { 
            Cell cell = row.getCell(c);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            // NUMERIC cell không có getStringCellValue() — kiểm tra theo type
            switch (cell.getCellType()) {
                case NUMERIC -> { return false; }
                case BOOLEAN -> { return false; }
                case STRING -> {
                    String val = cell.getStringCellValue();
                    if (val != null && !val.trim().isEmpty()) return false;
                }
                default -> { /* FORMULA, ERROR, BLANK — bỏ qua */ }
            }
        }
        return true;
    }

    // isRowEmptyForReconciliation đã bị loại bỏ:
    // Phương pháp check cột A (STT) theo cell type không đáng tin cậy với StreamingReader
    // vì STT trong file Viettel là shared string (type='s'), dễ bị trả về rỗng.
    // Thay bằng: check trực tiếp cột M (Mã HĐ) trong vòng lặp pass 1.

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
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
                try { yield new BigDecimal(cell.getStringCellValue().replaceAll("[^\\d.-]", "").trim()); }
                catch (Exception e) { yield BigDecimal.ZERO; }
            }
            default -> BigDecimal.ZERO;
        };
    }
}
