import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.FileOutputStream;

public class CreateSampleExcel {
    public static void main(String[] args) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Tư vấn viên");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Tên đăng nhập (*)");
            headerRow.createCell(1).setCellValue("Họ tên (*)");
            headerRow.createCell(2).setCellValue("Số điện thoại");
            headerRow.createCell(3).setCellValue("Mật khẩu (*)");

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("nguyenvana");
            dataRow.createCell(1).setCellValue("Nguyễn Văn A");
            dataRow.createCell(2).setCellValue("0987654321");
            dataRow.createCell(3).setCellValue("Password123!");

            try (FileOutputStream fileOut = new FileOutputStream("tvv.xlsx")) {
                workbook.write(fileOut);
            }
            System.out.println("Tạo file tvv.xlsx thành công!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
