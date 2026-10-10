package com.appvalidateexcel.excel.service.rules;

import com.appvalidateexcel.excel.model.dto.PhuLucI2;
import com.appvalidateexcel.excel.model.expected.PLI2ExpectedResult;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;

public class PLI2Calculator {

    public static PLI2ExpectedResult calculateExpected(PhuLucI2 data) {
        // Trần quân hàm
        int tran = getTran(data.getCapBac(), data.getChucVu(), data.isNu());

        // Tuổi đời còn lại = (trần quân hàm + Cột 2) - Cột 8
        int rawThangConLai = 0;
        if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemThoiViecHuongTroCap() != null) {
            Calendar calDob = Calendar.getInstance();
            calDob.setTime(data.getNgaySinh());
            int dobMonth = calDob.get(Calendar.YEAR) * 12 + calDob.get(Calendar.MONTH);

            Calendar calRetire = Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemThoiViecHuongTroCap());
            int retireMonth = calRetire.get(Calendar.YEAR) * 12 + calRetire.get(Calendar.MONTH);

            rawThangConLai = (dobMonth + tran * 12) - retireMonth;
        }

        // Cột 11 = Cột 8 - Cột 5 = ...(năm) (làm tròn: <= 0.5 -> +0.5, > 0.5 -> +1)
        int monthsCongTac = 0;
        if (data.getThoiDiemThoiViecHuongTroCap() != null && data.getNhapNgu() != null) {
            monthsCongTac = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getNhapNgu());
            if (monthsCongTac < 0) monthsCongTac = 0;
        }
        BigDecimal rawCot11 = calcNamLamTron(monthsCongTac);
        BigDecimal cot11 = rawCot11;

        // Cột 10: Số tháng thôi việc (khống chế tối đa 60 tháng theo NĐ 178; trường hợp thời gian đóng BHXH dưới 5 năm/60 tháng thì bằng đúng thời gian công tác thực tế có đóng BHXH)
        int maxThangThoiViec = (monthsCongTac > 0 && monthsCongTac < 60) ? monthsCongTac : 60;
        int rawCot10 = (rawThangConLai > 0) ? Math.min(rawThangConLai, maxThangThoiViec) : maxThangThoiViec;
        // Quy tắc: Nếu dữ liệu thẩm định >= 60 mà trong file excel nhập vào chỉ có 60 thì coi như đúng
        Integer actualC10 = data.getSoThangThoiViecTheoThongTu();
        boolean isCapped60 = ((rawThangConLai >= 60 || rawCot10 >= 60) && actualC10 != null && actualC10 == 60);
        int cot10 = isCapped60 ? 60 : rawCot10;
        int thangConLai = rawThangConLai;

        // Điều kiện tuổi đời còn lại từ đủ 2 năm (24 tháng) trở lên theo Khoản 1 Điều 10 TT 19
        boolean isEligibleByAge = thangConLai >= 24;
        boolean isOver2Years = isEligibleByAge;

        // Cột 8 - Cột 7 (khoảng cách tháng so với thời gian sáp nhập, giải thể)
        int distance8_7 = 0;
        if (data.getThoiDiemThoiViecHuongTroCap() != null && data.getThoiGianDonViSapNhapGiaiThe() != null) {
            distance8_7 = calcThang(data.getThoiDiemThoiViecHuongTroCap(), data.getThoiGianDonViSapNhapGiaiThe());
        }

        // Nhóm nghỉ trong 12 tháng đầu vs nghỉ từ tháng 13 trở đi
        boolean isWithin12Months = (data.getThoiGianDonViSapNhapGiaiThe() == null || distance8_7 <= 12);

        BigDecimal luong = data.getLuongThangHienThuongTheoThongTu() != null ? data.getLuongThangHienThuongTheoThongTu() : BigDecimal.ZERO;

        BigDecimal cot12 = BigDecimal.ZERO;
        BigDecimal cot13 = BigDecimal.ZERO;
        BigDecimal cot14 = BigDecimal.ZERO;
        BigDecimal cot15 = BigDecimal.ZERO;
        BigDecimal cot16 = BigDecimal.ZERO;
        BigDecimal cot17 = BigDecimal.ZERO;

        if (isEligibleByAge) {
            if (isWithin12Months) {
                // Cột 12 = Cột 10 * 0.8 tháng * Cột 9
                cot12 = BigDecimal.valueOf(cot10).multiply(BigDecimal.valueOf(0.8)).multiply(luong);
                // Cột 13 = Cột 11 * 1.5 tháng * Cột 9
                cot13 = cot11.multiply(BigDecimal.valueOf(1.5)).multiply(luong);
                // Cột 14 = 3 tháng * Cột 9
                cot14 = BigDecimal.valueOf(3).multiply(luong);
            } else {
                // Cột 15 = Cột 10 * 0.4 tháng * Cột 9
                cot15 = BigDecimal.valueOf(cot10).multiply(BigDecimal.valueOf(0.4)).multiply(luong);
                // Cột 16 = Cột 11 * 1.5 tháng * Cột 9
                cot16 = cot11.multiply(BigDecimal.valueOf(1.5)).multiply(luong);
                // Cột 17 = 3 tháng * Cột 9
                cot17 = BigDecimal.valueOf(3).multiply(luong);
            }
        }

        // Cột 18 = SUM(12..17)
        BigDecimal cot18 = cot12.add(cot13).add(cot14).add(cot15).add(cot16).add(cot17);

        return PLI2ExpectedResult.builder()
                .cot10(cot10)
                .rawCot10(rawCot10)
                .cot11(cot11)
                .cot12(cot12)
                .cot13(cot13)
                .cot14(cot14)
                .cot15(cot15)
                .cot16(cot16)
                .cot17(cot17)
                .cot18(cot18)
                .isWithin12Months(isWithin12Months)
                .isOver2Years(isOver2Years)
                .isEligibleByAge(isEligibleByAge)
                .distance8_7(distance8_7)
                .thangConLai(thangConLai)
                .monthsCongTac(monthsCongTac)
                .maxThangThoiViec(maxThangThoiViec)
                .build();
    }

    public static BigDecimal calcNamLamTron(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem <= 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
    }

    private static int calcThang(Date d1, Date d2) {
        if (d1 == null || d2 == null) return 0;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        int m1 = c1.get(Calendar.YEAR) * 12 + c1.get(Calendar.MONTH);
        int m2 = c2.get(Calendar.YEAR) * 12 + c2.get(Calendar.MONTH);
        return m1 - m2;
    }

    public static int getTran(String capBac, String chucVu) {
        return MilitaryRankHelper.getTran(capBac, chucVu);
    }

    public static int getTran(String capBac, String chucVu, boolean isNu) {
        return MilitaryRankHelper.getTran(capBac, chucVu, isNu);
    }


    private static boolean isEqual(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.setScale(1, java.math.RoundingMode.HALF_UP).compareTo(b.setScale(1, java.math.RoundingMode.HALF_UP)) == 0;
    }
}
