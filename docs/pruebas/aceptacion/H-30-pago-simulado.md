# Prueba de aceptación · H-30 Pagar un cobro en línea de forma simulada

| Campo | Valor |
| --- | --- |
| Historia | H-30 · subtarea H-30.3 |
| Requisitos | RF-138, VAL-028, RNF-004, DEC-37, ADR-0012 |
| Pantallas | F-34 (flujo móvil de pago simulado, abierto desde la tabla de cobros de F-33) |
| Versión probada | `develop` en `2b407ff`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000` y `20261106160000` |
| Fecha | 2026-11-07 |
| Ejecutó | Agente de IA (OpenCode) en el entorno local del equipo |
| Resultado global | **Aprobado (4 de 4 criterios)** |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17.11 local (contenedor Docker efímero `siglim-h303`, puerto 5433) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cinco migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que firma con ES256 los tokens de sesión y simula el inicio de sesión de Supabase Auth para la pantalla de acceso |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, contra la base local y el JWKS local (variables de entorno, sin tocar `backend/.env`) |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Microsoft Edge sin interfaz, controlado con Playwright, ventana de 390 × 844 px (móvil), idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase. El arnés (JWKS local, scripts de Playwright y SQL de preparación) vive fuera del
repositorio; aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva de club** | `DIRECTIVA_CLUB` · CLUB = «Club Halcones Morelia» | CA-1 a CA-4: es el actor de la historia y el rol que tiene `tesoreria.pago:PAGAR` en la matriz inicial (Apéndice E del ERS) |

La cuenta **Tesorero** (`TESORERO` · LIGA) no se usó en el recorrido manual: la matriz inicial le
da `tesoreria.pago:REGISTRAR` (pago externo de H-31), no `tesoreria.pago:PAGAR`. Su rechazo en el
pago en línea y el ámbito de la Directiva se comprueban en `PagoSimuladoIT` (ver CA-3).

Estructura sembrada en la base local: temporada **«Temporada de aceptación 2026-27» (ACTIVA)**,
categoría **Libre**, **Grupo 1**; **Club Halcones Morelia** con los equipos **Halcones A** y
**Halcones B**, y **Club Riviera Morelia** (club ajeno) con **Riviera F.C.** Todos los datos son
ficticios.

Cobros, todos `tipo = MANUAL`:

| # | Club | Equipo | Concepto | Monto | Estado inicial | Origen |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Halcones | Halcones A | Cuota de inscripción · pago aprobado | 1500.00 | PENDIENTE | SQL |
| 2 | Halcones | Halcones B | Cuota de arbitraje · rechazo y reintento | 275.25 | PENDIENTE | SQL |
| 3 | Halcones | Halcones A | Arbitraje jornada 1 · ya pagado | 400.00 | PAGADO (pago `EFECTIVO` de la semilla) | SQL |
| 4 | Halcones | Halcones A | Arbitraje jornada 2 · vencido | 200.00 | VENCIDO (límite 31 oct 2026) | SQL |
| 5 | Riviera | Riviera F.C. | Cuota de inscripción · club ajeno | 125.00 | PENDIENTE | SQL |

Los cobros 3 y 4 se sembraron por SQL porque hoy ningún flujo produce `PAGADO` por pago externo
(H-31) ni `VENCIDO` (vencimiento programado, B-31); los triggers `tg_pagos_validar` y
`tg_cobros_estado` siguieron activos. El cobro 3 tiene un pago `EFECTIVO` de la semilla y el cobro
5 pertenece a otro club, por lo que no aparece en la pantalla de la Directiva. Los pagos
`EN_LINEA_SIMULADO` que genera esta prueba son únicamente los de los cobros 1 y 2.

## 3. Escenarios

### CA-1 · Un cobro PENDIENTE queda PAGADO con un pago `EN_LINEA_SIMULADO`

> Dado un cobro PENDIENTE, cuando se aprueba la simulación, entonces queda PAGADO con un pago
> `EN_LINEA_SIMULADO`.

**Pasos**

1. Iniciar sesión como **Directiva de Halcones**, abrir `/app/tesoreria` y comprobar que la tabla
   solo muestra los cobros de su club y que el cobro 1 ofrece «Pagar en línea (simulado)».
2. Pulsar «Pagar en línea (simulado)» en el cobro 1 y revisar el paso 1 «Cobro a pagar»: concepto,
   destino, monto y el aviso de simulación.
3. Pulsar «Iniciar pago simulado» y capturar la referencia ficticia del paso 2.
4. Pulsar «Aprobar pago simulado» y revisar el paso 3 «Pago registrado» y su comprobante.
5. Cerrar el modal y comprobar el estado de la fila en la tabla.
6. Revisar en la base `siglim.cobros`, `siglim.pagos`, `siglim.intentos_pago` y la auditoría.

**Resultado esperado**

- El inicio responde `201` con una referencia `SIM-AAAAMMDD-XXXXXX` y estado `PENDIENTE`.
- La confirmación responde `200` con el intento `APROBADO` y `resueltoEn`.
- El cobro queda `PAGADO`, con un pago `medio = EN_LINEA_SIMULADO`, el monto del cobro y la misma
  referencia; la fila deja de ofrecer cualquier acción de pago.
- La auditoría registra `INICIAR_PAGO_SIMULADO` y `CONFIRMAR_PAGO_SIMULADO` con el cobro, la
  referencia, el resultado y el `pagoId`.

**Resultado obtenido:** **Aprobado.**

- La tabla mostró los cuatro cobros de Halcones y **no** el cobro 5 de Riviera. El cobro 1 ofrecía
  «Pagar en línea (simulado)».
- Paso 1: «Cobro a pagar», Club Halcones Morelia · Halcones A, «$1,500.00 MXN».
- Paso 2: referencia **`SIM-20261107-77D25V`**; la petición
  `POST /api/v1/cobros/c0b00000-0000-4000-8000-000000000031/intentos-pago` respondió `HTTP 201`
  con `"estado":"PENDIENTE"`.
- Paso 3: «Pago registrado» y «Comprobante de pago simulado» con la misma referencia; la
  confirmación `{"resultado":"APROBADO"}` respondió `HTTP 200` con `"estado":"APROBADO"` y
  `"resueltoEn":"2026-11-08T02:41:36.1291707Z"`.
- Al cerrar, la fila del cobro 1 mostró **Pagado** y **0** botones.
- Base: `siglim.pagos` con `EN_LINEA_SIMULADO`, `1500.00` y `SIM-20261107-77D25V`; el intento quedó
  `APROBADO`; dos eventos de auditoría `M-13`, el segundo con `monto`, `pagoId`, `cobroId`,
  `resultado: "APROBADO"`, `referencia` y `estadoCobro: "PAGADO"`.

**Evidencia:** [`CA1-cobros-antes.png`](evidencias/H-30/CA1-cobros-antes.png),
[`CA1-modal-paso1-cobro.png`](evidencias/H-30/CA1-modal-paso1-cobro.png),
[`CA1-modal-paso2-referencia.png`](evidencias/H-30/CA1-modal-paso2-referencia.png),
[`CA1-comprobante-aprobado.png`](evidencias/H-30/CA1-comprobante-aprobado.png),
[`CA1-cobros-despues.png`](evidencias/H-30/CA1-cobros-despues.png),
[`CA1-CA2-peticiones-api.txt`](evidencias/H-30/CA1-CA2-peticiones-api.txt) (peticiones y respuestas
crudas del navegador) y
[`CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt`](evidencias/H-30/CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt)
(salida real de SQL)

**Pruebas automáticas relacionadas**

- `PagoSimuladoServiceTest` · «H-30 criterio 1 · RF-138: aprobar crea el pago EN_LINEA_SIMULADO,
  audita y publica CobroPagado» y «RF-138: un cobro PENDIENTE inicia un intento PENDIENTE con
  referencia ficticia y lo audita».
- `IntentoPagoControllerTest` · «RF-138: POST del cobro responde 201 con Location y la referencia
  ficticia PENDIENTE» y «RF-138: confirmar APROBADO responde 200 con el intento resuelto y su
  fecha».
- `PagoSimuladoIT` · «H-30 criterio 1 · RF-138: la Directiva paga un cobro PENDIENTE y queda PAGADO
  con EN_LINEA_SIMULADO».
- `ModalPagoSimulado.test.tsx` · «H-30.2: recorre los tres pasos y muestra el comprobante al
  aprobar».

### CA-2 · Si se rechaza queda RECHAZADO y puede reintentarse

> Si se rechaza, queda RECHAZADO y puede reintentarse.

**Pasos**

1. En el cobro 2, abrir el flujo y pulsar «Iniciar pago simulado».
2. Pulsar «Rechazar pago simulado» y revisar el comprobante del rechazo.
3. Cerrar el modal y comprobar el estado de la fila y la acción que ofrece.
4. Abrir de nuevo el flujo, pulsar «Reintentar pago simulado» y capturar la referencia nueva.
5. Aprobar el reintento y comprobar el estado final.
6. Revisar en la base los tres intentos, los pagos y la auditoría.

**Resultado esperado**

- La confirmación `RECHAZADO` responde `200`, no crea pago y deja el cobro `RECHAZADO`.
- La fila muestra **Rechazado** y la acción «Reintentar pago».
- El reintento vuelve a empezar en el paso 1, genera una **referencia nueva** y, al aprobarlo, deja
  el cobro `PAGADO` con un solo pago `EN_LINEA_SIMULADO`.
- El intento rechazado se conserva y la auditoría registra las dos secuencias completas.

**Resultado obtenido:** **Aprobado.**

- Primer intento: referencia **`SIM-20261107-D7HKYS`**; la confirmación
  `{"resultado":"RECHAZADO"}` respondió `HTTP 200` con `"estado":"RECHAZADO"`. El comprobante mostró
  «El pago fue rechazado» y «La simulación no generó ningún cargo. El cobro quedó rechazado y puede
  reintentarse.».
- Al cerrar, la fila mostró **Rechazado** y el botón **Reintentar pago** (1).
- Reintento: el paso 1 ofreció «Reintentar pago simulado»; el paso 2 generó
  **`SIM-20261107-0GBPAJ`**, distinta de la rechazada; la aprobación respondió `HTTP 200` y la fila
  quedó **Pagado** sin acciones.
- Base: **3** intentos (`APROBADO` 77D25V, `RECHAZADO` D7HKYS y `APROBADO` 0GBPAJ) y **2** pagos
  `EN_LINEA_SIMULADO` (1500.00 y 275.25). La auditoría del rechazo no tiene `monto` ni `pagoId`; la
  del reintento registra `estadoAnterior: "RECHAZADO"` y el nuevo `pagoId`.

**Evidencia:** [`CA2-comprobante-rechazado.png`](evidencias/H-30/CA2-comprobante-rechazado.png),
[`CA2-cobros-rechazado.png`](evidencias/H-30/CA2-cobros-rechazado.png),
[`CA2-modal-reintento-referencia.png`](evidencias/H-30/CA2-modal-reintento-referencia.png),
[`CA2-comprobante-reintento-aprobado.png`](evidencias/H-30/CA2-comprobante-reintento-aprobado.png),
[`CA2-cobros-despues.png`](evidencias/H-30/CA2-cobros-despues.png),
[`CA1-CA2-peticiones-api.txt`](evidencias/H-30/CA1-CA2-peticiones-api.txt) y
[`CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt`](evidencias/H-30/CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt)

**Pruebas automáticas relacionadas**

- `PagoSimuladoServiceTest` · «H-30 criterio 2 · RF-138: rechazar deja el cobro RECHAZADO, sin pago
  ni evento» y «ERS §4.3.5: un cobro RECHAZADO vuelve a PENDIENTE al iniciar un nuevo intento».
- `PagoSimuladoIT` · «H-30 criterio 2 · RF-138: rechazar deja RECHAZADO y un nuevo intento permite
  aprobar».
- `ModalPagoSimulado.test.tsx` · «H-30.2: al rechazar muestra el comprobante y permite reintentar».

### CA-3 · Un cobro pagado o vencido no se paga en línea (VAL-028)

> Un cobro pagado o vencido no se paga en línea (VAL-028).

**Pasos**

1. En la tabla de tesorería, comprobar los cobros 3 (PAGADO) y 4 (VENCIDO): estado visible y
   ausencia de acciones.
2. Con el token de la Directiva, ejecutar `POST /api/v1/cobros/{id}/intentos-pago` sobre el cobro 3
   y sobre el cobro 4, y guardar la petición y la respuesta crudas.
3. Revisar en la base que no se creó ningún intento ni pago para esos cobros.

**Resultado esperado**

- La interfaz muestra **Pagado** y **Vencido** y no ofrece «Pagar en línea» ni «Reintentar pago».
- La API responde `409` con `codigo: "VAL-028"` y un `detail` que explica el estado del cobro.
- `siglim.intentos_pago` no tiene filas para los cobros 3 y 4.

**Resultado obtenido:** **Aprobado.**

- La interfaz mostró **Pagado** y **Vencido**, con **0** botones en ambas filas.
- API: los dos `POST` respondieron **`HTTP 409`** con `Content-Type: application/problem+json` y
  `"codigo":"VAL-028"`:
  - Cobro 3: «El cobro está PAGADO; no se puede pagar en línea.».
  - Cobro 4: «El cobro está VENCIDO; no se puede pagar en línea. Comunícate con Tesorería.».
- Base: **0** intentos de pago y **0** pagos para los cobros 3 y 4. El cobro 5 (Riviera) siguió
  `PENDIENTE` y con **0** pagos: la Directiva de Halcones no lo ve ni lo paga.

**Evidencia:** [`CA3-sin-accion-pagado-vencido.png`](evidencias/H-30/CA3-sin-accion-pagado-vencido.png),
[`CA3-respuesta-409-pagado.txt`](evidencias/H-30/CA3-respuesta-409-pagado.txt),
[`CA3-respuesta-409-vencido.txt`](evidencias/H-30/CA3-respuesta-409-vencido.txt) y
[`CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt`](evidencias/H-30/CA1-CA2-CA3-base-cobros-intentos-pagos-auditoria.txt)

**Pruebas automáticas relacionadas**

- `PagoSimuladoServiceTest` · «VAL-028: un cobro PAGADO o VENCIDO no inicia pago en línea y explica
  su estado», «H-30 criterio 3 · VAL-028: un cobro VENCIDO no se paga en línea», «VAL-028: un cobro
  ya PAGADO no admite otra confirmación» y «VAL-028: un intento ya APROBADO o RECHAZADO no se
  confirma otra vez».
- `IntentoPagoControllerTest` · «VAL-028: el inicio de un cobro PAGADO o VENCIDO responde 409» y
  «VAL-028 y CONCURRENCIA: los errores del servicio llegan como 409 en RFC 9457».
- `PagoSimuladoIT` · «H-30 criterio 3 · VAL-028: un cobro PAGADO no inicia otro pago en línea ni se
  confirma dos veces», «H-30 criterio 3 · VAL-028: un cobro VENCIDO no se paga en línea, pero sí lo
  registra Tesorería» y «VAL-015 y VAL-024: el Tesorero no paga en línea y la Directiva de otro club
  no ve el cobro».
- `PantallaTesoreria.test.tsx` · «H-30.2: ofrece pagar solo los cobros pendientes o rechazados».

### CA-4 · La pantalla muestra «Pago simulado: no se realiza ningún cargo»

> La pantalla muestra «Pago simulado: no se realiza ningún cargo».

**Pasos**

1. Recorrer los tres pasos de CA-1 (cobro 1) y los tres de CA-2 (rechazo), más los tres del
   reintento de CA-2, comprobando el texto exacto en cada paso.
2. Registrar la hora y el paso en que aparece el aviso.
3. Revisar la consola del navegador durante toda la corrida.

**Resultado esperado**

- El texto exacto **«Pago simulado: no se realiza ningún cargo»** está visible en los tres pasos de
  cada flujo, incluido el comprobante.
- La corrida no produce errores de la aplicación en la consola.

**Resultado obtenido:** **Aprobado.** El aviso apareció en **9 de 9** pasos (CA-1 pasos 1-3, CA-2
pasos 1-3 y reintento pasos 1-3), con el texto exacto y el subtítulo «Entorno demostrativo de la
Liga. No se piden datos bancarios ni se afecta ninguna cuenta real.». La corrida terminó con **0**
errores de consola de la aplicación (el único 404 es el `favicon` que `index.html` no declara; ver
Observación 4).

**Evidencia:** [`CA4-aviso-pago-simulado.txt`](evidencias/H-30/CA4-aviso-pago-simulado.txt),
[`CA1-modal-paso1-cobro.png`](evidencias/H-30/CA1-modal-paso1-cobro.png),
[`CA1-modal-paso2-referencia.png`](evidencias/H-30/CA1-modal-paso2-referencia.png),
[`CA1-comprobante-aprobado.png`](evidencias/H-30/CA1-comprobante-aprobado.png),
[`CA2-comprobante-rechazado.png`](evidencias/H-30/CA2-comprobante-rechazado.png),
[`CA2-modal-reintento-referencia.png`](evidencias/H-30/CA2-modal-reintento-referencia.png),
[`CA2-comprobante-reintento-aprobado.png`](evidencias/H-30/CA2-comprobante-reintento-aprobado.png) y
[`resumen-ejecucion.txt`](evidencias/H-30/resumen-ejecucion.txt) (31 comprobaciones, 31 correctas)

**Pruebas automáticas relacionadas**

- `ModalPagoSimulado.test.tsx` · los cinco escenarios de H-30.2 comprueban el aviso y el flujo; el
  texto también está en el componente `AvisoSimulado` de `ModalPagoSimulado.tsx`.

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Un cobro PENDIENTE queda PAGADO con un pago `EN_LINEA_SIMULADO` | Aprobado |
| CA-2 Si se rechaza queda RECHAZADO y puede reintentarse | Aprobado |
| CA-3 Un cobro pagado o vencido no se paga en línea (VAL-028) | Aprobado |
| CA-4 La pantalla muestra «Pago simulado: no se realiza ningún cargo» | Aprobado |

Pruebas automáticas de la misma versión:

- Backend: `./mvnw verify` → **BUILD SUCCESS**. Pruebas unitarias y de controlador: **1043/1043**.
  Pruebas de integración (PostgreSQL real en Testcontainers): **171 ejecutadas, 0 fallos, 0 errores,
  1 omitida** (`AlmacenamientoDocumentosAwsIT`, desactivada por defecto: requiere
  `-Dsiglim.s3.pruebaReal=true` y credenciales de AWS). `PagoSimuladoIT`: **8/8**, incluye los
  cuatro escenarios de los criterios 1 a 3 y los de concurrencia, idempotencia, permisos y ámbito.
- Frontend: `npm run lint` sin hallazgos; `npm test` → **467/467** pruebas en **61** archivos;
  `npm run build` → **`✓ built`**.

## 5. Defectos encontrados

Ninguno. Los cuatro criterios se cumplen y las verificaciones automáticas de backend y frontend
terminan correctamente.

## 6. Observaciones

1. **El actor es la Directiva de club.** La historia lo define así y la matriz inicial solo le da
   `tesoreria.pago:PAGAR` a `DIRECTIVA_CLUB` con ámbito de club; el `TESORERO` tiene
   `tesoreria.pago:REGISTRAR` (pago externo de H-31). No es un defecto: el pago en línea y el
   registro en ventanilla son acciones distintas.
2. **Estados PAGADO y VENCIDO sembrados por SQL.** Ningún flujo produce todavía un cobro `VENCIDO`
   (vencimiento programado, B-31) ni un `PAGADO` por pago externo (H-31). Para probar VAL-028 se
   insertaron los cobros 3 y 4 y se cambió su estado con los triggers activos; el cobro 3 tiene un
   pago `EFECTIVO` de la semilla. Solo los pagos de los cobros 1 y 2 son `EN_LINEA_SIMULADO` de
   esta corrida.
3. **La referencia usa la fecha de negocio.** La corrida ocurrió a las 20:41 de `America/Mexico_City`
   (02:41 UTC del día siguiente) y la referencia generada fue `SIM-20261107-…`, con la fecha local
   de la Liga. Es el comportamiento esperado y no afecta el criterio.
4. **404 del `favicon`.** `frontend/index.html` no declara un `favicon`, así que Edge registra un
   `404` por `/favicon.ico` en la primera carga. No es un error de la aplicación ni de la prueba;
   [`resumen-ejecucion.txt`](evidencias/H-30/resumen-ejecucion.txt) lo cuenta aparte de los errores
   de consola (0).
5. **La interfaz y la API coinciden en VAL-028.** La tabla oculta la acción para `PAGADO` y
   `VENCIDO`, pero la prueba también llamó a la API directamente: ambas capas rechazan el pago. La
   UI no es la única barrera.
6. **Nada se borra.** El intento rechazado de CA-2 se conserva junto con el aprobado del reintento;
   la corrección de estado no elimina historial (RF-044, RN-005).
7. **La segunda confirmación de un intento ya resuelto** responde `409` y no duplica el pago; se
   cubre en `PagoSimuladoServiceTest`, `IntentoPagoControllerTest` y `PagoSimuladoIT`, no en el
   recorrido manual, porque la interfaz cierra el flujo al terminar.

## 7. Conclusión

La historia **H-30 cumple sus cuatro criterios de aceptación** sobre `develop` en `2b407ff` con
frontend, backend y base de datos reales: un cobro `PENDIENTE` pasa a `PAGADO` con un pago
`EN_LINEA_SIMULADO` y su auditoría; un rechazo deja el cobro `RECHAZADO` sin pago y el reintento
genera una referencia nueva y lo deja `PAGADO`; un cobro `PAGADO` o `VENCIDO` no se paga en línea
(`409 VAL-028`) ni desde la interfaz ni desde la API; y el aviso «Pago simulado: no se realiza
ningún cargo» aparece en los nueve pasos recorridos. La corrida terminó con 31 comprobaciones
correctas, 0 fallos y 0 errores de consola de la aplicación. La subtarea **H-30.3 queda terminada**
y no quedan defectos abiertos para H-30.
