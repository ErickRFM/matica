-- Comprobación segura después de ejecutar backend/database/schema.sql
SELECT current_database() AS database_name,
       current_schema() AS current_schema_name,
       EXISTS(SELECT 1 FROM matica.schema_version WHERE version=1) AS matica_schema_v1,
       (SELECT count(*) FROM information_schema.tables
        WHERE table_schema='matica' AND table_type='BASE TABLE') AS matica_tables;
SELECT tablename, rowsecurity
  FROM pg_tables
 WHERE schemaname='matica'
 ORDER BY tablename;
-- Deben existir 11 tablas y todas deben indicar rowsecurity=true.
-- Las tablas son privadas; el backend Ktor usa DATABASE_URL exclusivamente en el servidor.
