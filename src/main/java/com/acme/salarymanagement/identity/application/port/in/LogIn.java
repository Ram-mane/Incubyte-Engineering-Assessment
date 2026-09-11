package com.acme.salarymanagement.identity.application.port.in;

public interface LogIn {

    LoggedIn withPassword(String email, String password);
}
