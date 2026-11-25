# Programación automática de fechas — diseño

**SIGLIM** · 24 de noviembre de 2026 · Aprobada por el PO (Olvera Lobato David Geovani)  
Decisiones: [DEC-173 a DEC-182](../requisitos/decisiones/registro-de-decisiones.md) · Algoritmo: [ADR-0015](../arquitectura/adr/0015-algoritmo-programacion-automatica.md)  
Historias: T-06, H-32, H-33, H-34 y H-35 (Sprint 4). Las fechas, los responsables y el estado de cada subtarea viven en **Jira** (etiqueta `programacion-automatica`), no en este archivo.

> [!IMPORTANT]
> Este documento reabre la programación automática que el ERS Rev. 0.4.1 deja fuera (§2.6, DEC-06).
> Hasta la revisión 0.5 del ERS, **DEC-173 a DEC-182 mandan sobre el ERS** en este tema (AGENTS.md §2).

---

## 1. Qué se construye

El Programador genera **una sola vez el calendario de la temporada**: quién juega contra quién en
cada fecha, con descansos y vueltas. Después, cada semana, con un botón asigna **cancha, horario y
árbitro central** a todos los partidos de la fecha, en todas las categorías y grupos a la vez.

El resultado queda en `BORRADOR` para que el Programador lo revise y lo ajuste con motivo. Después
envía la **fecha completa** y el Presidente la aprueba o la devuelve completa. Al publicarse,
aparece en el portal.

```text
Calendario (1 vez por temporada)             Cada fecha (cada semana)
┌──────────────────────────────┐            ┌─────────────────────────────────────────┐
│ Método del círculo por grupo │──fechas──▶ │ Asignar cancha y horario (H-33)         │
│ vueltas · descansos · localía│            │ Asignar árbitro central (H-34)          │
└──────────────────────────────┘            │ Revisar y ajustar en F-22 (H-35)        │
                                            │ Enviar fecha → Presidente aprueba (F-23)│
                                            │ PUBLICADO → portal                      │
                                            └─────────────────────────────────────────┘
```

**Fuera de este diseño:** disponibilidad arbitral (RF-022), asignación de asistentes, reprogramación
automática de partidos publicados (RF-038 sigue siendo manual, con RF-040 y RF-043) y restricciones
blandas de preferencias o desplazamientos.

---

## 2. Decisiones

El texto completo está en el [registro de decisiones](../requisitos/decisiones/registro-de-decisiones.md).

| DEC | Resumen |
| --- | --- |
| DEC-173 | Se reabre la programación automática (revierte en parte DEC-06). Vuelven al núcleo RF-034, RF-035, RF-037, RF-041, RF-042, RN-016, RN-017, VAL-007 y DD-005. RF-038 sigue manual. |
| DEC-174 | Calendario por método del círculo; `vueltas` en `parametros_temporada` (1 por defecto, versionado). |
| DEC-175 | La **fecha** (`fechas_programacion`) agrupa las jornadas de todos los grupos de una semana y se envía y aprueba completa. Los endpoints por jornada se conservan para fases finales y jornadas manuales. |
| DEC-176 | Horario de juego por defecto: sábado y domingo de 08:00 a 18:00 **[DEMO]**. Si una cancha tiene periodos `DISPONIBLE` ese día, se usan esos. Bloqueos y mantenimiento siempre excluyen. |
| DEC-177 | Días de juego por categoría en `categorias_temporada.dias_juego` (ISO 6 = sábado, 7 = domingo). Inicial por tipo: LIBRE → domingo; INFANTIL y VETERANOS → sábado; editable. Si ningún día de la fecha coincide, se usan todos con advertencia. |
| DEC-178 | Dentro de su día, la categoría con menor `orden` toma los horarios más tempranos. |
| DEC-179 | Preferencia de cancha: la del club local, luego la unidad deportiva, luego las demás; en las últimas `fechas_finales_unidad_deportiva` fechas, la unidad deportiva primero. Si no se cumple, `motivo_localia` se llena con «Asignación automática: …» y es editable. Sin `fechas_fase_regular`, el total de fechas sale del calendario. |
| DEC-180 | Lo que no cabe es un **emparejamiento sin partido** con código y causa; `partidos` no cambia (cancha e inicio siguen `NOT NULL`). |
| DEC-181 | «Asignar pendientes» no toca lo ya asignado. «Regenerar» pide motivo, **cancela** (no borra) los partidos de la fecha y sus designaciones y vuelve a asignar; solo con todas las jornadas en `BORRADOR`. |
| DEC-182 | El árbitro central automático requiere `arbitraje.designacion:AUTOMATICA` (asignado a PROGRAMADOR). El cambio manual sigue siendo de la Comisión en F-24. Los asistentes no se asignan. |

---

## 3. Modelo de datos (T-06)

Migración `supabase/migrations/20261125100000_programacion_automatica.sql`. Borrador; se ajusta al
implementar y se refleja en el diccionario de datos en el mismo PR.

```sql
-- M-07 · Programación automática (DEC-173 a DEC-182, ADR-0015; T-06)

-- 1. Parámetros de la temporada (versionados; ADD COLUMN con DEFAULT no dispara tg_parametros_inmutable)
ALTER TABLE siglim.parametros_temporada
  ADD COLUMN vueltas            smallint NOT NULL DEFAULT 1,
  ADD COLUMN hora_inicio_juego  time     NOT NULL DEFAULT '08:00',
  ADD COLUMN hora_fin_juego     time     NOT NULL DEFAULT '18:00',
  ADD CONSTRAINT ck_parametros_vueltas CHECK (vueltas BETWEEN 1 AND 4),
  ADD CONSTRAINT ck_parametros_horario_juego CHECK (hora_fin_juego > hora_inicio_juego);

-- 2. Días de juego por categoría de la temporada (ISO 8601: 1 = lunes … 7 = domingo)
ALTER TABLE siglim.categorias_temporada ADD COLUMN dias_juego smallint[];
UPDATE siglim.categorias_temporada ct
   SET dias_juego = CASE c.tipo WHEN 'LIBRE' THEN '{7}'::smallint[] ELSE '{6}'::smallint[] END
  FROM siglim.categorias c WHERE c.id = ct.categoria_id;
ALTER TABLE siglim.categorias_temporada
  ALTER COLUMN dias_juego SET NOT NULL,
  ADD CONSTRAINT ck_categorias_temporada_dias CHECK (
    cardinality(dias_juego) BETWEEN 1 AND 7 AND dias_juego <@ '{1,2,3,4,5,6,7}'::smallint[]);
-- Trigger BEFORE INSERT: si dias_juego viene NULL, lo llena según categorias.tipo (DEC-177).

-- 3. Fechas: agrupan las jornadas de todos los grupos de una semana (DEC-175)
CREATE TABLE siglim.fechas_programacion (
  id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  temporada_id           uuid NOT NULL REFERENCES siglim.temporadas (id),
  numero                 smallint NOT NULL,
  dias                   date[] NOT NULL,
  generada_por           uuid REFERENCES siglim.perfiles (id),
  generada_en            timestamptz,
  parametros_generacion  jsonb,          -- RF-041: versión de parámetros, modo, motivo, totales
  creado_por             uuid REFERENCES siglim.perfiles (id),
  creado_en              timestamptz NOT NULL DEFAULT now(),
  actualizado_en         timestamptz NOT NULL DEFAULT now(),
  UNIQUE (temporada_id, numero),
  CONSTRAINT ck_fechas_numero CHECK (numero > 0),
  CONSTRAINT ck_fechas_dias CHECK (cardinality(dias) BETWEEN 1 AND 7)
);
ALTER TABLE siglim.jornadas ADD COLUMN fecha_id uuid REFERENCES siglim.fechas_programacion (id);
CREATE UNIQUE INDEX ux_jornadas_fecha_grupo ON siglim.jornadas (fecha_id, grupo_id) WHERE fecha_id IS NOT NULL;

-- 4. Calendario: emparejamientos por grupo y fecha (DEC-174, DEC-180)
CREATE TABLE siglim.emparejamientos (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  grupo_id             uuid NOT NULL REFERENCES siglim.grupos (id),
  vuelta               smallint NOT NULL,
  numero_fecha         smallint NOT NULL,
  equipo_local_id      uuid NOT NULL REFERENCES siglim.equipos (id),  -- en un descanso: el que descansa
  equipo_visitante_id  uuid REFERENCES siglim.equipos (id),           -- NULL = descanso (RF-037)
  partido_id           uuid UNIQUE REFERENCES siglim.partidos (id),
  codigo_no_asignado   varchar(20),                                   -- VAL-007, VAL-006 …
  causa_no_asignado    text,
  creado_por           uuid REFERENCES siglim.perfiles (id),
  creado_en            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_emparejamientos_numeros CHECK (vuelta > 0 AND numero_fecha > 0),
  CONSTRAINT ck_emparejamientos_equipos CHECK (equipo_visitante_id IS NULL OR equipo_visitante_id <> equipo_local_id),
  CONSTRAINT ck_emparejamientos_descanso CHECK (equipo_visitante_id IS NOT NULL OR partido_id IS NULL)
);
CREATE UNIQUE INDEX ux_emparejamientos_local ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_local_id);
CREATE UNIQUE INDEX ux_emparejamientos_visitante ON siglim.emparejamientos (grupo_id, numero_fecha, equipo_visitante_id)
  WHERE equipo_visitante_id IS NOT NULL;

-- 5. Trazabilidad de lo automático
ALTER TABLE siglim.partidos      ADD COLUMN asignado_automaticamente boolean NOT NULL DEFAULT false;
ALTER TABLE siglim.designaciones ADD COLUMN automatica               boolean NOT NULL DEFAULT false;

-- 6. Permiso nuevo (DEC-182), con el patrón del seed de la migración inicial
--    'arbitraje.designacion:AUTOMATICA', 'M-05', 'Designar automáticamente al árbitro central (H-34)' → PROGRAMADOR

-- 7. Para cada tabla nueva: ENABLE RLS y políticas como jornadas, GRANT SELECT, INSERT, UPDATE a siglim_app
--    (sin DELETE), índices de FK y COMMENT ON TABLE/COLUMN (diccionario de datos).
```

Pruebas SQL en `supabase/pruebas/prueba_programacion_automatica.sql`: restricciones CHECK, índices
únicos, trigger de `dias_juego` y que `siglim_app` no puede borrar en las tablas nuevas.

---

## 4. Algoritmos

Justificación y alternativas descartadas en [ADR-0015](../arquitectura/adr/0015-algoritmo-programacion-automatica.md).

### 4.1 Calendario: método del círculo (H-32)

1. Por grupo, ordenar los equipos `ACTIVO` por `id`. Si N es impar, agregar un DESCANSO.
2. Fijar `e[0]`; en cada ronda `r = 0 … N−2` rotar los demás una posición y emparejar `e[i]` con `e[N−1−i]`.
3. Localía: el equipo fijo es local en rondas pares; los demás pares invierten el orden en las impares.
4. Vuelta `v > 1`: mismas rondas con la localía invertida; número de fecha `(v−1)·(N−1) + r + 1`.
5. La temporada tiene `F = máx(fechas de cada grupo)`; la fecha `k` lleva sábado y domingo de la
   semana `k` desde la fecha de inicio que da el Programador.

**Propiedades que se prueban** (jqwik si se agrega la dependencia; si no, JUnit parametrizado con
N = 2…20 y v = 1…2): cada par se enfrenta exactamente v veces; ningún equipo aparece dos veces en
una fecha (RN-012); con N impar cada equipo descansa v veces (RF-037); diferencia de localía ≤ 1
por vuelta; resultado determinista.

### 4.2 Cancha y horario: heurística voraz «más restringido primero» (H-33)

1. **Partidos a asignar:** emparejamientos de la fecha sin partido y que no son descanso. Un equipo
   con baja o sanción vigente deja su partido sin asignar con VAL-006.
2. **Ventanas:** por cada día de la fecha que esté en `dias_juego` de la categoría y por cada
   cancha activa, `[hora_inicio_juego, hora_fin_juego)` (o los periodos `DISPONIBLE` de ese día)
   menos bloqueos y mantenimiento.
3. **Orden:** categorías con menos días permitidos, luego `orden` ascendente, grupo y emparejamiento.
4. **Colocación:** día por día, en el orden de canchas de DEC-179, el inicio más temprano donde
   cabe el bloque de la categoría (`siglim.bloque_cancha`); candidatos: inicio de la ventana y fin
   de cada partido ya colocado en esa cancha (RF-032). Verificación en memoria de traslapes de
   cancha y de equipo.
5. **Garantía:** el candidato elegido se confirma con `ValidadorConflictos.evaluar` (las reglas de
   H-12) y se inserta; GiST y `tg_partidos_programacion` siguen como última línea. Se evalúa
   `ValidadorLocalia` y, si hay advertencias, se llena `motivo_localia` (DEC-179).
6. **Sin lugar:** `codigo_no_asignado = 'VAL-007'` y una causa legible, por ejemplo «Sin horario
   libre el domingo para Libre (bloque de 120 min): 14 franjas libres, 16 partidos». La respuesta
   incluye un resumen por día con franjas libres y partidos requeridos (RF-042, RN-017).
7. Una transacción (RNF-004), auditoría `GENERAR_FECHA` y `parametros_generacion` (RF-041).
   Determinista.

### 4.3 Árbitro central: asignación voraz equitativa (H-34)

Por partido sin central vigente, ordenado por `inicio`: candidatos `VALIDADO`, que no son del club
de ninguno de los dos equipos (VAL-019) y sin otro partido traslapado (RF-039), incluidos los de la
misma corrida. Se elige al de menor carga (designaciones vigentes en la temporada); desempate por la
designación más antigua y luego `id`. Se inserta con `funcion = 'CENTRAL'`, `automatica = true` y
`designado_por` = quien generó; `tg_designaciones_validar` es la garantía final. Sin candidato: «Sin
árbitro» con la causa, como advertencia que **no bloquea** el envío.

---

## 5. API

Sigue [convenciones-api.md](../arquitectura/convenciones-api.md); permisos por ADR-0007.

| Método y ruta | Uso | Permiso |
| --- | --- | --- |
| `POST /api/v1/temporadas/{id}/calendario` `{fechaInicio}` | Genera emparejamientos y fechas; `409 RN-020` si un grupo ya tiene partidos ligados | `programacion.jornada:EDITAR` |
| `GET /api/v1/temporadas/{id}/calendario` | Calendario por grupo y fecha | `programacion.jornada:CONSULTAR` |
| `PATCH /api/v1/categorias-temporada/{id}` `{diasJuego}` | Días de juego de la categoría | `programacion.jornada:EDITAR` |
| `GET /api/v1/fechas?temporadaId=` | Fechas con estado derivado de sus jornadas | `programacion.jornada:CONSULTAR` |
| `GET /api/v1/fechas/{id}` | Vista por fecha: bloques por categoría y grupo con partidos, descansos, sin asignar y árbitro | `programacion.jornada:CONSULTAR` |
| `PATCH /api/v1/fechas/{id}` `{dias, motivo}` | Cambiar o agregar días (solo en `BORRADOR`) | `programacion.jornada:EDITAR` |
| `POST /api/v1/fechas/{id}/asignacion` `{modo: PENDIENTES\|REGENERAR, motivo?}` | Motor (H-33) y, con el permiso, árbitros (H-34) | `programacion.jornada:EDITAR` (+ `arbitraje.designacion:AUTOMATICA`) |
| `POST /api/v1/fechas/{id}/verificacion` | Conflictos y advertencias de toda la fecha, sin guardar | `programacion.jornada:CONSULTAR` |
| `POST /api/v1/fechas/{id}/envio` | Todas las jornadas pasan a `REVISION` | `programacion.jornada:ENVIAR` |
| `POST /api/v1/fechas/{id}/resolucion` `{decision, observaciones}` | Aprobar o devolver la fecha completa | `programacion.jornada:APROBAR` |
| `POST /api/v1/jornadas/{id}/partidos` + `emparejamientoId?` | Asignar a mano un emparejamiento pendiente | `programacion.jornada:EDITAR` |

Los listados de partidos de una jornada deben **excluir `CANCELADO`** (DEC-181); hoy
`JornadaRepository` no los filtra.

---

## 6. Interfaz

**F-22 · Vista por fecha** (formato pedido por el profesor):

```text
Fecha 3 · sáb 12 y dom 13 dic 2026            [BORRADOR]   [Asignar pendientes] [Regenerar] [Verificar] [Enviar fecha]
┌─────────────── PRIMERA · GRUPO 1 ───────────────┬───────────┬──────────────┬──────────────┐
│ Local            vs   Visitante                 │ Cancha    │ Horario      │ Árbitro      │
│ Halcones         vs   Toros                     │ Cancha 2  │ Dom 08:00    │ J. Pérez     │
│ Pumas            vs   Leones        (Ajustado)  │ Cancha 1  │ Dom 10:00    │ Sin árbitro ⚠│
│ Descansa: Águilas                               │           │              │              │
│ Sin asignar: Lobos vs Osos — sin horario libre el domingo… [Asignar]                        │
└─────────────────────────────────────────────────┴───────────┴──────────────┴──────────────┘
```

- Bloques ordenados por día y por `orden` de categoría; insignias «Automático», «Ajustado» y «Localía».
- Editar cancha u horario usa el ajuste con motivo existente (RF-040); el árbitro se edita en F-24 (Comisión).
- Modal **«Generar calendario»**: fecha de inicio, vueltas (solo lectura, de los parámetros) y días por categoría (Sáb/Dom).
- **F-23 · Revisión de la fecha:** el Presidente ve todos los bloques en solo lectura y aprueba o devuelve la fecha completa.
- **F-11:** vueltas y horario de juego en la versión de parámetros.

---

## 7. Historias (Sprint 4)

Creadas directamente en Jira (no vienen del CSV de importación). Criterios completos y subtareas en
cada issue.

| Clave | Jira | Elemento | SP | Épica |
| --- | --- | --- | --- | --- |
| T-06 | SCRUM-497 | Preparar la base de datos y la documentación de la programación automática | 2 | E07 |
| H-32 | SCRUM-498 | Generar el calendario de la temporada | 3 | E07 |
| H-33 | SCRUM-499 | Asignar automáticamente cancha y horario a una fecha | 5 | E07 |
| H-34 | SCRUM-500 | Asignar automáticamente al árbitro central | 2 | E05 |
| H-35 | SCRUM-501 | Revisar la fecha generada y aprobarla completa | 3 | E07 |

Dependencias: T-06 → H-32 → H-33 → H-34 (también depende de H-14) y H-35. El motor de H-33 (dominio
puro) puede empezar antes de H-32; H-35 trabaja contra el contrato de la §5.

Ramas: `db/m07-t06-programacion-automatica`, `feat/m07-h32-calendario`, `feat/m07-h33-asignacion`,
`feat/m05-h34-arbitro-automatico` y `feat/m07-h35-vista-fecha`, todas desde `develop`.
