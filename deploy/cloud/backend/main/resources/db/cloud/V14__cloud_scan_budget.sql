-- Cloud-only persistent consumption budget. Original migrations remain unchanged.
CREATE TABLE cloud_scan_budget (
    usage_date DATE PRIMARY KEY,
    attempts INTEGER NOT NULL CHECK (attempts >= 0)
);
