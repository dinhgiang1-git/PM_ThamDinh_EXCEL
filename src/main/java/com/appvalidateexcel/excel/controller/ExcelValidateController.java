package com.appvalidateexcel.excel.controller;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor

public class ExcelValidateController {

    private final ExcelValidationService validationService;

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> validateExcel(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }

            // Gọi service để xử lý file và lấy output (đã ghi đè danh sách lỗi vào Phụ lục II)
            byte[] outputExcelBytes = validationService.validateAndGenerateErrorReport(file.getInputStream());

            // Trả về file Excel cho người dùng tải xuống
            String safeFileName = java.net.URLEncoder.encode("Result_" + file.getOriginalFilename(), "UTF-8").replaceAll("\\+", "%20");
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + safeFileName);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(outputExcelBytes);

        } catch (Exception e) {
            log.error("Lỗi khi validate file excel", e);
            String errMsg = publicMessage(e);
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(errMsg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @PostMapping(value = "/summary", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> getValidationSummary(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File không được rỗng");
            }
            ValidationSummaryDto summary = validationService.validateAndGetSummary(file.getInputStream());
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Lỗi khi tổng hợp báo cáo thẩm định", e);
            return ResponseEntity.status(500).body("Lỗi: " + publicMessage(e));
        }
    }

    /** Chỉ trả nội dung lỗi nghiệp vụ (file sai mẫu...) cho người dùng; lỗi hệ thống thì trả thông báo chung, chi tiết nằm trong log. */
    private static String publicMessage(Exception e) {
        if ((e instanceof IllegalArgumentException || e instanceof IllegalStateException)
                && e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        return "Không thể xử lý file Excel. Vui lòng kiểm tra lại định dạng/nội dung file.";
    }
}
