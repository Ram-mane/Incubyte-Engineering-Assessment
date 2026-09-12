package com.acme.salarymanagement.employee.adapter.out.persistence.jpa;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Who made a change, for display. Read-only: nothing in this application edits a user. */
@Entity
@Table(name = "app_user")
class AppUserEntity {

    @Id
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    protected AppUserEntity() {
        // for JPA
    }

    UUID id() {
        return id;
    }

    String email() {
        return email;
    }
}
