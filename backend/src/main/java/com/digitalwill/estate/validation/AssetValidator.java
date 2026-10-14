package com.digitalwill.estate.validation;

import com.digitalwill.common.exception.AssetValidationException;
import com.digitalwill.common.exception.DuplicateResourceException;
import com.digitalwill.common.validation.IdentifierMasker;
import com.digitalwill.common.validation.ProhibitedCredentialValidator;
import com.digitalwill.common.validation.SafeUrlValidator;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.repository.AssetRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative server-side validator for structured asset records, category-specific rules,
 * duplicate detection, credential protection, and estate modification permissions.
 */
@Component
public class AssetValidator {

    public static final int MAX_TITLE_LENGTH = 255;
    public static final int MAX_TEXT_LENGTH = 4000;
    public static final int MAX_ASSETS_PER_WILL = 500;
    public static final int MIN_VEHICLE_YEAR = 1886;

    private final AssetRepository assetRepository;

    public AssetValidator(AssetRepository assetRepository) {
        this.assetRepository = Objects.requireNonNull(assetRepository);
    }

    public record ValidatedAssetData(
            String title,
            AssetCategory category,
            String description,
            String encryptedAccessData,
            String instructions,
            String institutionProvider,
            String accountType,
            String currency,
            BigDecimal assetValue,
            LocalDate valuationDate,
            BigDecimal ownershipPercentage,
            Integer assetYear,
            String assetUrl,
            String identifierMasked
    ) {}

    /**
     * Verifies that the will is currently in an editable state and not locked or executed.
     */
    public void verifyWillModifiable(WillStateEntity will) {
        Objects.requireNonNull(will, "Will must not be null");
        if (will.getState() == WillState.EXECUTING || will.getState() == WillState.EXECUTED) {
            throw new AssetValidationException(
                    "INVALID_STATE",
                    "Cannot modify assets: Digital Will is in terminal or executing state (" + will.getState() + ")",
                    org.springframework.http.HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Validates candidate asset inputs against all system and category-specific rules.
     */
    public ValidatedAssetData validate(
            WillStateEntity will,
            String title,
            AssetCategory category,
            String description,
            String encryptedAccessData,
            String instructions,
            String institutionProvider,
            String accountType,
            String currencyCode,
            BigDecimal assetValue,
            LocalDate valuationDate,
            BigDecimal ownershipPercentage,
            Integer assetYear,
            String assetUrl,
            String rawIdentifier
    ) {
        verifyWillModifiable(will);

        // 1. Title validation
        if (title == null || title.isBlank()) {
            throw new AssetValidationException("REQUIRED_FIELD_MISSING", "Asset title is required and cannot be blank");
        }
        String cleanTitle = title.trim();
        if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            throw new AssetValidationException("INVALID_ARGUMENT", "Asset title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }
        try {
            ProhibitedCredentialValidator.validateNoProhibitedCredentials("title", cleanTitle);
        } catch (IllegalArgumentException e) {
            throw new AssetValidationException("CREDENTIAL_STORAGE_PROHIBITED", e.getMessage());
        }

        // 2. Category validation
        if (category == null) {
            throw new AssetValidationException("REQUIRED_FIELD_MISSING", "Asset category is required");
        }

        // 3. Text field bounds and credential screening
        String cleanDesc = validateBoundedText("description", description);
        String cleanAccess = validateBoundedText("encryptedAccessData", encryptedAccessData);
        String cleanInstructions = validateBoundedText("instructions", instructions);
        String cleanProvider = validateBoundedText("institutionProvider", institutionProvider);
        String cleanAccountType = validateBoundedText("accountType", accountType);

        // 4. URL validation (SSRF defense)
        if (assetUrl != null && !assetUrl.isBlank()) {
            try {
                SafeUrlValidator.validateUrl(assetUrl);
            } catch (IllegalArgumentException e) {
                throw new AssetValidationException("INVALID_URL", e.getMessage());
            }
        }

        // 5. Currency code validation
        String validatedCurrency = null;
        if (currencyCode != null && !currencyCode.isBlank()) {
            String trimmedCurrency = currencyCode.trim().toUpperCase();
            if (trimmedCurrency.length() != 3) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Currency must be a valid 3-letter ISO-4217 currency code");
            }
            try {
                Currency.getInstance(trimmedCurrency);
                validatedCurrency = trimmedCurrency;
            } catch (IllegalArgumentException e) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Invalid ISO-4217 currency code: " + trimmedCurrency);
            }
        }

        // 6. Numerical Value validation (Finite, non-negative, precision bounds, and magnitude check <= 15 integer digits)
        if (assetValue != null) {
            validateFiniteNonNegativeNumber("assetValue", assetValue);
            if (assetValue.scale() > 4) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Asset value precision cannot exceed 4 decimal places");
            }
            if (assetValue.precision() - assetValue.scale() > 15) {
                throw new AssetValidationException("INVALID_AMOUNT", "Asset value exceeds maximum supported amount");
            }
        }

        // 7. Valuation Date validation (cannot be in the future)
        LocalDate today = LocalDate.now();
        if (valuationDate != null && valuationDate.isAfter(today)) {
            throw new AssetValidationException("INVALID_DATE", "Valuation date cannot be in the future");
        }

        // 8. Ownership percentage validation (scale bound to 2 decimal places)
        if (ownershipPercentage != null) {
            if (ownershipPercentage.scale() > 2) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Ownership percentage precision cannot exceed 2 decimal places");
            }
            if (ownershipPercentage.compareTo(BigDecimal.ZERO) <= 0 || ownershipPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Ownership percentage must be between 0.01% and 100%");
            }
        }

        // Global asset year check for non-vehicle categories (vehicles have tighter 1886 bound)
        if (assetYear != null && category != AssetCategory.VEHICLE && (assetYear < 1800 || assetYear > 2100)) {
            throw new AssetValidationException("INVALID_DATE", "Asset year must be between 1800 and 2100");
        }

        // 9. Category-specific validations
        switch (category) {
            case BANK_ACCOUNT -> validateBankAccount(cleanTitle, cleanDesc, cleanProvider, validatedCurrency, assetValue);
            case INVESTMENT -> validateInvestment(cleanTitle, validatedCurrency, assetValue);
            case REAL_ESTATE -> validateRealEstate(cleanTitle, cleanDesc, ownershipPercentage);
            case VEHICLE -> validateVehicle(assetYear);
            case PERSONAL_PROPERTY -> validatePersonalProperty(assetYear);
            case DIGITAL_ACCOUNT, CRYPTO -> validateDigitalAndCrypto(cleanTitle, cleanDesc, cleanAccess, cleanInstructions);
            case OTHER -> {
                // General asset: standard bounds apply
            }
        }

        // 10. Mask identifiers for safe storage and logging after credential screening
        String cleanRawIdentifier = null;
        if (rawIdentifier != null && !rawIdentifier.isBlank()) {
            cleanRawIdentifier = rawIdentifier.trim();
            if (cleanRawIdentifier.length() > 255) {
                throw new AssetValidationException("INVALID_ARGUMENT", "Account identifier exceeds 255 characters");
            }
            try {
                ProhibitedCredentialValidator.validateNoProhibitedCredentials("rawIdentifier", cleanRawIdentifier);
            } catch (IllegalArgumentException e) {
                throw new AssetValidationException("CREDENTIAL_STORAGE_PROHIBITED", e.getMessage());
            }
        }
        String maskedIdentifier = IdentifierMasker.mask(cleanRawIdentifier);

        // 11. Collection size bound
        long count = assetRepository.countByWillId(will.getId());
        if (count >= MAX_ASSETS_PER_WILL) {
            throw new AssetValidationException("INVALID_ARGUMENT", "Estate asset limit reached (maximum " + MAX_ASSETS_PER_WILL + " assets per estate)");
        }

        // 12. Duplicate detection
        boolean isDuplicate = assetRepository.existsByWillIdAndTitleIgnoreCaseAndCategory(will.getId(), cleanTitle, category);
        if (isDuplicate) {
            throw new DuplicateResourceException("An asset with title '" + cleanTitle + "' and category " + category + " already exists in this estate");
        }

        return new ValidatedAssetData(
                cleanTitle,
                category,
                cleanDesc,
                cleanAccess,
                cleanInstructions,
                cleanProvider,
                cleanAccountType,
                validatedCurrency,
                assetValue,
                valuationDate,
                ownershipPercentage,
                assetYear,
                assetUrl,
                maskedIdentifier
        );
    }

    private String validateBoundedText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new AssetValidationException("INVALID_ARGUMENT", "Field [" + fieldName + "] exceeds maximum length of " + MAX_TEXT_LENGTH + " characters");
        }
        try {
            ProhibitedCredentialValidator.validateNoProhibitedCredentials(fieldName, trimmed);
        } catch (IllegalArgumentException e) {
            throw new AssetValidationException("CREDENTIAL_STORAGE_PROHIBITED", e.getMessage());
        }
        return trimmed;
    }

    private void validateFiniteNonNegativeNumber(String fieldName, BigDecimal number) {
        if (number.compareTo(BigDecimal.ZERO) < 0) {
            throw new AssetValidationException("INVALID_AMOUNT", "Field [" + fieldName + "] cannot be negative");
        }
    }

    private void validateBankAccount(String title, String description, String provider, String currency, BigDecimal balance) {
        if (balance != null && balance.scale() > 2) {
            throw new AssetValidationException("INVALID_AMOUNT", "Bank account balances must not exceed 2 decimal places");
        }
    }

    private void validateInvestment(String title, String currency, BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) == 0) {
            throw new AssetValidationException("INVALID_AMOUNT", "Investment holding value must be greater than zero");
        }
    }

    private void validateRealEstate(String title, String description, BigDecimal ownership) {
        if ((description == null || description.isBlank()) && title.length() < 3) {
            throw new AssetValidationException("REQUIRED_FIELD_MISSING", "Real estate assets require property details or address in title or description");
        }
    }

    private void validateVehicle(Integer year) {
        if (year != null) {
            int currentYear = LocalDate.now().getYear();
            if (year < MIN_VEHICLE_YEAR || year > currentYear + 1) {
                throw new AssetValidationException("INVALID_DATE", "Vehicle year must be between " + MIN_VEHICLE_YEAR + " and " + (currentYear + 1));
            }
        }
    }

    private void validatePersonalProperty(Integer year) {
        if (year != null) {
            int currentYear = LocalDate.now().getYear();
            if (year < 1800 || year > currentYear + 1) {
                throw new AssetValidationException("INVALID_DATE", "Personal property year must be between 1800 and " + (currentYear + 1));
            }
        }
    }

    private void validateDigitalAndCrypto(String title, String desc, String accessData, String instructions) {
        // Enforce strict prohibition of private keys, seed phrases, and passwords
        ProhibitedCredentialValidator.validateNoProhibitedCredentials("title", title);
        ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", desc);
        ProhibitedCredentialValidator.validateNoProhibitedCredentials("encryptedAccessData", accessData);
        ProhibitedCredentialValidator.validateNoProhibitedCredentials("instructions", instructions);
    }
}
