package com.digitalwill.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProhibitedCredentialValidatorTest {

    @Test
    @DisplayName("Null or empty string values pass validation without exception")
    void nullOrEmptyValuesPass() {
        assertThatCode(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", null))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", ""))
                .doesNotThrowAnyException();
        assertThatCode(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", "   "))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Normal safe text without credentials passes validation")
    void legitimateNormalTextPasses() {
        String safeText = "Checking account at Chase Bank for household utility payments and mortgage.";
        assertThatCode(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("instructions", safeText))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQD3...\n-----END PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0...",
            "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEI..."
    })
    @DisplayName("Cryptographic PEM private keys are rejected")
    void pemPrivateKeysAreRejected(String privateKeyContent) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("encryptedAccessData", privateKeyContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "My wallet seed phrase is apple banana cherry dog elephant fox grape horse igloo jacket kite lion",
            "Please find my recovery phrase attached: 12-word seed for Ledger",
            "Here is the mnemonic phrase for my cold storage vault",
            "Do not lose the 24-word seed phrase"
    })
    @DisplayName("BIP-39 mnemonic recovery and seed phrases are rejected")
    void seedPhrasesAreRejected(String seedText) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("instructions", seedText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recovery");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password: SuperSecretPassword123!",
            "Bank password = TopSecretPass999",
            "pwd: MyMasterPassword!"
    })
    @DisplayName("Plaintext password patterns are rejected")
    void passwordsAreRejected(String passwordText) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", passwordText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "atm pin: 1234",
            "bank pin = 4321",
            "cvv: 789",
            "cvc = 123"
    })
    @DisplayName("Bank PIN and card CVV patterns are rejected")
    void pinsAndCvvsAreRejected(String pinText) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("instructions", pinText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PIN or card CVV");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api_secret: sec_live_998877665544332211",
            "client_secret = myOAuthClientSecretXYZ",
            "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0",
            "cookie: session_id=abcdef123456"
    })
    @DisplayName("API secrets, bearer tokens, and session cookies are rejected")
    void apiSecretsAndTokensAreRejected(String secretText) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("description", secretText))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2fa secret: JBSWY3DPEHPK3PXP",
            "totp secret = 4859203948572019",
            "otp code: 123456"
    })
    @DisplayName("MFA codes and TOTP secrets are rejected")
    void mfaSecretsAreRejected(String mfaText) {
        assertThatThrownBy(() -> ProhibitedCredentialValidator.validateNoProhibitedCredentials("instructions", mfaText))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MFA");
    }
}
