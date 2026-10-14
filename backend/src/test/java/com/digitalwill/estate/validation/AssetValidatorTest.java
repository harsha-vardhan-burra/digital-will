package com.digitalwill.estate.validation;

import com.digitalwill.common.exception.AssetValidationException;
import com.digitalwill.common.exception.DuplicateResourceException;
import com.digitalwill.common.exception.ValidationException;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.repository.AssetRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AssetValidatorTest {

    private AssetRepository assetRepository;
    private AssetValidator validator;

    private WillStateEntity activeWill;
    private UUID willId;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        assetRepository = Mockito.mock(AssetRepository.class);
        validator = new AssetValidator(assetRepository);

        willId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        activeWill = new WillStateEntity(willId, ownerId, "Primary Will", WillState.ACTIVE, Instant.now(), Instant.now());

        when(assetRepository.countByWillId(willId)).thenReturn(0L);
        when(assetRepository.existsByWillIdAndTitleIgnoreCaseAndCategory(eq(willId), any(), any())).thenReturn(false);
    }

    @ParameterizedTest
    @EnumSource(value = AssetCategory.class)
    @DisplayName("Valid minimal asset record passes for all supported categories")
    void validMinimalAssetRecordPasses(AssetCategory category) {
        var result = validator.validate(
                activeWill,
                "Standard Asset Record",
                category,
                "Description for asset",
                null,
                null,
                "Acme Provider",
                "Checking",
                "USD",
                BigDecimal.valueOf(1500.00),
                LocalDate.now().minusDays(1),
                BigDecimal.valueOf(100.00),
                2022,
                "https://secure.example.com/asset",
                "1234567890"
        );

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Standard Asset Record");
        assertThat(result.category()).isEqualTo(category);
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.identifierMasked()).isEqualTo("****7890");
    }

    @Test
    @DisplayName("Bank account validates balance precision, currency, and masks identifier")
    void bankAccountValidatesCorrectly() {
        var result = validator.validate(
                activeWill,
                "Chase Premier Checking",
                AssetCategory.BANK_ACCOUNT,
                "Main primary household checking account",
                null,
                "Contact executor upon succession",
                "JPMorgan Chase",
                "Checking",
                "USD",
                new BigDecimal("12450.75"),
                LocalDate.now().minusDays(5),
                new BigDecimal("100.00"),
                null,
                "https://www.chase.com",
                "9876543210"
        );

        assertThat(result.category()).isEqualTo(AssetCategory.BANK_ACCOUNT);
        assertThat(result.institutionProvider()).isEqualTo("JPMorgan Chase");
        assertThat(result.identifierMasked()).isEqualTo("****3210");
        assertThat(result.assetValue()).isEqualTo(new BigDecimal("12450.75"));
    }

    @Test
    @DisplayName("Bank account rejects balance with more than 2 decimal places")
    void bankAccountRejectsExcessivePrecision() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "High Yield Savings",
                AssetCategory.BANK_ACCOUNT,
                "Savings",
                null,
                null,
                "Ally Bank",
                "Savings",
                "USD",
                new BigDecimal("100.123"),
                LocalDate.now(),
                null,
                null,
                null,
                "1234"
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Bank account balances must not exceed 2 decimal places");
    }

    @Test
    @DisplayName("Investment holding rejects zero holding value")
    void investmentRejectsZeroHoldingValue() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Vanguard S&P 500 ETF",
                AssetCategory.INVESTMENT,
                "Index fund holding",
                null,
                null,
                "Vanguard",
                "Brokerage",
                "USD",
                BigDecimal.ZERO,
                LocalDate.now(),
                null,
                null,
                null,
                "V09876"
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("must be greater than zero");
    }

    @Test
    @DisplayName("Vehicle rejects impossible years before 1886 or beyond next year")
    void vehicleRejectsImpossibleYear() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Family Sedan",
                AssetCategory.VEHICLE,
                "2020 Toyota Camry",
                null,
                null,
                null,
                null,
                "USD",
                BigDecimal.valueOf(18000),
                null,
                null,
                1850,
                null,
                "VIN12345678"
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Vehicle year must be between 1886 and");

        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Futuristic Car",
                AssetCategory.VEHICLE,
                "Future prototype",
                null,
                null,
                null,
                null,
                "USD",
                BigDecimal.valueOf(50000),
                null,
                null,
                2150,
                null,
                "VIN99999999"
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Vehicle year must be between 1886 and");
    }

    @Test
    @DisplayName("Negative valuation or holding value is rejected")
    void negativeAssetValueIsRejected() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Negative Asset",
                AssetCategory.OTHER,
                "Test",
                null,
                null,
                null,
                null,
                "USD",
                new BigDecimal("-50.00"),
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("cannot be negative");
    }

    @Test
    @DisplayName("Invalid ownership percentage bounds (<= 0 or > 100) are rejected")
    void invalidOwnershipPercentageRejected() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Beach Condo",
                AssetCategory.REAL_ESTATE,
                "123 Ocean Blvd",
                null,
                null,
                null,
                null,
                "USD",
                BigDecimal.valueOf(500000),
                null,
                BigDecimal.valueOf(0),
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Ownership percentage must be between 0.01% and 100%");

        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Beach Condo",
                AssetCategory.REAL_ESTATE,
                "123 Ocean Blvd",
                null,
                null,
                null,
                null,
                "USD",
                BigDecimal.valueOf(500000),
                null,
                BigDecimal.valueOf(101),
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Ownership percentage must be between 0.01% and 100%");
    }

    @Test
    @DisplayName("Future valuation date is rejected")
    void futureValuationDateRejected() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Valued Asset",
                AssetCategory.INVESTMENT,
                "Portfolio",
                null,
                null,
                "Fidelity",
                "IRA",
                "USD",
                BigDecimal.valueOf(10000),
                LocalDate.now().plusDays(2),
                null,
                null,
                null,
                "FID-123"
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Valuation date cannot be in the future");
    }

    @ParameterizedTest
    @ValueSource(strings = {"US", "DOLLARS", "ABCD", "123"})
    @DisplayName("Invalid ISO-4217 currency code is rejected")
    void invalidCurrencyCodeRejected(String invalidCurrency) {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Asset with bad currency",
                AssetCategory.OTHER,
                "Description",
                null,
                null,
                null,
                null,
                invalidCurrency,
                BigDecimal.valueOf(100),
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("currency code");
    }

    @Test
    @DisplayName("Credential injection in description or instructions is rejected")
    void credentialInjectionRejected() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Crypto Vault",
                AssetCategory.CRYPTO,
                "password: MySecretMasterPassword123!",
                null,
                null,
                null,
                null,
                "USD",
                BigDecimal.valueOf(5000),
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(ValidationException.class)
          .satisfies(e -> assertThat(((ValidationException) e).getCode()).isEqualTo("CREDENTIAL_STORAGE_PROHIBITED"));

        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Bitcoin Cold Storage",
                AssetCategory.CRYPTO,
                "Safe holding",
                null,
                "My secret recovery phrase is word one word two ...",
                null,
                null,
                "USD",
                BigDecimal.valueOf(50000),
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(ValidationException.class)
          .satisfies(e -> assertThat(((ValidationException) e).getCode()).isEqualTo("CREDENTIAL_STORAGE_PROHIBITED"));
    }

    @Test
    @DisplayName("SSRF loopback or private URLs in assetUrl are rejected")
    void ssrfUrlRejected() {
        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Server Asset",
                AssetCategory.DIGITAL_ACCOUNT,
                "Cloud server",
                null,
                null,
                "AWS",
                "Hosting",
                null,
                null,
                null,
                null,
                null,
                "http://169.254.169.254/latest/meta-data/",
                null
        )).isInstanceOf(ValidationException.class)
          .satisfies(e -> assertThat(((ValidationException) e).getCode()).isEqualTo("INVALID_URL"));
    }

    @Test
    @DisplayName("Duplicate asset detection triggers DuplicateResourceException")
    void duplicateAssetTriggersException() {
        when(assetRepository.existsByWillIdAndTitleIgnoreCaseAndCategory(
                eq(willId), eq("Chase Checking"), eq(AssetCategory.BANK_ACCOUNT)
        )).thenReturn(true);

        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "Chase Checking",
                AssetCategory.BANK_ACCOUNT,
                "Checking account",
                null,
                null,
                "Chase",
                "Checking",
                "USD",
                BigDecimal.valueOf(500),
                null,
                null,
                null,
                null,
                "1234"
        )).isInstanceOf(DuplicateResourceException.class)
          .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Collection bound limit of 500 assets per will is enforced")
    void collectionBoundLimitEnforced() {
        when(assetRepository.countByWillId(willId)).thenReturn(500L);

        assertThatThrownBy(() -> validator.validate(
                activeWill,
                "501st Asset",
                AssetCategory.OTHER,
                "Overflow asset",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("limit reached");
    }

    @ParameterizedTest
    @EnumSource(value = WillState.class, names = {"EXECUTING", "EXECUTED"})
    @DisplayName("Cannot modify assets when will is in EXECUTING or EXECUTED state")
    void immutableWillRejectsAssetModification(WillState terminalState) {
        WillStateEntity lockedWill = new WillStateEntity(willId, ownerId, "Locked Will", terminalState, Instant.now(), Instant.now());

        assertThatThrownBy(() -> validator.validate(
                lockedWill,
                "New Asset",
                AssetCategory.OTHER,
                "Attempt to add to locked will",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(AssetValidationException.class)
          .hasMessageContaining("Cannot modify assets: Digital Will is in terminal or executing state");
    }
}
