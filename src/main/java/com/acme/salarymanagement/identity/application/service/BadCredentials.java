package com.acme.salarymanagement.identity.application.service;

/**
 * One exception for "no such user" and "wrong password", carrying one message.
 *
 * <p>Telling the two apart tells an attacker which addresses are real, which is a list worth
 * having when the accounts hold payroll.
 */
public class BadCredentials extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadCredentials() {
        super("those credentials do not match a user");
    }
}
