package com.appvalidateexcel.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportModel {
    private String id;
    private String fileName;
    private String fileHash;
    private String storedFilePath;
    private String status; // PENDING, COMPLETED, FAILED, PREVIEW, SUCCESS, REPLACED
    private Integer totalRows;
    private Integer acceptedRows;
    private Integer rejectedRows;
    private String errorSummary;
    private String parentUnitId;
    private String fileUnitId;
    private String originalFileName;
    private String displayUnitName;
    private String parserMode;
    private String templateSignature;
    private String createdAt;
    private String completedAt;
    private Integer currentRecordCount;
}
