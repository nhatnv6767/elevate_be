package com.elevatebanking.controller;

import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.UserRepository;
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
    private final UserRepository userRepository;

    @PostMapping("/send-reset-email")
    public String sendResetEmail(@RequestParam String email, @RequestParam String token) {

        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        emailService.sendResetPasswordEmail(email, token, user.getUsername());
        return "Email sent successfully";
    }

}
