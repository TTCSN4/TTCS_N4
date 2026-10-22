
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.MeetingReportResponse;
import com.ttcs.meetingmanagement.dto.ReportFilterRequest;

import com.ttcs.meetingmanagement.service.ReportService;
import com.ttcs.meetingmanagement.service.ExcelExportService;
import com.ttcs.meetingmanagement.service.PdfExportService;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reports/meetings/export")
public class ReportController {

    private final ReportService reportService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;

    public ReportController(
            ReportService reportService,
            ExcelExportService excelExportService,
            PdfExportService pdfExportService) {

        this.reportService = reportService;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
    }

    // ==========================================
    // US24 - XUAT BAO CAO EXCEL
    // GET /api/reports/meetings/export/excel
    // ==========================================
    @GetMapping(
        value = "/excel",
        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportExcel(
            @ModelAttribute ReportFilterRequest filter) {

        List<MeetingReportResponse> meetings =
                getFilteredMeetings(filter);

        byte[] excelBytes =
                excelExportService.export(meetings);

        return buildDownloadResponse(
                excelBytes,
                "bao_cao_su_dung_huy_hop.xlsx",
                MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
        );
    }

    // ==========================================
    // US24 - XUAT BAO CAO PDF
    // GET /api/reports/meetings/export/pdf
    // ==========================================
    @GetMapping(
        value = "/pdf",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> exportPdf(
            @ModelAttribute ReportFilterRequest filter) {

        List<MeetingReportResponse> meetings =
                getFilteredMeetings(filter);

        byte[] pdfBytes =
                pdfExportService.export(meetings);

        return buildDownloadResponse(
                pdfBytes,
                "bao_cao_su_dung_huy_hop.pdf",
                MediaType.APPLICATION_PDF
        );
    }

    // ==========================================
    // DUNG CHUNG BO LOC CHO EXCEL VA PDF
    // ==========================================
    private List<MeetingReportResponse> getFilteredMeetings(
            ReportFilterRequest filter) {

        if (filter.fromDate() != null
                && filter.toDate() != null
                && filter.fromDate().isAfter(filter.toDate())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "fromDate khong duoc lon hon toDate"
            );
        }

        return reportService.getMeetingReport(filter);
    }

    // ==========================================
    // TAO HTTP RESPONSE DE TAI FILE
    // ==========================================
    private ResponseEntity<byte[]> buildDownloadResponse(
            byte[] data,
            String fileName,
            MediaType mediaType) {

        String disposition = ContentDisposition.attachment()
                .filename(fileName)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition
                )
                .contentType(mediaType)
                .contentLength(data.length)
                .cacheControl(CacheControl.noStore())
                .body(data);
    }
}
