package com.appvalidateexcel.excel.service.rules;

import com.appvalidateexcel.excel.model.dto.PhuLucI5;
import com.appvalidateexcel.excel.model.expected.PLI5ExpectedResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;

public class PLI5Calculator {

    public static final BigDecimal LCS = BigDecimal.valueOf(2340000); // 2.340.000 VNĐ

    public static PLI5ExpectedResult calculateExpected(PhuLucI5 data) {
        // Cột 13 = Cột 7 * 2.340.000đ (Chênh lệch bảo lưu)
        BigDecimal cot13 = BigDecimal.ZERO;
        if (data.getHeSoChenhLechBaoLuu() != null && data.getHeSoChenhLechBaoLuu() > 0) {
            cot13 = LCS.multiply(BigDecimal.valueOf(data.getHeSoChenhLechBaoLuu())).setScale(0, RoundingMode.HALF_UP);
        }

        // Cột 14 = Cột 6 * 2.340.000đ (Lương ngạch bậc)
        BigDecimal cot14 = BigDecimal.ZERO;
        if (data.getHeSoLuong() != null && data.getHeSoLuong() > 0) {
            cot14 = LCS.multiply(BigDecimal.valueOf(data.getHeSoLuong())).setScale(0, RoundingMode.HALF_UP);
        }

        // Cột 15 = Cột 8 * 2.340.000đ (Phụ cấp chức vụ)
        BigDecimal cot15 = BigDecimal.ZERO;
        if (data.getHeSoChucVu() != null && data.getHeSoChucVu() > 0) {
            cot15 = LCS.multiply(BigDecimal.valueOf(data.getHeSoChucVu())).setScale(0, RoundingMode.HALF_UP);
        }

        // Cột 16 = [((Cột 10 – 1 tháng) – Cột 9) đơn vị tính năm] * (Cột 14 + Cột 15)
        int diffMonths = 0;
        if (data.getThoiDiemNghi() != null && data.getNhapNgu() != null) {
            Calendar calRetire = Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemNghi());
            calRetire.add(Calendar.MONTH, -1); // lùi 1 tháng

            Calendar calJoin = Calendar.getInstance();
            calJoin.setTime(data.getNhapNgu());

            diffMonths = (calRetire.get(Calendar.YEAR) * 12 + calRetire.get(Calendar.MONTH))
                    - (calJoin.get(Calendar.YEAR) * 12 + calJoin.get(Calendar.MONTH));
            if (diffMonths < 0) diffMonths = 0;
        }

        double soNamNguyen = Math.floor(diffMonths / 12.0);
        double tiLeThamNien = Math.max(0, soNamNguyen / 100.0); // mỗi năm thâm niên tính 1%
        BigDecimal base14_15 = cot14.add(cot15);
        BigDecimal cot16 = base14_15.multiply(BigDecimal.valueOf(tiLeThamNien)).setScale(0, RoundingMode.HALF_UP);

        // Cột 17 = Phụ cấp thâm niên vượt khung (đọc từ Excel nếu có)
        BigDecimal cot17 = data.getPhuCapThamNienVuotKhung() != null ? data.getPhuCapThamNienVuotKhung() : BigDecimal.ZERO;

        // Căn cứ tính PC trách nhiệm, công vụ, đặc thù: (Cột 14 + Cột 15 + Cột 16)
        BigDecimal base14_15_16 = cot14.add(cot15).add(cot16);

        // Cột 18 = Cột 11 * (Cột 14 + Cột 15 + Cột 16)
        double rawC11 = data.getTiLePhuCapTrachNhiem() != null ? data.getTiLePhuCapTrachNhiem() : 0.0;
        double tiLeC11 = (rawC11 > 1.0) ? (rawC11 / 100.0) : rawC11;
        BigDecimal cot18 = base14_15_16.multiply(BigDecimal.valueOf(tiLeC11)).setScale(0, RoundingMode.HALF_UP);

        // Cột 19 = 25% * (Cột 13 + Cột 14 + Cột 15 + Cột 17)
        BigDecimal base13_14_15_17 = cot13.add(cot14).add(cot15).add(cot17);
        BigDecimal cot19 = base13_14_15_17.multiply(BigDecimal.valueOf(0.25)).setScale(0, RoundingMode.HALF_UP);

        // Cột 20 = Cột 12 * (Cột 14 + Cột 15 + Cột 16)
        double rawC12 = data.getTiLePhuCapDacThu() != null ? data.getTiLePhuCapDacThu() : 0.0;
        double tiLeC12 = (rawC12 > 1.0) ? (rawC12 / 100.0) : rawC12;
        BigDecimal cot20 = base14_15_16.multiply(BigDecimal.valueOf(tiLeC12)).setScale(0, RoundingMode.HALF_UP);

        // Cột 21 = Được nhận khác (đọc từ Excel nếu có)
        BigDecimal cot21 = data.getDuocNhanKhac() != null ? data.getDuocNhanKhac() : BigDecimal.ZERO;

        // Cột 22 = SUM(Cột 13 : Cột 21)
        BigDecimal cot22 = cot13.add(cot14).add(cot15).add(cot16).add(cot17).add(cot18).add(cot19).add(cot20).add(cot21);

        return PLI5ExpectedResult.builder()
                .cot13(cot13)
                .cot14(cot14)
                .cot15(cot15)
                .cot16(cot16)
                .cot17(cot17)
                .cot18(cot18)
                .cot19(cot19)
                .cot20(cot20)
                .cot21(cot21)
                .cot22(cot22)
                .diffMonths(diffMonths)
                .soNamThamNien(soNamNguyen)
                .tiLeThamNien(tiLeThamNien)
                .tiLeTrachNhiem(tiLeC11)
                .tiLeDacThu(tiLeC12)
                .build();
    }
}
