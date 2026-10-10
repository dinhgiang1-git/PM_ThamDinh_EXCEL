package com.appvalidateexcel.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitModel {
    private String id;
    private String code;
    private String name;
    private String normalizedName;
    private Integer level; // 1: Cục Tài chính (BQP), 2: Đầu mối BQP, 3: Sư/Lữ/Tỉnh, 4: Trung đoàn/Huyện
    private String unitType;
    private String parentId;
    private Integer displayOrder;
    private String aliasesJson;
    private Boolean isPreset;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;

    // Các trường quan hệ / hỗ trợ hiển thị
    private List<UnitModel> children;
    private Integer directRecordCount;
    private Integer branchRecordCount;
}
