package com.acme.salarymanagement.employee.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A malformed cursor, an impossible country code or a seniority level nobody has are all the
 * caller's mistake, and a 500 would say they were ours. Minimal on purpose: the full RFC 7807
 * mapping, with types and correlation ids, arrives with the rest of the API at 2.8.
 */
@RestControllerAdvice
class BadRequestHandler {

    @ExceptionHandler({IllegalArgumentException.class})
    ProblemDetail theRequestAskedForSomethingImpossible(IllegalArgumentException refused) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("The request could not be understood");
        // Domain messages name the employee and the currencies but never an amount (D080), so
        // this cannot leak pay. Nothing here echoes a stack trace or any SQL.
        problem.setDetail(refused.getMessage());
        return problem;
    }
}
