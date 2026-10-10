package com.appvalidateexcel.excel.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualCalculateResponseDto {
    private boolean success;
    private String sheetType;
    private String sheetTitle;
    private String hoTen;
    private String capBac;
    private String chucVu;
    private int tranTuoi;
    private boolean nghiTruoc172025;
    private List<CalculationItemDto> items;
    private BigDecimal totalAmount;
    private String formattedTotalAmount;
    private String totalAmountWords;
    private List<String> notes;
    private String errorMessage;
}
