package com.acme.salarymanagement.employee.application.port.in;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Where the last page stopped: the full sort key of its final row.
 *
 * <p>Keyset paging needs the whole ordering key, not a row number - that is what lets the next
 * page be "everyone after this person" rather than "skip 9,950 rows and read them anyway". The key
 * is {@code (family_name, given_name, id)}, because that is the directory's total order (D107).
 *
 * <p>Encoded opaquely on the way out. Not for secrecy - it is a name and a uuid - but so that no
 * client builds on its internals: the day the sort key gains a column, a client that parsed the
 * old shape breaks, and a client that treated it as a token does not.
 */
public record DirectoryCursor(String familyName, String givenName, UUID id) {

    /** A newline separates the parts: a name cannot contain one, and neither can a uuid. */
    private static final String SEPARATOR = "\n";

    private static final int PARTS = 3;

    public DirectoryCursor {
        Objects.requireNonNull(familyName, "a cursor needs the family name it stopped at");
        Objects.requireNonNull(givenName, "a cursor needs the given name it stopped at");
        Objects.requireNonNull(id, "a cursor needs the id it stopped at");
    }

    public String encoded() {
        String key = String.join(SEPARATOR, familyName, givenName, id.toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    /** Rejects anything it did not issue, rather than paging from a place nobody asked for. */
    public static DirectoryCursor decode(String encoded) {
        try {
            String key = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = key.split(SEPARATOR, -1);
            if (parts.length != PARTS) {
                throw new IllegalArgumentException("that is not a cursor this directory issued");
            }
            return new DirectoryCursor(parts[0], parts[1], UUID.fromString(parts[2]));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("that is not a cursor this directory issued", malformed);
        }
    }
}
