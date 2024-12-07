package com.elevatebanking;

public class PasswordPatternTest {
    public static void main(String[] args) {
        String password = "P@ssw0rd123";
        String pattern = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$";
        boolean matches = password.matches(pattern);
        System.out.println("====================================");
        System.out.println("Password matches: " + matches);
    }
}
