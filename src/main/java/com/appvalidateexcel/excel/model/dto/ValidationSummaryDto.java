package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationSummaryDto {
    private int totalRecords;
    private int validRecords;
    private int invalidRecords;
    private Map<String, Integer> recordsBySheet;
    private Map<String, Integer> recordsByUnit;
    private BigDecimal totalActualAmount;
    private BigDecimal totalExpectedAmount;
    private BigDecimal totalDifference;
    private String totalDifferenceWords;
    private List<ErrorRecordDto> errorRecords;
    private FormulaReadReport formulaReport;
}
