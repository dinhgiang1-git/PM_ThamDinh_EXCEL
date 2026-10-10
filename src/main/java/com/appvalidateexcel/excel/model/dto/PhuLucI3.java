package com.appvalidateexcel.excel.model.dto;

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
public class PhuLucI3 {
    private int rowIndex;

    private String hoTen;
    private Date ngaySinh;
    private boolean isNu;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private Date thoiGianDonViSapNhapGiaiThe;
    private Date thoiDiemNghiHuuHuongTroCap;
    
    private BigDecimal luongThangHienThuongTheoHuongDan;
    private BigDecimal soThangThoiViecTheoHuongDan;
    private BigDecimal soNamHuongTroCapTheoHuongDan;

    // Tuổi đời từ đủ 12 tháng đến 60 tháng đến tuổi nghỉ hưu
    private BigDecimal G5ThangTienLuongHienHuongCho1NamNghiSom;
    private BigDecimal G5ThangTienLuongHienHuongCho20NamDauCongTac;
    private BigDecimal tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong;

    private BigDecimal tongCongSoTienNghiThoiViecTheoNghiDinhSo177;

    public boolean isBlank() {
        return (hoTen == null || hoTen.isEmpty())
                && (capBac == null || capBac.isEmpty())
                && (chucVu == null || chucVu.isEmpty());
    }
}
