-- V8: Keep the tenant's name on the move-in inspection.
--
-- An inspection is evidence the landlord may need months later, so it must
-- still say who the tenant was after that tenant's account has been erased
-- (erasure renames the account to "Deleted user"). The name is therefore
-- copied onto the inspection itself, the same way the landlord's name
-- already is.
--
-- move_in_inspections is created by Hibernate (ddl-auto=update), not by an
-- earlier migration. On a fresh database it does not exist yet when Flyway
-- runs, and the entity creates it afterwards with this column, so the work
-- below only applies where the table is already there.

DO $$
BEGIN
    IF to_regclass('move_in_inspections') IS NOT NULL THEN
        ALTER TABLE move_in_inspections ADD COLUMN IF NOT EXISTS tenant_name VARCHAR(255);

        UPDATE move_in_inspections i
           SET tenant_name = u.name
          FROM users u
         WHERE i.tenant_id = u.id
           AND i.tenant_name IS NULL;
    END IF;
END $$;
