package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.PhuLucI4;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Parser cho Phụ lục I.4 (form 26.9.PHU_LUC_SUA.xlsx):
 *   C1=STT, C2=HọTên, C3=NgàySinh, C4=CấpBậc
 *   C5=ĐơnVị(sápNhập), C6=ChứcVụ(sápNhập)
 *   C7=ĐơnVị(nghỉHưởng), C8=ChứcVụ(nghỉHưởng)
 *   C9=BiênChế, C10=HiệnCó
 *   C11=ChínhQuyềnĐP2Cấp, C12=SápNhậpHCKT, C13=TinhGiảm
 *   C14=NghỉNĐ177HưởngNĐ178
 *   C15=KếtQuảĐánhGiá, C16=GhiChú
 *
 * Logic nghiệp vụ (tính tiền) sẽ bổ sung sau.
 */
@Component
public class ExcelRowParsePLI4 {

    public Optional<PhuLucI4> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        return parse(row, rowIndex, formulaEvaluator, null);
    }

    public Optional<PhuLucI4> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator, Map<Integer, Integer> colMap) {
        if (row == null) return Optional.empty();

        int c1  = getCol(colMap, 1,  1);
        int c2  = getCol(colMap, 2,  2);
        int c3  = getCol(colMap, 3,  3);
        int c4  = getCol(colMap, 4,  4);
        int c5  = getCol(colMap, 5,  5);
        int c6  = getCol(colMap, 6,  6);
        int c7  = getCol(colMap, 7,  7);
        int c8  = getCol(colMap, 8,  8);
        int c9  = getCol(colMap, 9,  9);
        int c10 = getCol(colMap, 10, 10);
        int c11 = getCol(colMap, 11, 11);
        int c12 = getCol(colMap, 12, 12);
        int c13 = getCol(colMap, 13, 13);
        int c14 = getCol(colMap, 14, 14);
        int c15 = getCol(colMap, 15, 15);
        int c16 = getCol(colMap, 16, 16);

        PhuLucI4 data = PhuLucI4.builder()
                .rowIndex(rowIndex)
                .hoTen(ExcelParserUtils.getString(row, c1, formulaEvaluator))
                .ngaySinh(ExcelParserUtils.getDate(row, c2, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, c3, formulaEvaluator))
                .donViSapNhap(ExcelParserUtils.getString(row, c4, formulaEvaluator))
                .chucVuSapNhap(ExcelParserUtils.getString(row, c5, formulaEvaluator))
                .donViNghiHuong(ExcelParserUtils.getString(row, c6, formulaEvaluator))
                .chucVuNghiHuong(ExcelParserUtils.getString(row, c7, formulaEvaluator))
                .bienChe(ExcelParserUtils.getInteger(row, c8, formulaEvaluator))
                .hienCo(ExcelParserUtils.getInteger(row, c9, formulaEvaluator))
                .chinhQuyenDiaPhong2Cap(ExcelParserUtils.getInteger(row, c10, formulaEvaluator))
                .sapNhapHCKTThanhTra(ExcelParserUtils.getInteger(row, c11, formulaEvaluator))
                .tinhGiamBienCheKhac(ExcelParserUtils.getInteger(row, c12, formulaEvaluator))
                .nghiTheoND177HuongND178(ExcelParserUtils.getString(row, c13, formulaEvaluator))
                .ketQuaDanhGiaXepLoai(ExcelParserUtils.getString(row, c14, formulaEvaluator))
                .ghiChu(ExcelParserUtils.getString(row, c15, formulaEvaluator))
                .build();

        if (data.isBlank()) return Optional.empty();
        return Optional.of(data);
    }

    private static int getCol(Map<Integer, Integer> colMap, int stdCol, int defaultCol) {
        return (colMap != null && colMap.containsKey(stdCol)) ? colMap.get(stdCol) : defaultCol;
    }
}
