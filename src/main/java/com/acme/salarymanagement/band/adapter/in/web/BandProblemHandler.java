package com.acme.salarymanagement.band.adapter.in.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.acme.salarymanagement.shared.CurrencyMismatchException;

/**
 * What this module returns when it will not answer.
 *
 * <p>Its own advice rather than a case in the employee module's handler: relying on another
 * module's web adapter to map this module's exceptions is the coupling rule 5 exists to prevent,
 * and ArchUnit cannot see it because there is no code reference to catch.
 *
 * <p>422 rather than 500: asking for a band in a currency the band is not denominated in is a
 * question with no answer, not a fault. It was a 500 until a review pointed out that the contract
 * already promised 422.
 */
@RestControllerAdvice(assignableTypes = BandController.class)
class BandProblemHandler {

    @ExceptionHandler(CurrencyMismatchException.class)
    ProblemDetail theBandIsInADifferentCurrency(CurrencyMismatchException mismatch) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setType(URI.create("https://salary-management.dev/errors/currency-mismatch"));
        problem.setTitle("That salary cannot be placed in this band");
        // Names currencies, never an amount (D080).
        problem.setDetail(mismatch.getMessage());
        return problem;
    }
}
