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


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/atms/{atmId}/denominations")
    public ResponseEntity<?> updateAtmDenominations(
            @PathVariable String atmId,
            @RequestBody Map<Integer, Integer> denominations
    ) {
        log.info("Updating denominations for ATM: {}", atmId);
        try {
            AtmMachine atm = atmManagementService.updateAtmDenominations(atmId, denominations);
            return ResponseEntity.ok(AtmResponse.from(atm));
        } catch (Exception e) {
            log.error("Error updating ATM denominations: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Failed to update ATM denominations",
                    "error", e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/atms/{atmId}")
    public ResponseEntity<?> getAtm(@PathVariable String atmId) {
        try {
            AtmMachine atm = atmManagementService.getAtmById(atmId);
            return ResponseEntity.ok(AtmResponse.from(atm));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/atms/{atmId}/status")
    public ResponseEntity<?> updateAtmStatus(
            @PathVariable String atmId,
            @RequestParam String status
    ) {
        try {
            AtmMachine atm = atmManagementService.updateAtmStatus(atmId, status);
            return ResponseEntity.ok(AtmResponse.from(atm));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Failed to update ATM status",
                    "error", e.getMessage()
            ));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/atms/{atmId}/denominations/add")
    public ResponseEntity<?> addAtmDenominations(
            @PathVariable String atmId,
            @RequestBody Map<Integer, Integer> denominationsToAdd
    ) {
        try {
            AtmMachine atm = atmManagementService.addAtmDenominations(atmId, denominationsToAdd);
            return ResponseEntity.ok(AtmResponse.from(atm));
        } catch (Exception e) {
            log.error("Error adding denominations to ATM: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Failed to add denominations to ATM",
                    "error", e.getMessage()
            ));
        }
    }

}
