package com.digitalwill.auth.security;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public class UserPrincipal implements Principal {

    private final UUID id;
    private final String email;
    private final String fullName;

    public UserPrincipal(UUID id, String email, String fullName) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.fullName = Objects.requireNonNull(fullName, "fullName must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String getName() {
        return email;
    }
}
