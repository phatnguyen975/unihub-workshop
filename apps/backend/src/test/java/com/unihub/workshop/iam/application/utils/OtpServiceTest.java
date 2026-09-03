package com.unihub.workshop.iam.application.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceTest {

    private OtpService otpService;
    private final String testSecret = "my-super-secret-hmac-key-for-testing-1234567890";

    @BeforeEach
    void setUp() {
        otpService = new OtpService(testSecret);
        otpService.init();
    }

    @Test
    void generateOtpShouldReturnSixDigitNumericString() {
        for (int i = 0; i < 50; i++) {
            String otp = otpService.generateOtp();
            assertThat(otp).isNotNull();
            assertThat(otp).matches("^[0-9]{6}$");
        }
    }

    @Test
    void hashOtpAndVerifyShouldReturnTrueForMatchingOtp() {
        String otp = "123456";
        String hash = otpService.hashOtp(otp);

        assertThat(hash).isNotNull().isNotBlank();
        assertThat(otpService.verifyOtp(otp, hash)).isTrue();
    }

    @Test
    void verifyOtpShouldReturnFalseForMismatch() {
        String otp = "123456";
        String hash = otpService.hashOtp(otp);

        assertThat(otpService.verifyOtp("654321", hash)).isFalse();
        assertThat(otpService.verifyOtp("000000", hash)).isFalse();
        assertThat(otpService.verifyOtp(null, hash)).isFalse();
        assertThat(otpService.verifyOtp(otp, null)).isFalse();
    }

    @Test
    void initShouldFailWhenSecretIsBlankOrNull() {
        OtpService invalidService = new OtpService("   ");
        assertThatThrownBy(invalidService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP HMAC secret");
    }
}
