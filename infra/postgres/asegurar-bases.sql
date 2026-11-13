-- Samaria360 - servicio de un solo uso postgres-bases (docker-compose.yml).
-- Crea las bases de cada microservicio si no existen. A diferencia de init/01-crear-bases.sql, que solo corre con el
-- volumen vacío, se ejecuta en cada arranque: así un volumen creado antes de que existiera una base también la
-- recibe, sin borrar datos (nunca docker compose down -v). Es idempotente.
-- Las tablas NO se crean aquí: cada servicio es dueño de su esquema mediante Flyway.
--
-- Mínimo privilegio (DEC-275): cada servicio se conecta con su propio usuario, sin superusuario, dueño solo de su
-- base. ms-auth usa :auth_usuario en auth_db y ms-booking, :booking_usuario en reservas_db; ninguno puede conectarse a
-- la base del otro (se revoca CONNECT a PUBLIC). El superusuario de POSTGRES_USER queda solo para este script.
-- Variables (las pasa docker-compose.yml con psql -v): auth_usuario, auth_clave, booking_usuario, booking_clave.

SELECT 'CREATE DATABASE auth_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec

SELECT 'CREATE DATABASE reservas_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'reservas_db')\gexec

-- Usuarios de los servicios: se crean si faltan y siempre se les vuelve a fijar la contraseña y los atributos, así un
-- cambio de contraseña en .env llega con el siguiente arranque.
SELECT format('CREATE ROLE %I LOGIN', :'auth_usuario')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'auth_usuario')\gexec
ALTER ROLE :"auth_usuario" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'auth_clave';

SELECT format('CREATE ROLE %I LOGIN', :'booking_usuario')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'booking_usuario')\gexec
ALTER ROLE :"booking_usuario" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'booking_clave';

-- Cada base: su usuario es el dueño y el único que se conecta (además del superusuario).
ALTER DATABASE auth_db OWNER TO :"auth_usuario";
REVOKE ALL ON DATABASE auth_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE auth_db TO :"auth_usuario";

ALTER DATABASE reservas_db OWNER TO :"booking_usuario";
REVOKE ALL ON DATABASE reservas_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE reservas_db TO :"booking_usuario";

-- Dentro de cada base, el esquema public y lo que ya creó Flyway con el superusuario (volúmenes anteriores a DEC-275)
-- pasan al usuario del servicio: tablas, vistas, secuencias sueltas y tipos (p. ej. el enum estado_cuenta). Las
-- secuencias de una columna identity cambian de dueño con su tabla.
\connect auth_db
SELECT set_config('samaria360.duenio', :'auth_usuario', false);
ALTER SCHEMA public OWNER TO :"auth_usuario";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
\ir reasignar-objetos.sql

\connect reservas_db
SELECT set_config('samaria360.duenio', :'booking_usuario', false);
ALTER SCHEMA public OWNER TO :"booking_usuario";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
\ir reasignar-objetos.sql

\echo 'Bases de datos al día: auth_db y reservas_db, cada una con su usuario propio'
