# Pruebas del esquema

Verifican la migración inicial sin un proyecto de Supabase: `mock_supabase.sql` crea lo mínimo que la
migración espera de Supabase (esquemas `auth` y `extensions`, tabla `auth.users` y roles `anon`,
`authenticated` y `service_role`).

| Archivo | Qué prueba |
| --- | --- |
| `prueba_flujo.sql` | Recorre P-01, P-02, P-03, P-05, P-06 y P-11 con el rol `siglim_app` y el contexto de cada usuario. Verifica 50 reglas: VAL-003 a VAL-005, VAL-017, VAL-019 a VAL-022, VAL-025, VAL-028, RN-021, RN-024, RN-054, RF-052, RF-127, inmutabilidad (también contra `TRUNCATE` directo y por cascada), privilegios y RLS por ámbito. Cada comprobación imprime `OK`. |
| `prueba_tabla.sql` | Desempates de `calcular_tabla()`: triple empate (posición compartida), sorteo y enfrentamiento directo. |
| `prueba_funciones.sql` | Regresión de seguridad: toda función del esquema `siglim` fija su `search_path` (aviso 0011 del asesor de Supabase). |
| `ejecutar.sh` | Crea la base `siglim_pruebas`, aplica la simulación, **todas** las migraciones en orden y las pruebas. |

```bash
PGHOST=localhost PGPORT=5432 PGUSER=postgres ./ejecutar.sh
```

La migración también se probó aplicándola con un usuario **no superusuario** con `CREATEROLE` y
`BYPASSRLS`, como el usuario `postgres` de Supabase. Estas pruebas no sustituyen la verificación en
un proyecto real: después de aplicar la migración en Supabase, ejecuta el paso de alta del primer
administrador descrito en el encabezado del script.
