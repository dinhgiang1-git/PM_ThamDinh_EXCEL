package com.appvalidateexcel.auth;

public record UserAccount(String id, String username, String displayName, boolean active,
                          boolean mustChangePassword, int tokenVersion) {
}
