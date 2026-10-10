package com.appvalidateexcel.storage.dto;

import com.appvalidateexcel.storage.model.PersonnelRecordModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportRequest {
    private String commonParentUnitId;
    private Boolean atomic;
    private String operationType;
    private List<BatchFileDto> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchFileDto {
        private String fileName;
        private String fileHash;
        private String displayUnitName;
        private String parentUnitId;
        private String parserMode; // STANDARD_CTC, LEGACY
        private String templateSignature;
        private String duplicateAction; // SKIP, REPLACE, NEW
        private List<BatchInternalUnitDto> internalUnits;
        private List<PersonnelRecordModel> records;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchInternalUnitDto {
        private String name;
        private String code;
    }
}
