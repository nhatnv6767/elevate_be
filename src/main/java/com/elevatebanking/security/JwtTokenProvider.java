package com.elevatebanking.security;

import com.elevatebanking.exception.InvalidTokenException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.elevatebanking.entity.user.Role;
import com.elevatebanking.entity.user.User;
import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.UserRepository;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private final UserRepository userRepository;

    private final RedisTemplate<String, String> redisTemplate;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenPair generateTokenPair(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        // save refresh token in Redis with key "refresh_token:username"
        String refreshTokenKey = "refresh_token:" + username;
        redisTemplate.opsForValue().set(
                refreshTokenKey,
                refreshToken,
                refreshExpiration,
                TimeUnit.MILLISECONDS
        );
        return new TokenPair(accessToken, refreshToken);
    }

    private String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim("userId", user.getId())
                .claim("roles", user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList())
                )
                .signWith(getSigningKey())
                .compact();
    }

    private String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        String refreshTokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setId(refreshTokenId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim("tokenType", "REFRESH")
                .signWith(getSigningKey())
                .compact();
    }

    public String refreshAccessToken(String refreshToken) {
        if (!validateToken(refreshToken)) {
            throw new InvalidTokenException("Invalid refresh token");
        }
        String username = getUsernameFromToken(refreshToken);
        // check if refresh token exists in Redis
        String storedToken = redisTemplate.opsForValue().get("refresh_token:" + username);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new InvalidTokenException("Refresh token not found or expired");
        }

        // generate new access token
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return generateAccessToken(user);
    }


    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            // Check if token is in blacklist
            if (isTokenBlacklisted(token)) {
                return false;
            }

            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void blacklistToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();

            if (ttl > 0) {
                redisTemplate.opsForValue().set(
                        "blacklist:" + token,
                        "blacklisted",
                        ttl,
                        TimeUnit.MILLISECONDS);
            }
        } catch (JwtException e) {
            // Invalid token, no need to blacklist
        }
    }

    private boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    @Data
    @AllArgsConstructor
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
    }
}