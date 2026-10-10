package com.appvalidateexcel.excel.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
public class ErrorRecordDto {
    private String sheetSource; // "I.1", "I.2", "I.3"
    private String donVi; // Bộ cơ quan đơn vị
    private String hoTen;
    private Date ngaySinh;
    private String capBac;
    private String chucVu;
    private Date nhapNgu;
    private Date thoiGianDonViSapNhapGiaiThe;

    // Trường hợp nghỉ hưởng chế độ
    private boolean nghiHuuND178;
    private boolean nghiThoiViec;
    private boolean nghiHuuND177;

    // Lỗi
    private boolean saiThoiGianDuocHuong;
    private boolean saiTongSoTien;
    private BigDecimal tongTienThucTe;
    private BigDecimal tongTienTinhLai;
    private BigDecimal tongTienChenhLech;
    private String noiDungSoTienSai;

    private List<String> errorDetails;

    public BigDecimal getChenhLech() {
        if (tongTienChenhLech != null) {
            return tongTienChenhLech;
        }
        if (tongTienThucTe != null && tongTienTinhLai != null) {
            return tongTienThucTe.subtract(tongTienTinhLai);
        }
        return null;
    }
}
