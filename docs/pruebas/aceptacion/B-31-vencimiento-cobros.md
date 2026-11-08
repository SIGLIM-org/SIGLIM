# Prueba de aceptación · B-31 Vencer cobros no pagados

| Campo | Valor |
| --- | --- |
| Historia | B-31 · subtarea B-31.3 · Sprint 2 · Could · Low · 2 SP |
| Requisitos | RF-141, VAL-028, ERS §4.3.5, DEC-60, ADR-0009 |
| Pantallas | F-33 (tesorería: cobros y adeudos) y F-34 (pago en línea) |
| Versión probada | `develop` en `9c6a62f` (incluye B-31.1 #92 y los ajustes de F-33/F-34 de B-31.2 #93), con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000` y `20261106160000` |
| Fecha | 2026-11-07 |
| Ejecutó | Geovani con un agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios) |

Alcance refinado de B-31: los cobros no pagados se marcan como vencidos al terminar su plazo. Quedan
**fuera de esta subtarea** la notificación al club (depende de H-25, Sprint 4) y el efecto en la baja
por falta de pago (B-22), como indica la historia refinada para el Sprint 2.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17.11 local en un contenedor Docker efímero (puerto 55432), con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cinco migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica una llave ES256 y responde el inicio de sesión que usa el frontend; las cuentas se sembraron en `auth.users` y `siglim.perfiles` |
| Backend | `java -jar target/siglim-0.1.0-SNAPSHOT.jar` en `http://localhost:8080`, Java 25.0.5.0.3 y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://127.0.0.1:5173` (Vite 8.3.5, proxy hacia el backend) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la simulación
de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de Supabase. El
arnés (JWKS local, datos de siembra y recorrido de Playwright) vive fuera del repositorio; aquí solo
se versionan este documento y sus evidencias.

La tarea de vencimiento se dejó habilitada (`siglim.tareas.vencimiento-cobros.habilitada=true`).
Corrió al arrancar el backend, como está documentado en DEC-60, y volvió a correr al reiniciarlo
para comprobar la idempotencia. El cron diario de las 00:05 de México no se esperó: es el mismo
método `vencer()` que se ejecutó al arrancar.

## 2. Cuenta y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Tesorero** | `TESORERO` · LIGA | `tesoreria.cobro:CONSULTAR` y `tesoreria.pago:REGISTRAR` |
| **Directiva** | `DIRECTIVA_CLUB` · Club Halcones B-31 | `tesoreria.cobro:CONSULTAR` y `tesoreria.pago:PAGAR`; **sin** `tesoreria.pago:REGISTRAR` |

Estructura sembrada en la base local: liga del sistema, club **Club Halcones B-31** y siete cobros
manuales del club. Todos los datos son ficticios. Los cobros se insertaron por SQL como `postgres`
antes de arrancar el backend; **ningún cambio de estado se hizo por SQL**.

| # | Concepto | Fecha límite | Monto | Estado al iniciar | Estado al terminar |
| --- | --- | --- | --- | --- | --- |
| 1 | Cuota de inscripción temporada 2026-27 | 2026-11-06 | 1,500.00 | PENDIENTE | **VENCIDO** |
| 2 | Arbitraje jornada 1 | 2026-11-07 (hoy) | 725.00 | PENDIENTE | PENDIENTE |
| 3 | Uso de cancha para entrenamiento | 2026-11-08 | 350.50 | PENDIENTE | PENDIENTE |
| 4 | Cuota atrasada desde octubre | 2026-10-28 | 900.00 | PENDIENTE | **VENCIDO** |
| 5 | Cobro ya pagado | 2026-11-01 | 100.00 | PAGADO | PAGADO |
| 6 | Cobro rechazado | 2026-11-01 | 200.00 | RECHAZADO | RECHAZADO |
| 7 | Cobro vencido de una corrida anterior | 2026-11-01 | 300.00 | VENCIDO | VENCIDO |

Antes de arrancar, la base no tenía ningún evento `VENCER_COBRO`: los siete cobros conservaban el
estado de la siembra ([`CA1-estado-antes.txt`](evidencias/B-31/CA1-estado-antes.txt)).

## 3. Escenarios

### CA-1 · Un cobro PENDIENTE con fecha límite vencida pasa a VENCIDO una sola vez aunque la tarea se repita

> Dado un cobro PENDIENTE con fecha límite vencida, cuando corre la tarea programada, entonces pasa a
> VENCIDO una sola vez aunque la tarea se repita.

**Pasos**

1. Sembrar los siete cobros de la tabla anterior y comprobar en la base que los cobros 1 y 4 están
   en **PENDIENTE** y que no existe ningún `VENCER_COBRO`.
2. Arrancar el backend con la tarea habilitada y esperar a que `/actuator/health` responda `UP`.
3. Consultar en la base el estado de los siete cobros y los eventos `VENCER_COBRO`.
4. Reiniciar el backend para que la tarea vuelva a ejecutarse al arrancar.
5. Consultar de nuevo los estados y contar los eventos `VENCER_COBRO`.

**Resultado esperado**

- En la primera ejecución, los cobros 1 y 4 pasan a **VENCIDO**; los demás conservan su estado.
- Hay **dos** eventos `VENCER_COBRO` (uno por cada cobro que cambió), con el perfil **Sistema**, el
  motivo «vencimiento automático por fecha límite» y el detalle con `estadoAnterior`, `estadoNuevo`,
  `fechaLimite` y `monto`.
- En la segunda ejecución no cambia ningún estado ni se crea otro evento: el total sigue en **2**.

**Resultado obtenido:** **Aprobado.** La primera ejecución corrió al arrancar el
`2026-11-08 03:22:10.621654+00` (7 de noviembre, 21:22 en México). Los cobros 1 y 4 quedaron
**VENCIDO**; los cobros 2, 3, 5, 6 y 7 conservaron su estado. Se registraron exactamente **dos**
eventos `VENCER_COBRO`, uno por el cobro 4 y otro por el cobro 1, ambos con
`perfil_id = a0000000-0000-4000-8000-000000000002` (**Sistema**). El reinicio del backend volvió a
ejecutar `vencer()` sin vencer nada: los siete estados siguieron igual, los dos eventos conservaron
su marca de tiempo y el total de `VENCER_COBRO` siguió en **2**.

El cobro 4, con fecha límite diez días antes, confirma además que la tarea se pone al corriente
cuando estuvo apagada en la fecha del cron.

**Evidencia:** [`CA1-estado-antes.txt`](evidencias/B-31/CA1-estado-antes.txt) (siembra y cero
auditorías), [`CA1-estado-despues.txt`](evidencias/B-31/CA1-estado-despues.txt) (estados y las dos
auditorías de la primera ejecución), [`CA1-reinicio-idempotente.txt`](evidencias/B-31/CA1-reinicio-idempotente.txt)
(segunda ejecución con los mismos dos eventos) y
[`CA1-tesoreria-vencidos.png`](evidencias/B-31/CA1-tesoreria-vencidos.png).

**Pruebas automáticas relacionadas**

- `VencimientoCobrosIT` · «RF-141 · DEC-60: un cobro PENDIENTE con fecha límite de ayer queda VENCIDO
  y auditado por el Sistema», «ADR-0009 §2: ejecutar dos veces seguidas vence una sola vez y deja una
  sola auditoría», «ADR-0009 §3: un cobro que venció hace 10 días también se vence en la primera
  ejecución» y «RF-141: publica un CobroVencido por cada cobro vencido y ninguno si no hubo cambios».
- `VencimientoCobrosServiceTest` · «RF-141 · ADR-0009: audita y publica un evento por cada cobro
  vencido, con el motivo fijo antes del cambio» y «ADR-0009 §2: si no vence ningún cobro, no audita
  ni publica eventos».

### CA-2 · El cobro vencido aparece como tal en tesorería y en los adeudos del club

> Un cobro vencido aparece como tal en tesorería y en los adeudos del club.

**Pasos**

1. Iniciar sesión como **Tesorero** y abrir `/app/tesoreria`.
2. Revisar la tabla de cobros y filtrar por estado **Vencido**.
3. Abrir la pestaña **Adeudos por club** y revisar el adeudo del Club Halcones B-31.
4. Consultar `GET /api/v1/adeudos` con el token del Tesorero.
5. Iniciar sesión como **Directiva** y revisar la misma pantalla desde el ámbito del club.

**Resultado esperado**

- En tesorería, los cobros 1 y 4 aparecen **VENCIDO** (y el 7 ya lo estaba); el filtro **Vencido**
  devuelve los tres cobros vencidos.
- En **Adeudos por club**, el Club Halcones B-31 muestra el total de $3,775.50, con $1,075.50
  pendiente y **$2,700.00 vencido**.
- `GET /api/v1/adeudos` devuelve `total: 3775.5`, `pendiente: 1075.5`, `vencido: 2700` y `cobros: 5`,
  con el desglose del club.
- La Directiva, en su ámbito de club, también ve los cobros **VENCIDO** y el adeudo vencido.

**Resultado obtenido:** **Aprobado.** En F-33, el Tesorero vio los cobros 1, 4 y 7 con la insignia
**VENCIDO** y la tabla completa; el filtro **Vencido** mostró «3 cobros con los filtros aplicados», y
el `GET /api/v1/cobros?pagina=0&tamano=20&estado=VENCIDO` respondió `200` con `totalElementos: 3`.
En la pestaña **Adeudos por club** apareció «1 club con adeudo · 5 cobros por cobrar», con el Club
Halcones B-31, «ADEUDO DEL CLUB $3,775.50», «PENDIENTE $1,075.50» y «VENCIDO $2,700.00». El
`GET /api/v1/adeudos` respondió `200` con `total: 3775.5`, `pendiente: 1075.5`, `vencido: 2700`,
`cobros: 5` y el club con `vencido: 2700`. La Directiva vio los mismos tres cobros **VENCIDO** en su
ámbito de club.

**Evidencia:** [`CA2-filtro-vencido.png`](evidencias/B-31/CA2-filtro-vencido.png),
[`CA2-respuesta-cobros-vencidos.json`](evidencias/B-31/CA2-respuesta-cobros-vencidos.json),
[`CA2-adeudos-por-club.png`](evidencias/B-31/CA2-adeudos-por-club.png),
[`CA2-adeudos-tesorero.json`](evidencias/B-31/CA2-adeudos-tesorero.json) y
[`CA1-tesoreria-vencidos.png`](evidencias/B-31/CA1-tesoreria-vencidos.png).

**Pruebas automáticas relacionadas**

- `PantallaTesoreria.test.tsx` · «H-29.2: muestra el resumen, la tabla de cobros y el estado de cada
  uno» y «H-29.2: aplica club, tipo, estado y rango de fecha límite».
- `TarjetaAdeudos.test.tsx` · «H-29.2: muestra el total, los cobros y los clubes con adeudo».
- `CobrosIT` · «H-29 criterios 2 y 3 · RF-140: la Directiva solo ve los cobros y adeudos de su club»
  y «RF-140: suma pendientes y vencidos por club y equipo, y excluye pagados y rechazados».
- `AdeudoControllerTest` · «RF-140: GET devuelve totales y desglose por club y equipo con dos
  decimales».

### CA-3 · Un cobro vencido no se paga en línea (VAL-028)

> Un cobro vencido no se paga en línea (VAL-028).

**Pasos**

1. Iniciar sesión como **Directiva**, que tiene `tesoreria.pago:PAGAR` pero no
   `tesoreria.pago:REGISTRAR`.
2. Abrir `/app/tesoreria` y revisar las acciones de los cobros **VENCIDO** y de los **PENDIENTE**.
3. Con el token de la Directiva, enviar
   `POST /api/v1/cobros/c1000000-0000-4000-8000-000000000001/intentos-pago` para intentar el pago en
   línea de un cobro **VENCIDO**.
4. Comprobar el estado del cobro y que no se creó un intento ni un pago.

**Resultado esperado**

- La interfaz no ofrece **Pagar en línea (simulado)** en los cobros **VENCIDO**; muestra «Vencido:
  comunícate con Tesorería». Los cobros **PENDIENTE** sí ofrecen el pago simulado.
- La API responde `409` con `codigo: "VAL-028"` y un mensaje que indica que el cobro está VENCIDO y
  que debe comunicarse con Tesorería.
- El cobro sigue **VENCIDO**, con **0** intentos de pago y **0** pagos.

**Resultado obtenido:** **Aprobado.** En F-33, la Directiva vio los cobros 1, 4 y 7 en **VENCIDO** con
el texto «Vencido: comunícate con Tesorería» y **sin** el botón de pago en línea; los cobros 2 y 3,
**PENDIENTE**, mostraron «Pagar en línea (simulado)». El `POST` al intento de pago del cobro 1
respondió **409** con el cuerpo:

```json
{
  "detail": "El cobro está VENCIDO; no se puede pagar en línea. Comunícate con Tesorería.",
  "instance": "/api/v1/cobros/c1000000-0000-4000-8000-000000000001/intentos-pago",
  "status": 409,
  "title": "Conflicto con el estado actual",
  "type": "https://siglim.mx/errores/val-028",
  "codigo": "VAL-028"
}
```

El cobro siguió **VENCIDO**, con `intentos_pago = 0` y `pagos = 0`. F-34 no se abre desde un cobro
VENCIDO porque la acción no está disponible; el rechazo del intento por la API es la garantía de
VAL-028. El comportamiento de F-34 para los cobros pagables está cubierto por las pruebas de B-31.2.

**Evidencia:** [`CA3-directiva-vencido.png`](evidencias/B-31/CA3-directiva-vencido.png),
[`CA3-respuesta-val-028.json`](evidencias/B-31/CA3-respuesta-val-028.json) y
[`CA3-sin-intento.txt`](evidencias/B-31/CA3-sin-intento.txt).

**Pruebas automáticas relacionadas**

- `VencimientoCobrosIT` · «VAL-028: un cobro vencido por la tarea no se paga en línea, pero sí admite
  el pago externo».
- `PantallaTesoreria.test.tsx` · «B-31.2: con PAGAR sin REGISTRAR, el cobro vencido no se paga en
  línea y remite a Tesorería».
- `ModalPagoSimulado.test.tsx` · «B-31.2: un 409 VAL-028 al confirmar muestra el detalle, refresca y
  ya no ofrece aprobar ni rechazar».

### CA-4 · El cambio queda auditado con el usuario Sistema

> El cambio queda auditado con el usuario Sistema.

**Pasos**

1. Después de la primera ejecución de la tarea, consultar en la base los eventos `VENCER_COBRO`.
2. Revisar el perfil que ejecutó el cambio, el módulo, la entidad, el motivo y el detalle.
3. Confirmar que el perfil es el **Sistema** (`es_sistema = true`) y que no se registran datos
   personales.

**Resultado esperado**

- Hay un evento `VENCER_COBRO` por cada cobro que pasó de PENDIENTE a VENCIDO.
- El evento lleva `perfil_id` del **Sistema**, módulo `M-13`, entidad `cobros`, el `entidad_id` del
  cobro, el motivo «vencimiento automático por fecha límite» y el detalle con `estadoAnterior`,
  `estadoNuevo`, `fechaLimite` y `monto`.
- No se guardan correos, nombres ni otros datos personales en el detalle.

**Resultado obtenido:** **Aprobado.** La consulta devolvió **dos** eventos `VENCER_COBRO`, ambos con
`perfil_id = a0000000-0000-4000-8000-000000000002`, `perfil = Sistema`, `es_sistema = true`, módulo
`M-13`, entidad `cobros`, motivo «vencimiento automático por fecha límite» y el detalle de estados,
fecha límite y monto. El total de `VENCER_COBRO` fue **2**. El detalle no contiene datos personales.

**Evidencia:** [`CA4-auditoria-sistema.txt`](evidencias/B-31/CA4-auditoria-sistema.txt).

**Pruebas automáticas relacionadas**

- `VencimientoCobrosIT` · «RF-141 · DEC-60: un cobro PENDIENTE con fecha límite de ayer queda VENCIDO
  y auditado por el Sistema».
- `VencimientoCobrosServiceTest` · «RF-141 · ADR-0009: audita y publica un evento por cada cobro
  vencido, con el motivo fijo antes del cambio» y «ADR-0009 punto 4: el cambio se hace como el perfil
  Sistema y la sesión se retira al terminar».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 PENDIENTE con fecha vencida pasa a VENCIDO una sola vez aunque la tarea se repita | Aprobado |
| CA-2 El cobro vencido aparece como tal en tesorería y en los adeudos del club | Aprobado |
| CA-3 El cobro vencido no se paga en línea (VAL-028) | Aprobado |
| CA-4 El cambio queda auditado con el usuario Sistema | Aprobado |

Pruebas automáticas de la misma versión:

- Backend: `./mvnw verify` → **BUILD SUCCESS**. Surefire: **1100** pruebas, 0 fallos, 0 errores, 0
  omitidas; Failsafe: **186** pruebas, 0 fallos, 0 errores, **1 omitida**
  (`AlmacenamientoDocumentosAwsIT`, que requiere AWS real). En tesorería, `VencimientoCobrosIT`
  **8/8**, `VencimientoCobrosServiceTest` **7/7**, `EjecutorSistemaIT` **1/1** y
  `EjecutorSistemaTest` **7/7**.
- Frontend: `npm run lint && npm test && npm run build` → **sin errores**. `oxlint` no reportó
  hallazgos; `vitest` pasó **500/500** pruebas en **62** archivos; `tsc -b && vite build` terminó
  correctamente. `PantallaTesoreria.test.tsx` pasó **33/33** y `ModalPagoSimulado.test.tsx` **8/8**.

## 5. Defectos encontrados

| ID | Defecto | Severidad | Afecta un criterio | Seguimiento |
| --- | --- | --- | --- | --- |
| — | Ninguno | — | — | — |

## 6. Observaciones

1. **Alcance de RF-141.** B-31 marca como vencidos los cobros no pagados y deja la transición
   auditada; el aviso al club (H-25) y la baja por falta de pago (B-22) están **fuera del alcance** de
   esta subtarea, como indica la historia refinada. El evento `CobroVencido` se publica para que H-25
   lo consuma cuando exista. No es un defecto de B-31.
2. **Regla de vencimiento [PROPUESTA].** DEC-60 fija `fecha_limite < hoy` en
   `America/Mexico_City`: el día límite todavía se puede pagar y el cobro vence a partir del día
   siguiente. Sigue pendiente de confirmar con el PO; la prueba confirma la regla implementada, no
   una regla oficial de la Liga.
3. **La tarea corrió al arrancar, no a las 00:05.** Para no esperar al cron, la prueba usó la
   ejecución de `ApplicationReadyEvent` que DEC-60 agregó para ponerse al corriente. El cron diario
   llama al mismo `vencer()`.
4. **Los cobros no se sembraron VENCIDOS por SQL.** Los cobros 1 y 4 estaban PENDIENTE y los puso
   VENCIDOS la tarea; la auditoría conserva el perfil Sistema y la marca de tiempo, así que la
   evidencia no se puede confundir con una siembra.
5. **El cobro 7 ya estaba VENCIDO.** Se sembró así para comprobar que la tarea no vuelve a auditar
   un cobro que ya estaba vencido; por eso hay tres cobros VENCIDO en la pantalla y solo dos eventos
   `VENCER_COBRO`.
6. **Dos cuentas para dos vistas.** El Tesorero ve «Registrar pago» en los vencidos porque RF-139 le
   permite el pago externo; la Directiva ve «Vencido: comunícate con Tesorería» porque no tiene
   `tesoreria.pago:REGISTRAR` y el pago en línea está prohibido para VENCIDO (VAL-028). Son dos
   vistas del mismo estado, no una contradicción.
7. **F-34 no se recorre desde un cobro VENCIDO.** La acción de pago en línea no se muestra, así que
   el flujo no se puede abrir; el rechazo `409 VAL-028` de la API es la garantía del criterio. El
   flujo de F-34 para cobros pagables está cubierto por las pruebas de B-31.2.
8. **Escala de los montos en los JSON.** Los JSON de evidencia se generaron serializando la
   respuesta, así que `3775.5`, `1075.5` y `2700` pierden los ceros finales; la API y la pantalla
   conservan dos decimales (`$3,775.50`, `$1,075.50` y `$2,700.00`). No es un defecto de la
   aplicación.

## 7. Conclusión

La historia **B-31 cumple sus cuatro criterios de aceptación** sobre `develop` en `9c6a62f`, con
frontend, backend y PostgreSQL reales: la tarea vence una sola vez los cobros PENDIENTE con fecha
límite vencida y se pone al corriente, el cobro vencido aparece en tesorería y en los adeudos del
club, no se paga en línea (VAL-028) y el cambio queda auditado con el perfil Sistema. La subtarea
**B-31.3 queda terminada** y **B-31 puede pasar a Finalizado**: B-31.1 (#92) y los ajustes de
F-33/F-34 de B-31.2 (#93) ya están en `develop`. Las observaciones de las secciones anteriores no
bloquean la historia; el aviso al club y la baja por falta de pago son dependencias explícitas de
H-25 y B-22.
