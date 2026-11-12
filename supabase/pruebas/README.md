# Pruebas del esquema

Verifican la migración inicial sin un proyecto de Supabase: `mock_supabase.sql` crea lo mínimo que la
migración espera de Supabase (esquemas `auth` y `extensions`, tabla `auth.users` y roles `anon`,
`authenticated` y `service_role`).

| Archivo | Qué prueba |
| --- | --- |
| `prueba_flujo.sql` | Recorre P-01, P-02, P-03, P-05, P-06 y P-11 con el rol `siglim_app` y el contexto de cada usuario. Verifica 50 reglas: VAL-003 a VAL-005, VAL-017, VAL-019 a VAL-022, VAL-025, VAL-028, RN-021, RN-024, RN-054, RF-052, RF-127, inmutabilidad (también contra `TRUNCATE` directo y por cascada), privilegios y RLS por ámbito. Cada comprobación imprime `OK`. |
| `prueba_tabla.sql` | Desempates de `calcular_tabla()`: triple empate (posición compartida), sorteo y enfrentamiento directo. |
| `prueba_funciones.sql` | Regresión de seguridad: toda función del esquema `siglim` fija su `search_path` (aviso 0011 del asesor de Supabase). Con los datos de `prueba_flujo.sql`, verifica también las funciones de lectura mínima: `persona_por_curp`, `es_menor` y `es_tutor_vigente` (H-09.1, DEC-78) y `registros_en_temporada` y `requisitos_menor` (H-09.2, DEC-90): son `SECURITY DEFINER`, solo las ejecuta `siglim_app` y ven datos que RLS no muestra a otro club; además, que `ux_registros_activo` documenta `VAL-017`. |
| `prueba_documentos.sql` | Documentos de un registro (H-10.1, DEC-94), con los datos de `prueba_flujo.sql` y en una transacción que se deshace: un segundo directivo del mismo club reemplaza un documento que cargó otro, un directivo de otro club no puede (0 filas por RLS), el reemplazo no cambia una vez puesto (`RNF-018`), un documento aprobado no vuelve a validarse (`RF-109`), un usuario de club no valida (`VAL-015`) y el archivo sigue siendo inmutable (`RNF-018`). |
| `ejecutar.sh` | Crea la base `siglim_pruebas`, aplica la simulación, **todas** las migraciones en orden y las pruebas. |

```bash
PGHOST=localhost PGPORT=5432 PGUSER=postgres ./ejecutar.sh
```

La migración también se probó aplicándola con un usuario **no superusuario** con `CREATEROLE` y
`BYPASSRLS`, como el usuario `postgres` de Supabase. Estas pruebas no sustituyen la verificación en
un proyecto real: después de aplicar la migración en Supabase, ejecuta el paso de alta del primer
administrador descrito en el encabezado del script.
