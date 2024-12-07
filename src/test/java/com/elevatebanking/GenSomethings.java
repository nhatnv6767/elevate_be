package com.elevatebanking;

import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenSomethings {
    public static void main(String[] args) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("Encoded Password: '" + encodedPassword + "'");

        System.out.println("UUID: ");
        System.out.println(generateUUID());
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
