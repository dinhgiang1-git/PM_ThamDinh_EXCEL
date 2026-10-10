package com.appvalidateexcel.storage.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Bộ chuẩn hóa tên đơn vị dùng chung toàn hệ thống (BQP 4 cấp).
 * Cung cấp khóa chuẩn (canonical key) duy nhất cho việc nhận diện, gộp và chống trùng dữ liệu.
 */
public final class UnitNameCanonicalizer {

    private UnitNameCanonicalizer() {
        // Utility class
    }

    /**
     * Chuẩn hóa tên đơn vị thành canonical key không dấu, viết thường, chuẩn hóa viết tắt quân sự.
     * Áp dụng đầy đủ cho chữ đ/Đ tiếng Việt, dấu gạch nối, khoảng trắng và số hiệu.
     *
     * @param name tên đơn vị gốc
     * @return khóa chuẩn (canonical key) dùng để đối soát và chống trùng
     */
    public static String canonicalize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        // 1. Xử lý ký tự đ/Đ trước khi phân tách NFD (vì Unicode NFD không phân rã chữ đ/Đ)
        String s = name.replace('đ', 'd').replace('Đ', 'd');

        // 2. Phân rã NFD và xóa tất cả combining marks (dấu thanh, mũ...)
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        // 3. Chuẩn hóa dấu gạch ngang (các loại dash en-dash, em-dash...) thành dấu '-' có khoảng trắng chuẩn
        s = s.replaceAll("[–—]", "-");
        s = s.replaceAll("\\s*-\\s*", " - ");

        // 4. Thu gọn khoảng trắng
        s = s.replaceAll("\\s+", " ").trim();

        // 5. Chuẩn hóa viết tắt quân sự thường gặp
        s = s.replaceAll("\\bban\\s+chi\\s+huy\\b", "ban ch");
        s = s.replaceAll("\\bban\\s+ch\\s+ptkv\\b", "ban chptkv");
        s = s.replaceAll("\\bcuc\\s+hc\\s*-\\s*kt\\b", "cuc hau can - ky thuat");
        s = s.replaceAll("\\bco\\s+quan\\s+tai\\s+chinh\\b", "phong tai chinh");
        s = s.replaceAll("\\bbo\\s+t\\.?\\s*muu\\b", "bo tham muu");

        // 6. Xóa khoảng trắng giữa chữ cái và số hiệu đơn vị (ví dụ: "PTKV 1" -> "ptkv1", "đoàn 452" -> "doan452")
        s = s.replaceAll("([a-z])\\s+([0-9])", "$1$2");

        // 7. Thu gọn lại khoảng trắng lần cuối
        s = s.replaceAll("\\s+", " ").trim();

        return s;
    }
}
