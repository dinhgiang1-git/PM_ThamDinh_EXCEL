package com.appvalidateexcel.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bqp.storage")
public class StorageProperties {
    /**
     * Thư mục gốc lưu trữ dữ liệu BQP (mặc định lấy %LOCALAPPDATA%\BQP\QuanLyCheDo)
     */
    private String baseDir;

    /**
     * Tên file cơ sở dữ liệu SQLite
     */
    private String dbFileName = "app-data.db";
}
