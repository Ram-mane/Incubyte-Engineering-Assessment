package com.acme.salarymanagement.employee.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.salarymanagement.employee.application.port.in.ChangeSalary;
import com.acme.salarymanagement.employee.application.port.in.GetSalaryRevisions;
import com.acme.salarymanagement.employee.domain.EmployeeId;
import com.acme.salarymanagement.employee.domain.UserId;

/**
 * Pay, and the record of it changing.
 *
 * <p>A sub-resource rather than a field on the employee, and PUT rather than PATCH, because a
 * salary change carries a mandatory reason and must produce an audit entry. Allowing
 * {@code PATCH /employees/{id} {"salary": ...}} alongside the profile fields would make it
 * possible to move pay silently; the shape of the API enforces what the domain enforces.
 */
@RestController
@RequestMapping("/api/v1/employees/{id}")
class SalaryController {

    private static final int DEFAULT_LOG_LENGTH = 50;

    private final ChangeSalary changeSalary;
    private final GetSalaryRevisions revisions;

    SalaryController(ChangeSalary changeSalary, GetSalaryRevisions revisions) {
        this.changeSalary = changeSalary;
        this.revisions = revisions;
    }

    @PutMapping("/salary")
    EmployeeResponse change(
            @PathVariable UUID id, @RequestBody ChangeSalaryRequest request, @AuthenticationPrincipal Jwt caller) {
        // The actor is the token's subject, never a field in the body. A caller who could name
        // somebody else as the author of a pay change could launder one through the audit log.
        UserId actor = new UserId(UUID.fromString(caller.getSubject()));
        return EmployeeResponse.of(changeSalary.change(request.toCommand(id, actor)));
    }

    @GetMapping("/salary-revisions")
    List<SalaryRevisionResponse> log(
            @PathVariable UUID id, @RequestParam(defaultValue = "" + DEFAULT_LOG_LENGTH) int limit) {
        return revisions.of(new EmployeeId(id), limit).stream()
                .map(SalaryRevisionResponse::of)
                .toList();
    }
}
