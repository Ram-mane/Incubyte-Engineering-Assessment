package com.acme.salarymanagement.analytics.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.acme.salarymanagement.analytics.domain.UnconvertibleSalaries;

/**
 * What the dashboard returns when it will not answer.
 *
 * <p>Its own advice rather than a case in the employee module's {@code ProblemHandler}: this
 * exception belongs to {@code analytics}, and a module reaching into another module's web adapter
 * to be handled is the coupling rule 5 exists to prevent.
 *
 * <p>422 rather than 400: the request is well formed and the caller is allowed to ask it. The
 * answer is unavailable because the rate table does not cover the data, which is a state of the
 * server, not a mistake by the caller.
 */
@RestControllerAdvice
class AnalyticsProblemHandler {

    @ExceptionHandler(UnconvertibleSalaries.class)
    ProblemDetail theseSalariesCannotBeConverted(UnconvertibleSalaries unconvertible) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("These salaries cannot be reported in that currency");
        // Names currencies and a date, never an amount (D080).
        problem.setDetail(unconvertible.getMessage());
        return problem;
    }
}
