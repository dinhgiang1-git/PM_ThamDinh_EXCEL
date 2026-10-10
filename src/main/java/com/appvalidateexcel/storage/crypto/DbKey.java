package com.appvalidateexcel.storage.crypto;

/** Giữ mật khẩu DB trong bộ nhớ sau khi người dùng mở khóa. Null = chạy với DB không mã hóa (dev/test). */
public final class DbKey {

    private static volatile String key;

    private DbKey() {
    }

    public static String get() {
        return key;
    }

    public static void set(String value) {
        key = value;
    }
}
