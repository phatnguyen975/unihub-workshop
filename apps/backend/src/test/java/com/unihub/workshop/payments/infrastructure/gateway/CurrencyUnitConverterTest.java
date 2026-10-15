package com.unihub.workshop.payments.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CurrencyUnitConverterTest {

    private CurrencyUnitConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyUnitConverter();
    }

    @Test
    @DisplayName("Converts 2-decimal standard currency (USD) to cents")
    void toSmallestUnit_UsdCurrency_MultipliesBy100() {
        long result = converter.toSmallestUnit(new BigDecimal("50.00"), "USD");
        assertThat(result).isEqualTo(5000L);

        long result2 = converter.toSmallestUnit(new BigDecimal("12.34"), "USD");
        assertThat(result2).isEqualTo(1234L);

        long result3 = converter.toSmallestUnit(new BigDecimal("0.99"), "usd");
        assertThat(result3).isEqualTo(99L);
    }

    @ParameterizedTest
    @CsvSource({
        "50000, VND, 50000",
        "1000, JPY, 1000",
        "25000, KRW, 25000",
        "50000, vnd, 50000",
    })
    @DisplayName("Converts zero-decimal currencies using factor 1")
    void toSmallestUnit_ZeroDecimalCurrencies_FactorIsOne(
        String amount,
        String currency,
        long expected
    ) {
        long result = converter.toSmallestUnit(
            new BigDecimal(amount),
            currency
        );
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Converts 3-decimal currency (KWD, BHD) multiplying by 1000")
    void toSmallestUnit_ThreeDecimalCurrency_MultipliesBy1000() {
        long result = converter.toSmallestUnit(new BigDecimal("1.500"), "KWD");
        assertThat(result).isEqualTo(1500L);
    }

    @Test
    @DisplayName(
        "Defaults to USD 2-decimal conversion if currency is null or blank"
    )
    void toSmallestUnit_NullOrBlankCurrency_DefaultsToTwoDecimals() {
        long resultNull = converter.toSmallestUnit(
            new BigDecimal("10.50"),
            null
        );
        assertThat(resultNull).isEqualTo(1050L);

        long resultBlank = converter.toSmallestUnit(
            new BigDecimal("10.50"),
            "   "
        );
        assertThat(resultBlank).isEqualTo(1050L);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when amount is null")
    void toSmallestUnit_NullAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> converter.toSmallestUnit(null, "USD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount must not be null");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when amount is negative")
    void toSmallestUnit_NegativeAmount_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() ->
            converter.toSmallestUnit(new BigDecimal("-5.00"), "USD")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Amount must not be negative");
    }
}
