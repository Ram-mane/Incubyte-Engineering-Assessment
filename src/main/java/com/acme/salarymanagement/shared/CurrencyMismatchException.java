package com.acme.salarymanagement.shared;

/**
 * Thrown when two amounts in different currencies are combined. Unchecked deliberately: mixing
 * currencies is a programming error, not a condition a caller should be expected to recover from.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String message) {
        super(message);
    }
}
