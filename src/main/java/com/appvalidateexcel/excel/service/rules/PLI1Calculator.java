package com.appvalidateexcel.excel.service.rules;

import com.appvalidateexcel.excel.model.dto.PhuLucI1;
import com.appvalidateexcel.excel.model.expected.PLI1ExpectedResult;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PLI1Calculator {

    public static PLI1ExpectedResult calculateExpected(PhuLucI1 data) {
        int tran = getTran(data.getCapBac(), data.getChucVu(), data.isNu());
        int rawCot10 = 0;
        if (tran > 0 && data.getNgaySinh() != null && data.getThoiDiemNghiHuuHuongTroCap() != null) {
            java.util.Calendar calDob = java.util.Calendar.getInstance();
            calDob.setTime(data.getNgaySinh());
            int expectedRetireYear = calDob.get(java.util.Calendar.YEAR) + tran;
            int expectedRetireMonth = calDob.get(java.util.Calendar.MONTH); // Trở về đúng tháng sinh, KHÔNG cộng thêm 1
            
            java.util.Calendar calRetire = java.util.Calendar.getInstance();
            calRetire.setTime(data.getThoiDiemNghiHuuHuongTroCap());
            int retireYear = calRetire.get(java.util.Calendar.YEAR);
            int retireMonth = calRetire.get(java.util.Calendar.MONTH);
            
            int diff = (expectedRetireYear * 12 + expectedRetireMonth) - (retireYear * 12 + retireMonth);
            // Tính số tháng nghỉ hưu trước tuổi bao gồm cả 2 đầu tháng (cộng thêm 1)
            rawCot10 = diff > 0 ? diff + 1 : 0;
        }
        if (rawCot10 < 0) rawCot10 = 0;

        // Quy tắc: Nếu cột >= 60 mà giá trị của cột họ ghi = 60 thì vẫn cho là đúng. Bỏ logic so sánh +- 24 tháng ở Sheet I.1
        Integer actualC10 = data.getSoThangNghiHuuTruocTuoiTheoThongTu();
        boolean isCapped60 = (rawCot10 >= 60 && actualC10 != null && actualC10 == 60);
        boolean c10Valid = (actualC10 != null && (actualC10 == rawCot10 || isCapped60));
        int cot10 = isCapped60 ? 60 : rawCot10;

        BigDecimal rawExp11 = calcNamLamTron(rawCot10);
        BigDecimal actualC11 = data.getSoNamNghiHuuTruocTuoiTheoThongTu();
        boolean c11Valid = (actualC11 != null && isEqual(actualC11, rawExp11));
        BigDecimal exp11 = c11Valid ? actualC11 : rawExp11;

        int monthsC12 = calcThang(data.getThoiDiemNghiHuuHuongTroCap(), data.getNhapNgu()) - 1;
        if (monthsC12 < 0) monthsC12 = 0;
        BigDecimal rawExp12 = calcNamBHXH(monthsC12);
        BigDecimal actualC12 = data.getSoNamCongTacDongBHXHTheoThongTu();
        boolean c12Valid = (actualC12 != null && isEqual(actualC12, rawExp12));
        BigDecimal exp12 = c12Valid ? actualC12 : rawExp12;

        BigDecimal luong = data.getLuongThangHienThuongTheoThongTu() != null ? data.getLuongThangHienThuongTheoThongTu() : BigDecimal.ZERO;

        boolean nghiTruoc172025 = true;
        if (data.getThoiDiemNghiHuuHuongTroCap() != null) {
            try {
                Date threshold = new SimpleDateFormat("dd/MM/yyyy").parse("01/07/2025");
                if (!data.getThoiDiemNghiHuuHuongTroCap().before(threshold)) {
                    nghiTruoc172025 = false;
                }
            } catch (Exception ignored) {}
        }

        int timeDiff = 0;
        if (data.getThoiDiemNghiHuuHuongTroCap() != null && data.getThoiGianDonViSapNhapGiaiThe() != null) {
            timeDiff = calcThang(data.getThoiDiemNghiHuuHuongTroCap(), data.getThoiGianDonViSapNhapGiaiThe());
        }
        boolean nhoHon12 = (data.getThoiGianDonViSapNhapGiaiThe() == null || timeDiff <= 12);

        // Nhóm và tiền luôn theo số năm nghỉ sớm thực tế, không khống chế về 5 năm
        boolean isOver60 = (rawCot10 > 60 && rawExp11.compareTo(BigDecimal.valueOf(5)) > 0);
        int cappedCot10 = Math.min(rawCot10, 60);

        BigDecimal expectedCol13 = BigDecimal.ZERO;
        BigDecimal expectedCol14 = BigDecimal.ZERO;
        BigDecimal expectedCol15 = BigDecimal.ZERO;
        BigDecimal expectedCol16 = BigDecimal.ZERO;

        if (nhoHon12) {
            if (!isOver60) {
                expectedCol13 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(1.0)).multiply(luong);
            } else {
                expectedCol14 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.9)).multiply(luong);
            }
        } else {
            if (!isOver60) {
                expectedCol15 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.5)).multiply(luong);
            } else {
                expectedCol16 = BigDecimal.valueOf(cappedCot10).multiply(BigDecimal.valueOf(0.45)).multiply(luong);
            }
        }

        BigDecimal expectedCol17 = BigDecimal.ZERO;
        BigDecimal expectedCol18 = BigDecimal.ZERO;
        BigDecimal expectedCol19 = BigDecimal.ZERO;
        BigDecimal expectedCol20 = BigDecimal.ZERO;
        BigDecimal expectedCol21 = BigDecimal.ZERO;
        BigDecimal expectedCol22 = BigDecimal.ZERO;

        BigDecimal val18_21 = BigDecimal.ZERO;
        BigDecimal val19_22 = BigDecimal.ZERO;
        
        BigDecimal exp12ForMoney = exp12;

        if (nghiTruoc172025) {
            if (exp12ForMoney.compareTo(BigDecimal.valueOf(20)) >= 0) {
                val18_21 = luong.multiply(BigDecimal.valueOf(5));
                if (exp12ForMoney.compareTo(BigDecimal.valueOf(20)) > 0) {
                    val19_22 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(exp12ForMoney.subtract(BigDecimal.valueOf(20)));
                }
            }
        } else {
            if (exp12ForMoney.compareTo(BigDecimal.valueOf(15)) >= 0) {
                val18_21 = luong.multiply(BigDecimal.valueOf(4));
                if (exp12ForMoney.compareTo(BigDecimal.valueOf(15)) > 0) {
                    val19_22 = luong.multiply(BigDecimal.valueOf(0.5)).multiply(exp12ForMoney.subtract(BigDecimal.valueOf(15)));
                }
            }
        }

        // Quy tắc phân bổ Cột 17..22:
        // Cột 17..22: Nếu cột 10 < 24 tháng thì không có dữ liệu (đều bằng 0)
        // 24 <= cột 10 <= 60 (tháng): cột 17, 18, 19 có giá trị
        // Cột 20, 21, 22 sẽ có giá trị nếu cột 10 > 60 và không có kết quả 17, 18, 19
        if (cot10 >= 24 && !isOver60) {
            expectedCol17 = rawExp11.multiply(BigDecimal.valueOf(5)).multiply(luong);
            expectedCol18 = val18_21;
            expectedCol19 = val19_22;
        } else if (isOver60) {
            expectedCol20 = rawExp11.multiply(BigDecimal.valueOf(4)).multiply(luong);
            expectedCol21 = val18_21;
            expectedCol22 = val19_22;
        }

        return PLI1ExpectedResult.builder()
                .cot10(cot10)
                .rawCot10(rawCot10)
                .cot11(exp11)
                .cot12(exp12)
                .cot13(expectedCol13)
                .cot14(expectedCol14)
                .cot15(expectedCol15)
                .cot16(expectedCol16)
                .cot17(expectedCol17)
                .cot18(expectedCol18)
                .cot19(expectedCol19)
                .cot20(expectedCol20)
                .cot21(expectedCol21)
                .cot22(expectedCol22)
                .build();
    }

    private static int calcThang(Date d1, Date d2) {
        if (d1 == null || d2 == null) return 0;
        java.util.Calendar c1 = java.util.Calendar.getInstance(); c1.setTime(d1);
        java.util.Calendar c2 = java.util.Calendar.getInstance(); c2.setTime(d2);
        int m1 = c1.get(java.util.Calendar.YEAR) * 12 + c1.get(java.util.Calendar.MONTH);
        int m2 = c2.get(java.util.Calendar.YEAR) * 12 + c2.get(java.util.Calendar.MONTH);
        return m1 - m2;
    }

    private static BigDecimal calcNamLamTron(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem <= 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
    }

    // Làm tròn số năm công tác đóng BHXH: < 6 tháng dư +0.5, >= 6 tháng dư +1
    private static BigDecimal calcNamBHXH(int thang) {
        if (thang <= 0) return BigDecimal.ZERO;
        int years = thang / 12;
        int rem = thang % 12;
        if (rem == 0) return BigDecimal.valueOf(years);
        if (rem < 6) return BigDecimal.valueOf(years).add(BigDecimal.valueOf(0.5));
        return BigDecimal.valueOf(years).add(BigDecimal.ONE);
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
