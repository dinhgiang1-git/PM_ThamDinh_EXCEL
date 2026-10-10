package com.appvalidateexcel.excel.model.expected;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PLI1ExpectedResult {
    private int cot10;
    private int rawCot10;
    private BigDecimal cot11;
    private BigDecimal cot12;
    private BigDecimal cot13;
    private BigDecimal cot14;
    private BigDecimal cot15;
    private BigDecimal cot16;
    private BigDecimal cot17;
    private BigDecimal cot18;
    private BigDecimal cot19;
    private BigDecimal cot20;
    private BigDecimal cot21;
    private BigDecimal cot22;

    public BigDecimal getCot23() {
        BigDecimal sum = BigDecimal.ZERO;
        if (cot13 != null) sum = sum.add(cot13);
        if (cot14 != null) sum = sum.add(cot14);
        if (cot15 != null) sum = sum.add(cot15);
        if (cot16 != null) sum = sum.add(cot16);
        if (cot17 != null) sum = sum.add(cot17);
        if (cot18 != null) sum = sum.add(cot18);
        if (cot19 != null) sum = sum.add(cot19);
        if (cot20 != null) sum = sum.add(cot20);
        if (cot21 != null) sum = sum.add(cot21);
        if (cot22 != null) sum = sum.add(cot22);
        return sum;
    }
}
