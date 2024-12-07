package com.elevatebanking;

public class PasswordPatternTest {
    public static void main(String[] args) {
        String password = "Password@123";
        String pattern = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,}$";
        boolean matches = password.matches(pattern);
        System.out.println("Password matches: " + matches);
    }
}
