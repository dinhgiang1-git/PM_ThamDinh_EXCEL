package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.dto.BatchImportRequest;
import com.appvalidateexcel.storage.dto.BatchImportResult;
import com.appvalidateexcel.storage.model.ImportModel;
import com.appvalidateexcel.storage.service.ImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/commit-batch")
    public ResponseEntity<BatchImportResult> commitBatch(@RequestBody BatchImportRequest request) {
        BatchImportResult result = importService.commitBatch(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<ImportModel>> getAllImports() {
        return ResponseEntity.ok(importService.getAllImports());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImportModel> getImportById(@PathVariable String id) {
        return importService.getImportById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-hash/{hash}")
    public ResponseEntity<ImportModel> getImportByHash(@PathVariable String hash) {
        return importService.getImportByHash(hash)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteImport(@PathVariable String id) {
        importService.deleteImport(id);
        return ResponseEntity.ok(Map.of("status", "DELETED", "id", id));
    }
}
