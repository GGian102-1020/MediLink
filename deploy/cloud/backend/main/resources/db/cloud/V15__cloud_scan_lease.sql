-- Infrastructure only; existing prescriptions, inventory and reservations are untouched.
CREATE TABLE cloud_scan_lease (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton),
    owner UUID,
    expires_at TIMESTAMPTZ,
    CHECK ((owner IS NULL) = (expires_at IS NULL))
);
INSERT INTO cloud_scan_lease(singleton) VALUES(TRUE);
