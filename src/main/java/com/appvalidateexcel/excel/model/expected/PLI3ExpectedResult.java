package com.appvalidateexcel.excel.model.expected;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PLI3ExpectedResult {
    private BigDecimal cot10; // Số năm nghỉ hưu trước tuổi
    private BigDecimal cot11; // Số năm công tác
    private BigDecimal cot12; // Cột 11 * 5 * Lương
    private BigDecimal cot13; // Lương * 5
    private BigDecimal cot14; // Lương * 0.5 * (Cột 11 - 20)
    private BigDecimal cot15; // Cột 12 + Cột 13 + Cột 14
}
