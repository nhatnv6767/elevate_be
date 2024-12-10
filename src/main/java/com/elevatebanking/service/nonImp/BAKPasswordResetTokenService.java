package com.elevatebanking.service.nonImp;

import com.elevatebanking.entity.token.PasswordResetToken;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BAKPasswordResetTokenService {
    private final PasswordResetTokenRepository tokenRepository;

    @Transactional
    public String createToken(User user) {
        String token = UUID.randomUUID().toString();
        PasswordResetToken passwordResetToken = new PasswordResetToken(token, user);
        tokenRepository.save(passwordResetToken);
        return token;
    }

    @Transactional(readOnly = true)
    public String validateTokenAndGetUsername(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Password reset token has expired");
        }
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Password reset token has already been used");
        }
        return resetToken.getUser().getUsername();
    }

    @Transactional
    public void invalidateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));


        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}
