package com.acme.salarymanagement.identity.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.identity.application.port.in.LogIn;
import com.acme.salarymanagement.identity.application.service.BadCredentials;

/** The one endpoint that does not need a token, because it is where tokens come from. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final LogIn logIn;

    AuthController(LogIn logIn) {
        this.logIn = logIn;
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        return LoginResponse.of(logIn.withPassword(request.email(), request.password()));
    }

    @ExceptionHandler(BadCredentials.class)
    ProblemDetail thoseCredentialsDoNotMatch(BadCredentials refused) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Login failed");
        // One message for both "no such user" and "wrong password": distinguishing them hands
        // an attacker a list of real addresses, and these accounts hold payroll.
        problem.setDetail(refused.getMessage());
        return problem;
    }
}
