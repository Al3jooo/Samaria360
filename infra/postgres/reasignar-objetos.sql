-- Samaria360 - lo incluye asegurar-bases.sql (\ir) una vez por base, ya conectado a ella.
-- Pasa al usuario del servicio (samaria360.duenio, fijado con set_config) los objetos del esquema public que tengan otro
-- dueño: tablas, vistas, secuencias sueltas y tipos propios. Idempotente: lo que ya es suyo no se toca (DEC-275).
DO $$
DECLARE
    duenio text := current_setting('samaria360.duenio');
    objeto record;
BEGIN
    FOR objeto IN
        SELECT c.relname, c.relkind
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
          AND pg_get_userbyid(c.relowner) <> duenio
          -- Las secuencias de una columna identity o serial cambian de dueño con su tabla.
          AND NOT (c.relkind = 'S' AND EXISTS (
              SELECT FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i')))
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO %I',
                       CASE objeto.relkind
                           WHEN 'S' THEN 'SEQUENCE'
                           WHEN 'v' THEN 'VIEW'
                           WHEN 'm' THEN 'MATERIALIZED VIEW'
                           ELSE 'TABLE' END,
                       objeto.relname, duenio);
    END LOOP;

    FOR objeto IN
        SELECT t.typname
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'public'
          AND t.typtype IN ('e', 'd', 'c')
          AND NOT EXISTS (SELECT FROM pg_class c WHERE c.reltype = t.oid)
          AND pg_get_userbyid(t.typowner) <> duenio
    LOOP
        EXECUTE format('ALTER TYPE public.%I OWNER TO %I', objeto.typname, duenio);
    END LOOP;
END
$$;
