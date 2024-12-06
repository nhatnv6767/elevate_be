package com.elevatebanking.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elevatebanking.service.nonImp.EmailService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;

    @PostMapping("/send-reset-email")
    public String sendResetEmail(@RequestParam String email, @RequestParam String token) {
        emailService.sendPasswordResetEmail(email, token);
        return "Email sent successfully";
    }

}
