# ADR-0004: Cambiar el esquema solo mediante migraciones versionadas

- **Estado:** Aceptada
- **Fecha:** 2026-10-13
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** DD-003, DD-007, RNF-004, RNF-005

## Contexto
El esquema depende de objetos propios de Supabase (`auth.users`, `auth.uid()`, roles `anon` y
`authenticated`), de RLS y de triggers. Si cada integrante cambia tablas desde el panel web, el
esquema real y el documentado divergen.

## Decisión
- `supabase/migrations/` es la **única fuente** del esquema. Formato de nombre de la CLI de
  Supabase: `AAAAMMDDHHMMSS_descripcion.sql`.
- La migración inicial es `20261020090000_esquema_inicial.sql` (Rev. 0.4.1). Por DEC-42 sustituyó
  a la de la Rev. 0.3 (`20261007000000_esquema_inicial.sql`, movida a
  `docs/base-de-datos/historico/`), que nunca se aplicó a un entorno compartido.
- **Una migración aplicada no se edita.** Cualquier cambio es una migración nueva.
- Cada migración nueva actualiza el [diccionario de datos](../../base-de-datos/diccionario-de-datos.md)
  en el mismo PR.
- El backend **no** ejecuta DDL (sin Flyway ni `ddl-auto`).

## Alternativas consideradas
- **Flyway desde Spring Boot:** válido, pero el SQL depende del entorno Supabase y ejecutarlo al
  arrancar la API mezcla responsabilidades.
- **Cambios manuales en el panel:** descartado por falta de trazabilidad.

## Consecuencias
- Aplicar: SQL Editor de Supabase (orden por nombre) o `supabase db push` con la CLI.
- **Actualización 2026-10-22:** el proyecto compartido está vinculado a GitHub y la integración de
  Supabase aplica las migraciones pendientes al fusionar en `main`. En ese proyecto no se aplican
  migraciones a mano; detalle en [`supabase/README.md`](../../../supabase/README.md).
- **Actualización 2026-10-26 (DEC-51):** el código se integra en `develop` y llega a `main` solo al
  cerrar cada sprint; por eso una migración fusionada en `develop` no se aplica al proyecto
  compartido hasta ese cierre. Se resolvió en DEC-52 (abajo).
- **Actualización 2026-10-27 (DEC-52):** la integración de GitHub usa `develop` como rama de
  producción, así que la migración se aplica al fusionar su PR en `develop`. El proyecto compartido
  es el entorno de integración del sprint; el PR `develop → main` no vuelve a aplicar nada.
- Con la CLI y Docker, `supabase start` levanta un entorno local completo (Auth incluido).
