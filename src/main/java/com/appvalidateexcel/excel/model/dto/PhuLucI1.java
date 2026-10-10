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
public class PhuLucI1 {
    private int rowIndex;

    private String hoTen;
    private Date ngaySinh;
    private boolean isNu;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private Date thoiGianDonViSapNhapGiaiThe;
    private Date thoiDiemNghiHuuHuongTroCap;
    private BigDecimal luongThangHienThuongTheoThongTu;
    private Integer soThangNghiHuuTruocTuoiTheoThongTu;
    private BigDecimal soNamNghiHuuTruocTuoiTheoThongTu;
    private BigDecimal soNamCongTacDongBHXHTheoThongTu;

    //Trợ cấp một lần cho thời gian nghỉ sớm
    private BigDecimal tuoiDoiCon5NamTroXuong1;
    private BigDecimal tuoiDoiConTren5NamDenDuoi10Nam1;
    private BigDecimal tuoiDoiCon5NamTroXuong2;
    private BigDecimal tuoiDoiConTren5NamDenDuoi10Nam2;

    //Trợ cấp nghỉ hưu trước tuổi theo thời gian đóng BHXH và số năm nghỉ sớm
    private BigDecimal G5ThangTienLuongHienHuongCho1NamNghiSom;
    private BigDecimal G5ThangTienLuongHienHuongCho20NamDauCongTac1;
    private BigDecimal tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong1;
    private BigDecimal G4ThangTienLuongHienHuongCho1NamNghi;
    private BigDecimal G5ThangTienLuongHienHuongCho20NamDauCongTac2;
    private BigDecimal tuNamThu21TroDiCuMoiNamCongTacHuong12ThangLuongHienHuong2;
    private BigDecimal tongCongSoTienTheoNghiDinhSo178;
    private BigDecimal tongSoTien;

    public boolean isBlank() {
        return (hoTen == null || hoTen.trim().isEmpty())
                && (capBac == null || capBac.trim().isEmpty())
                && (chucVu == null || chucVu.trim().isEmpty());
    }
}
