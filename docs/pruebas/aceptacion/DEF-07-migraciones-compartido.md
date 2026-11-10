# DEF-07 · Migraciones de DEF-06 y H-22 sin aplicar en el Supabase compartido

| Campo | Valor |
| --- | --- |
| Defecto | DEF-07 (SCRUM-406) · Sprint 3 · bloquea las pruebas contra el compartido (B-13.3, 12 nov) |
| Origen | Análisis del cierre del Sprint 2, 10 nov 2026 |
| Versión revisada | `develop` en `52bdd71` |
| Fecha | 2026-11-10 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado | **Pendiente**: el repositorio ya documenta la causa y el arreglo, pero la integración de Supabase sigue sin reconectar y la base compartida no tiene las dos migraciones |

## 1. Qué se comprobó

Consulta de solo lectura a `supabase_migrations.schema_migrations` del proyecto compartido, el
10 de noviembre de 2026:

| Versión | Nombre | Estado |
| --- | --- | --- |
| `20261020090000` | `esquema_inicial` | Aplicada |
| `20261022143000` | `fijar_search_path_error_negocio` | Aplicada |
| `20261027140000` | `proteger_inmutables_truncate` | Aplicada |
| `20261103100000` | `secretario_registra_equipos` | Aplicada |
| `20261106160000` | `tesorero_consulta_equipos` (DEF-06) | **Falta** |
| `20261108100000` | `cargo_directiva_pendiente` (H-22) | **Falta** |

También se comprobaron los dos efectos que dependen de esas migraciones:

- El rol `TESORERO` **no** tiene el permiso `organizacion.equipo:CONSULTAR` en
  `siglim.rol_permisos`, así que el modal «Nuevo cobro» de F-33 sigue sin ofrecer equipos.
- El índice `siglim.ux_cargos_vigentes` conserva la definición anterior,
  `WHERE (hasta IS NULL)`, en vez de `WHERE (hasta IS NULL AND autorizado_por IS NOT NULL)`;
  un cambio de directiva pendiente puede chocar con el cargo vigente.

La salida completa de las consultas está en
[`CA1-historial-compartido.txt`](evidencias/DEF-07/CA1-historial-compartido.txt).

## 2. Por qué la integración de GitHub no las aplicó (DEC-52)

La integración de Supabase estaba documentada contra el repositorio **`israelweil/SIGLIM`**
([`supabase/README.md`](../../../supabase/README.md)). El repositorio se transfirió a
**`SIGLIM-org`**: la organización se creó el **5 de noviembre de 2026 a las 04:46 UTC** y el
repositorio quedó en `SIGLIM-org/SIGLIM`. La instalación de la GitHub App de Supabase se quedó con
la ruta anterior y dejó de recibir los merges de `develop`.

La cronología coincide con el hueco del historial:

| Hecho | Fecha (UTC) |
| --- | --- |
| Última migración aplicada (`20261103100000`, PR #50) | 3 nov 2026, 21:18 |
| Transferencia a `SIGLIM-org` | 5 nov 2026, 04:46 |
| Merge de DEF-06 (`20261106160000`, PR #85) | 6 nov 2026, 21:19 |
| Merge de H-22 (`20261108100000`, PR #102) | 9 nov 2026, 05:00 |

La integración no volvió a ejecutarse después de la transferencia; por eso las dos migraciones
están en `develop` pero no en el historial del proyecto compartido. La comprobación definitiva de
la instalación se hace en *Project Settings → Integrations → GitHub Integration*; el plan de
reconexión está en [`CONTRIBUTING.md` §1](../../../CONTRIBUTING.md#si-una-migración-no-aparece-en-el-proyecto-compartido).

## 3. Corrección en el repositorio

Este cambio no aplica SQL ni modifica la base compartida. Agrega lo necesario para detectar y
corregir el problema sin romper DEC-52:

- [`supabase/verificar_migraciones.sh`](../../../supabase/verificar_migraciones.sh): compara, en
  solo lectura, las versiones de `supabase/migrations/` con `supabase_migrations.schema_migrations`
  y termina en error si falta alguna.
- [`CONTRIBUTING.md` §1](../../../CONTRIBUTING.md#si-una-migración-no-aparece-en-el-proyecto-compartido):
  explica la causa y el paso manual correcto: reconectar la integración, no aplicar migraciones a
  mano.
- [`supabase/README.md`](../../../supabase/README.md): actualiza el repositorio de la integración y
  advierte sobre futuras transferencias o renombrados.

## 4. Pasos pendientes en el proyecto compartido

Requieren acceso de administración al proyecto de Supabase:

1. Reconectar la integración de GitHub a `SIGLIM-org/SIGLIM`, con *working directory* `.`, rama de
   producción `develop` y *Deploy to production* activo.
2. Disparar la integración con el siguiente merge en `develop` (o su botón de reintento). Debe
   aplicar `20261106160000` y `20261108100000`, en ese orden.
3. Ejecutar `supabase/verificar_migraciones.sh`; debe terminar en `OK` con las seis versiones.
4. Repetir las pruebas de aceptación de **H-22** y **DEF-06** (H-29.3) contra el compartido y
   registrar el resultado en sus documentos.

## 5. Criterios de aceptación

| Criterio | Estado |
| --- | --- |
| 1. `supabase_migrations.schema_migrations` lista todas las migraciones de `develop` | **Pendiente**: 4 de 6. El script de verificación queda listo; se cierra al reconectar la integración |
| 2. Se explica por qué la integración no las aplicó y se documenta el paso manual | **Cumplido** en este cambio |
| 3. Se repiten las pruebas de aceptación de H-22 y DEF-06 contra el compartido | **Pendiente**: no se pueden repetir mientras falten las migraciones |

## 6. Reproducción

Desde `supabase/`, con las credenciales del proyecto compartido en las variables de `psql` (no se
guardan en el repositorio):

```bash
PGHOST=... PGPORT=6543 PGUSER=postgres.<ref> PGPASSWORD='...' PGSSLMODE=require \
  ./verificar_migraciones.sh
```

La consulta que usa el script es:

```sql
SELECT version, name
  FROM supabase_migrations.schema_migrations
 ORDER BY version;
```

## 7. Conclusión

El defecto no es de las migraciones: ambas son correctas y ya están en `develop`. La causa es que la
integración de GitHub quedó apuntando al repositorio anterior después de la transferencia a
`SIGLIM-org`. Hasta reconectarla y volver a ejecutarla, el proyecto compartido no refleja `develop` y
las pruebas de aceptación del Sprint 3 contra el compartido no son fiables.

**Refs:** DEF-06, H-22, DEC-52, SCRUM-406.
