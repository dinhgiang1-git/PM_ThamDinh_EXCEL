package com.appvalidateexcel.excel.model.dto;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Builder
public class PhuLucI2 {
    private int rowIndex;

    private String hoTen;
    private Date ngaySinh;
    private boolean isNu;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private Date thoiGianDonViSapNhapGiaiThe;
    private Date thoiDiemThoiViecHuongTroCap;
    
    private BigDecimal luongThangHienThuongTheoThongTu;
    private Integer soThangThoiViecTheoThongTu;
    private BigDecimal soNamHuongTroCapTheoThongTu;

    // Nghỉ trong 12 tháng đầu kể từ khi có quyết định sắp xếp
    private BigDecimal troCap1LanChoSoThangCongTacCoDongBHXH1;
    private BigDecimal troCap1LanChoSoNamCongTacDongBHXH1;
    private BigDecimal troCapTaoViecLam1;

    // Nghỉ từ tháng 13 trở đi kể từ khi có quyết định sắp xếp
    private BigDecimal troCap1LanChoSoThangCongTacCoDongBHXH2;
    private BigDecimal troCap1LanChoSoNamCongTacDongBHXH2;
    private BigDecimal troCapTaoViecLam2;

    private BigDecimal tongCongSoTienNghiThoiViecTheoNghiDinhSo178;

    public boolean isBlank() {
        return (hoTen == null || hoTen.trim().isEmpty())
                && (capBac == null || capBac.trim().isEmpty())
                && (chucVu == null || chucVu.trim().isEmpty());
    }
}
