package com.appvalidateexcel.storage.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Quản lý và chuẩn hóa các đường dẫn thư mục lưu trữ hệ thống BQP theo tiêu chuẩn:
 * [thư mục chứa file jar]\
 *   ├── app-data.db
 *   └── backups\   (chỉ tạo khi có sao lưu)
 */
@Component
public class DataDirectoryResolver {

    private final StorageProperties properties;
    private Path baseDirectory;
    private Path dataDirectory;
    private Path backupsDirectory;
    private Path databaseFile;

    public DataDirectoryResolver(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        resolveDirectories();
    }

    private synchronized void resolveDirectories() {
        if (baseDirectory != null) return;

        baseDirectory = resolveBaseDirectory(properties.getBaseDir());

        // Thư mục dữ liệu chính là thư mục gốc (file .db nằm ngay cạnh jar)
        dataDirectory = baseDirectory;
        backupsDirectory = baseDirectory.resolve("backups");
        databaseFile = dataDirectory.resolve(properties.getDbFileName());

        createDirectoryIfNotExists(baseDirectory);
        createDirectoryIfNotExists(dataDirectory);

        System.out.println("[BQP Storage] Thư mục dữ liệu: " + baseDirectory);
        System.out.println("[BQP Storage] File SQLite: " + databaseFile);
    }

    /** Thư mục gốc dữ liệu: giá trị cấu hình nếu có, ngược lại là thư mục chứa file jar. Dùng được trước khi Spring khởi động. */
    public static Path resolveBaseDirectory(String configBase) {
        if (configBase != null && !configBase.trim().isEmpty()) {
            return Paths.get(configBase.trim()).toAbsolutePath().normalize();
        }
        return resolveJarDirectory();
    }

    /** Thư mục chứa file jar; nếu không chạy từ jar thì dùng BQP_LAUNCHER_DIR hoặc thư mục làm việc. */
    private static Path resolveJarDirectory() {
        String classPath = System.getProperty("java.class.path", "");
        String first = classPath.split(File.pathSeparator)[0];
        if (first.toLowerCase().endsWith(".jar")) {
            Path parent = Paths.get(first).toAbsolutePath().normalize().getParent();
            if (parent != null) return parent;
        }
        String launcherDir = System.getenv("BQP_LAUNCHER_DIR");
        if (launcherDir != null && !launcherDir.isBlank() && Files.isDirectory(Paths.get(launcherDir.trim()))) {
            return Paths.get(launcherDir.trim()).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private void createDirectoryIfNotExists(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            System.err.println("[BQP Storage] Không thể tạo thư mục " + path + ": " + e.getMessage());
        }
    }

    public Path getBaseDirectory() {
        if (baseDirectory == null) resolveDirectories();
        return baseDirectory;
    }

    public Path getDataDirectory() {
        if (dataDirectory == null) resolveDirectories();
        return dataDirectory;
    }

    /** Thư mục sao lưu: chỉ được tạo khi thật sự cần. */
    public Path getBackupsDirectory() {
        if (backupsDirectory == null) resolveDirectories();
        createDirectoryIfNotExists(backupsDirectory);
        return backupsDirectory;
    }

    public Path getDatabaseFile() {
        if (databaseFile == null) resolveDirectories();
        return databaseFile;
    }
}
