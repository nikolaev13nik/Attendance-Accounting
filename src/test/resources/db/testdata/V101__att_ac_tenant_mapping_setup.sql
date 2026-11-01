INSERT INTO att_ac_tenant_mapping (tenant_id, company_name, contact_name, phone, status, email)
VALUES (100, 'Acme Corp', 'Wile E Coyote', '+1-555-0100', 'ACTIVE', 'admin@acme.local'),
       (200, 'Globex', 'Hank Scorpio', '+1-555-0200', 'ACTIVE', 'admin@globex.local'),
       (300, 'Initech', 'Bill Lumbergh', '+1-555-0300', 'ACTIVE', 'admin@initech.local');

ALTER TABLE att_ac_tenant_mapping
    ALTER COLUMN tenant_id RESTART WITH 301;
