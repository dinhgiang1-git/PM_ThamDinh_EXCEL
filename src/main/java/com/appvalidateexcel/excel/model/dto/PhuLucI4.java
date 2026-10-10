package com.appvalidateexcel.excel.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Date;

/**
 * DTO Phụ lục I.4 — Danh sách quân nhân nghỉ do sáp nhập/giải thể/tinh giảm biên chế
 * (Theo form 26.9.PHU_LUC_SUA.xlsx - Phụ lục I.4)
 *
 * Cột: C1=STT, C2=HọTên, C3=NgàySinh, C4=CấpBậc
 *       C5=ĐơnVịSapNhap, C6=ChứcVụSapNhap
 *       C7=ĐơnVịNghỉHưởng, C8=ChứcVụNghỉHưởng
 *       C9=BiênChế, C10=HiệnCó
 *       C11=ChínhQuyềnĐịaPhương, C12=SápNhậpHCKT, C13=TinhGiảmBiênChế
 *       C14=NghỉTheoNĐ177HưởngNĐ178
 *       C15=KếtQuảĐánhGiá, C16=GhiChú
 */
@Getter
@Builder
public class PhuLucI4 {
    private int rowIndex;

    // Thông tin cơ bản
    private String hoTen;
    private Date ngaySinh;
    private String capBac;

    // Đơn vị, chức vụ tại thời điểm sáp nhập/giải thể/tinh giảm
    private String donViSapNhap;
    private String chucVuSapNhap;

    // Đơn vị, chức vụ tại thời điểm nghỉ hưởng chế độ
    private String donViNghiHuong;
    private String chucVuNghiHuong;

    // Tổ chức biên chế tại thời điểm nghỉ chế độ
    private Integer bienChe;
    private Integer hienCo;

    // Đơn vị chịu sự tác động
    private Integer chinhQuyenDiaPhong2Cap;   // C11: Chính quyền địa phương 2 cấp
    private Integer sapNhapHCKTThanhTra;       // C12: Sáp nhập HC-KT; Thanh tra
    private Integer tinhGiamBienCheKhac;       // C13: Tinh giảm biên chế, khác

    // Nghỉ theo NĐ177 hưởng NĐ178
    private String nghiTheoND177HuongND178;    // C14

    // Kết quả đánh giá xếp loại cán bộ (XS, T, HT)
    private String ketQuaDanhGiaXepLoai;       // C15

    // Ghi chú
    private String ghiChu;                     // C16

    public boolean isBlank() {
        return (hoTen == null || hoTen.trim().isEmpty())
                && (capBac == null || capBac.trim().isEmpty())
                && (donViSapNhap == null || donViSapNhap.trim().isEmpty())
                && (donViNghiHuong == null || donViNghiHuong.trim().isEmpty());
    }
}
