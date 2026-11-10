# Base de datos (Supabase / PostgreSQL)

Esquema versionado del sistema. Documentación de cada tabla:
[`docs/base-de-datos/diccionario-de-datos.md`](../docs/base-de-datos/diccionario-de-datos.md).
Reglas del flujo: [ADR-0004](../docs/arquitectura/adr/0004-migraciones-supabase.md). Esquema propio y
roles: [ADR-0014](../docs/arquitectura/adr/0014-esquema-propio-y-roles-de-base-de-datos.md).

## Contenido

| Archivo | Qué contiene |
| --- | --- |
| `migrations/20261020090000_esquema_inicial.sql` | Migración inicial de la Rev. 0.4.1 (DEC-42): esquema `siglim`, roles `siglim_app` y `siglim_publico`, enums, 72 tablas, índices, triggers de reglas e historial, `calcular_tabla`, vistas `v_publica_*`, RLS por ámbito, comentarios de columnas y semilla (liga, perfil Sistema, 13 roles, permisos del Apéndice E, catálogos `[DEMO]`). Se ejecuta en una sola transacción. |
| `migrations/20261022143000_fijar_search_path_error_negocio.sql` | Fija el `search_path` de `error_negocio` (aviso 0011 del asesor de seguridad de Supabase). Sin cambio de comportamiento. |
| `migrations/20261027140000_proteger_inmutables_truncate.sql` | Impide vaciar `auditoria`, `resoluciones` y `parametros_temporada` con `TRUNCATE`, directo o por cascada (SCRUM-397, RNF-015). |
| `pruebas/` | Pruebas SQL de los flujos (P-01 a P-11) y de los desempates, con un simulador mínimo de Supabase para PostgreSQL local. Ver [`pruebas/README.md`](pruebas/README.md). |

La migración de la Rev. 0.3 quedó en
[`docs/base-de-datos/historico/`](../docs/base-de-datos/historico/) y no se aplica.

## Proyecto compartido del equipo: las migraciones se aplican al fusionar

El proyecto **SIGLIM** de Supabase está conectado al repositorio con la integración de GitHub de
Supabase, con **`develop` como rama de producción**
([DEC-52](../docs/requisitos/decisiones/registro-de-decisiones.md)). Al fusionar un PR en `develop`,
la verificación **«Supabase Preview»** compara `supabase/migrations/` con el historial del proyecto
(`supabase_migrations.schema_migrations`) y aplica solo las migraciones que no estén registradas.
El PR de cierre de sprint (`develop → main`) ya no aplica nada: esas migraciones ya están.

> 🗄️ **Lo que se fusiona en `develop` llega de inmediato a la base que usa todo el equipo.** Antes
> de fusionar un PR con migración, deben pasar `pruebas/ejecutar.sh` y `./mvnw verify` (backend).
> Después del merge, confirma que se aplicó: *Database → Migrations* en el panel de Supabase, o
> `supabase migration list` con la CLI vinculada. Si la verificación «Supabase Preview» falla, no
> se aplicó nada (la migración va en una transacción): corrige con **otra** migración en un PR nuevo.

**Configuración de la integración** (ya hecha; anotada para rehacerla si se reconecta el proyecto):
*Project Settings → Integrations → GitHub Integration*: repositorio `israelweil/SIGLIM`, *Working
directory* `.`, **Production branch `develop`** y **Deploy to production** activado.

- **No apliques migraciones a mano** (SQL Editor) en el proyecto compartido: el SQL Editor no
  registra la migración en el historial y la integración intentará aplicarla otra vez al siguiente
  merge en `develop`.
- Si ya se aplicó una a mano, regístrala como aplicada (equivale a
  `supabase migration repair --status applied <versión>`):
  ```sql
  INSERT INTO supabase_migrations.schema_migrations (version, name)
  VALUES ('AAAAMMDDHHMMSS', 'descripcion') ON CONFLICT (version) DO NOTHING;
  ```
- Antecedente: la migración inicial se aplicó con el SQL Editor y el historial quedó vacío; al
  fusionar el PR #10, la integración intentó repetirla y falló con `schema "siglim" already exists`
  (sin aplicar nada, porque va en una transacción). Se corrigió registrando `20261020090000` y
  `20261022143000` el 22 de octubre de 2026.

## Aplicar el esquema en un proyecto nuevo (personal o de pruebas)

**Opción A — SQL Editor (sin instalar nada):** no registra el historial; úsala solo en proyectos
que no estén vinculados a GitHub.
1. Abre tu proyecto en Supabase → *SQL Editor* → *New query*.
2. Pega el contenido completo de `migrations/20261020090000_esquema_inicial.sql` y pulsa *Run*.
   Si algo falla, nada queda aplicado (todo va en una transacción).
3. En *Authentication → Users* invita o crea al usuario administrador con su correo.
4. En el *SQL Editor* ejecuta:
   ```sql
   SELECT siglim.registrar_administrador_inicial('correo@ejemplo.com');
   ```
   Crea su perfil y le asigna el rol `ADMINISTRADOR_GENERAL` con ámbito de liga.

El esquema `siglim` **no** se agrega a *Settings → API → Exposed schemas*: los datos solo se leen a
través del backend (AGENTS.md §9).

**Opción B — Supabase CLI:**
```bash
supabase login
supabase link --project-ref <ref-del-proyecto>
supabase db push          # aplica las migraciones pendientes al proyecto remoto
```

## Reglas

- **Nunca edites una migración ya aplicada.** Crea una nueva:
  `supabase migration new descripcion_corta` o a mano con el formato `AAAAMMDDHHMMSS_descripcion.sql`.
- No cambies tablas desde el panel web de Supabase sin escribir la migración equivalente.
- Toda migración nueva actualiza el diccionario de datos en el mismo PR y conserva verdes las
  pruebas de `pruebas/`.
- Los datos de prueba (clubes, equipos ficticios) van en `seed.sql`, nunca en una migración.
