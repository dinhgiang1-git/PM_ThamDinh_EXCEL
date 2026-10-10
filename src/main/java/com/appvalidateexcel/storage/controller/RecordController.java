package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import com.appvalidateexcel.storage.service.RecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
public class RecordController {

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String unitId,
            @RequestParam(required = false, defaultValue = "branch") String scope,
            @RequestParam(required = false) String sheetType,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size
    ) {
        Map<String, Object> result = recordService.findPaged(source, unitId, scope, sheetType, categoryCode, status, q, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonnelRecordModel> getRecordById(@PathVariable String id) {
        return recordService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrUpdateRecord(@RequestBody PersonnelRecordModel record) {
        recordService.saveRecord(record);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "id", record.getId()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<PersonnelRecordModel>> getAllBySource(
            @RequestParam String source,
            @RequestParam(required = false) String unitId,
            @RequestParam(required = false, defaultValue = "branch") String scope,
            @RequestParam(required = false) String sheetType,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeErrors
    ) {
        return ResponseEntity.ok(recordService.findAllBySource(source, unitId, scope, sheetType, categoryCode, status, q, includeErrors));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Integer>> getCounts() {
        return ResponseEntity.ok(recordService.getCountsBySource());
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchSaveRecords(@RequestBody List<PersonnelRecordModel> records) {
        recordService.saveBatch(records);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "count", records.size()));
    }

    @PostMapping("/replace")
    public ResponseEntity<Map<String, Object>> replaceBySource(
            @RequestParam String source,
            @RequestBody List<PersonnelRecordModel> records
    ) {
        Map<String, Object> result = recordService.replaceSource(source, records);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteRecord(@PathVariable String id) {
        recordService.deleteById(id);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteBySource(@RequestParam(required = false) String source) {
        if (source == null || source.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Tham số 'source' là bắt buộc để ngăn chặn thao tác vô tình xóa toàn bộ dữ liệu."
            ));
        }
        int deleted = recordService.deleteBySource(source);
        return ResponseEntity.ok(Map.of("status", "DELETED", "count", deleted));
    }

    @PostMapping("/export/session")
    public ResponseEntity<?> createExportSession(
            @RequestBody(required = false) com.appvalidateexcel.storage.dto.ExportSessionRequest requestBody,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String unitId,
            @RequestParam(required = false, defaultValue = "branch") String scope,
            @RequestParam(required = false) String sheetType,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        String finalSource = (requestBody != null && requestBody.getSource() != null) ? requestBody.getSource() : source;
        String finalUnitId = (requestBody != null && requestBody.getUnitId() != null) ? requestBody.getUnitId() : unitId;
        String finalScope = (requestBody != null && requestBody.getScope() != null) ? requestBody.getScope() : scope;
        String finalSheetType = (requestBody != null && requestBody.getSheetType() != null) ? requestBody.getSheetType() : sheetType;
        String finalCategoryCode = (requestBody != null && requestBody.getCategoryCode() != null) ? requestBody.getCategoryCode() : categoryCode;
        String finalStatus = (requestBody != null && requestBody.getStatus() != null) ? requestBody.getStatus() : status;
        String finalQ = (requestBody != null && requestBody.getQ() != null) ? requestBody.getQ() : q;

        if (finalSource == null || finalSource.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Tham số 'source' là bắt buộc khi khởi tạo phiên xuất dữ liệu."
            ));
        }

        try {
            Map<String, Object> session = recordService.createExportSession(
                    finalSource, finalUnitId, finalScope, finalSheetType, finalCategoryCode, finalStatus, finalQ
            );
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/export/session/{sessionId}/page")
    public ResponseEntity<?> getExportSessionPage(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "1000") int size,
            @RequestParam(required = false, defaultValue = "true") boolean includeErrors
    ) {
        try {
            Map<String, Object> pageData = recordService.getExportSessionPage(sessionId, page, size, includeErrors);
            return ResponseEntity.ok(pageData);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/export/session/{sessionId}")
    public ResponseEntity<Map<String, String>> closeExportSession(@PathVariable String sessionId) {
        recordService.closeExportSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "CLOSED", "sessionId", sessionId));
    }
}
