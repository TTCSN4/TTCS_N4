
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.MeetingReportResponse;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExcelExportService {

    public byte[] export(List<MeetingReportResponse> meetings) {

        if (meetings == null) {
            throw new IllegalArgumentException(
                    "Danh sach cuoc hop khong duoc null");
        }

        // Excel chi ho tro toi da 1.048.576 dong
        if (meetings.size() > 1_048_573) {
            throw new IllegalArgumentException(
                    "Qua nhieu du lieu de xuat trong mot sheet Excel");
        }

        try (
                XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            Sheet sheet = workbook.createSheet("Bao cao cuoc hop");

            // ===== DINH DANG TIEU DE =====
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(
                    HorizontalAlignment.CENTER);

            // ===== DINH DANG HEADER =====
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(
                    HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(
                    IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(
                    FillPatternType.SOLID_FOREGROUND);

            // ===== DINH DANG NGAY GIO =====
            CellStyle dateStyle = workbook.createCellStyle();

            DataFormat dataFormat =
                    workbook.createDataFormat();

            dateStyle.setDataFormat(
                    dataFormat.getFormat("dd/mm/yyyy hh:mm")
            );

            // ===== DONG 1: TIEU DE =====
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);

            titleCell.setCellValue(
                    "BÁO CÁO SỬ DỤNG VÀ HỦY CUỘC HỌP");
            titleCell.setCellStyle(titleStyle);

            sheet.addMergedRegion(
                    new CellRangeAddress(0, 0, 0, 11));

            titleRow.setHeightInPoints(30);

            // ===== DONG 2: TONG SO CUOC HOP =====
            Row summaryRow = sheet.createRow(1);

            summaryRow.createCell(0).setCellValue(
                    "Tổng số cuộc họp: " + meetings.size());

            // ===== DONG 3: TEN CAC COT =====
            String[] headers = {
                    "STT",
                    "Mã cuộc họp",
                    "Tên cuộc họp",
                    "Mã người tổ chức",
                    "Người tổ chức",
                    "Mã phòng",
                    "Tên phòng",
                    "Thời gian bắt đầu",
                    "Thời gian kết thúc",
                    "Trạng thái",
                    "Lý do hủy",
                    "Thời điểm hủy"
            };

            Row headerRow = sheet.createRow(2);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ===== GHI DU LIEU =====
            for (int i = 0; i < meetings.size(); i++) {

                MeetingReportResponse m = meetings.get(i);

                Row row = sheet.createRow(i + 3);

                row.createCell(0).setCellValue(i + 1);

                setText(row, 1, m.meetingId());
                setText(row, 2, m.title());
                setText(row, 3, m.organizerId());
                setText(row, 4, m.organizerName());
                setText(row, 5, m.roomId());
                setText(row, 6, m.roomName());

                setDate(row, 7, m.startTime(), dateStyle);
                setDate(row, 8, m.endTime(), dateStyle);

                setText(row, 9, m.status());
                setText(row, 10, m.cancelReason());

                setDate(row, 11, m.cancelledAt(), dateStyle);
            }

            // ===== DO RONG CAC COT =====
            int[] widths = {
                    8, 20, 40, 20, 28, 18,
                    28, 22, 22, 18, 35, 22
            };

            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            // Co dinh 3 dong dau
            sheet.createFreezePane(0, 3);

            // Bat bo loc trong Excel
            sheet.setAutoFilter(
                    new CellRangeAddress(
                            2,
                            meetings.size() + 2,
                            0,
                            headers.length - 1
                    )
            );

            workbook.write(output);

            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Khong the xuat bao cao Excel", e);
        }
    }

    // ===== HAM GHI CHUOI =====
    private void setText(
            Row row,
            int column,
            String value) {

        Cell cell = row.createCell(column);

        cell.setCellValue(
                value == null ? "" : value
        );
    }

    // ===== HAM GHI NGAY GIO =====
    private void setDate(
            Row row,
            int column,
            LocalDateTime value,
            CellStyle style) {

        Cell cell = row.createCell(column);

        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }
}
