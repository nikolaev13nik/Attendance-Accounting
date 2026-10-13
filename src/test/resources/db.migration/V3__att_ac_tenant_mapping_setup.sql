-- create table att_ac_tenant_mapping
CREATE TABLE att_ac_tenant_mapping (
                       tenant_id INTEGER NOT NULL,
                       tenant_name VARCHAR(255),
                       status VARCHAR(15),
                       email VARCHAR(255),
                       PRIMARY KEY (tenant_id)
);

-- Seed the tenants the seeded users belong to (100, 200) plus the tenant the
-- registration tests create new users in (300)
INSERT INTO att_ac_tenant_mapping (tenant_id, tenant_name, status, email) VALUES
 ( 100, 'Acme Corp', 'ACTIVE', 'admin@acme.local' ),
 ( 200, 'Globex', 'ACTIVE', 'admin@globex.local' ),
 ( 300, 'Initech', 'ACTIVE', 'admin@initech.local' );
