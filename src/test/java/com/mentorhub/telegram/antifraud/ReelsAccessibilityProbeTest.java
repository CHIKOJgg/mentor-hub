package com.mentorhub.telegram.antifraud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReelsAccessibilityProbeTest {

    @Test
    @DisplayName("Empty or null URL returns invalid result with 400 status")
    void verifyAccessibility_emptyUrl() {
        ReelsAccessibilityProbe probe = new ReelsAccessibilityProbe();

        ReelsAccessibilityProbe.ProbeResult r1 = probe.verifyAccessibility("");
        assertThat(r1.isValid()).isFalse();
        assertThat(r1.httpStatus()).isEqualTo(400);

        ReelsAccessibilityProbe.ProbeResult r2 = probe.verifyAccessibility(null);
        assertThat(r2.isValid()).isFalse();
        assertThat(r2.httpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Network connection failure / unreachable host returns TIMEOUT_UNVERIFIED without failing report")
    void verifyAccessibility_networkFailure() {
        ReelsAccessibilityProbe probe = new ReelsAccessibilityProbe();

        // Testing unresolvable domain to verify graceful catch of network exceptions
        ReelsAccessibilityProbe.ProbeResult result =
                probe.verifyAccessibility("https://non-existent-domain-test-12345.test/reel/DC194kaP91m/");

        assertThat(result.isValid()).isTrue();
        assertThat(result.note()).contains("TIMEOUT_UNVERIFIED");
    }
}
