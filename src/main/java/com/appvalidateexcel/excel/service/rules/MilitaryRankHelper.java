package com.appvalidateexcel.excel.service.rules;

import java.util.regex.Pattern;

public class MilitaryRankHelper {

    private static final Pattern PATTERN_4_TA = Pattern.compile("(?<!\\d)4\\s*//");
    private static final Pattern PATTERN_3_TA = Pattern.compile("(?<!\\d)3\\s*//");
    private static final Pattern PATTERN_2_TA = Pattern.compile("(?<!\\d)2\\s*//");
    private static final Pattern PATTERN_1_TA = Pattern.compile("(?<!\\d)1\\s*//");

    private static final Pattern PATTERN_4_UY = Pattern.compile("(?<!\\d)4\\s*/(?!/|\\d)");
    private static final Pattern PATTERN_3_UY = Pattern.compile("(?<!\\d)3\\s*/(?!/|\\d)");
    private static final Pattern PATTERN_2_UY = Pattern.compile("(?<!\\d)2\\s*/(?!/|\\d)");
    private static final Pattern PATTERN_1_UY = Pattern.compile("(?<!\\d)1\\s*/(?!/|\\d)");

    /**
     * Xác định trần tuổi phục vụ (trần quân hàm) theo cấp bậc và chức vụ.
     * Hỗ trợ cả tên gọi và kí hiệu:
     * 4// -> Đại tá (58)
     * 3// -> Thượng tá (56)
     * 2// -> Trung tá (54)
     * 1// -> Thiếu tá (52, QNCN: 54)
     * 4/  -> Đại úy (50, QNCN: 52)
     * 3/  -> Thượng úy (50, QNCN: 52)
     * 2/  -> Trung úy (50, QNCN: 52)
     * 1/  -> Thiếu úy (50, QNCN: 52)
     */
    /** Phát hiện giới tính nữ từ chuỗi ngày sinh: định dạng mm/YYYY + ký tự 'N', ví dụ "07/1975N". */
    public static boolean detectIsNu(String rawNgaySinh) {
        if (rawNgaySinh == null) return false;
        return rawNgaySinh.trim().matches(".*\\d{4}\\s*[Nn]\\s*$");
    }

    public static int getTran(String capBac, String chucVu) {
        return getTran(capBac, chucVu, false);
    }

    public static int getTran(String capBac, String chucVu, boolean isNu) {
        if (capBac == null) return 0;
        String cb = capBac.replace('\u00A0', ' ').trim().toLowerCase().replaceAll("\\s+", " ");
        String cv = (chucVu != null) ? chucVu.replace('\u00A0', ' ').trim().toLowerCase().replaceAll("\\s+", " ") : "";
        boolean hasNv = cv.equals("nv") || cv.startsWith("nv ") || cv.startsWith("nv.") || cv.startsWith("nv/")
                     || cv.contains(" nv ") || cv.contains(" nv.") || cv.contains(" nv/") || cv.endsWith(" nv");

        boolean isQNCN = cv.contains("nhân viên") || hasNv
                      || cv.contains("y sĩ") || cv.contains("y sỹ") || cv.contains("y si") || cv.contains("y sy")
                      || cv.contains("thủ kho") || cv.contains("thu kho")
                      || cv.contains("bảo quản kho") || cv.contains("bao quan kho") || cv.contains("bảo quản")
                      || cv.contains("thủy thủ") || cv.contains("thuỷ thủ") || cv.contains("thuy thu")
                      || cv.contains("lái xe") || cv.contains("thợ") 
                      || cv.contains("chạm") || cv.contains("trạm") || cb.contains("qncn") || cv.contains("qncn")
                      || cb.contains(" cn") || cb.contains("/cn") || cb.endsWith("cn") || cb.contains("cia");

        cb = cb.replaceAll("^[-–—\\s]+", "");

        // 24.x: Đại tá (trần 58)
        if (cb.startsWith("24.") || cb.equals("24") || cb.startsWith("24cn") || cb.startsWith("24 cn")) {
            return 58;
        }
        // 23.x: Thượng tá (trần 56; Nữ: 55)
        if (cb.startsWith("23.") || cb.equals("23") || cb.startsWith("23cn") || cb.startsWith("23 cn")) {
            return isNu ? 55 : 56;
        }
        // 22.x: Trung tá (trần 54)
        if (cb.startsWith("22.") || cb.equals("22") || cb.startsWith("22cn") || cb.startsWith("22 cn")) {
            return 54;
        }
        // 21.x: Thiếu tá (trần 52)
        if (cb.startsWith("21.") || cb.equals("21") || cb.startsWith("21cn") || cb.startsWith("21 cn")) {
            return isQNCN ? 54 : 52;
        }
        // 14.x: Đại úy (trần 50, QNCN: 52)
        if (cb.startsWith("14.") || cb.equals("14") || cb.startsWith("14cn") || cb.startsWith("14 cn")) {
            return isQNCN ? 52 : 50;
        }

        // 4//: Đại tá
        if (PATTERN_4_TA.matcher(cb).find() || cb.contains("đại tá") || cb.contains("đai tá")) {
            return 58;
        }
        // 3//: Thượng tá (Nữ: 55)
        if (PATTERN_3_TA.matcher(cb).find() || cb.contains("thượng tá") || cb.contains("thượng tạ") || cb.contains("thuong tá") || cb.contains("thuong ta") || cb.contains("thượng\ntá")) {
            return isNu ? 55 : 56;
        }
        // 2//: Trung tá
        if (PATTERN_2_TA.matcher(cb).find() || cb.contains("trung tá")) {
            return 54;
        }
        // 1//: Thiếu tá
        if (PATTERN_1_TA.matcher(cb).find() || cb.contains("thiếu tá") || cb.contains("thiéu tá") || cb.contains("thiếu ta")) {
            return isQNCN ? 54 : 52;
        }

        // 4/: Đại uý / Đại úy
        if (PATTERN_4_UY.matcher(cb).find() || cb.contains("đại uý") || cb.contains("đại úy")) {
            return isQNCN ? 52 : 50;
        }
        // 3/: Thượng uý / Thượng úy
        if (PATTERN_3_UY.matcher(cb).find() || cb.contains("thượng uý") || cb.contains("thượng úy")) {
            return isQNCN ? 52 : 50;
        }
        // 2/: Trung uý / Trung úy
        if (PATTERN_2_UY.matcher(cb).find() || cb.contains("trung uý") || cb.contains("trung úy")) {
            return isQNCN ? 52 : 50;
        }
        // 1/: Thiếu uý / Thiếu úy
        if (PATTERN_1_UY.matcher(cb).find() || cb.contains("thiếu uý") || cb.contains("thiếu úy")) {
            return isQNCN ? 52 : 50;
        }

        // Cấp úy chung
        if (cb.contains("uý") || cb.contains("úy")) {
            return isQNCN ? 52 : 50;
        }

        // Fallback kiểm tra chức vụ nếu cột cấp bậc ghi chức vụ hoặc tên chung
        if (cv.contains("đại tá") || cv.contains("đai tá")) return 58;
        if (cv.contains("thượng tá") || cv.contains("thuong tá")) return isNu ? 55 : 56;
        if (cv.contains("trung tá")) return 54;
        if (cv.contains("thiếu tá") || cv.contains("thiéu tá")) return isQNCN ? 54 : 52;

        // QNCN / CNQP / VCQP
        if (isQNCN || cb.contains("quân nhân chuyên nghiệp")) {
            return 54;
        }
        if (cb.contains("vcqp") || cb.contains("cnqp") || cb.contains("viên chức quốc phòng") || cb.contains("công nhân quốc phòng") || cb.contains("lao động hợp đồng") || cb.contains("ldhd")) {
            return 60;
        }

        return 0;
    }
}
