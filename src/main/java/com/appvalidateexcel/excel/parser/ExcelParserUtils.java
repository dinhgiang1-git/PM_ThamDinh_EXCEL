package com.appvalidateexcel.excel.parser;

import com.appvalidateexcel.excel.model.dto.*;
import com.appvalidateexcel.excel.model.expected.*;
import com.appvalidateexcel.excel.parser.*;
import com.appvalidateexcel.excel.service.rules.*;
import com.appvalidateexcel.excel.service.*;


import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DataFormatter;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ExcelParserUtils {
    private static final DataFormatter dataFormatter = new DataFormatter();

    public static String cleanString(String val) {
        if (val == null) return "";
        // Thay thế các loại khoảng trắng đặc biệt (Non-breaking space \u00A0, \u2007, \u202F, zero-width space \u200B, \uFEFF)
        String cleaned = val.replace('\u00A0', ' ')
                            .replace('\u2007', ' ')
                            .replace('\u202F', ' ')
                            .replace('\u200B', ' ')
                            .replace('\uFEFF', ' ');
        // Xóa khoảng trắng thừa ở đầu/cuối và gộp các khoảng trắng liên tiếp bên trong thành 1 khoảng trắng đơn
        return cleaned.trim().replaceAll("\\s+", " ");
    }

    public static CellReadResult<Object> readCellValue(Row row, int col, FormulaEvaluator evaluator) {
        if (row == null) {
            return CellReadResult.<Object>builder()
                    .sheetName("")
                    .cellAddress("")
                    .formula(null)
                    .value(null)
                    .valueType("null")
                    .status(CellStatus.BLANK)
                    .valueSource("none")
                    .rawText("")
                    .build();
        }
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return readCellValue(cell, evaluator);
    }

    public static CellReadResult<Object> readCellValue(Cell cell, FormulaEvaluator evaluator) {
        String sheetName = (cell != null && cell.getSheet() != null) ? cell.getSheet().getSheetName() : "";
        String cellAddress = (cell != null && cell.getAddress() != null) ? cell.getAddress().formatAsString() : "";

        if (cell == null) {
            return CellReadResult.<Object>builder()
                    .sheetName(sheetName)
                    .cellAddress(cellAddress)
                    .formula(null)
                    .value(null)
                    .valueType("null")
                    .status(CellStatus.BLANK)
                    .valueSource("none")
                    .rawText("")
                    .build();
        }

        CellType cellType = cell.getCellType();

        // 1. Ô Blank
        if (cellType == CellType.BLANK) {
            return CellReadResult.<Object>builder()
                    .sheetName(sheetName)
                    .cellAddress(cellAddress)
                    .formula(null)
                    .value(null)
                    .valueType("null")
                    .status(CellStatus.BLANK)
                    .valueSource("none")
                    .rawText("")
                    .build();
        }

        // 2. Ô Lỗi trực tiếp
        if (cellType == CellType.ERROR) {
            String errCode;
            try {
                errCode = org.apache.poi.ss.usermodel.FormulaError.forInt(cell.getErrorCellValue()).getString();
            } catch (Exception ex) {
                errCode = "ERROR";
            }
            return CellReadResult.<Object>builder()
                    .sheetName(sheetName)
                    .cellAddress(cellAddress)
                    .formula(null)
                    .value(null)
                    .valueType("error")
                    .status(CellStatus.FORMULA_ERROR)
                    .valueSource("none")
                    .errorCode(errCode)
                    .rawText("[Lỗi ô: " + errCode + "]")
                    .build();
        }

        // 3. Ô Công thức (FORMULA)
        if (cellType == CellType.FORMULA) {
            String formula = "";
            try {
                formula = cell.getCellFormula();
            } catch (Exception ignored) {}

            // A. Ưu tiên đọc kết quả đã lưu sẵn (cached result)
            boolean hasCacheTag = true;
            if (cell instanceof org.apache.poi.xssf.usermodel.XSSFCell) {
                org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCell ctCell = ((org.apache.poi.xssf.usermodel.XSSFCell) cell).getCTCell();
                if (ctCell != null) {
                    if (!ctCell.isSetV()) {
                        hasCacheTag = false;
                    } else if (ctCell.getV() == null || ctCell.getV().trim().isEmpty()) {
                        // Thẻ <v> tồn tại trong XML nhưng rỗng -> chính là kết quả rỗng "" (FORMULA_EMPTY)
                        return CellReadResult.<Object>builder()
                                .sheetName(sheetName)
                                .cellAddress(cellAddress)
                                .formula(formula)
                                .value("")
                                .valueType("string")
                                .status(CellStatus.FORMULA_EMPTY)
                                .valueSource("cached")
                                .rawText("")
                                .build();
                    }
                }
            }

            CellType cachedType = null;
            if (hasCacheTag) {
                try {
                    cachedType = cell.getCachedFormulaResultType();
                } catch (Exception ignored) {}
            }

            if (cachedType == CellType.ERROR) {
                String errCode;
                try {
                    errCode = org.apache.poi.ss.usermodel.FormulaError.forInt(cell.getErrorCellValue()).getString();
                } catch (Exception ex) {
                    errCode = "ERROR";
                }
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(formula)
                        .value(null)
                        .valueType("error")
                        .status(CellStatus.FORMULA_ERROR)
                        .valueSource("none")
                        .errorCode(errCode)
                        .rawText("[Lỗi ô: " + errCode + "]")
                        .build();
            }

            if (cachedType == CellType.NUMERIC) {
                boolean isDate = false;
                try {
                    isDate = DateUtil.isCellDateFormatted(cell);
                } catch (Exception ignored) {}

                if (isDate) {
                    Date d = cell.getDateCellValue();
                    return CellReadResult.<Object>builder()
                            .sheetName(sheetName)
                            .cellAddress(cellAddress)
                            .formula(formula)
                            .value(d)
                            .valueType("date")
                            .status(CellStatus.FORMULA_CACHED)
                            .valueSource("cached")
                            .rawText(d != null ? new SimpleDateFormat("dd/MM/yyyy").format(d) : "")
                            .build();
                } else {
                    double num = cell.getNumericCellValue();
                    BigDecimal bd = BigDecimal.valueOf(num);
                    return CellReadResult.<Object>builder()
                            .sheetName(sheetName)
                            .cellAddress(cellAddress)
                            .formula(formula)
                            .value(bd)
                            .valueType("number")
                            .status(CellStatus.FORMULA_CACHED)
                            .valueSource("cached")
                            .rawText(dataFormatter.formatCellValue(cell))
                            .build();
                }
            }

            if (cachedType == CellType.STRING) {
                String str = cleanString(cell.getStringCellValue());
                if (str.isEmpty()) {
                    return CellReadResult.<Object>builder()
                            .sheetName(sheetName)
                            .cellAddress(cellAddress)
                            .formula(formula)
                            .value("")
                            .valueType("string")
                            .status(CellStatus.FORMULA_EMPTY)
                            .valueSource("cached")
                            .rawText("")
                            .build();
                } else {
                    return CellReadResult.<Object>builder()
                            .sheetName(sheetName)
                            .cellAddress(cellAddress)
                            .formula(formula)
                            .value(str)
                            .valueType("string")
                            .status(CellStatus.FORMULA_CACHED)
                            .valueSource("cached")
                            .rawText(str)
                            .build();
                }
            }

            if (cachedType == CellType.BOOLEAN) {
                boolean b = cell.getBooleanCellValue();
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(formula)
                        .value(b)
                        .valueType("boolean")
                        .status(CellStatus.FORMULA_CACHED)
                        .valueSource("cached")
                        .rawText(String.valueOf(b))
                        .build();
            }

            // B. Nếu thiếu cache hoặc cachedType == CellType.BLANK, thử dùng evaluator tính lại
            if (evaluator != null) {
                try {
                    org.apache.poi.ss.usermodel.CellValue cv = evaluator.evaluate(cell);
                    if (cv != null) {
                        CellType evalType = cv.getCellType();
                        if (evalType == CellType.NUMERIC) {
                            boolean isDate = false;
                            try {
                                isDate = DateUtil.isCellDateFormatted(cell);
                            } catch (Exception ignored) {}

                            if (isDate) {
                                Date d = DateUtil.getJavaDate(cv.getNumberValue());
                                return CellReadResult.<Object>builder()
                                        .sheetName(sheetName)
                                        .cellAddress(cellAddress)
                                        .formula(formula)
                                        .value(d)
                                        .valueType("date")
                                        .status(CellStatus.FORMULA_EVALUATED)
                                        .valueSource("evaluated")
                                        .rawText(d != null ? new SimpleDateFormat("dd/MM/yyyy").format(d) : "")
                                        .build();
                            } else {
                                BigDecimal bd = BigDecimal.valueOf(cv.getNumberValue());
                                return CellReadResult.<Object>builder()
                                        .sheetName(sheetName)
                                        .cellAddress(cellAddress)
                                        .formula(formula)
                                        .value(bd)
                                        .valueType("number")
                                        .status(CellStatus.FORMULA_EVALUATED)
                                        .valueSource("evaluated")
                                        .rawText(String.valueOf(cv.getNumberValue()))
                                        .build();
                            }
                        } else if (evalType == CellType.STRING) {
                            String str = cleanString(cv.getStringValue());
                            if (str.isEmpty()) {
                                return CellReadResult.<Object>builder()
                                        .sheetName(sheetName)
                                        .cellAddress(cellAddress)
                                        .formula(formula)
                                        .value("")
                                        .valueType("string")
                                        .status(CellStatus.FORMULA_EMPTY)
                                        .valueSource("evaluated")
                                        .rawText("")
                                        .build();
                            } else {
                                return CellReadResult.<Object>builder()
                                        .sheetName(sheetName)
                                        .cellAddress(cellAddress)
                                        .formula(formula)
                                        .value(str)
                                        .valueType("string")
                                        .status(CellStatus.FORMULA_EVALUATED)
                                        .valueSource("evaluated")
                                        .rawText(str)
                                        .build();
                            }
                        } else if (evalType == CellType.BOOLEAN) {
                            boolean b = cv.getBooleanValue();
                            return CellReadResult.<Object>builder()
                                    .sheetName(sheetName)
                                    .cellAddress(cellAddress)
                                    .formula(formula)
                                    .value(b)
                                    .valueType("boolean")
                                    .status(CellStatus.FORMULA_EVALUATED)
                                    .valueSource("evaluated")
                                    .rawText(String.valueOf(b))
                                    .build();
                        } else if (evalType == CellType.ERROR) {
                            String errCode;
                            try {
                                errCode = org.apache.poi.ss.usermodel.FormulaError.forInt(cv.getErrorValue()).getString();
                            } catch (Exception ex) {
                                errCode = "ERROR";
                            }
                            return CellReadResult.<Object>builder()
                                    .sheetName(sheetName)
                                    .cellAddress(cellAddress)
                                    .formula(formula)
                                    .value(null)
                                    .valueType("error")
                                    .status(CellStatus.FORMULA_ERROR)
                                    .valueSource("none")
                                    .errorCode(errCode)
                                    .rawText("[Lỗi ô: " + errCode + "]")
                                    .build();
                        } else if (evalType == CellType.BLANK) {
                            return CellReadResult.<Object>builder()
                                    .sheetName(sheetName)
                                    .cellAddress(cellAddress)
                                    .formula(formula)
                                    .value("")
                                    .valueType("string")
                                    .status(CellStatus.FORMULA_EMPTY)
                                    .valueSource("evaluated")
                                    .rawText("")
                                    .build();
                        }
                    }
                } catch (Exception evalEx) {
                    // Khi evaluator ném lỗi do công thức không hỗ trợ
                }
            }

            // C. Không tính được và không có cache -> FORMULA_NO_RESULT
            return CellReadResult.<Object>builder()
                    .sheetName(sheetName)
                    .cellAddress(cellAddress)
                    .formula(formula)
                    .value(null)
                    .valueType("null")
                    .status(CellStatus.FORMULA_NO_RESULT)
                    .valueSource("none")
                    .rawText("[Lỗi ô: Chưa tính kết quả]")
                    .build();
        }

        // 4. Literal NUMERIC
        if (cellType == CellType.NUMERIC) {
            boolean isDate = false;
            try {
                isDate = DateUtil.isCellDateFormatted(cell);
            } catch (Exception ignored) {}

            if (isDate) {
                Date d = cell.getDateCellValue();
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(null)
                        .value(d)
                        .valueType("date")
                        .status(CellStatus.VALUE)
                        .valueSource("literal")
                        .rawText(d != null ? new SimpleDateFormat("dd/MM/yyyy").format(d) : "")
                        .build();
            } else {
                double num = cell.getNumericCellValue();
                BigDecimal bd = BigDecimal.valueOf(num);
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(null)
                        .value(bd)
                        .valueType("number")
                        .status(CellStatus.VALUE)
                        .valueSource("literal")
                        .rawText(dataFormatter.formatCellValue(cell))
                        .build();
            }
        }

        // 5. Literal STRING
        if (cellType == CellType.STRING) {
            String str = cleanString(cell.getStringCellValue());
            if (str.isEmpty()) {
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(null)
                        .value("")
                        .valueType("string")
                        .status(CellStatus.BLANK)
                        .valueSource("none")
                        .rawText("")
                        .build();
            } else {
                return CellReadResult.<Object>builder()
                        .sheetName(sheetName)
                        .cellAddress(cellAddress)
                        .formula(null)
                        .value(str)
                        .valueType("string")
                        .status(CellStatus.VALUE)
                        .valueSource("literal")
                        .rawText(str)
                        .build();
            }
        }

        // 6. Literal BOOLEAN
        if (cellType == CellType.BOOLEAN) {
            boolean b = cell.getBooleanCellValue();
            return CellReadResult.<Object>builder()
                    .sheetName(sheetName)
                    .cellAddress(cellAddress)
                    .formula(null)
                    .value(b)
                    .valueType("boolean")
                    .status(CellStatus.VALUE)
                    .valueSource("literal")
                    .rawText(String.valueOf(b))
                    .build();
        }

        // Mặc định
        return CellReadResult.<Object>builder()
                .sheetName(sheetName)
                .cellAddress(cellAddress)
                .formula(null)
                .value(null)
                .valueType("null")
                .status(CellStatus.BLANK)
                .valueSource("none")
                .rawText("")
                .build();
    }

    public static FormulaReadReport generateFormulaReport(org.apache.poi.ss.usermodel.Workbook wb, FormulaEvaluator evaluator) {
        FormulaReadReport report = FormulaReadReport.builder()
                .sheetFormulaCounts(new java.util.HashMap<>())
                .issues(new java.util.ArrayList<>())
                .build();
        if (wb == null) return report;

        for (int s = 0; s < wb.getNumberOfSheets(); s++) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(s);
            int sheetFormulas = 0;
            for (Row row : sheet) {
                if (row == null) continue;
                for (Cell cell : row) {
                    if (cell == null || cell.getCellType() != CellType.FORMULA) continue;
                    sheetFormulas++;
                    report.setTotalFormulas(report.getTotalFormulas() + 1);

                    CellReadResult<Object> res = readCellValue(cell, evaluator);
                    switch (res.getStatus()) {
                        case FORMULA_CACHED:
                            report.setCachedFormulas(report.getCachedFormulas() + 1);
                            break;
                        case FORMULA_EVALUATED:
                            report.setEvaluatedFormulas(report.getEvaluatedFormulas() + 1);
                            break;
                        case FORMULA_EMPTY:
                            report.setEmptyFormulas(report.getEmptyFormulas() + 1);
                            break;
                        case FORMULA_NO_RESULT:
                            report.setNoResultFormulas(report.getNoResultFormulas() + 1);
                            report.getIssues().add(FormulaReadReport.FormulaIssue.builder()
                                    .sheet(sheet.getSheetName())
                                    .address(res.getCellAddress())
                                    .formula(res.getFormula())
                                    .status(res.getStatus())
                                    .errorCode("NO_RESULT")
                                    .row(cell.getRowIndex() + 1)
                                    .col(cell.getColumnIndex() + 1)
                                    .build());
                            break;
                        case FORMULA_ERROR:
                            report.setErrorFormulas(report.getErrorFormulas() + 1);
                            report.getIssues().add(FormulaReadReport.FormulaIssue.builder()
                                    .sheet(sheet.getSheetName())
                                    .address(res.getCellAddress())
                                    .formula(res.getFormula())
                                    .status(res.getStatus())
                                    .errorCode(res.getErrorCode())
                                    .row(cell.getRowIndex() + 1)
                                    .col(cell.getColumnIndex() + 1)
                                    .build());
                            break;
                        default:
                            break;
                    }
                }
            }
            if (sheetFormulas > 0) {
                report.getSheetFormulaCounts().put(sheet.getSheetName(), sheetFormulas);
            }
        }
        return report;
    }

    public static String getString(Row row, int col, FormulaEvaluator formulaEvaluator) {
        CellReadResult<Object> res = readCellValue(row, col, formulaEvaluator);
        if (res.getStatus() == CellStatus.BLANK || res.getStatus() == CellStatus.FORMULA_EMPTY) {
            return "";
        }
        if (res.getStatus() == CellStatus.FORMULA_ERROR) {
            return "[Lỗi ô: " + (res.getErrorCode() != null ? res.getErrorCode() : "ERROR") + "]";
        }
        if (res.getStatus() == CellStatus.FORMULA_NO_RESULT) {
            return "[Lỗi ô: Chưa tính kết quả]";
        }

        if (res.getValue() != null) {
            if (res.getValue() instanceof Date) {
                return new SimpleDateFormat("dd/MM/yyyy").format((Date) res.getValue());
            }
            if (res.getValue() instanceof BigDecimal) {
                BigDecimal bd = (BigDecimal) res.getValue();
                return bd.stripTrailingZeros().toPlainString();
            }
            return cleanString(String.valueOf(res.getValue()));
        }

        Cell cell = (row != null) ? row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) : null;
        if (cell != null && cell.getCellType() != CellType.FORMULA) {
            try {
                return cleanString(dataFormatter.formatCellValue(cell));
            } catch (Exception ignored) {}
        }
        return cleanString(res.getRawText());
    }

    public static BigDecimal getBigDecimal(Row row, int col, FormulaEvaluator formulaEvaluator) {
        CellReadResult<Object> res = readCellValue(row, col, formulaEvaluator);
        if (res.getStatus() == CellStatus.BLANK || res.getStatus() == CellStatus.FORMULA_EMPTY) {
            return null;
        }
        if (res.getStatus() == CellStatus.FORMULA_ERROR || res.getStatus() == CellStatus.FORMULA_NO_RESULT) {
            return null;
        }

        if (res.getValue() instanceof BigDecimal) {
            return (BigDecimal) res.getValue();
        }
        if (res.getValue() instanceof Number) {
            return BigDecimal.valueOf(((Number) res.getValue()).doubleValue());
        }

        String val = getString(row, col, formulaEvaluator);
        if (val == null || val.trim().isEmpty() || val.trim().equals("-") || val.startsWith("[Lỗi")) return null;
        
        val = val.trim().replaceAll("\\s+", "");

        // 1. Kiểm tra định dạng YY-MM (ví dụ "34-06", "05-05", "02-04", "30-09", "03-11")
        java.util.regex.Matcher ym = java.util.regex.Pattern.compile("^(\\d+)[-–](\\d+)$").matcher(val);
        if (ym.find()) {
            try {
                int yy = Integer.parseInt(ym.group(1));
                int mm = Integer.parseInt(ym.group(2));
                if (mm == 0) return BigDecimal.valueOf(yy);
                if (mm <= 6) return BigDecimal.valueOf(yy).add(new BigDecimal("0.5"));
                return BigDecimal.valueOf(yy).add(BigDecimal.ONE);
            } catch (Exception ignored) {}
        }

        // 2. Kiểm tra định dạng có chữ "năm", "tháng" (ví dụ "2 năm 2 tháng", "26 tháng", "36 năm")
        String valLower = val.toLowerCase();
        if (valLower.contains("năm") || valLower.contains("tháng") || valLower.contains("nam") || valLower.contains("thang")) {
            java.util.regex.Matcher nm = java.util.regex.Pattern.compile("(?:(\\d+)(?:năm|nam))?(?:(\\d+)(?:tháng|thang))?").matcher(valLower);
            if (nm.find() && (nm.group(1) != null || nm.group(2) != null)) {
                try {
                    int yy = nm.group(1) != null ? Integer.parseInt(nm.group(1)) : 0;
                    int mm = nm.group(2) != null ? Integer.parseInt(nm.group(2)) : 0;
                    if (yy > 0 && mm > 0) {
                        if (mm <= 6) return BigDecimal.valueOf(yy).add(new BigDecimal("0.5"));
                        return BigDecimal.valueOf(yy).add(BigDecimal.ONE);
                    } else if (yy > 0) {
                        return BigDecimal.valueOf(yy);
                    } else if (mm > 0) {
                        return BigDecimal.valueOf(mm);
                    }
                } catch (Exception ignored) {}
            }
        }
        
        int lastComma = val.lastIndexOf(',');
        int lastDot = val.lastIndexOf('.');
        int lastPunctuation = Math.max(lastComma, lastDot);
        
        if (lastPunctuation != -1) {
            String after = val.substring(lastPunctuation + 1);
            if (after.length() != 3) {
                val = val.replaceAll("[,\\.]", "");
                val = val.substring(0, val.length() - after.length()) + "." + after;
            } else {
                val = val.replaceAll("[,\\.]", "");
            }
        }
        
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer getInteger(Row row, int col, FormulaEvaluator formulaEvaluator) {
        BigDecimal bd = getBigDecimal(row, col, formulaEvaluator);
        if (bd == null) return null;
        return bd.intValue();
    }

    public static Date getDate(Row row, int col, FormulaEvaluator formulaEvaluator) {
        CellReadResult<Object> res = readCellValue(row, col, formulaEvaluator);
        if (res.getStatus() == CellStatus.BLANK || res.getStatus() == CellStatus.FORMULA_EMPTY
                || res.getStatus() == CellStatus.FORMULA_ERROR || res.getStatus() == CellStatus.FORMULA_NO_RESULT) {
            return null;
        }

        Date resultDate = null;
        if (res.getValue() instanceof Date) {
            resultDate = (Date) res.getValue();
        }

        if (resultDate == null) {
            String val = getString(row, col, formulaEvaluator);
            if (val == null || val.trim().isEmpty() || val.startsWith("[Lỗi")) return null;
            val = val.trim();

            // 1. Quét tìm tất cả các mốc ngày trong chuỗi (xử lý chuỗi nhiều dòng hoặc có tiền tố như TD: mm/yyyy, NN: mm/yyyy, BH: mm/yyyy, SQDB: mm/yyyy...)
            java.util.List<Date> dateMatches = new java.util.ArrayList<>();
            // Pattern 1: dd/MM/yyyy hoặc MM/yyyy (năm 4 chữ số)
            java.util.regex.Matcher m4 = java.util.regex.Pattern.compile("(?:(\\d{1,2})[\\/\\.-])?(\\d{1,2})[\\/\\.-](\\d{4})").matcher(val);
            while (m4.find()) {
                try {
                    int d = m4.group(1) != null ? Integer.parseInt(m4.group(1)) : 1;
                    int m = Integer.parseInt(m4.group(2));
                    int y = Integer.parseInt(m4.group(3));
                    if (y > 2045) y -= 100;
                    if (m >= 1 && m <= 12 && y >= 1940 && y <= 2045) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(y, m - 1, Math.min(d, 28), 0, 0, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        dateMatches.add(cal.getTime());
                    }
                } catch (Exception ignored) {}
            }

            // Pattern 2: Năm 2 chữ số (ví dụ "12/88", "7/96", "02/69") nếu không tìm thấy năm 4 chữ số
            if (dateMatches.isEmpty()) {
                java.util.regex.Matcher m2List = java.util.regex.Pattern.compile("(?:(\\d{1,2})[\\/\\.-])?(\\d{1,2})[\\/\\.-](\\d{2})\\b").matcher(val);
                while (m2List.find()) {
                    try {
                        int d = m2List.group(1) != null ? Integer.parseInt(m2List.group(1)) : 1;
                        int m = Integer.parseInt(m2List.group(2));
                        int yy = Integer.parseInt(m2List.group(3));
                        int y = (yy <= 45) ? (2000 + yy) : (1900 + yy);
                        if (m >= 1 && m <= 12) {
                            Calendar cal = Calendar.getInstance();
                            cal.set(y, m - 1, Math.min(d, 28), 0, 0, 0);
                            cal.set(Calendar.MILLISECOND, 0);
                            dateMatches.add(cal.getTime());
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!dateMatches.isEmpty()) {
                // Sắp xếp tăng dần để lấy mốc bắt đầu sớm nhất (thời gian bắt đầu đóng BHXH/tuyển dụng)
                dateMatches.sort(Date::compareTo);
                resultDate = dateMatches.get(0);
            }

            // Nếu có nhiều mốc ngày phân tách bằng dấu chấm phẩy (ví dụ: "12/88; 7/96") -> lấy mốc đầu tiên
            if (resultDate == null && val.contains(";")) {
                val = val.split(";")[0].trim();
            }

            // Xử lý các chuỗi có chữ "Thg" ví dụ "Thg7-25" -> "7-25"
            if (resultDate == null && val.toLowerCase().startsWith("thg")) {
                val = val.replaceAll("(?i)thg\\s*", "");
            }

            // Xử lý gõ nhầm chữ O/o thay vì số 0 (ví dụ "O2/1974" -> "02/1974")
            if (resultDate == null) {
                val = val.replaceAll("^[oO](\\d)", "0$1").replaceAll("[/-][oO](\\d)", "/0$1");
            }

            // Xóa khoảng trắng thừa quanh dấu gạch chéo hoặc gạch ngang
            if (resultDate == null) {
                val = val.replaceAll("\\s*/\\s*", "/").replaceAll("\\s*-\\s*", "-").replaceAll("\\s+", "");
            }

            // Chuẩn hóa năm 2 chữ số dạng dd/MM/yy hoặc MM/yy (ví dụ: "02/69" -> "02/1969", "3/25" -> "3/2025", "8/86" -> "8/1986")
            if (resultDate == null) {
                java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("^(\\d{1,2})([/-])(\\d{1,2})[/-](\\d{2})$").matcher(val);
                if (m3.find()) {
                    int yy = Integer.parseInt(m3.group(4));
                    int fullY = (yy <= 45) ? (2000 + yy) : (1900 + yy);
                    val = m3.group(1) + m3.group(2) + m3.group(3) + m3.group(2) + fullY;
                } else {
                    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("^(\\d{1,2})([/-])(\\d{2})$").matcher(val);
                    if (m2.find()) {
                        int yy = Integer.parseInt(m2.group(3));
                        int fullY = (yy <= 45) ? (2000 + yy) : (1900 + yy);
                        val = m2.group(1) + m2.group(2) + fullY;
                    }
                }
            }

            String[] formats = {
                    "dd/MM/yyyy", "d/M/yyyy", "dd/M/yyyy", "d/MM/yyyy",
                    "MM/yyyy", "M/yyyy",
                    "yyyy",
                    "dd-MM-yyyy", "d-M-yyyy", "dd-M-yyyy", "d-MM-yyyy",
                    "MM-yyyy", "M-yyyy"
            };

            for (String format : formats) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(format);
                    sdf.setLenient(false);
                    resultDate = sdf.parse(val);
                    break;
                } catch (Exception e) {
                    // ignore and try next
                }
            }
        }

        if (resultDate != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(resultDate);
            int y = cal.get(java.util.Calendar.YEAR);
            if (y < 100) {
                // Xử lý trường hợp lọt năm 2 chữ số (ví dụ 0025 AD, 0069 AD)
                cal.set(java.util.Calendar.YEAR, (y <= 45) ? (2000 + y) : (1900 + y));
                resultDate = cal.getTime();
            } else if (y > 2045) {
                // Xử lý lỗi Excel tự động chuyển năm sinh 69 thành 2069
                cal.set(java.util.Calendar.YEAR, y - 100);
                resultDate = cal.getTime();
            }
        }

        return resultDate;
    }

    public static java.util.Map<Integer, Integer> findColMap(org.apache.poi.ss.usermodel.Sheet sheet, FormulaEvaluator evaluator) {
        java.util.Map<Integer, Integer> colMap = new java.util.HashMap<>();
        if (sheet == null) return colMap;
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            java.util.Map<Integer, Integer> rowMap = new java.util.HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator);
                if (val != null) {
                    val = val.trim();
                    if (val.contains("/") || val.matches("^\\d+[-–]\\d+$")) {
                        continue;
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[\\[\\(]?(\\d{1,2})").matcher(val);
                    if (m.find()) {
                        try {
                            int num = Integer.parseInt(m.group(1));
                            if (num >= 1 && num <= 35 && !rowMap.containsKey(num)) {
                                rowMap.put(num, c);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (rowMap.size() >= 7 && rowMap.containsKey(1) && rowMap.containsKey(2) && rowMap.containsKey(3)
                    && rowMap.containsKey(4) && rowMap.containsKey(5)
                    && rowMap.get(1) < rowMap.get(2) && rowMap.get(2) < rowMap.get(3)
                    && rowMap.get(3) < rowMap.get(4) && rowMap.get(4) < rowMap.get(5)) {
                // Nhận diện xem cột Lương (Cột 9 chuẩn) nằm ở số nào trên dòng đánh số (9 hay 10)
                int colLuongNum = -1;
                for (java.util.Map.Entry<Integer, Integer> entry : rowMap.entrySet()) {
                    int num = entry.getKey();
                    int cIdx = entry.getValue();
                    for (int pr = Math.max(0, r - 5); pr < r; pr++) {
                        Row prevRow = sheet.getRow(pr);
                        if (prevRow != null) {
                            String hText = getString(prevRow, cIdx, evaluator).toLowerCase().replaceAll("\\s+", " ").trim();
                            if (hText.contains("lương tháng") || hText.contains("luong thang") || hText.contains("tiền lương")) {
                                colLuongNum = num;
                                break;
                            }
                        }
                    }
                    if (colLuongNum != -1) break;
                }

                // Nhận diện cột Họ và tên nằm ở số nào (1 hay 2)
                int colHoTenNum = -1;
                for (java.util.Map.Entry<Integer, Integer> entry : rowMap.entrySet()) {
                    int num = entry.getKey();
                    int cIdx = entry.getValue();
                    for (int pr = Math.max(0, r - 5); pr < r; pr++) {
                        Row prevRow = sheet.getRow(pr);
                        if (prevRow != null) {
                            String hText = getString(prevRow, cIdx, evaluator).toLowerCase().replaceAll("\\s+", " ").trim();
                            if (hText.contains("họ và tên") || hText.contains("ho va ten") || hText.contains("họ tên")) {
                                colHoTenNum = num;
                                break;
                            }
                        }
                    }
                    if (colHoTenNum != -1) break;
                }

                if (colLuongNum == 10 && colHoTenNum == 2) {
                    // Mẫu cũ có thêm cột "Đánh giá xếp loại cán bộ" (Số TT: 1, Họ tên: 2... Lương: 10, các cột chế độ từ 11..35)
                    colMap.put(1, rowMap.get(1)); // STT
                    colMap.put(2, rowMap.get(2)); // Họ tên
                    colMap.put(3, rowMap.get(3)); // Ngày sinh
                    colMap.put(4, rowMap.get(4)); // Cấp bậc
                    colMap.put(5, rowMap.get(5)); // Chức vụ
                    colMap.put(6, rowMap.get(6)); // Nhập ngũ
                    // Cột 7 cũ là Đánh giá xếp loại cán bộ -> bỏ qua
                    colMap.put(7, rowMap.get(8)); // Sáp nhập
                    colMap.put(8, rowMap.get(9)); // Thời điểm nghỉ
                    colMap.put(9, rowMap.get(10)); // Lương
                    for (int k = 10; k <= 35; k++) {
                        if (rowMap.containsKey(k + 1)) {
                            colMap.put(k, rowMap.get(k + 1));
                        }
                    }
                } else if (colHoTenNum == 1 && colLuongNum == 8) {
                    // Mẫu không có cột STT (Họ tên: 1, Ngày sinh: 2... Lương: 8)
                    colMap.put(2, rowMap.get(1)); // Họ tên
                    colMap.put(3, rowMap.get(2)); // Ngày sinh
                    colMap.put(4, rowMap.get(3)); // Cấp bậc
                    colMap.put(5, rowMap.get(4)); // Chức vụ
                    colMap.put(6, rowMap.get(5)); // Nhập ngũ
                    colMap.put(7, rowMap.get(6)); // Sáp nhập
                    colMap.put(8, rowMap.get(7)); // Thời điểm nghỉ
                    colMap.put(9, rowMap.get(8)); // Lương
                    for (int k = 10; k <= 35; k++) {
                        if (rowMap.containsKey(k - 1)) {
                            colMap.put(k, rowMap.get(k - 1));
                        }
                    }
                } else {
                    // Mẫu chuẩn mới 26.9.PHU_LUC_SUA.xlsx (STT: 1, Họ tên: 2, Ngày sinh: 3, Cấp bậc: 4, Chức vụ: 5, Nhập ngũ: 6, Sáp nhập: 7, Nghỉ: 8, Lương: 9, Cột 10..35 khớp 1-1)
                    colMap.putAll(rowMap);
                }
                break;
            }
        }

        // Fallback: nếu không có dòng đánh số 1, 2, 3... (như sheet I.1 của BC Tăng thiết giáp)
        if (colMap.isEmpty()) {
            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int colHoTen = -1;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getString(row, c, evaluator).toLowerCase();
                    if (val.contains("họ và tên") || val.contains("ho va ten") || val.contains("họ tên")) {
                        colHoTen = c;
                        break;
                    }
                }
                if (colHoTen != -1) {
                    if (colHoTen > 0) {
                        colMap.put(0, colHoTen - 1);
                    }
                    colMap.put(1, colHoTen);
                    for (int k = 2; k <= 35; k++) {
                        int colIdx = colHoTen + (k - 1);
                        colMap.put(k, colIdx);
                    }
                    break;
                }
            }
        }

        return colMap;
    }

    public static int getHeaderRowNum(org.apache.poi.ss.usermodel.Sheet sheet, FormulaEvaluator evaluator) {
        if (sheet == null) return -1;
        // 1. Tìm dòng đánh số cột có ít nhất 7 số
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            java.util.Map<Integer, Integer> rowMap = new java.util.HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator);
                if (val != null) {
                    val = val.trim();
                    if (val.contains("/") || val.matches("^\\d+[-–]\\d+$")) {
                        continue;
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[\\[\\(]?(\\d{1,2})").matcher(val);
                    if (m.find()) {
                        try {
                            int num = Integer.parseInt(m.group(1));
                            if (num >= 1 && num <= 35 && !rowMap.containsKey(num)) {
                                rowMap.put(num, c);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (rowMap.size() >= 7 && rowMap.containsKey(1) && rowMap.containsKey(2) && rowMap.containsKey(3)
                    && rowMap.containsKey(4) && rowMap.containsKey(5)
                    && rowMap.get(1) < rowMap.get(2) && rowMap.get(2) < rowMap.get(3)
                    && rowMap.get(3) < rowMap.get(4) && rowMap.get(4) < rowMap.get(5)) {
                return r;
            }
        }

        // 2. Fallback: tìm dòng có chữ 'Họ và tên'
        for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getString(row, c, evaluator).toLowerCase();
                if (val.contains("họ và tên") || val.contains("ho va ten") || val.contains("họ tên")) {
                    int lastHeaderRow = r;
                    for (int checkR = r + 1; checkR <= Math.min(r + 5, sheet.getLastRowNum()); checkR++) {
                        Row nextRow = sheet.getRow(checkR);
                        if (nextRow == null) continue;
                        String cVal = getString(nextRow, c, evaluator).toLowerCase().trim();
                        if (cVal.contains("năm 2025") || cVal.contains("sĩ quan") || cVal.contains("quân nhân") || cVal.isEmpty()) {
                            lastHeaderRow = checkR;
                        } else {
                            break;
                        }
                    }
                    return lastHeaderRow;
                }
            }
        }

        return -1;
    }
}
