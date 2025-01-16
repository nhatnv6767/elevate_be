package com.elevatebanking.controller.atm;

import com.elevatebanking.dto.atm.AtmDTOs.*;
import com.elevatebanking.entity.atm.AtmMachine;
import com.elevatebanking.service.atm.AtmManagementService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/atm-management")
@RequiredArgsConstructor
@Tag(name = "ATM Management", description = "APIs for managing ATM machines")
@SecurityRequirement(name = "Bearer Authentication")
@Slf4j
public class AtmManagementController {
    private final AtmManagementService atmManagementService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/atms")
    public ResponseEntity<?> createAtm(@RequestBody AtmCreationRequest request) {
        log.info("Creating new ATM with bankCode: {}", request.getBankCode());
        try {
            AtmMachine atm = atmManagementService.createAtm(request);
            return ResponseEntity.ok(AtmResponse.from(atm));
        } catch (Exception e) {
            log.error("Error creating ATM: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Failed to create ATM",
                    "error", e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/atms")
    public ResponseEntity<List<AtmResponse>> getAllAtms() {
        List<AtmMachine> atms = atmManagementService.getAllAtms();
        return ResponseEntity.ok(atms.stream()
                .map(AtmResponse::from)
                .collect(Collectors.toList()));

    }
}
