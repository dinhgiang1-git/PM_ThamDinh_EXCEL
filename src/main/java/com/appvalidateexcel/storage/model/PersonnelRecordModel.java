package com.appvalidateexcel.storage.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonnelRecordModel {
    private String id;
    private String source; // 'manual', 'excel', 'validated'
    private String importId;
    private Integer sourceRow;
    private String unitId;
    private String categoryCode; // 'SQ', 'QNCN', 'CNVCQP', 'LDHD', 'UNKNOWN'

    @JsonAlias({"sheet", "sheetSource"})
    private String sheetType; // 'I.1', 'I.2', 'I.3', 'I.5'

    private String policyCode; // 'ND178', 'ND177', etc.
    private String classificationSource; // 'EXPLICIT_MARKER', 'UNIT_ALIAS', 'FILENAME', 'INFERRED', 'MANUAL'
    private Double classificationConfidence;

    @JsonAlias({"hoTen"})
    private String fullName;

    @JsonAlias({"ngaySinh"})
    private String birthDate;

    @JsonAlias({"capBac"})
    private String rank;

    @JsonAlias({"chucVu"})
    private String position;

    @JsonAlias({"nhapNgu"})
    private String enlistmentDate;

    @JsonAlias({"recruitmentDate", "sapNhap"})
    private String mergerDate;

    @JsonAlias({"demobilizationDate", "thoiDiemNghi"})
    private String retirementDate;

    @JsonAlias({"luongThang"})
    private Double monthlySalary;

    @JsonAlias({"tongTienThucTe"})
    private Double actualTotal;

    @JsonAlias({"tongTienTinhLai"})
    private Double calculatedTotal;

    @JsonAlias({"diff", "chenhLech"})
    private Double difference;

    private Boolean hasErrors;
    private String rawColumnsJson;
    private String inputJson;
    private String resultJson;
    private String createdAt;
    private String updatedAt;

    // Chi tiết lỗi liên kết
    private List<ValidationErrorModel> errorDetails;

    @JsonAlias({"unit", "donVi"})
    private String unitName;
}
