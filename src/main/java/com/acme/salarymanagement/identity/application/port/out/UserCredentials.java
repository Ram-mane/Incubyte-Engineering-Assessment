package com.acme.salarymanagement.identity.application.port.out;

import java.util.Optional;

import com.acme.salarymanagement.identity.domain.Credentials;

/** Looking someone up by the only thing they type. */
public interface UserCredentials {

    Optional<Credentials> forEmail(String email);
}
