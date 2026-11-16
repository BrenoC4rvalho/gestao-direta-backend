package br.com.gestaodireta.user.config;

public final class PasswordPolicy {
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    private PasswordPolicy() {}
}
