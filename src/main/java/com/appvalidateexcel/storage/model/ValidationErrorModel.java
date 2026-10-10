package com.appvalidateexcel.storage.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = ValidationErrorModel.ValidationErrorModelDeserializer.class)
public class ValidationErrorModel {
    private String id;
    private String recordId;
    private Integer columnNumber;
    private String errorCode;
    private String message;
    private String actualValue;
    private String expectedValue;
    private String createdAt;

    public ValidationErrorModel(String message) {
        this.message = message;
        this.errorCode = "INVALID_VALUE";
    }

    public static class ValidationErrorModelDeserializer extends JsonDeserializer<ValidationErrorModel> {
        @Override
        public ValidationErrorModel deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                String s = p.getText();
                Integer col = null;
                if (s != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^Cột\\s*(\\d+)\\s*:\\s*(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);
                    if (m.find()) {
                        try {
                            col = Integer.parseInt(m.group(1));
                        } catch (Exception ignored) {}
                        s = m.group(2).trim();
                    }
                }
                return ValidationErrorModel.builder()
                        .columnNumber(col)
                        .errorCode("INVALID_VALUE")
                        .message(s)
                        .build();
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                JsonNode node = p.getCodec().readTree(p);
                Integer col = node.has("columnNumber") && !node.get("columnNumber").isNull() ? node.get("columnNumber").asInt() : null;
                String code = node.has("errorCode") && !node.get("errorCode").isNull() ? node.get("errorCode").asText() : "INVALID_VALUE";
                String msg = node.has("message") && !node.get("message").isNull() ? node.get("message").asText() : "";
                String act = node.has("actualValue") && !node.get("actualValue").isNull() ? node.get("actualValue").asText() : null;
                String exp = node.has("expectedValue") && !node.get("expectedValue").isNull() ? node.get("expectedValue").asText() : null;
                String id = node.has("id") && !node.get("id").isNull() ? node.get("id").asText() : null;
                String recId = node.has("recordId") && !node.get("recordId").isNull() ? node.get("recordId").asText() : null;
                String createdAt = node.has("createdAt") && !node.get("createdAt").isNull() ? node.get("createdAt").asText() : null;
                return ValidationErrorModel.builder()
                        .id(id)
                        .recordId(recId)
                        .columnNumber(col)
                        .errorCode(code)
                        .message(msg)
                        .actualValue(act)
                        .expectedValue(exp)
                        .createdAt(createdAt)
                        .build();
            }
            return null;
        }
    }
}
