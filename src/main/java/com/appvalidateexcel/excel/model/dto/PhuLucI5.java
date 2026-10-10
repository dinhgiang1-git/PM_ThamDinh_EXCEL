package com.appvalidateexcel.excel.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * DTO Phụ lục I.5 — Danh sách quân nhân tính hưởng lương & phụ cấp
 * (Theo form 26.9.PHU_LUC_SUA.xlsx - Phụ lục I.5)
 *
 * C1=STT, C2=Họ tên, C3=Đơn vị, C4=Chức vụ, C5=Cấp bậc
 * C6=Hệ số lương, C7=Hệ số chênh lệch bảo lưu, C8=Hệ số chức vụ
 * C9=Nhập ngũ, C10=Thời điểm nghỉ hưu hưởng trợ cấp
 * C11=Tỉ lệ % phụ cấp trách nhiệm, C12=Tỉ lệ % phụ cấp đặc thù
 * C13=Hệ số chênh lệch bảo lưu (tiền), C14=Tiền lương theo ngạch bậc, C15=Phụ cấp chức vụ
 * C16=Phụ cấp thâm niên nghề, C17=Phụ cấp thâm niên vượt khung
 * C18=Phụ cấp trách nhiệm theo nghề, C19=Phụ cấp công vụ, C20=Phụ cấp đặc thù, C21=Được nhận khác
 * C22=Cộng
 */
@Getter
@Builder
public class PhuLucI5 {
    private int rowIndex;

    // C1..C5: Thông tin nhân thân & đơn vị
    private String stt;
    private String hoTen;
    private String donVi;
    private String chucVu;
    private String capBac;

    // C6..C8: Hệ số
    private Double heSoLuong;              // Cột 6
    private Double heSoChenhLechBaoLuu;    // Cột 7
    private Double heSoChucVu;             // Cột 8

    // C9..C10: Thời gian
    private Date nhapNgu;                  // Cột 9
    private Date thoiDiemNghi;             // Cột 10

    // C11..C12: Tỉ lệ phần trăm
    private Double tiLePhuCapTrachNhiem;   // Cột 11
    private Double tiLePhuCapDacThu;       // Cột 12

    // C13..C22: Tiền thực tế theo báo cáo Excel
    private BigDecimal tienChenhLechBaoLuu;     // Cột 13
    private BigDecimal tienLuongNgachBac;        // Cột 14
    private BigDecimal phuCapChucVu;            // Cột 15
    private BigDecimal phuCapThamNienNghe;      // Cột 16
    private BigDecimal phuCapThamNienVuotKhung; // Cột 17
    private BigDecimal phuCapTrachNhiemNghe;    // Cột 18
    private BigDecimal phuCapCongVu;            // Cột 19
    private BigDecimal phuCapDacThu;            // Cột 20
    private BigDecimal duocNhanKhac;            // Cột 21
    private BigDecimal tongCong;                // Cột 22

    public boolean isBlank() {
        return (hoTen == null || hoTen.trim().isEmpty())
                && (capBac == null || capBac.trim().isEmpty())
                && heSoLuong == null;
    }
}
