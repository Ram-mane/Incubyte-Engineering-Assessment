package com.acme.salarymanagement.employee.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Deliberately a shape check rather than an RFC 5322 implementation: exactly one {@code @}, with
 * something either side. A regex that claims to validate every legal address rejects real ones.
 *
 * <p>Stored lowercased, so equality is case-insensitive and the stored value can differ from what
 * was typed. That is correct for email - the domain part is case-insensitive and nobody treats the
 * local part as case-sensitive in practice - but it is a decision, not an accident.
 */
public record EmailAddress(String value) {

    public EmailAddress {
        Objects.requireNonNull(value, "an email address is required");
        value = value.trim().toLowerCase(Locale.ROOT);
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
            throw new IllegalArgumentException("not an email address: %s".formatted(value));
        }
    }
}
