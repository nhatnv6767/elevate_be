package com.elevatebanking;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class testPass {
    public static void main(String[] args) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String rawPassword = "123456"; // Thay thế bằng mật khẩu thực tế
        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("Encoded Password: '" + encodedPassword + "'");
    }
}
