package com.acme.salarymanagement.identity.domain;

/**
 * Who may do what. Two roles, because the customer named two.
 *
 * <p>The analyst sees everything the manager sees and changes nothing: pay data is worth reading
 * widely and worth writing narrowly.
 */
public enum Role {
    HR_MANAGER,
    HR_ANALYST
}
