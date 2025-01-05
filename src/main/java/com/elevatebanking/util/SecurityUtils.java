package com.elevatebanking.util;

import java.util.Collection;
import java.util.Collections;

import com.elevatebanking.exception.ResourceNotFoundException;
import com.elevatebanking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.exception.UnauthorizedException;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository UserRepository;

    public String getCurrentUserId() {
        String username = getCurrentUsername();
        return UserRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found")).getId();
    }

    public static String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetails) {
                return ((UserDetails) auth.getPrincipal()).getUsername();
            }
            throw new UnauthorizedException("User not authenticated");
        } catch (Exception e) {
            log.error("Error getting current username: {}", e.getMessage());
            throw new UnauthorizedException("Could not get current username", e);
        }
    }

    public static boolean isCurrentUserAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null &&
                authentication.isAuthenticated() &&
                !(authentication instanceof AnonymousAuthenticationToken);
    }

    public static Collection<? extends GrantedAuthority> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getAuthorities() : Collections.emptyList();
    }
}
