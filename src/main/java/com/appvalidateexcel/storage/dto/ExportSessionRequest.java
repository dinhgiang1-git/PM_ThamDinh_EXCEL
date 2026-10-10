package com.appvalidateexcel.storage.dto;

/**
 * DTO nhận yêu cầu khởi tạo phiên Export Snapshot Session từ Client
 * Hỗ trợ khớp chính xác payload JSON từ fetch('/api/records/export/session')
 */
public class ExportSessionRequest {

    private String source;
    private String unitId;
    private String scope = "branch";
    private String sheetType;
    private String categoryCode;
    private String status;
    private String q;

    public ExportSessionRequest() {
    }

    public ExportSessionRequest(String source, String unitId, String scope, String sheetType, String categoryCode, String status, String q) {
        this.source = source;
        this.unitId = unitId;
        this.scope = scope != null ? scope : "branch";
        this.sheetType = sheetType;
        this.categoryCode = categoryCode;
        this.status = status;
        this.q = q;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSheetType() {
        return sheetType;
    }

    public void setSheetType(String sheetType) {
        this.sheetType = sheetType;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }
}
