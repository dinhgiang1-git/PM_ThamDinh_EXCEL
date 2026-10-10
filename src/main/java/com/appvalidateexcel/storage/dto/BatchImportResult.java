package com.appvalidateexcel.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult {
    private int totalFiles;
    private int successCount;
    private int skippedCount;
    private int failedCount;
    private Boolean atomic;
    private Boolean committed;
    private Boolean rolledBack;
    private String message;
    private List<FileResult> fileResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileResult {
        private String fileName;
        private String displayUnitName;
        private String status; // SUCCESS, SKIPPED, FAILED, REPLACED
        private String importId;
        private String fileUnitId;
        private int internalUnitsCount;
        private int recordsCount;
        private String message;
    }
}
