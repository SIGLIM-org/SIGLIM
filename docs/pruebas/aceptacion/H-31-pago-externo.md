# Prueba de aceptación · H-31 Registrar un pago recibido fuera del sistema con su comprobante

| Campo | Valor |
| --- | --- |
| Historia | H-31 · subtarea H-31.3 |
| Requisitos | RF-139, VAL-028, RNF-018 |
| Pantallas | F-33 (tesorería: cobros y modal «Registrar pago») |
| Versión probada | `develop` en `9644ef5`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000` y `20261106160000` |
| Fecha | 2026-11-07 |
| Ejecutó | López Meza Leonardo (QA) con un agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado (3 de 3 criterios)** |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local (contenedor Docker efímero) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cinco migraciones del repositorio aplicadas |
| Almacenamiento | S3Mock local en `http://localhost:9090`, bucket privado `siglim-documentos-aceptacion` |
| Autenticación | Servidor JWKS local que firma con ES256 los tokens de sesión y simula el inicio de sesión de Supabase Auth para la pantalla de acceso |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, contra la base local, el JWKS local y S3Mock (variables de entorno, sin tocar `backend/.env`) |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium sin interfaz, controlado con Playwright, ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase. El arnés (JWKS local, S3Mock, scripts de Playwright y SQL de preparación) vive fuera del
repositorio; aquí solo se versionan este documento y sus evidencias.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Tesorero** | `TESORERO` · LIGA | CA-1, CA-2 y CA-3 (`tesoreria.cobro:CONSULTAR` y `tesoreria.pago:REGISTRAR`) |

Estructura sembrada en la base local: temporada **«Temporada de aceptación 2026-27» (ACTIVA)**,
categoría **Libre**, **Grupo 1**, y **Club Halcones Morelia** con el equipo **Halcones A**. Todos
los datos son ficticios.

Cobros, todos `tipo = MANUAL` y del club Halcones Morelia:

| # | Concepto | Periodo | Límite | Monto | Estado al iniciar | Origen |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Cuota de inscripción temporada 2026-27 | desde 1 nov 2026 | 20 nov 2026 | 1500.00 | PENDIENTE | SQL |
| 2 | Uso de cancha para entrenamiento | desde 1 oct 2026 | 5 nov 2026 | 350.50 | VENCIDO | SQL y vencimiento automático (B-31.1) |
| 3 | Arbitraje jornada 1 | desde 1 nov 2026 | 20 nov 2026 | 725.00 | PENDIENTE | SQL |

Los tres cobros se insertaron por SQL como `postgres`; el cobro 2 quedó **VENCIDO** porque el
servicio programado de vencimiento (B-31.1) lo transicionó al arrancar el backend, con la auditoría
`VENCER_COBRO` y el motivo «vencimiento automático por fecha límite». No se cambió de estado por
SQL.

Total de adeudos al iniciar: **2,575.50** (pendiente 2,225.00 y vencido 350.50), como mostró la
pantalla y confirmó la base.

El comprobante usado en ambos pagos es un PDF mínimo válido de **218 bytes**
(`comprobante-aceptacion-H31.pdf`), con el mismo contenido en los dos envíos.

## 3. Escenarios

### CA-1 · Un cobro pendiente o vencido queda PAGADO al registrar el pago con comprobante

> Dado un cobro pendiente o vencido, cuando se registra el pago con comprobante, entonces queda
> PAGADO.

**Pasos**

1. Iniciar sesión como **Tesorero**, abrir `/app/tesoreria` y comprobar los tres cobros y sus
   estados (PENDIENTE, VENCIDO y PENDIENTE).
2. En el cobro **VENCIDO** («Uso de cancha para entrenamiento»), pulsar «Registrar pago», elegir
   medio **Transferencia**, fecha de pago `2026-11-06`, referencia `SPEI-98432170`, adjuntar el PDF
   y pulsar «Registrar pago», capturando la petición y la respuesta crudas.
3. En el cobro **PENDIENTE** («Cuota de inscripción temporada 2026-27»), pulsar «Registrar pago»,
   elegir medio **Efectivo**, dejar vacíos la fecha y la referencia, adjuntar el PDF y confirmar.
4. Revisar la pantalla, la base de datos (cobros, pagos, documentos y auditoría) y el bucket.

**Resultado esperado**

- Ambos registros responden `201` con `Location: /api/v1/cobros/{id}/pagos` y el pago con su medio,
  monto total, fecha, referencia (si se envió) y los metadatos del comprobante, sin datos de S3.
- Los dos cobros quedan **PAGADO**, con **un** pago y **un** documento cada uno; la auditoría
  registra `REGISTRAR_PAGO` con el estado anterior y sin URL, bucket, clave, hash ni referencia.
- El comprobante se guarda en el bucket privado como `application/pdf`, y la pantalla confirma el
  pago y actualiza el resumen de adeudos.
- Con `fechaPago`, el pago queda al inicio de ese día en `America/Mexico_City`; sin ella, en el
  momento del registro.

**Resultado obtenido:** **Aprobado.**

- **Antes:** la pantalla mostró «$2,575.50 · 3 cobros por cobrar · Vencido: $350.50», con «Uso de
  cancha para entrenamiento» en **VENCIDO** y los otros dos en **PENDIENTE**. La auditoría de la
  base confirmó que el VENCIDO lo produjo el servicio programado B-31.1 a las `2026-11-08
  02:38:52+00` (7 de noviembre, 20:38 en México), no una sentencia SQL.
- **Pago del VENCIDO:** el modal mostró «Importe a liquidar **$350.50** MXN» y el aviso «Se registra
  el pago total; el cobro quedará PAGADO y no admitirá otro pago.». El `POST` respondió **201** con
  `Location: /api/v1/cobros/c0000000-0000-4000-8000-000000000002/pagos` y el cuerpo crudo incluye
  `"medio":"TRANSFERENCIA","monto":350.50,"referencia":"SPEI-98432170","pagadoEn":"2026-11-06T06:00:00Z"`
  (cuerpo completo en la evidencia). El pago quedó con `monto = 350.50`, `pagadoEn` al inicio del
  6 de noviembre en la zona de negocio y `comprobante = {documentoId: 20ea…, tipoMime:
  application/pdf, tamanoBytes: 218}`. La pantalla confirmó «Pago de $350.50 registrado para «Uso
  de cancha para entrenamiento»; el cobro quedó pagado.» y el resumen bajó a «$2,225.00 · 2 cobros
  por cobrar · Vencido: $0.00».
- **Pago del PENDIENTE:** el modal mostró «Importe a liquidar **$1,500.00** MXN». El `POST` respondió
  **201** con `Location: /api/v1/cobros/c0000000-0000-4000-8000-000000000001/pagos`, `medio =
  EFECTIVO`, `monto = 1500.00`, sin `referencia` y con `pagadoEn = 2026-11-08T02:41:25.20042Z`
  (momento del registro, porque no se envió `fechaPago`). La pantalla confirmó «Pago de $1,500.00
  registrado para «Cuota de inscripción temporada 2026-27»; el cobro quedó pagado.» y el resumen
  bajó a «$725.00 · 1 cobro por cobrar · Vencido: $0.00».
- **Base de datos:** los dos cobros quedaron **PAGADO**, con **1** pago y **1** comprobante cada uno;
  los documentos son `COMPROBANTE_PAGO`, `application/pdf`, de 218 bytes, con
  `s3_bucket = siglim-documentos-aceptacion` y una clave distinta por pago. La auditoría registró
  **dos** eventos `REGISTRAR_PAGO` en `M-13`, entidad `pagos`, con `estadoAnterior` **VENCIDO** y
  **PENDIENTE**, `documentoId`, `tipoMime` y `tamanoBytes`; el detalle no incluye URL, bucket,
  clave, hash ni referencia.
- **S3:** el bucket privado quedó con **2** objetos, uno por pago, de 218 bytes,
  `Content-Type: application/pdf` y `HEAD 200`. La respuesta de la API no expone `s3Bucket`,
  `s3Clave` ni URL.

**Evidencia:** [`CA1-tesoreria-antes.png`](evidencias/H-31/CA1-tesoreria-antes.png),
[`CA1-modal-pago-vencido.png`](evidencias/H-31/CA1-modal-pago-vencido.png),
[`CA1-pago-vencido-registrado.png`](evidencias/H-31/CA1-pago-vencido-registrado.png),
[`CA1-modal-pago-pendiente.png`](evidencias/H-31/CA1-modal-pago-pendiente.png),
[`CA1-pago-pendiente-registrado.png`](evidencias/H-31/CA1-pago-pendiente-registrado.png),
[`CA1-peticiones-observadas.txt`](evidencias/H-31/CA1-peticiones-observadas.txt) (peticiones y
respuestas crudas),
[`CA1-respuesta-pago-vencido.json`](evidencias/H-31/CA1-respuesta-pago-vencido.json) y
[`CA1-respuesta-pago-pendiente.json`](evidencias/H-31/CA1-respuesta-pago-pendiente.json),
[`CA1-pagos-documentos-auditoria.txt`](evidencias/H-31/CA1-pagos-documentos-auditoria.txt) (salida
real de SQL, incluida la auditoría del vencimiento automático) y
[`CA1-s3-objetos.txt`](evidencias/H-31/CA1-s3-objetos.txt) (listado y `HEAD` de los objetos).

**Pruebas automáticas relacionadas**

- `PagoServiceTest` · «RF-139: registra el pago total con comprobante, en este orden: sube, inserta
  pago y documento, audita», «RNF-018: el detalle de auditoría no lleva URL, bucket, clave, hash ni
  referencia», «RF-139: con fechaPago, el pago queda al inicio de ese día en America/Mexico_City;
  sin referencia, nula» y «RF-139: la fecha de hoy en la zona de negocio se acepta».
- `PagoControllerTest` · «RF-139: POST multipart responde 201 con Location, los metadatos del
  comprobante y sin datos de S3».
- `PagosExternosIT` · «H-31 criterio 1 · RF-139: el TESORERO paga un cobro PENDIENTE y uno VENCIDO;
  quedan PAGADO con comprobante y auditoría».
- `PantallaTesoreria.test.tsx` · «H-31.2: registra el pago con el monto del cobro y confirma sin
  fecha ni referencia» y «H-31.2: envía la fecha del pago y la referencia recortada».

### CA-2 · El pago debe cubrir el monto total del cobro

> El pago debe cubrir el monto total (RF-139).

**Pasos**

1. Con el token de la sesión del **Tesorero**, enviar por
   `POST /api/v1/cobros/c0000000-0000-4000-8000-000000000003/pagos` un `multipart/form-data` con
   `medio = EFECTIVO`, `monto = 100.00` (el cobro es de 725.00) y el PDF, y capturar la respuesta.
2. Revisar el estado del cobro, los pagos, comprobantes, eventos de auditoría y objetos del bucket.
3. En la pantalla, abrir «Registrar pago» del cobro 3 y comprobar el importe que muestra el modal y
   si permite editar el monto.

**Resultado esperado**

- La API responde `422` con `codigo: "RF-139"` y un mensaje con el monto total esperado.
- El cobro sigue **PENDIENTE**, sin pago, comprobante, auditoría ni objeto nuevo en S3.
- La pantalla muestra el importe total y no ofrece pagos parciales.

**Resultado obtenido:** **Aprobado.** La API respondió **422** con el cuerpo
`{"detail":"El pago debe cubrir el monto total del cobro (725.00 MXN).","instance":"/api/v1/cobros/c0000000-0000-4000-8000-000000000003/pagos","status":422,"title":"No se puede procesar","type":"https://siglim.mx/errores/rf-139","codigo":"RF-139"}`.
El cobro siguió **PENDIENTE** con 725.00; la base quedó con **0** pagos, **0** comprobantes y **0**
eventos `REGISTRAR_PAGO` para ese cobro, y el bucket siguió con los **2** objetos de los pagos
válidos. En la pantalla, el modal de «Arbitraje jornada 1» muestra «Importe a liquidar **$725.00**
MXN» y el aviso «Se registra el pago total; el cobro quedará PAGADO y no admitirá otro pago.»; no
hay campo para editar el monto, así que la interfaz no puede enviar un pago parcial.

**Evidencia:** [`CA2-modal-importe-total.png`](evidencias/H-31/CA2-modal-importe-total.png),
[`CA2-respuesta-422-monto-distinto.json`](evidencias/H-31/CA2-respuesta-422-monto-distinto.json) y
[`CA2-cobro-sin-pago.txt`](evidencias/H-31/CA2-cobro-sin-pago.txt) (estado, conteos y bucket).

**Pruebas automáticas relacionadas**

- `PagoServiceTest` · «RF-139: un monto distinto del cobro responde 422 con el monto esperado, sin
  subir el comprobante» y «RF-139: el monto se compara por valor (1500 equivale a 1500.00)».
- `PagoControllerTest` · «VAL-028 y RF-139: los errores del servicio llegan como 409 y 422 en RFC
  9457».
- `PagosExternosIT` · «H-31 criterio 2 · RF-139: un monto distinto responde 422, el cobro sigue
  PENDIENTE y no se sube nada».
- `PantallaTesoreria.test.tsx` · «H-31.2: registra el pago con el monto del cobro y confirma sin
  fecha ni referencia» (la interfaz envía siempre el monto total del cobro).

### CA-3 · Un cobro pagado no admite otro pago

> Un cobro pagado no admite otro pago (VAL-028).

**Pasos**

1. Después del CA-1, con el token del **Tesorero**, enviar un segundo
   `POST /api/v1/cobros/c0000000-0000-4000-8000-000000000002/pagos` con `medio = EFECTIVO`,
   `monto = 350.50` y otro PDF.
2. Revisar la respuesta, los conteos de pagos, comprobantes y auditoría, y el bucket.
3. En la pantalla, revisar los dos cobros **PAGADO** y contar las acciones «Registrar pago».

**Resultado esperado**

- La API responde `409` con `codigo: "VAL-028"` y un mensaje que indica el estado del cobro.
- No se crea un segundo pago, comprobante, evento de auditoría ni objeto en S3.
- La pantalla no ofrece «Registrar pago» en los cobros pagados.

**Resultado obtenido:** **Aprobado.** La API respondió **409** con el cuerpo
`{"detail":"El cobro está PAGADO; no admite otro pago.","instance":"/api/v1/cobros/c0000000-0000-4000-8000-000000000002/pagos","status":409,"title":"Conflicto con el estado actual","type":"https://siglim.mx/errores/val-028","codigo":"VAL-028"}`.
La base conservó **1** pago, **1** comprobante y **1** evento `REGISTRAR_PAGO` por cada cobro
pagado, y el bucket siguió con **2** objetos. En la pantalla, los dos cobros **PAGADO** no muestran
el botón «Registrar pago» (**0** botones); solo el cobro **PENDIENTE** de 725.00 conserva su acción.

**Evidencia:** [`CA3-tesoreria-pagados-sin-accion.png`](evidencias/H-31/CA3-tesoreria-pagados-sin-accion.png),
[`CA3-respuesta-409-segundo-pago.json`](evidencias/H-31/CA3-respuesta-409-segundo-pago.json),
[`CA3-un-solo-pago.txt`](evidencias/H-31/CA3-un-solo-pago.txt) y
[`CA3-acciones-en-pagados.txt`](evidencias/H-31/CA3-acciones-en-pagados.txt) (conteo de botones y
estados visibles).

**Pruebas automáticas relacionadas**

- `PagoServiceTest` · «VAL-028: un cobro PAGADO o RECHAZADO responde 409 con su estado, sin subir el
  comprobante».
- `PagoControllerTest` · «VAL-028 y RF-139: los errores del servicio llegan como 409 y 422 en RFC
  9457».
- `PagosExternosIT` · «H-31 criterio 3 · VAL-028: un segundo pago al mismo cobro responde 409 y no
  se duplica», «VAL-028: un cobro RECHAZADO no admite pago y sigue RECHAZADO» y «VAL-028: si dos
  pagos se cruzan, el trigger rechaza el segundo y TraductorErroresBd responde 409».
- `PantallaTesoreria.test.tsx` · «H-31.2: con REGISTRAR, solo los cobros PENDIENTE y VENCIDO ofrecen
  «Registrar pago»» y «VAL-028: un 409 muestra el detalle en el modal y refresca los cobros».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Un cobro pendiente o vencido queda PAGADO al registrar el pago con comprobante | Aprobado |
| CA-2 El pago debe cubrir el monto total del cobro | Aprobado |
| CA-3 Un cobro pagado no admite otro pago | Aprobado |

Pruebas automáticas de la misma versión:

- Backend: `./mvnw verify` → **BUILD SUCCESS**. Surefire: **1100** pruebas, 0 fallos, 0 errores, 0
  omitidas; Failsafe: **186** pruebas, 0 fallos, 0 errores, **1 omitida**
  (`AlmacenamientoDocumentosAwsIT`, que requiere AWS real). En tesorería: `PagoServiceTest`
  **16/16**, `PagoControllerTest` **18/18** y `PagosExternosIT` **7/7**.
- Frontend: `npm run lint && npm test && npm run build` → **sin errores**. `oxlint` no reportó
  hallazgos; `vitest` pasó **500/500** pruebas en **62** archivos; `tsc -b && vite build` terminó
  correctamente.

## 5. Defectos encontrados

| ID | Defecto | Severidad | Afecta un criterio | Seguimiento |
| --- | --- | --- | --- | --- |
| — | Ninguno | — | — | — |

## 6. Observaciones

1. **Escala del monto en los archivos `.json`.** Los `.json` de evidencia se generaron parseando y
   volviendo a serializar la respuesta en Node, así que `monto` aparece como `350.5` y `1500` (los
   números pierden los ceros finales). El cuerpo crudo de la API en
   [`CA1-peticiones-observadas.txt`](evidencias/H-31/CA1-peticiones-observadas.txt) conserva
   `350.50` y `1500.00`, y la base y la pantalla muestran dos decimales. No es un defecto de la
   aplicación.
2. **Fecha en el campo nativo del navegador.** En las capturas, el campo de fecha vacío muestra
   `mm/dd/yyyy` y el valor del pago por transferencia se ve `11/06/2026`, porque el Chromium sin
   interfaz renderiza el control nativo con el locale del sistema aunque la página esté en `es-MX`.
   El valor enviado fue `2026-11-06` y el pago quedó en `2026-11-06T06:00:00Z`, es decir, el 6 de
   noviembre en `America/Mexico_City`; no es un defecto de la aplicación. Es la misma observación de
   H-29.
3. **El estado VENCIDO lo produjo B-31.1.** El cobro 2 no se sembró VENCIDO por SQL: el servicio
   programado de vencimiento (B-31.1) lo transicionó al arrancar el backend porque su límite
   (2026-11-05) ya había pasado. La evidencia incluye la auditoría `VENCER_COBRO` con el motivo y la
   fecha límite, así que la prueba también confirma la dependencia entre B-31.1 y H-31.
4. **Mismo PDF en los dos pagos.** Se usó el mismo archivo de 218 bytes en ambos registros; por eso
   los dos documentos tienen el mismo `sha256`, pero cada pago tiene su propio `documentoId` y su
   propia clave en S3. No hay reutilización ni duplicación de documentos.
5. **Enlace temporal al comprobante.** La respuesta expone `documentoId`, `tipoMime` y
   `tamanoBytes`, pero no `s3Bucket`, `s3Clave` ni URL (RNF-018). El enlace firmado para ver el
   comprobante queda pendiente para el Sprint 3 (DEC-56) y no forma parte de los criterios de H-31.
6. **Historial de pagos.** No se recorrió manualmente `GET /cobros/{id}/pagos` porque no es uno de
   los tres criterios de H-31; el contrato de H-31.1 lo cubre con `PagoControllerTest` y
   `PagosExternosIT` (RF-140).

## 7. Conclusión

La historia **H-31 cumple sus tres criterios de aceptación** sobre `develop` en `9644ef5`, con
frontend, backend y PostgreSQL reales, y S3Mock local con el mismo contrato de S3: un cobro PENDIENTE
y uno VENCIDO quedan **PAGADO** al registrar el pago con su comprobante, con un pago y un documento
por cobro, la auditoría `REGISTRAR_PAGO` y el objeto en el bucket privado; un monto distinto responde
**422 RF-139** sin escribir nada; y un segundo pago responde **409 VAL-028** sin duplicar pagos,
documentos, auditoría ni objetos. La pantalla refleja los estados y retira la acción de los cobros
pagados.

No se encontraron defectos. La subtarea **H-31.3 queda terminada** y **H-31 puede pasar a
Finalizado**: H-31.1 (#86) y H-31.2 (#90) ya están en `develop`. Las observaciones de las secciones
anteriores no bloquean la historia; el enlace firmado al comprobante es una limitación conocida del
Sprint 3.
