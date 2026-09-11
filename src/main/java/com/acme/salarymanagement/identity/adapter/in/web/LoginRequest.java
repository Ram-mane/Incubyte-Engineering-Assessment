package com.acme.salarymanagement.identity.adapter.in.web;

/** What a person types. Never logged, never echoed back. */
record LoginRequest(String email, String password) {}
