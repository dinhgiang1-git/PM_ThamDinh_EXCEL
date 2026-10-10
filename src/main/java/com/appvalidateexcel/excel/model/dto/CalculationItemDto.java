package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculationItemDto {
    private int colIndex;
    private String colName;
    private String title;
    private Object value;
    private String formattedValue;
    private String unit;
    private String formula;
}
