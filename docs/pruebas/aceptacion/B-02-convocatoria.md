# Prueba de aceptación · B-02 Asociar la convocatoria de una temporada

| Campo | Valor |
| --- | --- |
| Historia | B-02 (SCRUM-233) · subtarea B-02.3 (SCRUM-383) |
| Requisitos | RF-002, RF-003, RF-093, RN-002, VAL-015 |
| Pantallas | F-10 (gestión de temporadas) |
| Versión probada | `feature/B-02.3-aceptacion-convocatoria` en `ed2d79d`, con el mismo código de `develop` en `1455acd` y las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-06 |
| Ejecutó | Agente de IA (Codebuff) en el entorno local del equipo, con navegador |
| Resultado global | **Aprobado** (4 de 4 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 18.6 local en un clúster efímero (puerto 55432), con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica una llave ES256 y responde a los endpoints de Supabase Auth que usa el frontend; las cuentas se crearon en `auth.users` y se sembraron con SQL para la verificación de permisos |
| Backend | `spring-boot` en `http://localhost:8080`, Java 25.0.4 (JBR) y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5 y proxy hacia el backend) |
| Navegador | Brave 157.0.8103.39 (Chromium 157) sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px, zona `America/Mexico_City` y locale `es-MX` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para esta corrida: no se alteró el proyecto compartido de Supabase. El navegador recorrió F-10 de punta a punta con cada cuenta: inicio de sesión, localización de la temporada en la tabla, apertura del formulario, captura y guardado, y verificación del mensaje y de la fila. Las respuestas de la API que se muestran en las capturas de los criterios CA-3 y CA-4 se pidieron **desde esa misma sesión del navegador** con el token de la persona logueada y se muestran en la pestaña; el estado HTTP aparece en la propia imagen.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `temporadas.temporada:EDITAR`, `temporadas.parametros:EDITAR` y `temporadas.interpretacion:REGISTRAR` (LIGA); **sin** `temporadas.convocatoria:EDITAR` |
| **Secretaría** | Secretario de la Liga · Liga | `temporadas.convocatoria:EDITAR` y `temporadas.temporada:CONSULTAR` (LIGA); **sin** `temporada:EDITAR` |
| **Programador** | Programador de partidos · Liga | `temporadas.temporada:CONSULTAR` (solo lectura) |

El Administrador creó la estructura semilla; la Secretaría asoció y reemplazó la convocatoria; el Programador se usó para el caso de denegación.

Datos creados durante la corrida:

- Categoría **«Primera»** (`9d12d269…`), tipo `INFANTIL`.
- Temporada **«Temporada B-02 de aceptacion»** (`27a1639c…`), del 1 de diciembre de 2026 al 30 de junio de 2027, en estado `ACTIVA` al ejecutar los criterios.
- Categoría-temporada (`60e23918…`) y grupo **«Grupo 1»** (`f9e4d7c6…`) para activar la temporada (RF-100, RF-004, RF-005).
- Parámetros deportivos de la temporada, con bloque de cancha ≥ duración de juego: la validación de `VALIDACION` rechaza `bloqueCanchaMin < duracionJuegoMin` ([`parametros.json`](evidencias/B-02/parametros.json)) y la activación sin parámetros responde `409 RF-100` ([`CA3-activacion.json`](evidencias/B-02/CA3-activacion.json)).
- **Convocatoria «Convocatoria B-02»** (`fa4742ef…`): asociada, reemplazada por «Convocatoria B-02 actualizada» y, al cerrar la temporada, quedó en solo lectura.

## 3. Escenarios

### CA-1 · La Secretaría asocia la convocatoria de una temporada activa

> Dada una temporada activa sin convocatoria, cuando la Secretaría la asocia desde F-10, entonces la temporada muestra la convocatoria y su fecha de asociación, y la operación se audita.

**Pasos**

1. En F-10, con **Secretaría** logueada, localizar la fila de «Temporada B-02 de aceptacion» y pulsar el botón **Convocatoria** (IEEE 830 §2.3: la Secretaría gestiona la convocatoria de la temporada).
2. En el formulario, capturar la referencia «Convocatoria B-02», la versión «1.0» y el enlace `https://liga.mx/convocatoria-b02.pdf`.
3. Guardar y revisar el mensaje de confirmación, la fila de la temporada y la respuesta de la API `PUT /api/v1/temporadas/{id}/convocatoria`.
4. Consultar la temporada y verificar que la convocatoria aparece en la respuesta y en la auditoría del módulo M-02.

**Resultado esperado**

- El `PUT` responde `200` con `referencia`, `version` y `documentoUrl`; la convocatoria queda asociada a la temporada (una sola fila en `siglim.convocatorias`).
- La fila de F-10 muestra la convocatoria asociada y la fecha; sin convocatoria mostraría «Sin asociar».
- La auditoría registra `ASOCIAR_CONVOCATORIA` con la referencia, versión y enlace, sin borrar la anterior porque era la primera asociación.

**Resultado obtenido:** **Aprobado.** Antes de guardar, la fila mostraba «Sin asociar» y el botón **Convocatoria**. El `PUT` respondió `200` con `{"id":"fa4742ef-dcb7-43d7-af2c-bc06d3ec8f22","temporadaId":"27a1639c-573b-46ea-8413-7c00299d98b7","referencia":"Convocatoria B-02","version":"1.0","documentoUrl":"https://liga.mx/convocatoria-b02.pdf","creadoEn":"2026-11-06T18:58:22.364568-06:00"}`. La pantalla mostró el aviso «Convocatoria de «Temporada B-02 de aceptacion» asociada.» y la fila pasó a «Convocatoria B-02», «Versión 1.0» y «Asociada el 6 nov 2026». La consulta a la temporada devolvió la convocatoria con `referencia: Convocatoria B-02`, `version: 1.0` y `creadoEn` con la fecha de asociación. La auditoría registró un `ASOCIAR_CONVOCATORIA` con `version: 1.0`, `referencia: Convocatoria B-02` y `documentoUrl: https://liga.mx/convocatoria-b02.pdf`.

**Evidencia**

- [`CA1-f10-sin-convocatoria.png`](evidencias/B-02/CA1-f10-sin-convocatoria.png) — F-10 con la temporada activa y «Sin asociar».
- [`CA1-formulario.png`](evidencias/B-02/CA1-formulario.png) — formulario de la convocatoria capturado desde la fila.
- [`CA1-convocatoria-asociada.png`](evidencias/B-02/CA1-convocatoria-asociada.png) — aviso de confirmación y fila con la convocatoria y su fecha.
- [`CA1-asociar.json`](evidencias/B-02/CA1-asociar.json) — respuesta del `PUT` de asociación.
- [`CA1-consulta.json`](evidencias/B-02/CA1-consulta.json) — consulta de la temporada con la convocatoria asociada.
- [`auditoria.txt`](evidencias/B-02/auditoria.txt) — evento `ASOCIAR_CONVOCATORIA` del alta.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-002: asocia la convocatoria, recorta los textos y audita con la temporada».
- `TemporadaControllerTest` · «RF-002: PUT asocia la convocatoria y responde la referencia guardada».
- `ConvocatoriasIT` · «B-02.1: el Secretario asocia y reemplaza la convocatoria y queda en la temporada».
- `PantallaTemporadas.test.tsx` · «B-02.2: el Secretario asocia la convocatoria sin permiso de editar la temporada» (frontend, B-02.2).

### CA-2 · La Secretaría reemplaza la convocatoria sin duplicar

> Dada una temporada con convocatoria asociada, cuando la Secretaría la reemplaza, entonces la convocatoria conserva su id, la anterior queda en la auditoría como `referenciaAnterior`/`versionAnterior`/`documentoUrlAnterior`, y la fila de F-10 muestra la nueva.

**Pasos**

1. Con la temporada ya asociada en CA-1, pulsar **Convocatoria** de nuevo y capturar la referencia «Convocatoria B-02 actualizada» y la versión «2.0» (sin enlace, para probar la omisión).
2. Guardar y revisar el mensaje, la respuesta del `PUT` y la fila de la temporada.
3. Consultar la auditoría del módulo M-02 y revisar que el reemplazo conserva los datos de la convocatoria anterior.

**Resultado esperado**

- El formulario llega prellenado con la convocatoria vigente y su fecha de asociación.
- El `PUT` responde `200` con el **mismo id** de la convocatoria, la nueva referencia y versión, y sin `documentoUrl` (porque no se envió).
- `siglim.convocatorias` tiene una sola fila para la temporada (no duplica).
- La auditoría registra un segundo `ASOCIAR_CONVOCATORIA` con `referenciaAnterior`, `versionAnterior` y `documentoUrlAnterior` de la convocatoria que se reemplazó.

**Resultado obtenido:** **Aprobado.** El formulario abrió prellenado con «Convocatoria B-02», «1.0», el enlace y la fecha de asociación, y el aviso de que al guardar se reemplaza la referencia anterior. El `PUT` respondió `200` con el mismo `id: fa4742ef-dcb7-43d7-af2c-bc06d3ec8f22`, `referencia: Convocatoria B-02 actualizada` y `version: 2.0`, sin `documentoUrl`. La pantalla mostró «Convocatoria de «Temporada B-02 de aceptacion» reemplazada.» y la fila quedó con la nueva referencia y «Versión 2.0». La auditoría registró `ASOCIAR_CONVOCATORIA` con `versionAnterior: 1.0`, `referenciaAnterior: Convocatoria B-02` y `documentoUrlAnterior: https://liga.mx/convocatoria-b02.pdf`, sobre la misma fila de `siglim.convocatorias`.

**Evidencia**

- [`CA2-formulario-prellenado.png`](evidencias/B-02/CA2-formulario-prellenado.png) — formulario prellenado con la convocatoria vigente.
- [`CA2-convocatoria-reemplazada.png`](evidencias/B-02/CA2-convocatoria-reemplazada.png) — aviso de reemplazo y fila con la versión 2.0.
- [`CA2-reemplazar.json`](evidencias/B-02/CA2-reemplazar.json) — respuesta del `PUT` de reemplazo.
- [`auditoria.txt`](evidencias/B-02/auditoria.txt) — evento `ASOCIAR_CONVOCATORIA` del reemplazo con los datos anteriores.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-093: al reemplazar, la auditoría conserva la convocatoria anterior» y «RF-002: los campos opcionales vacíos se guardan en nulo».
- `ConvocatoriasIT` · «B-02.1: el Secretario asocia y reemplaza la convocatoria y queda en la temporada» (verifica el mismo id y una sola fila).
- `PantallaTemporadas.test.tsx` · «B-02.2: reemplaza la convocatoria y vaciar un campo opcional lo borra» y «B-02.2: muestra la convocatoria asociada y el formulario llega prellenado».

### CA-3 · La convocatoria de una temporada cerrada es de solo lectura

> Dada una temporada cerrada, cuando se intenta modificar su convocatoria, entonces la API responde `409 RF-003` y la operación no se lleva a cabo.

**Pasos**

1. Como **Administrador**, cerrar la temporada «Temporada B-02 de aceptacion» con motivo «Cierre de temporada por finalizacion del periodo de aceptacion B-02».
2. Verificar que la temporada pasa a `CERRADA`, que la convocatoria sigue asociada y que F-10 ya no ofrece la acción de convocatoria.
3. Como **Secretaría**, intentar reemplazar la convocatoria de la temporada cerrada.
4. Revisar la respuesta y la fila de la temporada.

**Resultado esperado**

- El cierre responde con la temporada en `CERRADA`.
- La fila de F-10 muestra el estado «CERRADA», la leyenda «Solo lectura» y ninguna acción de escritura, con la convocatoria que quedó asociada.
- El `PUT` sobre la convocatoria responde `409` con `codigo: "RF-003"` y el detalle indica que la temporada está cerrada y la convocatoria es de solo lectura.
- `siglim.convocatorias` no registra una nueva operación de asociación.

**Resultado obtenido:** **Aprobado.** Desde el modal de cierre, el Administrador capturó el motivo y el cierre respondió con `estado: CERRADA` en la respuesta de la temporada. La fila mostró «CERRADA», «Convocatoria B-02 actualizada», «Versión 2.0» y «Solo lectura», y el botón **Convocatoria** desapareció. Con la Secretaría, la misma fila se mostró en solo lectura y el `PUT` respondió `409` con `{"detail":"La temporada «Temporada B-02 de aceptacion» está cerrada: su convocatoria es de solo lectura.","instance":"/api/v1/temporadas/27a1639c-573b-46ea-8413-7c00299d98b7/convocatoria","status":409,"title":"Conflicto con el estado actual","type":"https://siglim.mx/errores/rf-003","codigo":"RF-003"}`. La auditoría no registró una nueva asociación.

**Evidencia**

- [`CA3-modal-cierre.png`](evidencias/B-02/CA3-modal-cierre.png) — cierre de la temporada con el motivo capturado.
- [`CA3-temporada-cerrada.png`](evidencias/B-02/CA3-temporada-cerrada.png) — F-10 con la temporada `CERRADA`, la convocatoria conservada y «Solo lectura».
- [`CA3-secretaria-solo-lectura.png`](evidencias/B-02/CA3-secretaria-solo-lectura.png) — la misma temporada vista por la Secretaría, sin acción de convocatoria.
- [`CA3-respuesta-409.png`](evidencias/B-02/CA3-respuesta-409.png) — respuesta `409 RF-003` pedida desde la sesión de la Secretaría y mostrada en el navegador.
- [`CA3-cierre.txt`](evidencias/B-02/CA3-cierre.txt) — temporada cerrada con la convocatoria asociada.
- [`CA3-cerrada-409.txt`](evidencias/B-02/CA3-cerrada-409.txt) — respuesta `409 RF-003` sobre la convocatoria cerrada.
- [`auditoria.txt`](evidencias/B-02/auditoria.txt) — evento `CERRAR_TEMPORADA` con el estado anterior y el nuevo.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-003: una temporada cerrada no admite cambios en su convocatoria».
- `TemporadaControllerTest` · «RF-003: una temporada cerrada conserva el código del conflicto en el PUT».
- `ConvocatoriasIT` · «RF-003: una temporada cerrada no admite convocatoria».
- `PantallaTemporadas.test.tsx` · «RF-003: si el servidor rechaza la convocatoria, conserva el modal y explica la causa» y «H-04 criterio 3: una temporada cerrada es de solo lectura y no ofrece acciones».

### CA-4 · Sin el permiso de convocatoria la operación se rechaza con 403

> Dado un rol que no tiene `temporadas.convocatoria:EDITAR`, cuando intenta modificar la convocatoria, entonces la API responde `403 VAL-015` sin realizar la operación.

**Pasos**

1. Con **Programador** logueado (solo `temporadas.temporada:CONSULTAR`), abrir F-10 y revisar las acciones de la temporada activa.
2. Intentar asociar una convocatoria a esa temporada desde la misma sesión del navegador.
3. Revisar la respuesta y verificar que no se crea ni se modifica ninguna convocatoria.

**Resultado esperado**

- La fila de F-10 muestra la convocatoria vigente en modo consulta, sin el botón **Convocatoria** ni otras acciones de escritura.
- El `PUT` responde `403` con `codigo: "VAL-015"` y el detalle «Tu cuenta no tiene el permiso necesario para esta operación.»
- `siglim.convocatorias` permanece inalterada.

**Resultado obtenido:** **Aprobado.** Con el Programador, la fila mostró «Convocatoria B-02 actualizada» junto a la leyenda «Solo consulta» y sin el botón de convocatoria. El `PUT` pedido desde esa sesión respondió `403` con `{"detail":"Tu cuenta no tiene el permiso necesario para esta operación.","instance":"/api/v1/temporadas/27a1639c-573b-46ea-8413-7c00299d98b7/convocatoria","status":403,"title":"Acción no permitida","type":"https://siglim.mx/errores/val-015","codigo":"VAL-015"}`. La auditoría no registró asociación desde el Programador.

**Evidencia**

- [`CA4-programador-sin-accion.png`](evidencias/B-02/CA4-programador-sin-accion.png) — F-10 con el Programador: «Solo consulta» y sin acciones de escritura.
- [`CA4-respuesta-403.png`](evidencias/B-02/CA4-respuesta-403.png) — respuesta `403 VAL-015` pedida desde la sesión del Programador y mostrada en el navegador.
- [`CA4-sin-permiso.txt`](evidencias/B-02/CA4-sin-permiso.txt) — respuesta `403 VAL-015`.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «VAL-015: sin el permiso de la convocatoria no se escribe».
- `ConvocatoriasIT` · «VAL-015: el Programador consulta temporadas pero no asocia su convocatoria».
- `PantallaTemporadas.test.tsx` · «H-02.4: sin EDITAR no aparecen las acciones de escritura» y «VAL-015: sin CONSULTAR muestra la 403 y no pide datos».

## 4. Verificaciones adicionales

### V1 · La referencia es obligatoria

**Pasos**

1. Como **Secretaría**, enviar un `PUT` con `referencia: ""` sobre la temporada activa.

**Resultado esperado**

- La API responde `400` con `codigo: "VALIDACION"` y el campo `referencia` marcado con «La referencia de la convocatoria es obligatoria».

**Resultado obtenido:** **Aprobado.** La API respondió `400` con `{"detail":"Revisa los campos marcados.","instance":"/api/v1/temporadas/27a1639c-573b-46ea-8413-7c00299d98b7/convocatoria","status":400,"title":"Solicitud inválida","type":"https://siglim.mx/errores/validacion","codigo":"VALIDACION","errores":[{"campo":"referencia","mensaje":"La referencia de la convocatoria es obligatoria"}]}`.

**Evidencia**

- [`V1-sin-referencia.txt`](evidencias/B-02/V1-sin-referencia.txt) — respuesta `400 VALIDACION`.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-002: rechaza una referencia vacía, una versión larga y un enlace que no es URL».
- `TemporadaControllerTest` · «RF-002: una referencia vacía responde 400 VALIDACION sin llegar al servicio».

### V2 · La versión admite hasta 30 caracteres

**Pasos**

1. Como **Secretaría**, enviar un `PUT` con `version` de 31 caracteres sobre la temporada activa.

**Resultado esperado**

- La API responde `400` con `codigo: "VALIDACION"` y `version` marcado con «La versión admite hasta 30 caracteres».

**Resultado obtenido:** **Aprobado.** La API respondió `400` con `{"detail":"Revisa los campos marcados.","instance":"/api/v1/temporadas/27a1639c-573b-46ea-8413-7c00299d98b7/convocatoria","status":400,"title":"Solicitud inválida","type":"https://siglim.mx/errores/validacion","codigo":"VALIDACION","errores":[{"campo":"version","mensaje":"La versión admite hasta 30 caracteres"}]}`.

**Evidencia**

- [`V2-version-larga.txt`](evidencias/B-02/V2-version-larga.txt) — respuesta `400 VALIDACION`.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-002: rechaza una referencia vacía, una versión larga y un enlace que no es URL».

### V3 · El enlace del documento debe ser http o https

**Pasos**

1. Como **Secretaría**, enviar un `PUT` con `documentoUrl: "ftp://liga.mx/doc"` sobre la temporada activa.

**Resultado esperado**

- La API responde `400` con `codigo: "VALIDACION"` y el detalle «El enlace de la convocatoria debe empezar por http:// o https://.»

**Resultado obtenido:** **Aprobado.** Antes de cerrar la temporada, la API respondió `400` con `{"detail":"El enlace de la convocatoria debe empezar por http:// o https://.","instance":"/api/v1/temporadas/27a1639c-573b-46ea-8413-7c00299d98b7/convocatoria","status":400,"title":"Solicitud inválida","type":"https://siglim.mx/errores/validacion","codigo":"VALIDACION"}` al intentar enviar un enlace `ftp://`.

**Evidencia**

- [`V3-enlace-invalido.txt`](evidencias/B-02/V3-enlace-invalido.txt) — respuesta `400 VALIDACION`.

**Pruebas automáticas relacionadas**

- `ConvocatoriaServiceTest` · «RF-002: rechaza una referencia vacía, una versión larga y un enlace que no es URL».

## 5. Verificación automática

| Comando | Dónde | Resultado |
| --- | --- | --- |
| `./mvnw verify` | `backend/` | **BUILD SUCCESS** · 80 pruebas, 0 fallos |
| `npm run lint` | `frontend/` | 0 advertencias y 0 errores |
| `npm test` | `frontend/` | 312 pruebas aprobadas |
| `npm run build` | `frontend/` | compilación de producción correcta |

**Reverificación (2026-11-06):** se volvió a ejecutar la prueba de frontend de la convocatoria con `npx vitest run src/features/temporadas/PantallaTemporadas.test.tsx` en `frontend/`: **23 de 23 pruebas aprobadas**, incluidas «B-02.2: el Secretario asocia la convocatoria sin permiso de editar la temporada», «B-02.2: reemplaza la convocatoria y vaciar un campo opcional lo borra», «RF-002: no envía una convocatoria sin referencia ni con un enlace inválido» y «RF-003: si el servidor rechaza la convocatoria, conserva el modal y explica la causa». `./mvnw verify` **no se pudo repetir** en este entorno por falta de JDK (`JAVA_HOME` sin definir, `java` no disponible) y de Docker para las IT; la fila de `./mvnw verify` de la tabla corresponde a la corrida del 2026-11-05 en el entorno del equipo. La corrida de navegador usó el artefacto del backend ya compilado (`backend/target/siglim-0.1.0-SNAPSHOT.jar`) contra esta misma base local.

## 6. Defectos encontrados

Ninguno. Los cuatro criterios de aceptación y las validaciones adicionales pasaron sin errores en esta corrida.

## 7. Conclusión

La historia **B-02 cumple sus 4 criterios de aceptación** con frontend, backend y base de datos reales, y con la interfaz recorrida en el navegador: la Secretaría asocia y reemplaza la convocatoria de una temporada activa sin duplicar; la convocatoria de una temporada cerrada es de solo lectura con `409 RF-003`; y sin el permiso `temporadas.convocatoria:EDITAR` la operación se rechaza con `403 VAL-015`. Las validaciones de referencia, versión y enlace también pasaron.

La prueba confirma la integración de B-02.1 (backend: servicio, controlador y RLS/permisos de la convocatoria) y B-02.2 (frontend: formulario, fila y configuración de permisos en F-10), con capturas de la pantalla en cada criterio. Las pruebas automáticas citadas cubren los casos de error que no se repitieron aquí (404 `RECURSO_NO_ENCONTRADO`, `409 DUPLICADO` de temporada y la restricción `UNIQUE` como última garantía).

**Nota de la corrida:** al sembrar la temporada activa, los parámetros deportivos rechazan `bloqueCanchaMin` menor que `duracionJuegoMin`; la corrida usó `bloqueCanchaMin = 90` para activar la temporada y poder ejecutar los criterios sobre una temporada activa.
