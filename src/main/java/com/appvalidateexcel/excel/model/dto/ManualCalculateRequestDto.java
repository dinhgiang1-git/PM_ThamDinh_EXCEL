package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCalculateRequestDto {
    /**
     * "I.1" (Nghỉ hưu NĐ 178), "I.2" (Thôi việc NĐ 178), "I.3" (Nghỉ hưu NĐ 177)
     */
    private String sheetType;

    private String hoTen;
    private String ngaySinh; // dd/MM/yyyy hoặc MM/yyyy
    private String nhapNgu;  // dd/MM/yyyy hoặc MM/yyyy
    private String thoiDiemNghi; // Ngày nghỉ hưu hoặc ngày thôi việc
    private String thoiGianDonViSapNhapGiaiThe; // Dành cho Phụ lục I.2
    private String capBac;
    private String chucVu;
    private BigDecimal luongThang;
    private Integer tranTuoi; // Tuỳ chọn, nếu không nhập hệ thống tự tính theo trần quân hàm
}
