package com.appvalidateexcel;

import com.appvalidateexcel.storage.config.DataDirectoryResolver;
import com.appvalidateexcel.storage.crypto.DbUnlockGate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động ứng dụng Spring Boot BQP.
 *
 * Lưu ý: Java KHÔNG tự mở trình duyệt nữa.
 * Launcher (Chay_CongCu_BQP.exe) kiểm soát việc mở URL bootstrap sau khi backend sẵn sàng.
 * Điều này đảm bảo chỉ một tab trình duyệt được mở và luôn có token phiên hợp lệ.
 */
@SpringBootApplication
@EnableScheduling
public class AppValidateExcelApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        if (!"false".equalsIgnoreCase(argOrProp(args, "bqp.db.encryption", null))) {
            unlockDatabase(args);
        }
        SpringApplication.run(AppValidateExcelApplication.class, args);
    }

    /** Chờ người dùng nhập mật khẩu DB (SQLCipher) trên trang web trước khi Spring khởi động. */
    private static void unlockDatabase(String[] args) {
        try {
            String baseDir = argOrProp(args, "bqp.storage.base-dir", System.getenv("BQP_DATA_DIR"));
            String dbName = argOrProp(args, "bqp.storage.db-file-name", "app-data.db");
            int port = Integer.parseInt(argOrProp(args, "server.port", "8080"));
            DbUnlockGate.awaitUnlock(DataDirectoryResolver.resolveBaseDirectory(baseDir).resolve(dbName), port);
        } catch (Exception e) {
            System.err.println("[BQP Unlock LỖI] " + e.getMessage());
            throw new IllegalStateException("Không thể mở khóa cơ sở dữ liệu", e);
        }
    }

    /** Đọc tham số dạng --name=value, rồi system property, rồi giá trị mặc định. */
    private static String argOrProp(String[] args, String name, String defaultValue) {
        String prefix = "--" + name + "=";
        for (String a : args) {
            if (a.startsWith(prefix)) return a.substring(prefix.length());
        }
        String prop = System.getProperty(name);
        return prop != null ? prop : defaultValue;
    }
}
