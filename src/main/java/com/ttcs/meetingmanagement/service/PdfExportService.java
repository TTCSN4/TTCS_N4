
package com.ttcs.meetingmanagement.service;

import com.ttcs.meetingmanagement.dto.MeetingReportResponse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfExportService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${report.pdf.font-path:}")
    private String configuredFontPath;

    public byte[] export(List<MeetingReportResponse> meetings) {

        if (meetings == null) {
            throw new IllegalArgumentException(
                    "Danh sách cuộc họp không được null");
        }

        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            Path fontPath = findFont();

            PDType0Font font = PDType0Font.load(
                    document,
                    fontPath.toFile()
            );

            try (PdfWriter writer = new PdfWriter(document, font)) {

                writer.write(
                        "BÁO CÁO SỬ DỤNG VÀ HỦY CUỘC HỌP",
                        16
                );

                writer.write(
                        "Tổng số cuộc họp: " + meetings.size(),
                        11
                );

                long cancelledCount = meetings.stream()
                        .filter(m -> "CANCELLED"
                                .equalsIgnoreCase(m.status()))
                        .count();

                writer.write(
                        "Số cuộc họp đã hủy: " + cancelledCount,
                        11
                );

                writer.space(15);

                if (meetings.isEmpty()) {
                    writer.write(
                            "Không có dữ liệu phù hợp với bộ lọc.",
                            11
                    );
                }

                int index = 1;

                for (MeetingReportResponse m : meetings) {

                    writer.write(
                            index++ + ". " + safe(m.title()),
                            13
                    );

                    writer.write(
                            "Mã cuộc họp: " + safe(m.meetingId()),
                            10
                    );

                    writer.write(
                            "Người tổ chức: "
                                    + safe(m.organizerName())
                                    + " (" + safe(m.organizerId()) + ")",
                            10
                    );

                    writer.write(
                            "Phòng họp: "
                                    + safe(m.roomName())
                                    + " (" + safe(m.roomId()) + ")",
                            10
                    );

                    writer.write(
                            "Bắt đầu: " + formatDate(m.startTime()),
                            10
                    );

                    writer.write(
                            "Kết thúc: " + formatDate(m.endTime()),
                            10
                    );

                    writer.write(
                            "Trạng thái: " + safe(m.status()),
                            10
                    );

                    writer.write(
                            "Lý do hủy: " + safe(m.cancelReason()),
                            10
                    );

                    writer.write(
                            "Thời điểm hủy: "
                                    + formatDate(m.cancelledAt()),
                            10
                    );

                    writer.space(12);
                }
            }

            document.save(output);
            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể xuất báo cáo PDF", e
            );
        }
    }

    private Path findFont() {

        if (configuredFontPath != null
                && !configuredFontPath.isBlank()) {

            Path configured = Path.of(configuredFontPath);

            if (Files.isRegularFile(configured)) {
                return configured;
            }

            throw new IllegalStateException(
                    "Không tìm thấy font PDF: " + configured
            );
        }

        // Ưu tiên font Unicode có sẵn trên Windows.
        List<Path> candidates = List.of(
                Path.of("C:\\Windows\\Fonts\\tahoma.ttf"),
                Path.of("C:\\Windows\\Fonts\\arial.ttf"),
                Path.of("C:\\Windows\\Fonts\\segoeui.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Không tìm thấy font Unicode để xuất PDF. "
                + "Hãy cấu hình report.pdf.font-path"
        );
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
    }

    private static String formatDate(LocalDateTime value) {
        return value == null
                ? "-"
                : value.format(DATE_FORMAT);
    }

    private static class PdfWriter implements AutoCloseable {

        private static final float MARGIN = 45;
        private static final float LINE_GAP = 6;

        private final PDDocument document;
        private final PDType0Font font;

        private PDPageContentStream content;
        private float y;

        PdfWriter(
                PDDocument document,
                PDType0Font font) throws IOException {

            this.document = document;
            this.font = font;

            newPage();
        }

        private void newPage() throws IOException {

            if (content != null) {
                content.close();
            }

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            content = new PDPageContentStream(
                    document,
                    page
            );

            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void write(String value, float fontSize)
                throws IOException {

            float maxWidth =
                    PDRectangle.A4.getWidth() - 2 * MARGIN;

            for (String line : wrap(
                    safe(value),
                    fontSize,
                    maxWidth)) {

                if (y < MARGIN + fontSize) {
                    newPage();
                }

                content.beginText();
                content.setFont(font, fontSize);
                content.newLineAtOffset(MARGIN, y);
                content.showText(line);
                content.endText();

                y -= fontSize + LINE_GAP;
            }
        }

        void space(float height) {
            y -= height;
        }

        private List<String> wrap(
                String text,
                float fontSize,
                float maxWidth) throws IOException {

            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String word : text.split("\\s+")) {

                String candidate = current.isEmpty()
                        ? word
                        : current + " " + word;

                float width = font.getStringWidth(candidate)
                        / 1000f * fontSize;

                if (width <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }

                    // Tách cả chuỗi dài không chứa khoảng trắng.
                    for (int offset = 0;
                         offset < word.length();) {

                        int cp = word.codePointAt(offset);
                        String character =
                                new String(Character.toChars(cp));

                        String next = current.toString() + character;

                        float nextWidth = font.getStringWidth(next)
                                / 1000f * fontSize;

                        if (nextWidth > maxWidth
                                && !current.isEmpty()) {

                            lines.add(current.toString());
                            current.setLength(0);
                        }

                        current.append(character);
                        offset += Character.charCount(cp);
                    }
                }
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
            }

            if (lines.isEmpty()) {
                lines.add(" ");
            }

            return lines;
        }

        @Override
        public void close() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }
}
