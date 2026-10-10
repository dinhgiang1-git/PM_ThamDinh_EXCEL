package com.appvalidateexcel.storage.controller;

import com.appvalidateexcel.storage.service.BackupService;
import com.appvalidateexcel.storage.service.DatabaseIntegrityService;
import com.appvalidateexcel.storage.service.LocalStorageMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final DatabaseIntegrityService integrityService;
    private final LocalStorageMigrationService migrationService;
    private final BackupService backupService;

    public StorageController(
            DatabaseIntegrityService integrityService,
            LocalStorageMigrationService migrationService,
            BackupService backupService) {
        this.integrityService = integrityService;
        this.migrationService = migrationService;
        this.backupService = backupService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(integrityService.getStorageStatus());
    }

    @GetMapping("/integrity")
    public ResponseEntity<Map<String, Object>> getIntegrity() {
        return ResponseEntity.ok(integrityService.checkIntegrity());
    }

    @PostMapping("/migrate-local-storage")
    public ResponseEntity<Map<String, Object>> migrateLocalStorage(@RequestBody Map<String, Object> payload) {
        try {
            Map<String, Object> result = migrationService.migrate(payload);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> createBackup() {
        return ResponseEntity.ok(backupService.createBackup());
    }

    @GetMapping("/backups")
    public ResponseEntity<java.util.List<Map<String, Object>>> getBackups() {
        return ResponseEntity.ok(backupService.listBackups());
    }

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreBackup(@RequestBody Map<String, String> request) {
        String backupFile = request.get("backupFile");
        Map<String, Object> result = backupService.restore(backupFile);
        if ("SUCCESS".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
