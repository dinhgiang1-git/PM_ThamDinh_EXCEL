package com.appvalidateexcel.excel.model.expected;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Kết quả tính chuẩn cho Phụ lục I.5:
 * Lương tháng hiện hưởng làm căn cứ tính hưởng chế độ theo NĐ 177, NĐ 178, NĐ 67.
 */
@Data
@Builder
public class PLI5ExpectedResult {
    private BigDecimal cot13; // Cột 13 = Cột 7 * 2.340.000đ (Chênh lệch bảo lưu)
    private BigDecimal cot14; // Cột 14 = Cột 6 * 2.340.000đ (Tiền lương ngạch bậc)
    private BigDecimal cot15; // Cột 15 = Cột 8 * 2.340.000đ (Phụ cấp chức vụ)
    private BigDecimal cot16; // Cột 16 = [((Cột 10 – 1 tháng) – Cột 9) đơn vị năm] * (Cột 14 + Cột 15)
    private BigDecimal cot17; // Cột 17 = Phụ cấp thâm niên vượt khung (đọc từ Excel nếu có)
    private BigDecimal cot18; // Cột 18 = Cột 11 * (Cột 14 + Cột 15 + Cột 16)
    private BigDecimal cot19; // Cột 19 = 25% * (Cột 14 + Cột 15 + Cột 16)
    private BigDecimal cot20; // Cột 20 = Cột 12 * (Cột 14 + Cột 15 + Cột 16)
    private BigDecimal cot21; // Cột 21 = Được nhận khác
    private BigDecimal cot22; // Cột 22 = SUM(Cột 13 : Cột 21)

    private Integer diffMonths;
    private Double soNamThamNien;
    private Double tiLeThamNien;
    private Double tiLeTrachNhiem;
    private Double tiLeDacThu;
}
