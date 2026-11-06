# Prueba de aceptación · H-29 Crear cobros manuales y consultar adeudos

| Campo | Valor |
| --- | --- |
| Historia | H-29 (SCRUM-207) · subtarea H-29.3 (SCRUM-356) |
| Requisitos | RF-137, RF-140, UI-027 |
| Pantallas | F-33 (tesorería: resumen, cobros, adeudos por club y modal «Nuevo cobro») y F-08 (Inicio, tarjeta de adeudos) |
| Versión probada | `develop` en `b377e0d`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-06 |
| Ejecutó | López Meza Leonardo (QA) con un agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado (3 de 3 criterios)**, con D-01 y D-02 abiertos: la historia no se cierra hasta corregirlos |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local (contenedor Docker efímero) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que firma con ES256 los tokens de sesión y simula el inicio de sesión de Supabase Auth para la pantalla de acceso |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, contra la base local y el JWKS local (variables de entorno, sin tocar `backend/.env`) |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Microsoft Edge sin interfaz, controlado con Playwright, ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase. El arnés (JWKS local, scripts de Playwright y SQL de preparación) vive fuera del
repositorio; aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Tesorero** | `TESORERO` · LIGA | CA-1 y CA-2 (`tesoreria.cobro:CONSULTAR` y `tesoreria.cobro:CREAR`) |
| **Directiva de club** | `DIRECTIVA_CLUB` · CLUB = «Club Halcones Morelia» | CA-2 (cambio de ámbito) y CA-3 (solo `tesoreria.cobro:CONSULTAR`) |

Estructura sembrada en la base local: temporada **«Temporada de aceptación 2026-27» (ACTIVA)**,
categoría **Libre**, **Grupo 1**; **Club Halcones Morelia** con los equipos **Halcones A** y
**Halcones B**, y **Club Riviera Morelia** (club ajeno) con **Riviera F.C.** Todos los datos son
ficticios.

Cobros, todos `tipo = MANUAL`:

| # | Club | Equipo | Concepto | Monto | Estado | Origen |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Halcones | Halcones A | Cuota de inscripción temporada 2026-27 | 1500.00 | PENDIENTE | API con el token del Tesorero (CA-1, ver D-02) |
| 2 | Halcones | — (al club) | Uso de cancha para entrenamiento | 350.50 | PENDIENTE | Pantalla F-33 (CA-1) |
| 3 | Halcones | Halcones A | Arbitraje jornada 1 | 200.00 | VENCIDO (límite 31 oct 2026) | SQL |
| 4 | Halcones | Halcones A | Arbitraje jornada 2 | 400.00 | PAGADO | SQL |
| 5 | Halcones | Halcones B | Cuota de inscripción temporada 2026-27 | 275.25 | PENDIENTE | SQL |
| 6 | Halcones | Halcones B | Reposición de credencial | 100.00 | RECHAZADO | SQL |
| 7 | Riviera | Riviera F.C. | Arbitraje jornada 1 | 600.00 | VENCIDO (límite 31 oct 2026) | SQL |
| 8 | Riviera | Riviera F.C. | Cuota de inscripción temporada 2026-27 | 125.00 | PENDIENTE | SQL |

Totales esperados (cálculo manual previo), comparados contra pantalla, API y SQL:

| Vista | Total | Pendiente | Vencido | Cobros |
| --- | --- | --- | --- | --- |
| Tesorero (toda la liga) | **3,050.75** | 2,250.75 | 800.00 | 6 |
| Club Halcones Morelia | **2,325.75** | 2,125.75 | 200.00 | 4 |
| · Halcones A | 1,700.00 | 1,500.00 | 200.00 | 2 |
| · Halcones B | 275.25 | 275.25 | 0.00 | 1 |
| · (cargo al club, sin equipo) | 350.50 | 350.50 | 0.00 | 1 |
| Club Riviera Morelia | **725.00** | 125.00 | 600.00 | 2 |

Los cobros 4 (PAGADO) y 6 (RECHAZADO) no suman en ninguna vista de adeudos, pero sí aparecen en el
listado de cobros.

## 3. Escenarios

### CA-1 · Un cobro con concepto, monto y periodo queda PENDIENTE

> Dado un cobro con concepto, monto y periodo, cuando se crea, entonces queda PENDIENTE.

**Pasos**

1. Con el token de la sesión del **Tesorero**, crear por `POST /api/v1/cobros` el cobro 1 a
   **Halcones A** (periodo 2026-11-06, límite 2026-11-20): el modal no ofrece equipos a esta cuenta
   (D-02).
2. Iniciar sesión como **Tesorero**, abrir `/app/tesoreria` y pulsar «Nuevo cobro».
3. En el modal, con el club Halcones, intentar crear con monto `0`, con monto `350.505` y con fecha
   límite (2026-11-05) anterior al inicio (2026-11-06), contando los `POST /cobros` que salen del
   navegador.
4. Capturar el cobro 2 (Club Halcones Morelia, «Uso de cancha para entrenamiento», 350.50, del
   2026-11-06 al 2026-11-20) y pulsar «Crear cobro», capturando la petición y la respuesta crudas.
5. Revisar en la base las filas de `siglim.cobros` y su auditoría.
6. Por API con el token del Tesorero: equipo **Riviera F.C.** con el `clubId` de Halcones, y dos
   cuerpos con `"estado": "PAGADO"` y `"tipo": "MULTA"` (estos dos se ejecutaron **al final de la
   corrida**, después de las evidencias del CA-2 y CA-3, para no alterar los totales).

**Resultado esperado**

- El modal rechaza los tres datos inválidos con un mensaje junto al campo y **sin llamar a la API**.
- El alta responde `201` con `Location`, `tipo = MANUAL`, `estado = PENDIENTE` y `monto` con dos
  decimales; en el cobro al club se omite `equipo` (§6.13).
- La pantalla confirma el alta y la fila aparece con estado **Pendiente**, monto en formato
  mexicano y el destino correcto.
- Cada alta deja `REGISTRAR_COBRO` en `M-13`, entidad `cobros`, sin el concepto en el detalle.
- Equipo de otro club: `422 REFERENCIA_INVALIDA`. Campos `estado`/`tipo`: §6.13 dice que «no se
  aceptan».

**Resultado obtenido:** **Aprobado.**

- Validaciones: «El monto debe ser mayor que cero.», «Escribe un monto mayor que cero, con hasta
  dos decimales.» y «La fecha límite no puede ser anterior al inicio del periodo.»; **0** `POST`
  enviados en los tres intentos.
- Alta desde la pantalla: aviso «Cobro «Uso de cancha para entrenamiento» registrado para Club
  Halcones Morelia por $350.50.» y la tabla con 2 cobros: «Cargo al club · $350.50 MXN ·
  20 nov 2026 · PENDIENTE» y «Halcones A · $1,500.00 MXN · 20 nov 2026 · PENDIENTE».
- API: ambos `201` con `Location: /api/v1/cobros/{id}`, `"tipo":"MANUAL"`, `"estado":"PENDIENTE"`,
  `"monto":1500.00` y `"monto":350.50` en el cuerpo crudo; el cobro al club no trae `equipo`.
- Base: dos filas `MANUAL`/`PENDIENTE` con `creado_por` = Tesorero; dos eventos `REGISTRAR_COBRO`
  en `M-13` con club, equipo (solo el cobro 1), tipo, monto, periodo, fecha límite y estado;
  `detalle ? 'concepto'` es **falso** en ambos.
- Equipo de otro club: `422` con `codigo: "REFERENCIA_INVALIDA"` y «El equipo «Riviera F.C.» no
  pertenece al club indicado.».
- `"estado": "PAGADO"` y `"tipo": "MULTA"`: la API respondió `201` y **creó el cobro como `MANUAL`
  `PENDIENTE`** (ignora los campos). El criterio se cumple; la diferencia de redacción con §6.13 va
  en la Observación 1.

**Evidencia:** [`CA1-tesoreria-antes.png`](evidencias/H-29/CA1-tesoreria-antes.png) (con el cobro 1
ya creado por API),
[`CA1-validacion-monto-cero.png`](evidencias/H-29/CA1-validacion-monto-cero.png),
[`CA1-validacion-monto-tres-decimales.png`](evidencias/H-29/CA1-validacion-monto-tres-decimales.png),
[`CA1-validacion-fecha-limite-anterior.png`](evidencias/H-29/CA1-validacion-fecha-limite-anterior.png),
[`CA1-validaciones-pantalla.txt`](evidencias/H-29/CA1-validaciones-pantalla.txt) (mensajes y conteo
de `POST`), [`CA1-modal-cobro-club.png`](evidencias/H-29/CA1-modal-cobro-club.png),
[`CA1-cobros-creados.png`](evidencias/H-29/CA1-cobros-creados.png),
[`CA1-respuesta-post-cobro-equipo.txt`](evidencias/H-29/CA1-respuesta-post-cobro-equipo.txt) y
[`CA1-respuesta-post-cobro-club.txt`](evidencias/H-29/CA1-respuesta-post-cobro-club.txt) (petición y
respuesta crudas),
[`CA1-cobros-y-auditoria-en-base.txt`](evidencias/H-29/CA1-cobros-y-auditoria-en-base.txt) (salida
real de SQL),
[`CA1-respuesta-422-equipo-de-otro-club.txt`](evidencias/H-29/CA1-respuesta-422-equipo-de-otro-club.txt),
[`CA1-respuesta-post-con-estado-pagado.json`](evidencias/H-29/CA1-respuesta-post-con-estado-pagado.json),
[`CA1-respuesta-post-con-tipo-multa.json`](evidencias/H-29/CA1-respuesta-post-con-tipo-multa.json) y
[`CA1-campos-ignorados-en-base.txt`](evidencias/H-29/CA1-campos-ignorados-en-base.txt); resumen en
[`CA1-api-resumen.txt`](evidencias/H-29/CA1-api-resumen.txt)

**Pruebas automáticas relacionadas**

- `CobroServiceTest` · «RF-137: crea un cobro MANUAL PENDIENTE con monto BigDecimal y periodo
  predeterminado», «RF-137: un cobro al club no lleva equipo y lo omite en la auditoría»,
  «REFERENCIA_INVALIDA: un equipo de otro club responde 422 sin escribir ni revelar su nombre»,
  «RF-137: rechaza monto nulo, cero o negativo» y «RF-137: la fecha límite es obligatoria y no
  puede ser anterior al periodo».
- `CobroControllerTest` · «RF-137: POST responde 201 con Location y el monto con dos decimales» y
  «RF-137: POST con monto no positivo o con más de dos decimales responde 400 VALIDACION».
- `CobrosIT` · «H-29 criterio 1 · RF-137: el TESORERO crea un cobro MANUAL PENDIENTE con monto
  BigDecimal y lo audita» y «RF-137: rechaza un equipo de otro club y un club inexistente sin
  escribir».
- `PantallaTesoreria.test.tsx` · «H-29.2: crea un cobro manual para un equipo y confirma el alta»,
  «H-29.2: valida los campos obligatorios del alta antes de llamar a la API» y «H-29.2: si no puede
  consultar equipos, el alta carga el cobro al club».

### CA-2 · Los adeudos se suman por club y equipo según el ámbito

> Los adeudos se suman por club y equipo según el ámbito del usuario.

**Pasos**

1. Sembrar con SQL los cobros 3 a 8 (insertados `PENDIENTE` y luego cambiados con `UPDATE`, con el
   trigger `tg_cobros_estado` activo) y guardar las sentencias y su salida.
2. Como **Tesorero**: revisar la tarjeta de adeudos de Inicio (`/app`, F-08), el resumen de F-33 y
   la pestaña «Adeudos por club».
3. `GET /api/v1/adeudos` sin filtro y con `clubId` de Halcones, y `GET /api/v1/cobros`.
4. Sumar `PENDIENTE` + `VENCIDO` por club y equipo con una consulta SQL directa.
5. Como **Directiva de Halcones** (asignación del paso 1 del CA-3): revisar la tarjeta, el resumen,
   la pestaña de adeudos y `GET /api/v1/adeudos`.

**Resultado esperado**

- Tarjeta, resumen, pestaña, API y SQL coinciden con la tabla de totales esperados; PAGADO y
  RECHAZADO no suman; el cargo al club suma en el club y no en un equipo.
- `GET /cobros` del Tesorero devuelve los 8 cobros (`totalElementos = 8`), con PAGADO y RECHAZADO.
- Con el ámbito de la Directiva, todas las sumas se reducen a Halcones: **2,325.75**.

**Resultado obtenido:** **Aprobado.** La siembra aplicó sin errores (`INSERT 0 6`, tres `UPDATE`)
sin deshabilitar el trigger. Con el **Tesorero**: tarjeta «$3,050.75 · 6 cobros en 2 clubes ·
Vencido: $800.00»; resumen «Adeudo total $3,050.75 · Pendiente $2,250.75 · Vencido $800.00 ·
Clubes con adeudo 2»; pestaña con Halcones $2,325.75 (A $1,700.00, B $275.25) y Riviera $725.00
(Riviera F.C. $725.00). `GET /adeudos` devolvió exactamente esos montos con dos decimales
(`"total":3050.75`, `"vencido":0.00` en Halcones B) y sin el cargo al club en `equipos`; con
`clubId` de Halcones, solo Halcones con 2325.75. `GET /cobros` regresó `totalElementos = 8`. La
consulta SQL con `ROLLUP` dio los mismos totales por equipo, club y liga, y listó aparte los dos
cobros excluidos (PAGADO 400.00 y RECHAZADO 100.00). Con la **Directiva**: tarjeta «$2,325.75 ·
4 cobros en 1 club · Vencido: $200.00», resumen y pestaña solo con Halcones y `GET /adeudos` con
`"total":2325.75` y un solo club.

**Evidencia:** [`CA2-siembra-cobros.txt`](evidencias/H-29/CA2-siembra-cobros.txt) (sentencias y
salida real), [`CA2-inicio-tarjeta-adeudos-tesorero.png`](evidencias/H-29/CA2-inicio-tarjeta-adeudos-tesorero.png),
[`CA2-tesoreria-resumen-y-cobros-tesorero.png`](evidencias/H-29/CA2-tesoreria-resumen-y-cobros-tesorero.png),
[`CA2-pestana-adeudos-tesorero.png`](evidencias/H-29/CA2-pestana-adeudos-tesorero.png),
[`CA2-respuesta-adeudos-tesorero.json`](evidencias/H-29/CA2-respuesta-adeudos-tesorero.json),
[`CA2-respuesta-adeudos-tesorero-halcones.json`](evidencias/H-29/CA2-respuesta-adeudos-tesorero-halcones.json),
[`CA2-respuesta-cobros-tesorero.json`](evidencias/H-29/CA2-respuesta-cobros-tesorero.json),
[`CA2-suma-sql-directa.txt`](evidencias/H-29/CA2-suma-sql-directa.txt),
[`CA2-inicio-tarjeta-adeudos-directiva.png`](evidencias/H-29/CA2-inicio-tarjeta-adeudos-directiva.png),
[`CA2-pestana-adeudos-directiva.png`](evidencias/H-29/CA2-pestana-adeudos-directiva.png) y
[`CA2-respuesta-adeudos-directiva.json`](evidencias/H-29/CA2-respuesta-adeudos-directiva.json) (los
`.json` son el cuerpo crudo de la respuesta); resumen en
[`CA2-api-resumen.txt`](evidencias/H-29/CA2-api-resumen.txt)

**Pruebas automáticas relacionadas**

- `AdeudoServiceTest` · «RF-140: suma adeudos por club y equipo, solo PENDIENTE y VENCIDO, con dos
  decimales», «RF-140: sin adeudos responde ceros con dos decimales y lista vacía» y «RF-140: dos
  filas del mismo club y equipo se acumulan en una sola entrada».
- `AdeudoControllerTest` · «RF-140: GET devuelve totales y desglose por club y equipo con dos
  decimales» y «RF-140: GET sin club consulta todos los adeudos del ámbito».
- `CobrosIT` · «RF-140: suma pendientes y vencidos por club y equipo, y excluye pagados y
  rechazados» y «H-29 criterios 2 y 3 · RF-140: la Directiva solo ve los cobros y adeudos de su
  club».
- `PantallaTesoreria.test.tsx` · «H-29.2: muestra el resumen, la tabla de cobros y el estado de cada
  uno» y «H-29.2: la pestaña de adeudos desglosa club y equipos»; `TarjetaAdeudos.test.tsx` ·
  «H-29.2: muestra el total, los cobros y los clubes con adeudo» y «H-29.2: sin adeudos lo indica y
  no muestra el vencido».

### CA-3 · La Directiva solo ve los cobros de su club

> La Directiva solo ve los cobros de su club.

**Pasos**

1. En la base local, insertar la asignación `DIRECTIVA_CLUB` con ámbito `CLUB` sobre Halcones.
2. Iniciar sesión como **Directiva** y abrir `/app/tesoreria`.
3. Con su token: `GET /cobros`, `GET /cobros?clubId=<Riviera>`, `GET /adeudos?clubId=<Riviera>` y
   `POST /cobros` sobre su propio club.

**Resultado esperado**

- La tabla muestra los **6 cobros de Halcones** (cobros 1 a 6: los 4 con adeudo, más el PAGADO y
  el RECHAZADO) y ninguno de Riviera; no aparece «Nuevo cobro» (no tiene
  `tesoreria.cobro:CREAR`).
- `GET /cobros` responde `totalElementos = 6`, todos de Halcones; el club ajeno da lista vacía con
  total 0 y adeudos en `0.00` sin clubes; el `POST` responde `403` con `codigo: "VAL-015"`.

**Resultado obtenido:** **Aprobado.** La pantalla mostró «6 cobros», todos de Club Halcones
Morelia (VENCIDO, PAGADO, PENDIENTE, RECHAZADO, PENDIENTE y PENDIENTE), el filtro de clubes solo
ofrece Halcones, la palabra «Riviera» no aparece en la página y no hay botón «Nuevo cobro» (0). La
API devolvió `totalElementos = 6` con un único club; con `clubId` de Riviera,
`{"contenido":[],…,"totalElementos":0}` y `{"total":0.00,…,"clubes":[]}`; el alta respondió `403`
con `codigo: "VAL-015"` y la base siguió con los mismos cobros.

**Evidencia:** [`CA3-asignacion-directiva.txt`](evidencias/H-29/CA3-asignacion-directiva.txt)
(sentencia y asignación vigente),
[`CA3-tesoreria-cobros-directiva.png`](evidencias/H-29/CA3-tesoreria-cobros-directiva.png),
[`CA3-respuesta-cobros-directiva.json`](evidencias/H-29/CA3-respuesta-cobros-directiva.json),
[`CA3-respuesta-cobros-directiva-club-ajeno.json`](evidencias/H-29/CA3-respuesta-cobros-directiva-club-ajeno.json),
[`CA3-respuesta-adeudos-directiva-club-ajeno.json`](evidencias/H-29/CA3-respuesta-adeudos-directiva-club-ajeno.json)
y [`CA3-respuesta-403-crear-cobro.json`](evidencias/H-29/CA3-respuesta-403-crear-cobro.json);
resumen en [`CA3-api-resumen.txt`](evidencias/H-29/CA3-api-resumen.txt)

**Pruebas automáticas relacionadas**

- `CobroServiceTest` · «RF-140: el listado usa el alcance del permiso y la liga activa» y «VAL-015:
  sin permiso de crear no escribe ni audita».
- `CobroControllerTest` · «RNF-003: seguridad de GET /api/v1/cobros» y «RNF-003: seguridad de POST
  /api/v1/cobros».
- `CobrosIT` · «H-29 criterios 2 y 3 · RF-140: la Directiva solo ve los cobros y adeudos de su
  club» y «VAL-015: la Directiva, que solo consulta, no puede crear cobros».
- `PantallaTesoreria.test.tsx` · «H-02.4: sin tesoreria.cobro:CREAR no ofrece el alta de cobros».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Un cobro con concepto, monto y periodo queda PENDIENTE | Aprobado |
| CA-2 Los adeudos se suman por club y equipo según el ámbito | Aprobado |
| CA-3 La Directiva solo ve los cobros de su club | Aprobado |

Pruebas automáticas de la misma versión:

- Backend: `./mvnw verify -Dtest='CobroServiceTest,CobroControllerTest,AdeudoServiceTest,
  AdeudoControllerTest' -Dit.test='CobrosIT'` → **BUILD FAILURE**. Pruebas unitarias y de
  controlador: **39/39** (`CobroServiceTest` 12, `CobroControllerTest` 17, `AdeudoServiceTest` 3 y
  `AdeudoControllerTest` 7). `CobrosIT` (PostgreSQL real en Testcontainers): **5/6**, por D-01 en
  `listadoFiltraYPagina`; se repitió `CobrosIT` sola y falló igual.
- Frontend: `npx vitest run src/features/tesoreria src/layouts src/app/router.test.tsx` → **72/72**
  pruebas en 7 archivos (`PantallaTesoreria.test.tsx` 14, `TarjetaAdeudos.test.tsx` 3,
  `fechas.test.ts` 3, `InternalLayout.test.tsx` 8, `PublicLayout.test.tsx` 3,
  `navegacion.test.ts` 11 y `router.test.tsx` 30).


## 5. Defectos encontrados

| ID | Defecto | Severidad | Afecta un criterio | Seguimiento |
| --- | --- | --- | --- | --- |
| D-01 | `CobrosIT.listadoFiltraYPagina` (línea 220) registra «Cuota 3» con `clubA` y `equipoB`, pero el escenario de apoyo crea `equipoB` en `clubB` (línea 258). El servicio responde correctamente `422 REFERENCIA_INVALIDA` («El equipo «Equipo B …» no pertenece al club indicado.»), así que la prueba se detiene antes de sus aserciones: `CobrosIT` queda en 5/6 y `./mvnw verify` termina en `BUILD FAILURE` desde #75 (`68a1d49`). El código de producción no tiene defecto; hay que corregir la prueba (un segundo equipo de `clubA`), no el servicio | Alta: `develop` está en rojo desde #75 e incumple la Definición de Terminado | No: los criterios funcionales se cumplen | DEF-05 (Jira por crear); corrección en un PR aparte |
| D-02 | El rol `TESORERO` no tiene `organizacion.equipo:CONSULTAR` en la matriz inicial (Apéndice E del ERS), así que F-33 no le deja cargar un cobro a un equipo: el modal «Nuevo cobro» muestra «Tu perfil no puede consultar el catálogo de equipos; el cobro se cargará al club.» y no ofrece equipos. La historia pide «cobros a clubes o equipos» | Media | No del texto literal del CA-1: el cobro al equipo (cobro 1, Halcones A) se creó por API con el token del Tesorero, que sí lo acepta | DEF-06 (Jira por crear); propuesta: una migración que agregue ese permiso de solo lectura al `TESORERO` |

**Reproducción de D-01:** `cd backend && ./mvnw verify -Dit.test='CobrosIT'` → `Tests run: 6,
Failures: 0, Errors: 1` y `BUILD FAILURE`:

```text
CobrosIT.listadoFiltraYPagina <<< ERROR!
ReglaNegocioException: El equipo «Equipo B …» no pertenece al club indicado.
  at CobroService.crear(CobroService.java:100)
  at CobrosIT.listadoFiltraYPagina(CobrosIT.java:220)
```

**Reproducción de D-02:** iniciar sesión como **Tesorero**, abrir `/app/tesoreria`, pulsar «Nuevo
cobro» y elegir un club: el formulario no muestra el campo de equipo
([`CA1-validacion-monto-cero.png`](evidencias/H-29/CA1-validacion-monto-cero.png)).

Ninguno de los dos se corrigió en esta tarea, como pide la prueba de aceptación.

## 6. Observaciones

1. **`POST /cobros` ignora `estado` y `tipo` en lugar de rechazarlos.** Con `"estado": "PAGADO"` o
   `"tipo": "MULTA"` la API respondió `201` y creó el cobro `MANUAL` `PENDIENTE`. El resultado es
   seguro y cumple el criterio, pero §6.13 dice que esos campos «no se aceptan», lo que también se
   puede leer como un `400`. Propuesta: ajustar la redacción de §6.13 («se ignoran») en H-29.4
   (SCRUM-400). Los dos cobros de prueba («Prueba campo estado» y «Prueba campo tipo», a Riviera)
   se crearon al final de la corrida, después de las evidencias de totales.
2. **Monto en la auditoría.** La pantalla envía el monto como número JSON (`350.5`) y el detalle de
   `REGISTRAR_COBRO` lo guarda igual (`"monto": 350.5`), mientras que la API responde `350.50` y la
   columna guarda `350.50`. El valor es el mismo; solo cambia la escala. Propuesta: normalizar la
   escala a dos decimales antes de auditar, en H-29.4.
3. **Fechas en inglés en dos capturas.** `CA1-modal-cobro-club.png` y `CA1-cobros-creados.png` se
   tomaron antes de fijar el idioma del navegador de Playwright, por eso sus campos de fecha vacíos
   dicen «mm/dd/yyyy»; es el locale del navegador de prueba, no de la aplicación. El resto usa
   «dd/mm/aaaa». Ninguna captura muestra correos, tokens ni contraseñas.
4. **Siembra de VENCIDO, PAGADO y RECHAZADO por SQL.** Hoy ningún flujo produce esos estados: el
   vencimiento es una tarea programada del backlog (B-31) y los pagos y rechazos llegan con
   H-30/H-31. Para comprobar que los adeudos suman solo PENDIENTE y VENCIDO, los cobros 3 a 8 se
   insertaron `PENDIENTE` como `postgres` y se cambiaron con `UPDATE`; el trigger
   `tg_cobros_estado` siguió activo y aceptó las transiciones PENDIENTE → VENCIDO/PAGADO/RECHAZADO.
   El cobro PAGADO no tiene una fila en `pagos` y la siembra no genera auditoría: solo sirve para
   este cálculo.
5. **La asignación de la Directiva se insertó con SQL**, igual que en H-06: la pantalla de
   asignaciones de roles (H-02) no crea esta asignación con ámbito de club desde la interfaz.
6. **Mensaje del 422 y regla de decimales del monto (H-29.4).** El `422 REFERENCIA_INVALIDA` por un
   equipo de otro club ya no incluye su nombre («El equipo indicado no pertenece al club.»), porque
   el permiso es configurable y el equipo puede estar fuera del ámbito de quien llama;
   [`CA1-respuesta-422-equipo-de-otro-club.txt`](evidencias/H-29/CA1-respuesta-422-equipo-de-otro-club.txt)
   conserva la respuesta de esta corrida. La escala del monto se valida una sola vez, con `@Digits`
   en `CrearCobroRequest`, igual que `numeric(12,2)` y que la pantalla: `1500.000` responde `400
   VALIDACION`, como `10.123`.

## 7. Conclusión

La historia **H-29 cumple funcionalmente sus tres criterios de aceptación** sobre `develop` en
`b377e0d` con frontend, backend y base de datos reales: un cobro manual nace `MANUAL` `PENDIENTE`
con su monto de dos decimales y su auditoría sin concepto, los adeudos suman solo PENDIENTE y
VENCIDO por club y equipo y cambian con el ámbito (3,050.75 para el Tesorero y 2,325.75 para la
Directiva, iguales en pantalla, API y SQL), y la Directiva solo ve los 6 cobros de su club y no
puede crearlos (`403 VAL-015`). La subtarea **H-29.3 queda terminada**, pero **H-29 sigue En curso**
por D-01 (DEF-05), que deja `./mvnw verify` en rojo, y D-02 (DEF-06), que impide al Tesorero cargar
cobros a un equipo desde F-33. Tras corregirlos, basta repetir la ejecución automática del §4 y el
alta a un equipo desde el modal.
