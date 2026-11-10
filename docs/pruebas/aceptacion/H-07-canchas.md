# Prueba de aceptación · H-07 Registrar canchas con su disponibilidad y bloqueos

| Campo | Valor |
| --- | --- |
| Historia | H-07 · subtarea H-07.3 (prueba de aceptación) |
| Requisitos | RF-027, RF-029, RF-030, VAL-005, UI-008, PEN-007 |
| Pantallas | F-21 (canchas, disponibilidad y bloqueos) |
| Versión probada | `develop` en `4be2795`, con las migraciones `20261020090000`, `20261022143000` y `20261027140000` |
| Fecha | 2026-10-31 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17.6 local con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las tres migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica la clave con la que el backend valida el token de la sesión |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para cada
corrida: no se alteró el proyecto compartido de Supabase y la prueba se reproduce desde cero con
`supabase start` y el alta de cuentas.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `canchas.cancha:CONSULTAR` y `canchas.cancha:EDITAR` (LIGA) |

Datos creados durante la corrida, en la semana del **2 al 8 de noviembre de 2026**:

- Cancha **«Cancha de aceptación H-07»**, ubicación Unidad deportiva, propietario externo «Club
  Deportivo Ejemplo», dirección Av. Universidad 123, Morelia, características «Pasto sintético,
  iluminación».
- Disponibilidad del martes 3 de noviembre, **08:00–20:00**.
- Bloqueo del martes 3 de noviembre, **14:00–15:00**, motivo «Riego y mantenimiento del césped».
- Bloqueo del martes 3 de noviembre, **10:00–12:00**, motivo «Riego del campo por mantenimiento»,
  sobre un partido ya programado.
- Un partido **10:00–12:00** del martes 3 de noviembre. Como la programación manual es la historia
  H-12 (Sprint 3) y todavía no existe, el partido se sembró con SQL directo para poder comprobar el
  criterio; no se usó ninguna pantalla de programación.

## 3. Escenarios

### CA-1 · La cancha registrada aparece en el catálogo

> Dada una cancha, cuando se registra con ubicación y propietario, entonces aparece en el catálogo.

**Pasos**

1. Iniciar sesión como **Administrador** y abrir `/app/programacion/canchas`.
2. Pulsar «Nueva cancha» y capturar nombre, ubicación, dirección, propietario externo,
   características y estado activo.
3. Guardar y revisar la fila del catálogo y el panel de detalle.

**Resultado esperado**

- El alta se guarda y la pantalla abre la cancha recién creada.
- La fila del catálogo muestra el nombre, la ubicación y el propietario; el detalle repite ubicación,
  propietario, dirección y características.
- La cancha queda en estado **ACTIVA**.

**Resultado obtenido:** **Aprobado.** La cancha apareció en el catálogo como única fila («1 cancha»)
con ubicación «Unidad deportiva · Club Deportivo Ejemplo» y, en el detalle, el propietario externo,
la dirección y las características capturadas, con la insignia **ACTIVA**.

**Evidencia:** [`CA1-cancha-registrada.png`](evidencias/H-07/CA1-cancha-registrada.png)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-027: rechaza propietario CLUB sin club», «RF-027: rechaza propietario
  EXTERNO sin propietario externo o con club» y «RF-027: rechaza propietario LIGA con club o
  propietario externo».
- `CanchasIT` · «RF-027: una cancha registrada aparece en el catálogo y se filtra por texto,
  ubicación y activa».
- `PantallaCanchas.test.tsx` · «H-07 criterio 1: muestra la cancha con su ubicación y propietario».

### CA-2 · Disponibilidad y bloqueo visibles en el calendario semanal

> Dado un intervalo, cuando se registra disponibilidad o bloqueo con motivo, entonces se ve en el
> calendario semanal.

**Pasos**

1. Con la cancha abierta, pulsar «Agregar disponibilidad», capturar inicio `2026-11-03T08:00` y fin
   `2026-11-03T20:00`, y guardar.
2. Pulsar «Bloquear intervalo», capturar inicio `2026-11-03T14:00`, fin `2026-11-03T15:00` y motivo
   «Riego y mantenimiento del césped», y guardar.
3. Revisar el calendario de la semana y la leyenda de tipos de bloque.

**Resultado esperado**

- Ambas operaciones confirman con «Disponibilidad registrada.» y «Bloqueo registrado.».
- El calendario pinta la franja **Disponible** de 08:00 a 20:00 del martes 3 y, encima, el bloqueo de
  14:00 a 15:00 con su motivo completo.
- La leyenda distingue Disponible, Bloqueo, Mantenimiento y Partido; las horas sin disponibilidad
  registrada no se presentan como prohibidas (D3).

**Resultado obtenido:** **Aprobado.** El calendario mostró la franja de 08:00 a 20:00 como
**Disponible** y, sobre ella, el bloqueo de 14:00 a 15:00 rotulado «Riego y mantenimiento del
césped». La leyenda de cuatro tipos y la nota de que las horas vacías no están prohibidas quedaron
visibles.

**Evidencia:** [`CA2-calendario-semanal.png`](evidencias/H-07/CA2-calendario-semanal.png)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-029: rechaza un periodo cuyo inicio no es anterior a su fin» y
  «RF-029: rechaza disponibilidad traslapada y nombra el intervalo existente».
- `CanchasIT` · «RF-029: hasta es exclusivo: [10:00, 12:00) y [12:00, 14:00) no se traslapan».
- `CalendarioSemanal.test.tsx` · «H-07 criterio 2: muestra disponible, bloqueo con motivo,
  mantenimiento y partido» y «D3: no presenta las horas vacías como no disponibles».
- `calendario.test.ts` y `fechas.test.ts` · organización de la semana y conversión de horas locales.

### CA-3 · Bloqueo sobre partidos programados, sin reprogramarlos

> Dado un bloqueo sobre partidos programados, cuando se intenta guardar, entonces se listan los
> partidos afectados y no se reprograma ninguno automáticamente.

**Pasos**

1. Con el partido de 10:00 a 12:00 del martes 3 ya programado en la cancha, pulsar «Bloquear
   intervalo» y capturar `2026-11-03T10:00` a `2026-11-03T12:00` con motivo «Riego del campo por
   mantenimiento».
2. Pulsar «Guardar» y revisar el aviso con los partidos afectados.
3. Pulsar «Bloquear de todos modos» y revisar el calendario.
4. Consultar en la base el estado y el inicio del partido.

**Resultado esperado**

- El primer envío responde `409` con `codigo: "RF-030"`, `aceptable: true` y la lista
  `partidosAfectados` con el partido, su periodo y sus equipos; no guarda nada.
- El aviso indica que los partidos **no se reprogramarán ni se cancelarán** y no ofrece reprogramar.
- Al confirmar, el bloqueo se guarda, se avisa de «1 partido(s) que quedaron dentro del bloqueo» y
  el calendario marca el partido **Dentro de un bloqueo**.
- El partido conserva su estado `PROGRAMADO` y su horario: ningún proceso lo movió.

**Resultado obtenido:** **Aprobado.** El `409` llegó con `RF-030`, `aceptable: true` y un partido
`PROGRAMADO` de 10:00 a 12:00 con sus equipos local y visitante. El aviso del modal listó ese partido
y advirtió que no se reprogramaría. Tras «Bloquear de todos modos» el bloqueo quedó registrado con el
aviso «1 partido(s) quedaron dentro del bloqueo y no se reprogramaron» y el partido se pintó como
**Dentro de un bloqueo**. La consulta a la base devolvió `estado = PROGRAMADO` e
`inicio = 2026-11-03 10:00:00-06`: el partido no cambió.

**Evidencia:** [`CA3-respuesta-409.json`](evidencias/H-07/CA3-respuesta-409.json) (respuesta cruda de
la API), [`CA3-aviso-partidos.png`](evidencias/H-07/CA3-aviso-partidos.png) y
[`CA3-bloqueo-confirmado.png`](evidencias/H-07/CA3-bloqueo-confirmado.png)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-030: lista los partidos afectados y no guarda sin confirmación» y
  «RF-030: guarda con confirmación y devuelve los partidos, que no se tocan».
- `CanchaControllerTest` · «RF-030: el 409 aceptable lleva aceptable y partidosAfectados».
- `CanchasIT` · «RF-030: un bloqueo sobre un partido responde 409 con el partido; con confirmación se
  guarda y el partido no cambia».
- `ModalIntervalo.test.tsx` · «H-07 criterio 3 / RF-030: lista los partidos afectados y confirma con
  confirmarPartidosAfectados» y «PEN-007: cambiar un dato en el segundo paso vuelve al primero y no
  confirma».
- `PantallaCanchas.test.tsx` · «H-07 criterio 3: tras bloquear avisa de los partidos que no se
  reprogramaron».
- `calendario.test.ts` · «PEN-007: marca el partido que cae dentro de un bloqueo; [) no cuenta el
  contiguo».

### CA-4 · Una cancha bloqueada o inactiva no se usa en la programación (VAL-005)

> Una cancha inactiva o bloqueada no puede usarse en la programación.

**Pasos**

1. Con el bloqueo de 10:00 a 12:00 vigente, intentar programar un partido nuevo a las 10:30 del
   martes 3 en esa cancha.
2. En la pantalla, pulsar «Desactivar», capturar el motivo «Cancha en mantenimiento mayor» y revisar
   el aviso de partidos afectados.
3. Pulsar «Desactivar de todos modos» y revisar el estado de la cancha.
4. Intentar programar un partido nuevo el miércoles 4 a las 10:00 en la misma cancha.

**Resultado esperado**

- El primer partido se rechaza con `VAL-005` y el motivo del bloqueo; no se crea.
- La desactivación con partidos pide confirmación con el mismo flujo del bloqueo y, al confirmar,
  deja la cancha **INACTIVA** con el aviso «Cancha inactiva: no se puede usar en la programación».
- El segundo partido se rechaza con `VAL-005` porque la cancha ya no está activa.

**Resultado obtenido:** **Aprobado.** El intento sobre la cancha bloqueada falló en la base con
`SQLSTATE=P0001`, `HINT=VAL-005` y «La cancha tiene bloqueo en ese horario: Riego del campo por
mantenimiento.»; el intento sobre la cancha inactiva falló con el mismo `HINT` y «La cancha no está
activa.». La desactivación mostró el partido afectado, guardó con el motivo y dejó la cancha
**INACTIVA**, con el botón «Activar» en lugar de «Desactivar» y el aviso visible.

**Evidencia:** [`CA4-aviso-desactivar.png`](evidencias/H-07/CA4-aviso-desactivar.png),
[`CA4-cancha-inactiva.png`](evidencias/H-07/CA4-cancha-inactiva.png),
[`CA4-val-005-bloqueada.txt`](evidencias/H-07/CA4-val-005-bloqueada.txt) y
[`CA4-val-005-inactiva.txt`](evidencias/H-07/CA4-val-005-inactiva.txt) (salida real del intento de
programar)

**Pruebas automáticas relacionadas**

- `CanchasIT` · «VAL-005: el trigger rechaza un partido nuevo que cae en un bloqueo» y «VAL-005: el
  trigger rechaza un partido nuevo en una cancha inactiva» (`tg_partidos_programacion` de la
  migración `20261020090000`).
- `PantallaCanchas.test.tsx` · «VAL-005: una cancha inactiva lo avisa» y «RF-030: desactivar con
  partidos reenvía con confirmación y el mismo motivo».
- `CanchaServiceTest` · «RF-030: desactivar una cancha con partidos futuros sigue el mismo flujo de
  dos pasos» y «RN-021: exige motivo al cambiar activa y lo fija en la transacción».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Cancha registrada en el catálogo | Aprobado |
| CA-2 Disponibilidad y bloqueo en el calendario | Aprobado |
| CA-3 Bloqueo sobre partidos sin reprogramarlos | Aprobado |
| CA-4 Cancha bloqueada o inactiva rechazada (VAL-005) | Aprobado |

Pruebas automáticas de la misma versión: backend `./mvnw test -Dtest='CanchaServiceTest,CanchaControllerTest'`
con **69/69** pruebas (28 de servicio y 41 de controlador, sin fallos); frontend
`npx vitest run src/features/canchas` con **53/53** pruebas en 6 archivos.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-07 ni una regresión de
RF-027, RF-029, RF-030 o VAL-005.

## 6. Observaciones

1. **PEN-007 sigue abierto.** Lo probado es la propuesta del ERS: el bloqueo se confirma en dos pasos
   y los partidos afectados se reprograman a mano. La formalidad del bloqueo debe validarse con la
   Liga; si cambia, cambian el `409` y el aviso, no la validación `VAL-005`.
2. **`MANTENIMIENTO` ya se registra desde B-12.** La pantalla ofrece «Mantenimiento» en el modal
   «Bloquear intervalo» (B-12.2) y el backend lo guarda como bloqueo tipificado (B-12.1); la prueba
   de aceptación de esa historia está en
   [`B-12-mantenimiento-canchas.md`](B-12-mantenimiento-canchas.md). Esta observación describe la
   versión `4be2795`, en la que la funcionalidad aún no existía.
3. El partido de la prueba se sembró con SQL directo porque la programación manual es H-12. La
   comprobación de `VAL-005` se hizo, por tanto, en el trigger del esquema, que es el que usará la
   historia de programación.
4. La consola del navegador registró dos respuestas `409`: son los conflictos esperados de CA-3 y de
   la desactivación de CA-4, no errores de la aplicación.
5. Las capturas no muestran correos, tokens ni contraseñas; la cuenta se identifica por su rol.

## 7. Conclusión

La historia **H-07 cumple sus cuatro criterios de aceptación** sobre `develop` en `4be2795` con
frontend, backend y base de datos reales: la cancha se registra y aparece en el catálogo, la
disponibilidad y el bloqueo con motivo se ven en el calendario semanal, el bloqueo sobre partidos
lista los afectados sin reprogramarlos y una cancha bloqueada o inactiva queda fuera de la
programación por `VAL-005`. H-07 puede pasar a «Finalizado».
