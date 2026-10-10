package com.appvalidateexcel.excel.service.rules;

import com.appvalidateexcel.excel.model.dto.PhuLucI1;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class PhuLucI1Validator {

    public List<String> validate(PhuLucI1 data) {
        List<String> errors = new ArrayList<>();






        // Validate basic calculations or logic here if needed
        if (data.getSoThangNghiHuuTruocTuoiTheoThongTu() != null && data.getSoThangNghiHuuTruocTuoiTheoThongTu() < 0) {
            errors.add("Số tháng nghỉ hưu trước tuổi không được âm.");
        }

        if (data.getSoNamNghiHuuTruocTuoiTheoThongTu() != null && data.getSoNamNghiHuuTruocTuoiTheoThongTu().compareTo(java.math.BigDecimal.ZERO) < 0) {
            errors.add("Số năm nghỉ hưu trước tuổi không được âm.");
        }

        return errors;
    }
}
