# Prueba de aceptación · B-30 Generar cobros automáticos al publicar la jornada

| Campo | Valor |
| --- | --- |
| Historia | B-30 (SCRUM-239) · subtarea B-30.2 (SCRUM-446) · Sprint 3 · Could · Low · 3 SP |
| Requisitos | RF-136, RF-135, RF-093, RF-121, RF-141, DEC-33, DEC-36, ADR-0006, ADR-0007 |
| Pantallas | F-22 (Programación › Jornadas), F-23 (revisión de la jornada), F-33 (tesorería: cobros y tarifas), F-10 y F-11 (temporadas), F-15 (clubes y equipos), F-14 (auditoría) |
| Implementó | Israel (B-30.1 #154, squash `5911a87`) |
| Versión probada | `develop` en `b531ffc` (incluye B-30.1 `5911a87`) |
| Fecha | 2026-11-18 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (5 de 5 criterios de SCRUM-446 y V-1), sin defectos |

Los criterios salen de la descripción de SCRUM-446 y del contrato de las convenciones de API §6.13
(«Cobros automáticos al publicar la jornada (B-30, RF-136)»). B-30 es una historia B y no trae
criterios numerados en SCRUM-239, así que esta prueba los numera CA-1 a CA-5 en el orden de
SCRUM-446. V-1 no es un criterio, pero se verifica en la misma corrida.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52). B-30 no trae migración: `cobros.partido_id`, `ux_cobros_partido` y `ck_cobros_monto` vienen del esquema de H-29 |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite reenvía `/api` al backend) |
| Navegador | Google Chrome, ventana de 1440 px; dos sesiones a la vez (ventana normal e incógnito) |

**No hay endpoint de B-30.** Los cobros los genera `POST /api/v1/jornadas/{id}/resolucion` con
`decision: PUBLICADO`: `JornadaService.resolver` emite `JornadaPublicada` y
`CobroAutomaticoService.alPublicarJornada` la atiende en la misma transacción (ADR-0006). La
devolución no emite el evento. La tarifa se busca por la **temporada y la categoría del grupo** de
la jornada; el estado de la temporada no interviene.

**Temporada de prueba aparte.** F-22 exige una temporada `ACTIVA` para programar y la base solo
admite una por liga (`ux_temporadas_activa`). En el compartido no hay ninguna activa y la
«Temporada 2026–2027» sigue en `CONFIGURACION`: activarla no tiene vuelta atrás y le quitaría al
equipo la posibilidad de crear grupos (RF-004). Por eso, igual que en B-05.4 y H-13.3, la prueba usa
la **«Temporada prueba B-30»**, que se activa para la corrida y se cierra al final. Sus tarifas de
Libre repiten los montos que dejó B-29.3 en la «Temporada 2026–2027» (400.00 / 200.00); las de
Veteranos se eligen para cubrir las dos causas de omisión del contrato (`SIN_TARIFA` y
`TARIFA_EN_CERO`), que con las tarifas de la temporada de trabajo (300.00 / 0.00) no se podían
cubrir juntas.

**Aviso al equipo.** Mientras la temporada de prueba esté `ACTIVA`, es la temporada activa de la
liga para todos: por ejemplo, `GET /clubes/{id}/cuota-arbitros` (B-08.3) la toma como referencia.
Se avisa en el chat del equipo al activarla y al cerrarla.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican. No se crea ninguna
cuenta nueva.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador general** | Administrador general · LIGA | Prepara, activa y cierra la temporada de prueba; consulta la auditoría (F-14) |
| **Secretario** | Secretario de la Liga · LIGA | Registra los seis equipos de prueba (F-15) |
| **Tesorero** | Tesorero (liga) · LIGA (`tesoreria.tarifa:EDITAR`, `tesoreria.cobro:CONSULTAR` de toda la liga) | Captura las tarifas; CA-1 a CA-4: revisa los cobros de toda la liga en F-33 |
| **Programador de prueba** | Programador de partidos · LIGA | Crea, programa, envía y reenvía las jornadas |
| **Presidente** | Presidente del Comité Ejecutivo · LIGA (`programacion.jornada:APROBAR`) | Publica las jornadas y devuelve la de Libre (CA-4) |
| **Directiva Real Patzcuaro** | Directiva de club · CLUB Real Patzcuaro (`tesoreria.cobro:CONSULTAR`) | CA-5 |
| **Directiva CamiloSextos** | Directiva de club · CLUB CamiloSextos (`tesoreria.cobro:CONSULTAR`) | CA-5 |

Estado inicial verificado el 2026-11-18 a las 13:20 en el Supabase compartido
([`base-antes.txt`](evidencias/B-30/base-antes.txt)): ninguna temporada `ACTIVA`; 1 jornada (la de
H-13, publicada antes de B-30, sin cobros); 3 cobros `MANUAL` de Real Patzcuaro y ninguno con
`partido_id`; 0 eventos `GENERAR_COBRO` y `GENERAR_COBROS_JORNADA`; último evento de auditoría, el
**146**.

Datos que se crean para la corrida:

| Id | Dato | Destino en la prueba |
| --- | --- | --- |
| T | **«Temporada prueba B-30»** (18-nov-2026 a 31-dic-2026), parámetros v1 **sin** total de fechas de la fase regular, categorías «Libre Demostración» y «Veteranos Demostración», cada una con su «Grupo A» | Se activa para la corrida y se cierra al final |
| TL | Tarifas de **Libre** en **T**: arbitraje **400.00**, cancha **200.00** | CA-1, CA-2 |
| TV | Tarifas de **Veteranos** en **T**: arbitraje **sin registrar**, cancha **0.00** | CA-3 |
| E1 | **«B30 Halcones»**, Real Patzcuaro, Libre / Grupo A | Local de P1 |
| E2 | **«B30 Toros»**, CamiloSextos, Libre / Grupo A | Visitante de P1 |
| E3 | **«B30 Águilas»**, Real Patzcuaro, Libre / Grupo A | Visitante de P2 |
| E4 | **«B30 Lobos»**, CamiloSextos, Libre / Grupo A | Local de P2 |
| E5 | **«B30 Venados»**, Real Patzcuaro, Veteranos / Grupo A | Visitante de P3 |
| E6 | **«B30 Pumas»**, CamiloSextos, Veteranos / Grupo A | Local de P3 |
| JL | **Jornada 1** de Libre / Grupo A de **T**, con P1 y P2 | CA-4 (se devuelve), después CA-1 y CA-2 (se publica) |
| JV | **Jornada 1** de Veteranos / Grupo A de **T**, con P3 | CA-3 |
| P1 | B30 Halcones vs. B30 Toros, **05/12/2026 10:00**, **«Cancha Real Pátzcuaro · Demostración»** (`CLUB`, de Real Patzcuaro) | DEC-33: el local juega en la cancha de su club |
| P2 | B30 Lobos vs. B30 Águilas, **05/12/2026 10:00**, **«Cancha externa · Demostración»** (`EXTERNO`) | Cancha ajena a los dos clubes |
| P3 | B30 Pumas vs. B30 Venados, **05/12/2026 13:00**, **«Cancha externa · Demostración»** | Categoría sin tarifas que cobrar |

El 5 de diciembre no choca con los partidos ni con la disponibilidad arbitral que ya hay en el
compartido (H-13 el 28-nov; B-09 dejó periodos el 28 y el 29-nov para H-14).

**Localía (B-15.1, VAL-027).** Ningún partido debe pedir motivo de aceptación:

- *Cancha del local (DEC-34):* Real Patzcuaro es dueño de una cancha, así que sus equipos solo son
  locales en ella (P1). En la cancha externa el local es siempre de CamiloSextos, que no tiene cancha
  propia ni horarios (`horarios_cancha_club`) en **T** (P2 y P3).
- *Últimas fechas en la unidad deportiva (RN-049):* **T** no tiene total de fechas de la fase
  regular, así que la regla no se evalúa.

En la corrida, P2 se capturó primero con B30 Águilas de local y el modal mostró «Advertencia de
localía» (DEC-34); se canceló y se programó con el local invertido. La advertencia es la esperada
por B-15 y no afecta a los cobros: en una cancha `EXTERNO` pagan los dos equipos sea cual sea el local.

**Cobros esperados al publicar las dos jornadas** (cada equipo paga la tarifa completa, [PROPUESTA]
del contrato):

| Partido | Equipo (club) | Arbitraje | Cancha | Motivo |
| --- | --- | --- | --- | --- |
| P1 | B30 Halcones (Real Patzcuaro) | 400.00 | — | Juega en la cancha de su club (DEC-33) |
| P1 | B30 Toros (CamiloSextos) | 400.00 | 200.00 | |
| P2 | B30 Lobos (CamiloSextos) | 400.00 | 200.00 | Cancha `EXTERNO`: pagan los dos |
| P2 | B30 Águilas (Real Patzcuaro) | 400.00 | 200.00 | |
| P3 | B30 Pumas (CamiloSextos) | — (`SIN_TARIFA`) | — (`TARIFA_EN_CERO`) | Omisiones auditadas |
| P3 | B30 Venados (Real Patzcuaro) | — (`SIN_TARIFA`) | — (`TARIFA_EN_CERO`) | Omisiones auditadas |

Total: **7 cobros** por **2,200.00**: Real Patzcuaro 3 (1,000.00) y CamiloSextos 4 (1,200.00), todos
`PENDIENTE`, con periodo desde el día de la publicación hasta el **12/12/2026** (día del partido más
`dias-para-pagar` = 7) y concepto con la forma «Arbitraje · Jornada 1 · B30 Halcones vs B30 Toros».

### Preparación (no es criterio; se documenta para poder repetirla)

1. Como **Administrador general**, en **Administración → Temporadas** (F-10), crear la temporada
   **T**.
2. En su configuración (F-11): guardar los parámetros deportivos v1 **dejando vacío el total de
   fechas de la fase regular**; asignar «Libre Demostración» y «Veteranos Demostración»; crear el
   «Grupo A» en cada una.
3. Como **Tesorero**, en `/app/tesoreria` → pestaña **Tarifas**, elegir «Temporada prueba B-30 ·
   Configuración» y guardar:
   - **Libre Demostración:** Arbitraje `400.00` y Cancha `200.00`.
   - **Veteranos Demostración:** solo Cancha `0.00`; Arbitraje se deja en «Sin tarifa».
4. Como **Administrador general**, comprobar que la lista de verificación de **T** está completa y
   pulsar **Activar**. **Antes de confirmar, verificar que la fila es «Temporada prueba B-30» y no
   «Temporada 2026–2027».** Avisar al equipo.
5. Como **Secretario**, registrar en F-15 los equipos **E1** a **E6** en su club, categoría y grupo
   de **T**.
6. Como **Programador de prueba**, en **Programación › Jornadas** (debe decir «Temporada activa:
   Temporada prueba B-30»):
   - grupo **Libre Demostración · Grupo A**: crear la **Jornada 1** y programar **P1** y **P2**;
   - grupo **Veteranos Demostración · Grupo A**: crear la **Jornada 1** y programar **P3**;
   - ningún partido debe mostrar «Advertencia de localía»; si aparece, cancelar y revisar el local;
   - en cada jornada, **Verificar conflictos** debe decir «Sin conflictos».
7. Antes de la primera publicación, ejecutar la consulta **C-1** (§ «Consultas»): no debe devolver
   filas.

Las acciones de la preparación quedan en la auditoría (M-02, M-03, M-07 y M-13); no se capturan
aparte salvo `PREP-tarifas.png`: la pestaña Tarifas de **T** (insignias **Activa** y **No oficial**)
con Libre 400.00 / 200.00 y Veteranos «Sin tarifa» / 0.00. Se tomó al final de la corrida, antes del
cierre, por eso la pestaña Cobros cuenta 10.

La preparación quedó registrada el 2026-11-18 entre las 13:57 y las 14:18 (eventos 147 a 168,
[`auditoria-cobros.txt`](evidencias/B-30/auditoria-cobros.txt)): `REGISTRAR_TEMPORADA`, dos
`ASIGNAR_CATEGORIA_TEMPORADA`, dos `REGISTRAR_GRUPO`, un `EDITAR_GRUPO` (se renombró un grupo a
«Grupo A»), `REGISTRAR_PARAMETROS` (v1 sin `fechasFaseRegular`) y `ACTIVAR_TEMPORADA` en M-02; tres
`REGISTRAR_TARIFA` en M-13 (Libre 400 y 200, Veteranos cancha 0); seis `REGISTRAR_EQUIPO` en M-03; y
dos `REGISTRAR_JORNADA` y tres `REGISTRAR_PARTIDO` en M-07, sin ningún `ACEPTAR_LOCALIA`. Antes de la
primera publicación, C-1 no devolvía filas.

### Consultas de solo lectura (SQL Editor del Supabase compartido)

**C-1 · Cobros automáticos de la temporada de prueba**

```sql
select c.nombre as categoria, j.numero as jornada,
       el.nombre || ' vs ' || ev.nombre as partido, ca.nombre as cancha,
       cl.nombre as club, e.nombre as equipo, co.tipo, co.monto,
       co.periodo_inicio, co.fecha_limite, co.estado, co.concepto
from siglim.cobros co
join siglim.partidos p on p.id = co.partido_id
join siglim.jornadas j on j.id = p.jornada_id
join siglim.grupos g on g.id = j.grupo_id
join siglim.categorias_temporada ct on ct.id = g.categoria_temporada_id
join siglim.categorias c on c.id = ct.categoria_id
join siglim.temporadas t on t.id = ct.temporada_id
join siglim.equipos el on el.id = p.equipo_local_id
join siglim.equipos ev on ev.id = p.equipo_visitante_id
join siglim.canchas ca on ca.id = p.cancha_id
join siglim.clubes cl on cl.id = co.club_id
join siglim.equipos e on e.id = co.equipo_id
where t.nombre = 'Temporada prueba B-30'
order by c.nombre, p.inicio, e.nombre, co.tipo;
```

**C-2 · Auditoría de los cobros automáticos**

```sql
select a.id, a.ocurrido_en, a.accion, a.entidad, a.entidad_id, a.motivo, a.detalle
from siglim.auditoria a
where a.id > 146 and a.accion in ('GENERAR_COBRO', 'GENERAR_COBROS_JORNADA')
order by a.id;
```

## 3. Escenarios

### CA-1 · Al publicar la jornada, cada equipo recibe su cobro de arbitraje y de cancha con la tarifa de su categoría

> Al publicar una jornada, cada equipo recibe su cobro de arbitraje y de cancha por partido, con el
> monto de la tarifa de su categoría.

Se ejecuta **después del CA-4**: la Jornada 1 de Libre ya se devolvió y se reenvió, y está en
`REVISION`.

**Pasos**

1. Como **Presidente**, en **Programación › Jornadas**, abrir la Jornada 1 de **Libre** desde
   **Jornadas por revisar** (F-23), pulsar **Aprobar y publicar** sin nota y confirmar.
2. Ejecutar **C-1**.
3. Como **Tesorero**, abrir `/app/tesoreria` → pestaña **Cobros**, filtrar por **Tipo: Arbitraje** y
   después por **Tipo: Cancha**.

**Resultado esperado**

- Paso 1: «Jornada 1 aprobada y publicada. Sus partidos ya aparecen en el portal.». La respuesta del
  dictamen no cambia por los cobros.
- Paso 2: las **7 filas** de Libre de la tabla de cobros esperados: 4 `ARBITRAJE` de 400.00 (los
  cuatro equipos) y 3 `CANCHA` de 200.00 (Toros, Águilas y Lobos), cada una con el club del equipo,
  `estado = PENDIENTE`, `periodo_inicio` = día de la publicación, `fecha_limite = 2026-12-12` y
  concepto «Arbitraje · Jornada 1 · B30 Halcones vs B30 Toros» (o el de su partido y equipo).
  Ninguna fila de Veteranos (CA-3).
- Paso 3: los mismos 7 cobros en F-33, con tipo, destino (club y equipo), monto, fecha límite
  12 dic 2026 e insignia **Pendiente**.

**Resultado obtenido:** **Aprobado.** El diálogo **Aprobar y publicar** avisó que «Los 2 partidos
de la jornada 1 aparecerán en el portal público y la jornada ya no podrá editarse.»; al confirmar sin
nota, F-23 mostró «Jornada 1 aprobada y publicada. Sus partidos ya aparecen en el portal.», la
insignia **Publicado** y el flujo concluido (enviada 14:53, aprobada y publicada 14:56). C-1 devolvió
**7 filas**, todas de Libre Demostración, Jornada 1, `PENDIENTE`, con `periodo_inicio` 2026-11-18 y
`fecha_limite` 2026-12-12: `ARBITRAJE` 400.00 para B30 Halcones y B30 Águilas (Real Patzcuaro) y para
B30 Toros y B30 Lobos (CamiloSextos), y `CANCHA` 200.00 para B30 Águilas, B30 Lobos y B30 Toros, cada
una cargada al club de su equipo. Ninguna fila de Veteranos. En F-33, el **Tesorero** vio «4 cobros
con los filtros aplicados» de **Arbitraje** ($400.00 MXN) y «3 cobros» de **Cancha** ($200.00 MXN),
con destino equipo y club, fecha límite 12 dic 2026, «Periodo desde el 18 nov 2026» e insignia
**Pendiente**. Total: 7 cobros por 2,200.00.

**Evidencia:** `CA1-confirmar-publicacion.png`, `CA1-jornada-publicada.png`, `CA1-sql-cobros.png`,
`CA1-tesorero-arbitraje.png`, `CA1-tesorero-cancha.png`

**Pruebas automáticas relacionadas:** `CobroAutomaticoServiceTest` («RF-136: cada equipo recibe
arbitraje y cancha con la tarifa, el periodo y su club», «RF-141 [PROPUESTA]: si el partido ya pasó,
el plazo se cuenta desde hoy»); `CobrosAutomaticosIT` («RF-136: al publicar la jornada, cada equipo
recibe su cobro de arbitraje y de cancha según la tarifa»); `JornadaServiceTest` («RF-121, RF-136:
el Presidente aprueba una jornada en revisión, queda PUBLICADO y se emite JornadaPublicada»).

### CA-2 · El equipo que juega en la cancha de su propio club no recibe cobro de cancha (DEC-33)

> El equipo que juega en la cancha de su propio club no recibe cobro de cancha (DEC-33).

Se revisa con los mismos datos del CA-1, sin pasos nuevos de pantalla.

**Pasos**

1. En el resultado de **C-1**, revisar las filas de **P1** (Cancha Real Pátzcuaro · Demostración) y
   de **P2** (Cancha externa · Demostración).
2. Como **Tesorero**, en F-33, filtrar por **Club: Real Patzcuaro** y **Tipo: Cancha**.

**Resultado esperado**

- Paso 1: en **P1**, B30 Halcones (Real Patzcuaro, dueño de la cancha) solo tiene `ARBITRAJE`;
  B30 Toros tiene `ARBITRAJE` y `CANCHA`. En **P2**, los dos equipos tienen `ARBITRAJE` y `CANCHA`.
- Paso 2: un solo cobro de cancha de Real Patzcuaro: el de **B30 Águilas** (P2), 200.00. No aparece
  ninguno de B30 Halcones.

**Resultado obtenido:** **Aprobado.** En C-1, el partido B30 Halcones vs B30 Toros, en «Cancha Real
Pátzcuaro · Demostración», tiene **un solo** cobro de B30 Halcones (`ARBITRAJE` 400.00) y dos de
B30 Toros (`ARBITRAJE` 400.00 y `CANCHA` 200.00). En B30 Lobos vs B30 Águilas, en «Cancha externa ·
Demostración», los dos equipos tienen `ARBITRAJE` y `CANCHA`. En F-33, con **Club: Real Patzcuaro** y
**Tipo: Cancha**, el Tesorero vio «1 cobro con los filtros aplicados»: «Cancha · Jornada 1 · B30
Águilas vs B30 Lobos», $200.00; ninguno de B30 Halcones.

**Evidencia:** `CA1-sql-cobros.png` (mismas filas), `CA2-tesorero-cancha-real-patzcuaro.png`

**Pruebas automáticas relacionadas:** `CobroAutomaticoServiceTest` («RF-136: sin cobro de cancha al
equipo que juega en la cancha de su club»); `CobrosAutomaticosIT` («RF-136: el equipo que juega en
la cancha de su club no recibe cobro de cancha; el rival sí»).

### CA-3 · Una categoría sin tarifa no genera cobro y la omisión queda en la auditoría de la jornada

> Una categoría sin tarifa no genera cobro y la omisión queda en la auditoría de la jornada.

Se ejecuta **primero**, justo después de la preparación.

**Pasos**

1. Como **Programador de prueba**, enviar a aprobación la **Jornada 1 de Veteranos** y confirmar.
2. Como **Presidente**, abrirla desde **Jornadas por revisar**, pulsar **Aprobar y publicar** sin nota
   y confirmar.
3. Ejecutar **C-1** y **C-2**.
4. Como **Administrador general**, abrir **Administración → Auditoría** (F-14), filtrar por el
   módulo **M-13** y expandir el evento `GENERAR_COBROS_JORNADA`.

**Resultado esperado**

- Paso 2: la jornada queda **Publicado**; la publicación no se bloquea por falta de tarifas.
- Paso 3: **C-1** no devuelve filas. **C-2** devuelve **un** evento `GENERAR_COBROS_JORNADA` sobre la
  entidad `jornadas` (el `id` de la Jornada 1 de Veteranos), motivo «cobro automático al publicar la
  jornada», y ningún `GENERAR_COBRO`.
- Paso 4: el detalle tiene `partidos: 1`, `cobrosGenerados: 0` y `omitidos` con **4** entradas, cada
  una con `partidoId`, `equipoId`, `tipo` y `causa`: `ARBITRAJE` · `SIN_TARIFA` y `CANCHA` ·
  `TARIFA_EN_CERO` para B30 Pumas y para B30 Venados. Sin concepto ni datos personales.

**Resultado obtenido:** **Aprobado.** La Jornada 1 de Veteranos (B30 Pumas vs B30 Venados, Cancha
externa, sáb 5 dic 13:00–15:00) se envió a las 14:22 y se publicó a las 14:23 con «Jornada 1 aprobada y
publicada. Sus partidos ya aparecen en el portal.»: la falta de tarifas no bloqueó la publicación.
Después de publicar, C-1 respondió «Success. No rows returned» y C-2 devolvió **una** fila: el
`GENERAR_COBROS_JORNADA` 171 sobre `jornadas` (`24d36054-…`, la Jornada 1 de Veteranos) con el motivo
«cobro automático al publicar la jornada», sin ningún `GENERAR_COBRO`. En F-14 (M-13), el detalle
muestra `partidos: 1`, `cobrosGenerados: 0` y cuatro omisiones del mismo partido: `ARBITRAJE` ·
`SIN_TARIFA` y `CANCHA` · `TARIFA_EN_CERO` para B30 Pumas (`db367b54-…`) y para B30 Venados
(`32f055e9-…`), sin concepto ni datos personales; el usuario es **Presidente Lio**.

**Evidencia:** `CA3-jornada-veteranos-publicada.png`, `CA3-sql-sin-cobros.png`,
`CA3-sql-sin-cobros-c2.png`, `CA3-auditoria-omisiones.png`

**Pruebas automáticas relacionadas:** `CobroAutomaticoServiceTest` («RF-136 [PROPUESTA]: sin tarifa
o en cero se omite el cobro y la omisión queda auditada»); `CobrosAutomaticosIT` («RF-136
[PROPUESTA]: sin tarifa o con tarifa en cero no se genera ese cobro y la omisión se audita»).

### CA-4 · Una jornada devuelta (no publicada) no genera cobros

> Una jornada devuelta (no publicada) no genera cobros.

Se ejecuta **después del CA-3** y **antes del CA-1**.

**Pasos**

1. Como **Programador de prueba**, enviar a aprobación la **Jornada 1 de Libre** y confirmar.
2. Como **Presidente**, abrirla desde **Jornadas por revisar**, pulsar **Devolver con
   observaciones**, escribir `Revisar los horarios antes de publicar.` y confirmar.
3. Ejecutar **C-1** y **C-2**.
4. Como **Tesorero**, en F-33, filtrar por **Tipo: Arbitraje**.
5. Como **Programador de prueba**, recargar F-22, abrir la Jornada 1 de Libre (en **Borrador**, con
   las observaciones) y **Enviar a aprobación** de nuevo sin cambios (reenvío, necesario para el
   CA-1).

**Resultado esperado**

- Paso 2: «Jornada 1 devuelta a borrador. El Programador verá tus observaciones.»
- Paso 3: **C-1** sigue sin filas. **C-2** solo tiene el `GENERAR_COBROS_JORNADA` de Veteranos del
  CA-3: la devolución no generó ningún evento de cobros.
- Paso 4: no hay cobros de arbitraje de B30.
- Paso 5: «Jornada 1 enviada a aprobación. La Presidencia la revisará antes de publicarla.»; la
  jornada vuelve a `REVISION` y sigue sin cobros.

**Resultado obtenido:** **Aprobado.** La Jornada 1 de Libre (2 partidos) se envió a las 14:31 y el
Presidente la devolvió a las 14:32 con «Revisar los horarios antes de publicar.». F-23 mostró «Jornada
1 devuelta a borrador. El Programador verá tus observaciones.», la insignia **Borrador** y
«Observaciones de la última devolución». Después de la devolución, C-1 siguió sin filas y C-2 siguió
con una sola fila, el `GENERAR_COBROS_JORNADA` 171 de Veteranos: la devolución (`DEVOLVER_JORNADA`
173) no generó ningún evento de M-13. En F-33, el Tesorero con **Tipo: Arbitraje** vio «Ningún cobro
coincide con los filtros». El reenvío sin cambios (14:53, `ENVIAR_JORNADA_A_APROBACION` 174) dejó la
jornada otra vez en `REVISION`, todavía sin cobros.

**Evidencia:** `CA4-jornada-devuelta.png`, `CA4-sql-sin-cobros.png`, `CA4-sql-sin-cobros-c2.png`,
`CA4-tesorero-sin-cobros.png`

**Pruebas automáticas relacionadas:** `JornadaServiceTest` («RF-121: devolver con observaciones
vuelve a BORRADOR, fija el motivo y lo audita»: verifica que no se emite ningún evento);
`ProgramacionIT` («RF-121: devolver sin observaciones se rechaza; con observaciones vuelve a
BORRADOR y admite cambios»).

### CA-5 · La Directiva solo ve en F-33 los cobros de su club

> La Directiva solo ve en F-33 los cobros de su club.

Se ejecuta **después del CA-1**.

**Pasos**

1. Como **Directiva Real Patzcuaro**, abrir `/app/tesoreria` → pestaña **Cobros**.
2. Revisar el filtro **Club** y filtrar por **Tipo: Arbitraje** y después **Tipo: Cancha**.
3. En DevTools → **Red**, recargar, abrir la petición `GET /api/v1/cobros…`, copiar el valor de
   `Authorization`, pegar en la **Consola** el ayudante de B-29 (§ «Preparación», paso 2) y limpiar la
   consola (Ctrl+L). Ejecutar
   `api('GET', '/cobros?clubId=<id de CamiloSextos>&tamano=100')`, con el id que da la consulta
   `select id from siglim.clubes where nombre = 'CamiloSextos';`.
4. Como **Directiva CamiloSextos**, abrir la misma pestaña y filtrar por **Tipo: Arbitraje** y
   **Tipo: Cancha**.

**Resultado esperado**

- Pasos 1 y 2: solo cobros de **Real Patzcuaro**: los 3 automáticos (Halcones arbitraje 400.00;
  Águilas arbitraje 400.00 y cancha 200.00) y los 3 manuales que ya tenía. Ningún cobro de
  B30 Toros ni de B30 Lobos. Se anota qué clubes ofrece el filtro **Club** (aunque ofreciera otro,
  la lista solo puede traer filas del ámbito; eso lo comprueba el paso 3).
- Paso 3: `200` con `contenido: []` y `totalElementos: 0`: el club ajeno simplemente no aparece
  (ADR-0007); no hay error ni se revela nada.
- Paso 4: solo los 4 cobros automáticos de **CamiloSextos** (Toros y Lobos, arbitraje 400.00 y cancha
  200.00 cada uno) y ninguno de Real Patzcuaro.

**Resultado obtenido:** **Aprobado.** La **Directiva Real Patzcuaro** vio en F-33 «6 cobros», todos
de su club: los tres automáticos (Arbitraje de B30 Halcones y de B30 Águilas, $400.00, y Cancha de
B30 Águilas, $200.00) y los tres manuales que ya tenía. Las tarjetas suman su adeudo: $1,650.00 en 6
cobros por cobrar, $1,200.00 pendiente y $450.00 vencido, y «Adeudos por club» lista un solo club.
Con **Tipo: Arbitraje** y **Tipo: Cancha** aparecieron 2 y 1 cobros, ninguno de B30 Toros ni de
B30 Lobos. En la consola, `GET /cobros?clubId=034779f4-…&tamano=100` (CamiloSextos) respondió **200**
con `contenido: []`, `totalElementos: 0` y `totalPaginas: 0`: el club ajeno simplemente no aparece.
La **Directiva CamiloSextos** vio solo sus 4 cobros automáticos: Arbitraje $400.00 y Cancha $200.00
de B30 Toros y de B30 Lobos, y ninguno de Real Patzcuaro. Las capturas no muestran token ni correos.

**Evidencia:** `CA5-directiva-real-patzcuaro.png`, `CA5-directiva-real-patzcuaro-c2.png`,
`CA5-consola-club-ajeno.png`, `CA5-directiva-camilosextos.png`, `CA5-directiva-camilosextos-c2.png`
(sin token ni correos en las capturas)

**Pruebas automáticas relacionadas:** `CobrosIT` («H-29 criterios 2 y 3 · RF-140: la Directiva solo
ve los cobros y adeudos de su club»).

### V-1 · Auditoría de cada cobro generado

No es un criterio: confirma la auditoría del contrato (§6.13) con los cobros de Libre.

**Pasos**

1. Ejecutar **C-2** después del CA-1.
2. En F-14 (módulo **M-13**), expandir un `GENERAR_COBRO` y el `GENERAR_COBROS_JORNADA` de Libre.

**Resultado esperado**

- **7** `GENERAR_COBRO`, uno por cobro, con entidad `cobros`, motivo «cobro automático al publicar la
  jornada», usuario el **Presidente** que publicó y detalle con `jornadaId`, `partidoId`, `clubId`,
  `equipoId`, `tipo`, `monto`, `periodoInicio`, `fechaLimite` y `estado: PENDIENTE`, **sin**
  concepto ni datos personales.
- **1** `GENERAR_COBROS_JORNADA` de Libre con `partidos: 2`, `cobrosGenerados: 7` y `omitidos: []`.
  En total, 2 `GENERAR_COBROS_JORNADA` en la corrida.

**Resultado obtenido:** **Aprobado.** F-14 filtrado por M-13 lista 23 eventos; los de la corrida son
**7 `GENERAR_COBRO`** (ids 176 a 182) y **2 `GENERAR_COBROS_JORNADA`** (171 de Veteranos y 183 de
Libre), todos del **Presidente Lio** a las 14:23:43 y 14:56:44 con el motivo «cobro automático al
publicar la jornada». El `GENERAR_COBROS_JORNADA` 183 tiene `omitidos: []`, `partidos: 2` y
`cobrosGenerados: 7`. El `GENERAR_COBRO` 182 (entidad `cobros`) tiene `tipo: CANCHA`, `monto: 200`,
`clubId`, `estado: PENDIENTE`, `equipoId`, `jornadaId`, `partidoId`, `fechaLimite: 2026-12-12` y
`periodoInicio: 2026-11-18`, sin concepto ni datos personales. El detalle de los 9 eventos está en
`auditoria-cobros.txt`. La captura de F-14 se redujo a 1440 px de ancho.

**Evidencia:** `V1-auditoria-m13.png`, `V1-detalle-generar-cobro.png`, `auditoria-cobros.txt`

**Pruebas automáticas relacionadas:** `CobroAutomaticoServiceTest` («RF-136: cada equipo recibe
arbitraje y cancha con la tarifa, el periodo y su club», «RF-136: un cobro que ya existía no se
duplica ni se vuelve a auditar»); `CobrosAutomaticosIT` («RF-136: volver a procesar la jornada
publicada no duplica los cobros»).

### Limpieza (no es criterio)

1. Como **Administrador general**, en **Administración → Temporadas**, pulsar **Cerrar** en
   **«Temporada prueba B-30»** (verificar el nombre) con el motivo
   `Cierre de la temporada de prueba de aceptación B-30.` Avisar al equipo.
2. Comprobar que «Temporada 2026–2027» sigue en `CONFIGURACION` y que ya no hay temporada `ACTIVA`.

El cierre no toca los cobros: los 7 cobros de prueba quedan `PENDIENTE` y la tarea de vencimiento
(B-31) los pasará a `VENCIDO` después del 12-dic.

Ejecutada el 2026-11-18: `CERRAR_TEMPORADA` de **T** a las 15:38:27 (evento 184, M-02), con el motivo
indicado y `operacionesAplicadas: 0`. Después del cierre no hay temporada `ACTIVA`, «Temporada
2026–2027» sigue en `CONFIGURACION` sin cambios (sus 4 tarifas de B-29 intactas) y los 7 cobros
automáticos siguen `PENDIENTE` ([`base-despues.txt`](evidencias/B-30/base-despues.txt)).

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Cada equipo recibe arbitraje y cancha con la tarifa de su categoría | Aprobado |
| CA-2 Sin cobro de cancha al equipo en la cancha de su club (DEC-33) | Aprobado |
| CA-3 Categoría sin tarifa: sin cobro y omisión auditada | Aprobado |
| CA-4 Una jornada devuelta no genera cobros | Aprobado |
| CA-5 La Directiva solo ve los cobros de su club | Aprobado |
| V-1 Auditoría de cada cobro generado | Aprobado |

Las pruebas automáticas citadas ya corrieron en el PR de B-30.1 (#154), fusionado en `develop`.
Esta subtarea no cambia código.

## 5. Defectos encontrados

| Clave | Severidad | Descripción | Estado |
| --- | --- | --- | --- |
| — | — | Ninguno | — |

## 6. Observaciones (no son defectos)

1. **El concepto pone primero al equipo que paga, no al local.** B30 Toros, visitante en P1, ve
   «Arbitraje · Jornada 1 · **B30 Toros vs B30 Halcones**», y B30 Águilas, visitante en P2, ve
   «… B30 Águilas vs B30 Lobos». `CobroAutomaticoService.concepto` arma «equipo vs rival» a
   propósito, y el ejemplo del contrato (§6.13, «Halcones vs Toros») no dice si debe seguir el orden
   local–visitante. Se deja a Israel y a Geovani decidir si se documenta así o se cambia al orden del
   partido.
2. **La advertencia de localía de DEC-34 condiciona los datos de prueba.** Al capturar P2 con
   B30 Águilas (Real Patzcuaro) de local en la cancha externa, el modal mostró «Advertencia de
   localía»: Real Patzcuaro es dueño de una cancha. Se canceló y se programó con el local de
   CamiloSextos, que no tiene cancha; P3 se capturó igual. Es el comportamiento esperado de B-15.1 y
   no afecta a los cobros: en una cancha `EXTERNO` pagan los dos equipos.
3. **Primer intento del CA-5 con `400`.** El primer `GET /cobros?clubId=<…>` se envió con los signos
   `<` `>` del marcador del documento y respondió `400 VALIDACION` (uuid inválido); es correcto y no
   se conservó en la evidencia. Con el id limpio respondió `200` vacío.
4. **Montos en la auditoría.** El JSONB del `GENERAR_COBRO` guarda `400.00` y `200.00` con dos
   decimales, pero F-14 los muestra como `400` y `200`, igual que la observación 3 de B-29 con las
   tarifas.
5. **La tarifa de Veteranos se eligió para cubrir las dos omisiones.** Con las tarifas de la
   «Temporada 2026–2027» (300.00 / 0.00) solo se habría cubierto `TARIFA_EN_CERO`; en **T** se dejó el
   arbitraje sin registrar para cubrir también `SIN_TARIFA`. Los montos de Libre son los de B-29.
6. **La idempotencia no se prueba desde la interfaz.** Una jornada publicada no se vuelve a publicar
   (`409 RF-090`), así que el `ON CONFLICT DO NOTHING` sobre `ux_cobros_partido` solo lo cubren
   `CobroAutomaticoServiceTest` y `CobrosAutomaticosIT`.
7. **Datos que quedan en el compartido.** La «Temporada prueba B-30» (`CERRADA` tras la limpieza)
   con sus 3 tarifas, sus 2 grupos, los 6 equipos «B30 …», las dos Jornadas 1 `PUBLICADO` (visibles en
   `v_publica_programacion`, que no filtra por estado de la temporada) y los **7 cobros** `PENDIENTE`
   por 2,200.00 (Real Patzcuaro 1,000.00 y CamiloSextos 1,200.00). Aparecen en F-33 y en los adeudos
   de las Directivas y del Tesorero, y la tarea de vencimiento (B-31) los pasará a `VENCIDO` después
   del 12-dic.

## 7. Conclusión

B-30 cumple sus cinco criterios de aceptación sobre `develop` en `b531ffc`, con el frontend, el
backend y el Supabase compartido. Al publicar la jornada, cada equipo recibe en la misma transacción
su cobro de arbitraje y de cancha con la tarifa de su temporada y categoría, cargado a su club, con
periodo hasta siete días después del partido. El equipo que juega en la cancha de su club no paga
cancha. Una categoría sin tarifa, o con tarifa en cero, no genera cobros, no bloquea la publicación y
deja las omisiones en la auditoría de la jornada. Una jornada devuelta no genera nada. Cada Directiva
ve solo los cobros de su club, en la pantalla y en la API. Cada cobro queda auditado a nombre del
Presidente que publicó, sin concepto ni datos personales. No se encontraron defectos.

La subtarea **B-30.2 queda terminada** y **B-30 puede pasar a Finalizado** al fusionar este
documento: B-30.1 (#154) ya está en `develop`.

## Orden de ejecución recomendado

Preparación → CA-3 (Veteranos se publica) → CA-4 (Libre se devuelve y se reenvía) → CA-1 (Libre se
publica) → CA-2 → V-1 → CA-5 (Directivas) → Limpieza.

El CA-3 va primero para comprobar las omisiones sin otros cobros en la temporada; el CA-4 va antes
del CA-1 porque publicar es definitivo; el cierre de la temporada va en la limpieza porque es
irreversible (RF-003) y libera el lugar de temporada activa.

## Capturas por guardar en `evidencias/B-30/`

| Archivo | Escenario |
| --- | --- |
| `PREP-tarifas.png` | Preparación, paso 3 |
| `CA3-jornada-veteranos-publicada.png`, `CA3-sql-sin-cobros.png`, `CA3-sql-sin-cobros-c2.png`, `CA3-auditoria-omisiones.png` | CA-3 |
| `CA4-jornada-devuelta.png`, `CA4-sql-sin-cobros.png`, `CA4-sql-sin-cobros-c2.png`, `CA4-tesorero-sin-cobros.png` | CA-4 |
| `CA1-confirmar-publicacion.png`, `CA1-jornada-publicada.png`, `CA1-sql-cobros.png`, `CA1-tesorero-arbitraje.png`, `CA1-tesorero-cancha.png` | CA-1 |
| `CA2-tesorero-cancha-real-patzcuaro.png` | CA-2 |
| `V1-auditoria-m13.png`, `V1-detalle-generar-cobro.png` | V-1 |
| `CA5-directiva-real-patzcuaro.png`, `CA5-directiva-real-patzcuaro-c2.png`, `CA5-consola-club-ajeno.png`, `CA5-directiva-camilosextos.png`, `CA5-directiva-camilosextos-c2.png` | CA-5 |

Además: `base-antes.txt`, `base-despues.txt` y `auditoria-cobros.txt` (consultas de solo lectura al
compartido).
