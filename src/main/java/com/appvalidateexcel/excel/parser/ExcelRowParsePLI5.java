package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.PhuLucI5;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Parser cho Phụ lục I.5 (form 26.9.PHU_LUC_SUA.xlsx):
 *   C1=STT, C2=Họ tên, C3=Đơn vị, C4=Chức vụ, C5=Cấp bậc
 *   C6=Hệ số lương, C7=Hệ số chênh lệch bảo lưu, C8=Hệ số chức vụ
 *   C9=Nhập ngũ, C10=Thời điểm nghỉ hưu hưởng trợ cấp
 *   C11=Tỉ lệ % phụ cấp trách nhiệm, C12=Tỉ lệ % phụ cấp đặc thù
 *   C13=Hệ số chênh lệch bảo lưu (tiền), C14=Tiền lương theo ngạch bậc, C15=Phụ cấp chức vụ
 *   C16=Phụ cấp thâm niên nghề, C17=Phụ cấp thâm niên vượt khung
 *   C18=Phụ cấp trách nhiệm theo nghề, C19=Phụ cấp công vụ, C20=Phụ cấp đặc thù, C21=Được nhận khác
 *   C22=Cộng
 */
@Component
public class ExcelRowParsePLI5 {

    public Optional<PhuLucI5> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator) {
        return parse(row, rowIndex, formulaEvaluator, null);
    }

    public Optional<PhuLucI5> parse(Row row, int rowIndex, FormulaEvaluator formulaEvaluator, Map<Integer, Integer> colMap) {
        if (row == null) return Optional.empty();

        int c1  = getCol(colMap, 1,  0);
        int c2  = getCol(colMap, 2,  1);
        int c3  = getCol(colMap, 3,  2);
        int c4  = getCol(colMap, 4,  3);
        int c5  = getCol(colMap, 5,  4);
        int c6  = getCol(colMap, 6,  5);
        int c7  = getCol(colMap, 7,  6);
        int c8  = getCol(colMap, 8,  7);
        int c9  = getCol(colMap, 9,  8);
        int c10 = getCol(colMap, 10, 9);
        int c11 = getCol(colMap, 11, 10);
        int c12 = getCol(colMap, 12, 11);
        int c13 = getCol(colMap, 13, 12);
        int c14 = getCol(colMap, 14, 13);
        int c15 = getCol(colMap, 15, 14);
        int c16 = getCol(colMap, 16, 15);
        int c17 = getCol(colMap, 17, 16);
        int c18 = getCol(colMap, 18, 17);
        int c19 = getCol(colMap, 19, 18);
        int c20 = getCol(colMap, 20, 19);
        int c21 = getCol(colMap, 21, 20);
        int c22 = getCol(colMap, 22, 21);

        BigDecimal bdC6 = ExcelParserUtils.getBigDecimal(row, c6, formulaEvaluator);
        BigDecimal bdC7 = ExcelParserUtils.getBigDecimal(row, c7, formulaEvaluator);
        BigDecimal bdC8 = ExcelParserUtils.getBigDecimal(row, c8, formulaEvaluator);
        BigDecimal bdC11 = ExcelParserUtils.getBigDecimal(row, c11, formulaEvaluator);
        BigDecimal bdC12 = ExcelParserUtils.getBigDecimal(row, c12, formulaEvaluator);

        PhuLucI5 data = PhuLucI5.builder()
                .rowIndex(rowIndex)
                .stt(ExcelParserUtils.getString(row, c1, formulaEvaluator))
                .hoTen(ExcelParserUtils.getString(row, c2, formulaEvaluator))
                .donVi(ExcelParserUtils.getString(row, c3, formulaEvaluator))
                .chucVu(ExcelParserUtils.getString(row, c4, formulaEvaluator))
                .capBac(ExcelParserUtils.getString(row, c5, formulaEvaluator))
                .heSoLuong(bdC6 != null ? bdC6.doubleValue() : null)
                .heSoChenhLechBaoLuu(bdC7 != null ? bdC7.doubleValue() : null)
                .heSoChucVu(bdC8 != null ? bdC8.doubleValue() : null)
                .nhapNgu(ExcelParserUtils.getDate(row, c9, formulaEvaluator))
                .thoiDiemNghi(ExcelParserUtils.getDate(row, c10, formulaEvaluator))
                .tiLePhuCapTrachNhiem(bdC11 != null ? bdC11.doubleValue() : null)
                .tiLePhuCapDacThu(bdC12 != null ? bdC12.doubleValue() : null)
                .tienChenhLechBaoLuu(ExcelParserUtils.getBigDecimal(row, c13, formulaEvaluator))
                .tienLuongNgachBac(ExcelParserUtils.getBigDecimal(row, c14, formulaEvaluator))
                .phuCapChucVu(ExcelParserUtils.getBigDecimal(row, c15, formulaEvaluator))
                .phuCapThamNienNghe(ExcelParserUtils.getBigDecimal(row, c16, formulaEvaluator))
                .phuCapThamNienVuotKhung(ExcelParserUtils.getBigDecimal(row, c17, formulaEvaluator))
                .phuCapTrachNhiemNghe(ExcelParserUtils.getBigDecimal(row, c18, formulaEvaluator))
                .phuCapCongVu(ExcelParserUtils.getBigDecimal(row, c19, formulaEvaluator))
                .phuCapDacThu(ExcelParserUtils.getBigDecimal(row, c20, formulaEvaluator))
                .duocNhanKhac(ExcelParserUtils.getBigDecimal(row, c21, formulaEvaluator))
                .tongCong(ExcelParserUtils.getBigDecimal(row, c22, formulaEvaluator))
                .build();

        if (data.isBlank()) return Optional.empty();
        return Optional.of(data);
    }

    private static int getCol(Map<Integer, Integer> colMap, int stdCol, int defaultCol) {
        return (colMap != null && colMap.containsKey(stdCol)) ? colMap.get(stdCol) : defaultCol;
    }
}
