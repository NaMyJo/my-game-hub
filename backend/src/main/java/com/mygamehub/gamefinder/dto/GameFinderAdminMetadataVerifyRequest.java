package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamMetadataVerificationService.VerificationMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Locale;
import java.util.Set;

public record GameFinderAdminMetadataVerifyRequest(
        @Min(10) @Max(500) Integer sampleSize,
        @NotBlank String mode) {
    private static final Set<Integer> SUPPORTED_SIZES = Set.of(10, 50, 100, 200, 500);

    public boolean supportedSampleSize() { return SUPPORTED_SIZES.contains(sampleSize); }
    public VerificationMode verificationMode() {
        return VerificationMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
    }
}
