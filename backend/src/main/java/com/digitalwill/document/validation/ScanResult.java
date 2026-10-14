package com.digitalwill.document.validation;

/**
 * Result of an antivirus / antimalware file scan.
 */
public record ScanResult(
        ScanStatus status,
        String virusName,
        String message
) {
    public static ScanResult clean() {
        return new ScanResult(ScanStatus.CLEAN, null, "File is clean");
    }

    public static ScanResult infected(String virusName) {
        return new ScanResult(ScanStatus.INFECTED, virusName, "Malware signature detected: " + virusName);
    }

    public static ScanResult error(String message) {
        return new ScanResult(ScanStatus.ERROR, null, message);
    }
}
