package com.appvalidateexcel.excel.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class VietnameseNumberToWords {

    private static final String[] DIGITS = {
            "không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"
    };

    private static final String[] UNITS = {
            "", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"
    };

    /**
     * Chuyển đổi số tiền thành chữ tiếng Việt (kèm "đồng chẵn").
     */
    public static String toWords(BigDecimal amount) {
        if (amount == null) return "Không đồng";
        return toWords(amount.setScale(0, RoundingMode.HALF_UP).longValue());
    }

    public static String toWords(long number) {
        if (number == 0) {
            return "Không đồng";
        }

        boolean isNegative = number < 0;
        long absNumber = Math.abs(number);

        StringBuilder sb = new StringBuilder();
        int unitIndex = 0;

        while (absNumber > 0) {
            int block = (int) (absNumber % 1000);
            if (block > 0) {
                String blockWords = readThreeDigits(block, absNumber >= 1000);
                String unit = UNITS[unitIndex];
                if (!unit.isEmpty()) {
                    blockWords += " " + unit;
                }
                if (sb.length() > 0) {
                    sb.insert(0, blockWords + " ");
                } else {
                    sb.insert(0, blockWords);
                }
            }
            absNumber /= 1000;
            unitIndex++;
        }

        String result = sb.toString().trim().replaceAll("\\s+", " ");
        if (result.isEmpty()) {
            return "Không đồng";
        }

        // Viết hoa chữ cái đầu tiên
        result = Character.toUpperCase(result.charAt(0)) + result.substring(1) + " đồng chẵn";
        if (isNegative) {
            result = "Âm " + Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }

    private static String readThreeDigits(int n, boolean hasHigherGroup) {
        int hundreds = n / 100;
        int tens = (n % 100) / 10;
        int units = n % 10;

        StringBuilder sb = new StringBuilder();

        if (hundreds > 0 || hasHigherGroup) {
            sb.append(DIGITS[hundreds]).append(" trăm ");
        }

        if (tens > 1) {
            sb.append(DIGITS[tens]).append(" mươi ");
            if (units == 1) {
                sb.append("mốt");
            } else if (units == 5) {
                sb.append("lăm");
            } else if (units > 0) {
                sb.append(DIGITS[units]);
            }
        } else if (tens == 1) {
            sb.append("mười ");
            if (units == 5) {
                sb.append("lăm");
            } else if (units > 0) {
                sb.append(DIGITS[units]);
            }
        } else {
            // tens == 0
            if (units > 0) {
                if (hundreds > 0 || hasHigherGroup) {
                    sb.append("lẻ ");
                }
                sb.append(DIGITS[units]);
            }
        }

        return sb.toString().trim();
    }
}
