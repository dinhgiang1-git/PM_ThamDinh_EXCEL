package com.appvalidateexcel.excel.model.expected;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PLI2ExpectedResult {
    private Integer cot10; // Số tháng thôi việc: Cột 8 - Cột 5
    private int rawCot10;
    private BigDecimal cot11; // Số năm hưởng trợ cấp: Cột 10 / 12 làm tròn (<=0.5 -> +0.5, >0.5 -> +1)
    private BigDecimal cot12; // Cột 10 * 0.8 * Cột 9 (Nghỉ trong 12 tháng đầu & tuổi đời > 2 năm)
    private BigDecimal cot13; // Cột 11 * 1.5 * Cột 9 (Nghỉ trong 12 tháng đầu & tuổi đời > 2 năm)
    private BigDecimal cot14; // 3 * Cột 9 (Nghỉ trong 12 tháng đầu & tuổi đời > 2 năm)
    private BigDecimal cot15; // Cột 10 * 0.4 * Cột 9 (Nghỉ từ tháng 13 trở đi & tuổi đời > 2 năm)
    private BigDecimal cot16; // Cột 11 * 1.5 * Cột 9 (Nghỉ từ tháng 13 trở đi & tuổi đời > 2 năm)
    private BigDecimal cot17; // 3 * Cột 9 (Nghỉ từ tháng 13 trở đi & tuổi đời > 2 năm)
    private BigDecimal cot18; // Tổng cộng: SUM(12..17)

    private boolean isWithin12Months;
    private boolean isOver2Years;
    private boolean isEligibleByAge;
    private int distance8_7;
    private int thangConLai;
    private int monthsCongTac;
    private int maxThangThoiViec;
}
