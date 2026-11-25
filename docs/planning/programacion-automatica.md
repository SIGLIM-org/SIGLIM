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

Migración `supabase/migrations/20261125100000_programacion_automatica.sql` (T-06.1) y diccionario de
datos 2.14. El bloque resume la migración aplicada; los `COMMENT ON` están en el script.

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
-- Trigger BEFORE INSERT: si dias_juego viene NULL, lo llena con la misma regla (DEC-177).
CREATE FUNCTION siglim.tg_categorias_temporada_dias_juego() RETURNS trigger
LANGUAGE plpgsql SET search_path = siglim, pg_temp AS $$
BEGIN
  IF NEW.dias_juego IS NULL THEN
    SELECT CASE c.tipo WHEN 'LIBRE' THEN '{7}'::smallint[] ELSE '{6}'::smallint[] END
      INTO NEW.dias_juego FROM siglim.categorias c WHERE c.id = NEW.categoria_id;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_categorias_temporada_dias_juego BEFORE INSERT ON siglim.categorias_temporada
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_categorias_temporada_dias_juego();

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
CREATE INDEX ix_fechas_programacion_generada_por ON siglim.fechas_programacion (generada_por) WHERE generada_por IS NOT NULL;
CREATE INDEX ix_fechas_programacion_creado_por ON siglim.fechas_programacion (creado_por) WHERE creado_por IS NOT NULL;
CREATE TRIGGER tg_fechas_programacion_actualizado_en BEFORE UPDATE ON siglim.fechas_programacion
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_actualizado_en();
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
CREATE INDEX ix_emparejamientos_equipo_local ON siglim.emparejamientos (equipo_local_id);
CREATE INDEX ix_emparejamientos_equipo_visitante ON siglim.emparejamientos (equipo_visitante_id) WHERE equipo_visitante_id IS NOT NULL;
CREATE INDEX ix_emparejamientos_creado_por ON siglim.emparejamientos (creado_por) WHERE creado_por IS NOT NULL;
-- temporada_id, fecha_id, grupo_id y partido_id ya quedan cubiertas por sus índices únicos.

-- RN-012: un equipo, un solo emparejamiento por grupo y fecha en cualquier papel. Los índices únicos
-- solo cubren el mismo papel y VAL-003 solo el traslape de horario (sábado y domingo no se traslapan).
CREATE FUNCTION siglim.tg_emparejamientos_equipo_unico() RETURNS trigger
LANGUAGE plpgsql SET search_path = siglim, pg_temp AS $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM siglim.emparejamientos e
     WHERE e.id <> NEW.id AND e.grupo_id = NEW.grupo_id AND e.numero_fecha = NEW.numero_fecha
       AND (NEW.equipo_local_id IN (e.equipo_local_id, e.equipo_visitante_id)
            OR (NEW.equipo_visitante_id IS NOT NULL
                AND NEW.equipo_visitante_id IN (e.equipo_local_id, e.equipo_visitante_id)))) THEN
    PERFORM siglim.error_negocio('RN-012', 'El equipo ya tiene un emparejamiento en esta fecha del grupo.');
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER tg_emparejamientos_equipo_unico
  BEFORE INSERT OR UPDATE OF grupo_id, numero_fecha, equipo_local_id, equipo_visitante_id ON siglim.emparejamientos
  FOR EACH ROW EXECUTE FUNCTION siglim.tg_emparejamientos_equipo_unico();

-- 5. Trazabilidad de lo automático
ALTER TABLE siglim.partidos      ADD COLUMN asignado_automaticamente boolean NOT NULL DEFAULT false;
ALTER TABLE siglim.designaciones ADD COLUMN automatica               boolean NOT NULL DEFAULT false;

-- 6. Permiso nuevo (DEC-182); no entra en pares_incompatibles
INSERT INTO siglim.permisos (codigo, modulo, descripcion)
VALUES ('arbitraje.designacion:AUTOMATICA', 'M-05', 'Designar automáticamente al árbitro central (H-34)')
ON CONFLICT (codigo) DO NOTHING;
INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM siglim.roles r
  JOIN siglim.permisos p ON p.codigo = 'arbitraje.designacion:AUTOMATICA'
 WHERE r.codigo = 'PROGRAMADOR'
ON CONFLICT DO NOTHING;

-- 7. Tablas nuevas: RLS como jornadas (el bucle de la §10 inicial solo corrió una vez), sin DELETE
ALTER TABLE siglim.fechas_programacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE siglim.emparejamientos     ENABLE ROW LEVEL SECURITY;
CREATE POLICY app_sin_restriccion ON siglim.fechas_programacion TO siglim_app USING (true) WITH CHECK (true);
CREATE POLICY app_sin_restriccion ON siglim.emparejamientos     TO siglim_app USING (true) WITH CHECK (true);
GRANT SELECT, INSERT, UPDATE ON siglim.fechas_programacion, siglim.emparejamientos TO siglim_app;
```

Pruebas SQL en `supabase/pruebas/prueba_programacion_automatica.sql`: restricciones CHECK, índices
únicos, trigger de `dias_juego`, la regla RN-012 de `emparejamientos` (en cualquier papel y al mover de fecha), que `siglim_app` no puede borrar en las tablas nuevas, el permiso del
PROGRAMADOR y los valores por defecto de `asignado_automaticamente` y `automatica`.

---

## 4. Algoritmos

Justificación y alternativas descartadas en [ADR-0015](../arquitectura/adr/0015-algoritmo-programacion-automatica.md).

### 4.1 Calendario: método del círculo (H-32)

1. Por grupo, ordenar los equipos `ACTIVO` por el texto de su `id` (`UUID::toString`, el mismo
   orden que `ORDER BY id`; DEC-185). Arreglo `a`: con N impar, `[DESCANSO, e0, …, eN−1]` (el
   descanso es el elemento **fijo**, DEC-184); con N par, `[e0, …, eN−1]`. `M = |a|` (par) y
   `R = M − 1` rondas por vuelta.
2. En cada ronda `r = 0 … R−1`, la mesa `i = 0 … M/2−1` empareja `a[i]` con `a[M−1−i]`; si uno es
   el DESCANSO, el otro descansa (visitante `NULL`, RF-037). Después de la ronda, `a[1..M−1]` rota
   una posición a la derecha: `a = [a0, a[M−1], a1, …, a[M−2]]`.
3. Localía (DEC-184): en la mesa 0, `a[0]` es local en las rondas pares y visitante en las impares;
   en la mesa `i ≥ 1`, `a[i]` es local si `i` es impar y `a[M−1−i]` si es par. (La regla anterior,
   «los demás pares invierten en las rondas impares», deja con N = 4 a un equipo con sus 3 partidos
   de visitante.)
4. Vuelta `v > 1`: mismas rondas; en las vueltas pares se invierte la localía (los descansos no
   cambian); número de fecha `(v−1)·R + r + 1`.
5. La temporada tiene `F = máx(fechas de cada grupo)`; la fecha `k` lleva el sábado
   `primerSábado + 7·(k−1)` y el domingo siguiente, con `primerSábado` el primer sábado en o después
   de la fecha de inicio que da el Programador (DEC-186).

**Propiedades que se prueban** (`GeneradorCalendarioTest`, sin jqwik: DEC-183), con
`@ParameterizedTest` exhaustivo sobre N = 2…30 y v = 1…4: cada par se enfrenta exactamente v veces
y cambia de localía en vueltas consecutivas; cada equipo aparece una vez por fecha (RN-012); con N
impar hay un descanso por fecha y cada equipo descansa v veces (RF-037); diferencia de localía ≤ 1
por vuelta; fechas sin huecos con N/2 partidos y vueltas pares en espejo; resultado determinista
ante cualquier orden de entrada. Además, casos dorados con N = 4 y N = 5.

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
