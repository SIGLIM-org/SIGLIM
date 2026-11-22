#!/usr/bin/env bash
# Comprueba, sin modificar nada, que el proyecto compartido de Supabase tenga registradas todas las
# migraciones de supabase/migrations/ (DEC-52). Sirve para detectar que la integración de GitHub dejó
# de aplicar merges (por ejemplo, después de transferir o renombrar el repositorio).
#
# Uso, desde supabase/ (los datos de conexión se pasan por las variables estándar de psql; no se
# guardan en el repositorio):
#   PGHOST=... PGPORT=6543 PGUSER=postgres.<ref> PGPASSWORD='...' PGSSLMODE=require \
#     ./verificar_migraciones.sh
#
# Sale con 0 si están todas y con 1 si falta alguna. Solo lee supabase_migrations.schema_migrations.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v psql >/dev/null 2>&1; then
  echo "Falta psql. Instala el cliente de PostgreSQL o usa 'supabase migration list'." >&2
  exit 2
fi

# El proyecto de Supabase usa la base «postgres»; permite sobrescribirla con PGDATABASE.
export PGDATABASE="${PGDATABASE:-postgres}"

locales="$(for archivo in migrations/*.sql; do
  basename "$archivo" | cut -d_ -f1
done | sort)"

aplicadas="$(psql -X -At -v ON_ERROR_STOP=1 -c \
  'SELECT version FROM supabase_migrations.schema_migrations ORDER BY version')"

faltantes="$(comm -23 <(printf '%s\n' "$locales") <(printf '%s\n' "$aplicadas" | sort))"
sobrantes="$(comm -13 <(printf '%s\n' "$locales") <(printf '%s\n' "$aplicadas" | sort))"

echo "Migraciones en el repositorio: $(printf '%s\n' "$locales" | wc -l | tr -d ' ')"
echo "Migraciones registradas en el proyecto compartido: $(printf '%s\n' "$aplicadas" | wc -l | tr -d ' ')"

if [[ -n "$sobrantes" ]]; then
  echo "Aviso: el proyecto tiene versiones que no están en supabase/migrations/:" >&2
  printf '%s\n' "$sobrantes" | sed 's/^/  /' >&2
fi

if [[ -n "$faltantes" ]]; then
  echo "FALTAN en supabase_migrations.schema_migrations:" >&2
  printf '%s\n' "$faltantes" | sed 's/^/  /' >&2
  echo "Reconecta la integración de GitHub (CONTRIBUTING.md §1); no apliques SQL a mano." >&2
  exit 1
fi

echo "OK: todas las migraciones de supabase/migrations/ están aplicadas."
