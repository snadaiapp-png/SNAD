package com.sanad.platform.hr.identity;

import java.util.UUID;

/**
 * HR Person — core identity record.
 *
 * <p>A Person represents a human tracked by the HR module. A Person may
 * optionally link to one tenant-scoped User (login identity). Person and
 * User remain separate identities — linking is optional and 1:1 max
 * within a tenant (enforced at DB boundary by partial UNIQUE index on
 * {@code hr_people(tenant_id, user_id) WHERE user_id IS NOT NULL}).</p>
 *
 * <p>This is a Cycle 2 minimal skeleton — methods are intentionally
 * unimplemented and throw {@link UnsupportedOperationException}. The
 * real behavior is added in Cycle 4 GREEN after Cycle 3 establishes
 * the real behavioral RED against this contract.</p>
 */
public final class HrPerson {

    private final UUID id;
    private final UUID tenantId;
    private final UUID userId;          // nullable — Person may have no User
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final String displayName;
    private final long version;

    public HrPerson(UUID id, UUID tenantId, UUID userId,
                    String firstName, String middleName, String lastName,
                    String displayName, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.version = version;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID userId() { return userId; }
    public String firstName() { return firstName; }
    public String middleName() { return middleName; }
    public String lastName() { return lastName; }
    public String displayName() { return displayName; }
    public long version() { return version; }
}
