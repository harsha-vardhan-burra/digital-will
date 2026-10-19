package com.digitalwill.common.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict URL validator enforcing HTTPS/HTTP schemes and blocking Server-Side
 * Request Forgery (SSRF) targets, loopback, private IP ranges, and link-local metadata endpoints.
 */
public final class SafeUrlValidator {

    private SafeUrlValidator() {}

    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http");
    private static final int MAX_URL_LENGTH = 2000;

    // Pattern for private / link-local / loopback IPv4 addresses
    private static final Pattern PRIVATE_OR_LOOPBACK_IPV4 = Pattern.compile(
            "^(?:127\\.|10\\.|192\\.168\\.|169\\.254\\.|172\\.(?:1[6-9]|2[0-9]|3[0-1])\\.|0\\.0\\.0\\.0)"
    );

    /**
     * Validates that a user-submitted URL is safe and well-formed.
     * Note: The backend NEVER fetches user-supplied URLs.
     */
    public static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("URL exceeds maximum length of " + MAX_URL_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + e.getMessage());
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs must not contain embedded user credentials");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException("Invalid URL scheme [" + scheme + "]. Only HTTPS (or HTTP) URLs are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must contain a valid, non-empty host");
        }

        String hostLower = host.toLowerCase();
        // Normalize trailing dots e.g. "localhost." or "example.com."
        while (hostLower.endsWith(".")) {
            hostLower = hostLower.substring(0, hostLower.length() - 1);
        }

        // Normalize IPv6 bracketed hosts e.g. "[::1]" -> "::1"
        if (hostLower.startsWith("[") && hostLower.endsWith("]")) {
            hostLower = hostLower.substring(1, hostLower.length() - 1);
        }

        if (hostLower.equals("localhost") || hostLower.endsWith(".localhost")
                || hostLower.equals("::1") || hostLower.equals("0:0:0:0:0:0:0:1") || hostLower.equals("::")
                || hostLower.startsWith("fe80:") || hostLower.startsWith("fc00:") || hostLower.startsWith("fd00:")
                || hostLower.startsWith("::ffff:127.") || hostLower.startsWith("::ffff:10.") || hostLower.startsWith("::ffff:192.168.")
                || hostLower.equals("instance-data") || hostLower.contains("metadata")) {
            throw new IllegalArgumentException("Loopback and local addresses are disallowed in asset URLs");
        }

        // Disallow numeric decimal/hex IP representations (e.g. 2130706433 or 0x7f000001)
        if (hostLower.matches("^\\d+$") || hostLower.matches("^0x[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("Integer or hex formatted IP addresses are disallowed in asset URLs");
        }

        if (PRIVATE_OR_LOOPBACK_IPV4.matcher(hostLower).find()) {
            throw new IllegalArgumentException("Private subnet, link-local, and loopback IP addresses are disallowed in asset URLs");
        }
    }
}
