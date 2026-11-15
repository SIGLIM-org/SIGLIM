# Prueba de aceptación · H-10 Cargar documentos del jugador y validar el registro

| Campo | Valor |
| --- | --- |
| Historia | H-10 · subtarea H-10.4 (prueba de aceptación del registro completo) |
| Requisitos | RF-108, RF-109, RF-017, VAL-001, RNF-018, UI-024 |
| Pantallas | F-18 (paso 4, documentos del registro) y F-19 (validación de registros) |
| Implementó | H-10.1 (#122), H-10.2 (#123) y H-10.3 (#127): Leonardo López |
| Versión probada | `develop` en `53cc39b` (base de la rama `test/m04-h10.4-aceptacion-registro`) |
| Fecha | 2026-11-14 |
| Ejecutó | Agente de IA; revisión y prueba cruzada de DEC-73 pendientes |
| Resultado global | **Aprobado** (5 de 5 criterios y verificaciones complementarias) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 en un contenedor local, con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las diez migraciones del repositorio aplicadas en orden |
| Autenticación | Servidor local que publica el JWKS ES256 y responde el inicio de sesión; el backend validó firma, emisor, audiencia y `sub` como en Supabase Auth |
| Backend | `siglim-0.1.0-SNAPSHOT.jar` compilado de `53cc39b`, en `http://localhost:8080`, contra la base local y el JWKS local |
| Almacenamiento | `adobe/s3mock:4.12.4` en `http://127.0.0.1:9090` con el backend apuntando a su endpoint: misma API S3, `Content-Type` y URL prefirmada que produce el SDK real (ADR-0013) |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium 1248 controlado con Playwright, ventana de 1440 × 1200 px y zona `America/Mexico_City` |

La corrida se hizo sobre una **base local efímera** y un S3 simulado: no se alteró el proyecto compartido de
Supabase ni el bucket de AWS del equipo. El arnés (JWKS local, datos de siembra, S3 simulado y recorrido de
Playwright) vive fuera del repositorio; aquí quedan las capturas y las respuestas crudas. El doble de S3 se
usó solo como almacenamiento: el backend firmó y validó los enlaces con el mismo código de producción.

> La prueba no sustituye a las pruebas automáticas: las complementa. La integración con AWS real y con el
> Supabase compartido se cubre con `DocumentosRegistroIT`, `ValidacionRegistroIT` y las pruebas de
> controlador; aquí se comprobó que las piezas funcionan juntas desde la interfaz.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones** | Directiva de club · CLUB *Deportivo Halcones* | CA-1 y CA-2: alta, requisitos y carga de documentos |
| **Secretario de la Liga** | Secretario · LIGA | CA-3, CA-4 y CA-5: revisión, enlace, activación y rechazo |

Datos ficticios sembrados en la base local:

- Temporada **Temporada 2026-2027** (ACTIVA) con la categoría **Libre** (`tipo = LIBRE`).
- Club **Deportivo Halcones** con el equipo **Halcones** (ACTIVO) y su tarjetón **APROBADA** (RF-101).
- Aviso de privacidad vigente **v1** y requisitos de la categoría tomados de `siglim.requisitos_documento`:
  **Identificación** (1 archivo) y **Fotografía** (2 archivos). No hay requisitos para menores porque la
  persona de la prueba es adulta.
- Persona **María Martínez Hernández**, CURP ficticia `MAHL920202MMCRRN05`, 1992-02-02.
- Persona de apoyo **Sergio Sánchez Herrera**, CURP ficticia `SAHL950303HMCRRN04`, para el rechazo y la
  captura de la respuesta de un documento recién cargado.

## 3. Escenarios

### CA-1 · La Directiva carga los documentos requeridos y quedan PENDIENTE con tipo, tamaño y hash

> Dado un registro, cuando la Directiva carga los documentos requeridos, entonces quedan PENDIENTE con tipo,
> tamaño y hash.

**Pasos**

1. Iniciar sesión como **Directiva Halcones** y abrir **Organización → Jugadores → Alta**
   (`/app/organizacion/jugadores/alta`).
2. Buscar la CURP de María. Como no existe, el asistente muestra **Persona nueva**; capturar sus datos,
   elegir el equipo **Halcones** y continuar.
3. Leer el aviso vigente, marcar las finalidades y la declaración de consentimiento; pulsar
   **Enviar a validación**.
4. Comprobar que el `201` lleva al paso 4 de F-18, **Documentos del registro**, con los requisitos de la
   categoría: Identificación (1) y Fotografía (2).
5. Cargar `identificacion.jpg` en Identificación y dos fotografías JPEG en Fotografía; en cada carga pulsar
   **Subir** y comprobar el estado **Por revisar**.

**Resultado esperado**

- El alta responde `201` y el registro queda `PENDIENTE_VALIDACION`.
- La pantalla muestra `3/3 cargados` y cada documento queda **Por revisar** (PENDIENTE).
- En la base, cada fila de `siglim.documentos` conserva el tipo detectado por firma (`image/jpeg`), el
  tamaño en bytes y el `sha256`; la interfaz no expone el hash (RNF-018).

**Resultado obtenido:** **Aprobado.** El alta respondió `201` y abrió F-18 en el paso 4. La categoría
Libre pidió 1 identificación y 2 fotografías; las tres cargas respondieron `201` y quedaron **Por revisar**,
con `3/3 cargados` y `0/3 aprobados`. La base conserva `tipo_mime`, `tamano_bytes` y `sha256` de los tres
archivos (ver §2 de `base-de-datos.txt`). El DTO de la API omite el hash y la URL guardada (DEC-95): el hash
se comprobó en la base.

La respuesta cruda de una carga recién hecha se conserva en `CA1-carga-documento.json` y los requisitos de
ese mismo registro de apoyo en `CA1-requisitos-pendiente.json`: el documento aparece `PENDIENTE` con
`image/jpeg`, `25056` bytes y la lista de requisitos con `cargados: 1`, `aprobados: 0` y las dos fotografías
pendientes. La fila correspondiente en `base-de-datos.txt` (§2b) muestra su `sha256` completo.

**Evidencia**

- [`CA1-alta-datos.png`](evidencias/H-10/CA1-alta-datos.png): datos y equipo del alta.
- [`CA1-consentimiento.png`](evidencias/H-10/CA1-consentimiento.png): aviso y consentimiento.
- [`CA1-documentos-sin-cargar.png`](evidencias/H-10/CA1-documentos-sin-cargar.png): requisitos antes de cargar.
- [`CA1-documentos-cargados.png`](evidencias/H-10/CA1-documentos-cargados.png): los tres documentos en **Por revisar**.
- [`CA1-carga-documento.json`](evidencias/H-10/CA1-carga-documento.json): `201` con `estadoValidacion: "PENDIENTE"`, MIME y tamaño.
- [`CA1-requisitos-pendiente.json`](evidencias/H-10/CA1-requisitos-pendiente.json): requisitos con el documento pendiente.
- [`base-de-datos.txt`](evidencias/H-10/base-de-datos.txt): filas con `sha256`, tamaño y estado (§2 y §2b).

**Pruebas automáticas relacionadas**

- `DocumentosRegistroIT` · «H-10 criterios 1 y 4 · RF-108 · RF-109: la Directiva carga, otro directivo
  reemplaza, el Secretario valida y el enlace no se audita».
- `DocumentoRegistroServiceTest` · «RF-108 · RN-055: la Directiva carga el INE del tutor de un menor; queda
  PENDIENTE en REGISTROS y se audita» y las pruebas de 413/415.
- `PantallaDocumentosRegistro.test.tsx` · carga, vista previa y requisitos de la categoría (H-10.3).

### CA-2 · Un archivo que no es JPEG, PNG, WebP ni PDF por su firma se rechaza con 415

> Dado un archivo que no es JPEG, PNG, WebP ni PDF por su firma, cuando se carga, entonces se rechaza con 415.

**Pasos**

1. En la misma página de documentos, pulsar **Reemplazar** en una fotografía.
2. Elegir `firma-falsa.png`: un archivo con extensión `.png` y `Content-Type: image/png` que en realidad
   contiene texto, no la firma binaria de un PNG.
3. Pulsar **Subir**.

**Resultado esperado**

- La revisión del navegador no basta: el servidor detecta la firma y responde `415`.
- El documento anterior no se modifica y no se inserta una fila nueva.

**Resultado obtenido:** **Aprobado.** El panel mostró «El tipo de archivo no está permitido. Usa JPG, PNG,
WebP o PDF.» y la API respondió `415 TIPO_ARCHIVO_NO_PERMITIDO`. El archivo real sigue en el cupo y la
fotografía conserva su estado; no se creó una fila nueva en `siglim.documentos`.

**Evidencia**

- [`CA2-error-415.png`](evidencias/H-10/CA2-error-415.png): mensaje del servidor en el panel de reemplazo.
- [`CA2-error-415.json`](evidencias/H-10/CA2-error-415.json): problema RFC 9457 con `codigo`.
- [`respuestas-401-403-404-415.json`](evidencias/H-10/respuestas-401-403-404-415.json): incluye el mismo 415 junto con los errores de seguridad.

**Pruebas automáticas relacionadas**

- `DocumentoRegistroServiceTest` · «RF-108: 413 y 415 del almacenamiento llegan sin insertar ni auditar» y
  «RF-108: un 415 del almacenamiento llega sin insertar, marcar ni auditar».
- `DocumentosRegistroIT` · «H-10 criterio 2 · RF-108: un PDF (aunque se llame .jpg) se guarda como
  application/pdf; un texto responde 415 sin fila».
- `DocumentoRegistroControllerTest` · «RF-108: 413 ARCHIVO_DEMASIADO_GRANDE y 415 TIPO_ARCHIVO_NO_PERMITIDO
  en RFC 9457».

### CA-3 · El Secretario aprueba todos los documentos y valida; si rechaza, exige observaciones

> Dado el Secretario, cuando aprueba todos los documentos y valida, entonces el registro pasa a ACTIVO; si
> rechaza, exige observaciones y pasa a RECHAZADO.

**Pasos (activación)**

1. Iniciar sesión como **Secretario de la Liga** y abrir **Organización → Validación de registros**
   (`/app/organizacion/registros`). La bandeja abre en **Pendientes** y muestra el registro de María.
2. Comprobar que el detalle dice «Para activar el registro, todos los documentos deben estar aprobados»,
   lista «Identificación: falta revisarlo» y «Fotografía: falta revisarlo», y que **Activar registro** está
   deshabilitado (VAL-001).
3. En cada documento pulsar **Ver**, revisar la imagen y pulsar **Aprobar** → **Confirmar**. El visor avisa
   que aprobar es definitivo.
4. Con los tres documentos aprobados, pulsar **Activar registro**, escribir las observaciones
   `Documentos de identidad revisados en ventanilla.` y confirmar.

**Pasos (rechazo)**

5. Abrir el registro de **Sergio Sánchez Herrera**, dado de alta sin documentos.
6. Pulsar **Rechazar registro** y confirmar sin motivo: el modal exige al menos 10 caracteres.
7. Escribir `La CURP no corresponde a la persona y falta la identificación oficial.` y confirmar.

**Resultado esperado**

- Antes de aprobar, el registro no se puede activar y la pantalla indica qué falta (VAL-001).
- Al activar, el registro queda **ACTIVO**, con `validadoPor`, `validadoEn` y las observaciones; el historial
  guarda la transición con el motivo.
- El rechazo sin observaciones no se envía; con observaciones queda **RECHAZADO** y el club las ve.
- Cada operación crítica queda auditada sin datos personales.

**Resultado obtenido:** **Aprobado.** Con los documentos pendientes, **Activar registro** estaba deshabilitado
y la pantalla listaba lo que faltaba. Tras aprobar los tres documentos en el visor, se habilitó; la
activación respondió `200` y el registro quedó **ACTIVO** con *Secretario de la Liga*, la fecha y las
observaciones. El rechazo sin motivo mostró «Escribe un motivo de al menos 10 caracteres.»; con el motivo
completo el registro de Sergio quedó **RECHAZADO** con las observaciones y la fecha. El historial tiene
`PENDIENTE_VALIDACION → ACTIVO` y `PENDIENTE_VALIDACION → RECHAZADO`, y la auditoría registra
`VALIDAR_REGISTRO_JUGADOR` dos veces con el estado y el motivo, sin nombres ni CURP.

**Evidencia**

- [`CA3-bandeja-por-revisar.png`](evidencias/H-10/CA3-bandeja-por-revisar.png): documentos pendientes y activación bloqueada.
- [`CA3-documentos-aprobados.png`](evidencias/H-10/CA3-documentos-aprobados.png): 1/1 y 2/2 aprobados.
- [`CA3-modal-activar.png`](evidencias/H-10/CA3-modal-activar.png): observaciones de la activación.
- [`CA3-registro-activo.png`](evidencias/H-10/CA3-registro-activo.png): registro **ACTIVO** con resolución.
- [`CA5-rechazo-sin-observaciones.png`](evidencias/H-10/CA5-rechazo-sin-observaciones.png): mínimo de 10 caracteres.
- [`CA5-rechazo-con-observaciones.png`](evidencias/H-10/CA5-rechazo-con-observaciones.png): motivo completo.
- [`CA5-registro-rechazado.png`](evidencias/H-10/CA5-registro-rechazado.png): registro **RECHAZADO** con observaciones.
- [`CA3-validacion-documentos.json`](evidencias/H-10/CA3-validacion-documentos.json): `200` al aprobar un documento.
- [`CA3-rechazo-registro.json`](evidencias/H-10/CA3-rechazo-registro.json): `200` al rechazar el registro.
- [`base-de-datos.txt`](evidencias/H-10/base-de-datos.txt): estados, historial y auditoría (§1, §4 y §6).

**Pruebas automáticas relacionadas**

- `ValidacionRegistroIT` · «H-10 criterios 3 y 5 · RF-109 · VAL-001: con todos los documentos aprobados la
  liga activa el registro» y «H-10 criterio 5 · RF-109 · DEC-100: el rechazo guarda las observaciones y deja
  dar de alta otro registro».
- `DocumentoRegistroServiceTest` · aprobación y rechazo con observaciones (RF-109).
- `PantallaValidacionRegistros.test.tsx` · visor, aprobación, rechazo y activación (H-10.3).

### CA-4 · La URL del documento expira a los 60 s y no se guarda ni se registra (RNF-018)

> La URL de un documento expira a los 60 s y no se guarda ni se registra (RNF-018).

**Pasos**

1. Con la sesión del Secretario (o de la Directiva), pulsar **Ver** en la Identificación.
2. Revisar la petición `GET /api/v1/documentos/{id}/enlace` en las herramientas del navegador y la cuenta
   regresiva del visor.
3. Consultar la base y la auditoría.

**Resultado esperado**

- `GET /documentos/{id}/enlace` responde `200 { url, expiraEn }` con `Cache-Control: no-store`.
- `expiraEn` está a 60 s del momento de la respuesta y el visor muestra la cuenta regresiva.
- La URL prefirmada no se guarda en la base ni se registra en la auditoría ni en la bitácora; la columna
  `documentos.url` solo conserva la URL del objeto, sin firma (DEC-45 y DEC-95).

**Resultado obtenido:** **Aprobado.** La respuesta llegó con `expiraEn` a **60.0 s** del reloj de la prueba y
`Cache-Control: no-store`; el visor mostró **0:59** y la imagen se cargó desde el enlace firmado. En la base,
las tres filas de `documentos` guardan una URL de objeto **sin** `X-Amz`, `Signature` ni `Expires`; no hay
columnas de URL firmada y la auditoría tiene **0** filas con URL, firma o hash. La bitácora del backend solo
registra la clave S3, nunca la URL prefirmada.

> `documentos.url` existe por diseño (DEC-45, DEC-95): guarda la URL del objeto en el bucket privado, que no
> es pública. Lo que RNF-018 prohíbe guardar es la URL **prefirmada**, y esa no se persiste ni se audita.

**Evidencia**

- [`CA4-enlace-60s.png`](evidencias/H-10/CA4-enlace-60s.png): visor con «vence en 0:59».
- [`CA4-enlace.json`](evidencias/H-10/CA4-enlace.json): `expiraEn`, vigencia de 60 s y URL sin los parámetros de firma.
- [`base-de-datos.txt`](evidencias/H-10/base-de-datos.txt): §3 (URL de objeto sin firma) y §5 (0 filas con URL o hash).

**Pruebas automáticas relacionadas**

- `DocumentoRegistroServiceTest` · «RNF-018 · DEC-95: con un reloj fijo, expiraEn es el reloj más 60 s; se
  audita sin la URL».
- `DocumentosRegistroIT` · «H-10 criterios 1 y 4 · … el enlace no se audita» y «DEC-95 · RNF-011: la
  auditoría de las cuatro acciones no lleva URL, bucket, clave, SHA-256 ni nombre del archivo».
- `VisorDocumento.test.tsx` · cuenta regresiva, descarte del enlace y «Ver de nuevo».

### CA-5 · Faltando un documento obligatorio, el registro no puede validarse (VAL-001)

> Faltando un documento obligatorio, el registro no puede validarse (VAL-001).

**Pasos**

1. Abrir en F-19 el registro de **Sergio Sánchez Herrera**, que no tiene documentos.
2. Comprobar el estado de **Activar registro** y la lista de documentos pendientes.
3. Intentar la activación por API con `POST /registros-jugador/{id}/validacion` y `{ "decision": "ACTIVO" }`.

**Resultado esperado**

- La interfaz deshabilita **Activar registro** y explica qué falta cargar.
- La API responde `422 VAL-001` con la extensión `faltantes` (tipo, cantidad, cargados y aprobados).
- El registro sigue `PENDIENTE_VALIDACION`.

**Resultado obtenido:** **Aprobado.** La pantalla mostró «Identificación: falta cargarlo» y «Fotografía: falta
cargarlo» con **Activar registro** deshabilitado. La API respondió `422 VAL-001` con los dos requisitos y sus
conteos; el registro siguió pendiente hasta que se rechazó en CA-3. La misma regla está respaldada por el
trigger de la base (DEC-101), que también impide activar sin documentos.

**Evidencia**

- [`CA5-val-001.png`](evidencias/H-10/CA5-val-001.png): activación bloqueada y lista de faltantes.
- [`CA5-validacion-sin-documentos.json`](evidencias/H-10/CA5-validacion-sin-documentos.json): `422` con `faltantes`.
- [`base-de-datos.txt`](evidencias/H-10/base-de-datos.txt): el registro sigue `PENDIENTE_VALIDACION` en el historial.

**Pruebas automáticas relacionadas**

- `ValidacionRegistroIT` · «H-10 criterio 3 · VAL-001: con un documento pendiente responde 422 con faltantes y
  el registro sigue pendiente» y «DEC-101 · VAL-015 · VAL-001: el trigger impide validar a un usuario de club
  y activar sin documentos».
- `RegistroJugadorServiceTest` y `ValidacionRegistroIT` · VAL-001 con `faltantes`.
- `PantallaValidacionRegistros.test.tsx` · «VAL-001: sin listoParaValidar, «Activar registro» está
  deshabilitado y lista lo que falta».

## 4. Verificaciones complementarias

### 4.1 Permisos y errores 401/403/404

| Caso | Petición | Respuesta |
| --- | --- | --- |
| Sin token | `GET /registros-jugador/{id}/requisitos` | `401 NO_AUTENTICADO` |
| Directiva intenta validar el registro | `POST /registros-jugador/{id}/validacion` | `403 VAL-015` |
| Secretario intenta cargar un documento | `POST /registros-jugador/{id}/documentos` | `403 VAL-015` |
| Registro inexistente | `GET /registros-jugador/{uuid-inexistente}/requisitos` | `404 NO_ENCONTRADO` |
| Firma inválida | `POST /documentos/{id}/reemplazo` con un `.png` falso | `415 TIPO_ARCHIVO_NO_PERMITIDO` |

Las respuestas crudas están en
[`respuestas-401-403-404-415.json`](evidencias/H-10/respuestas-401-403-404-415.json). La Directiva no puede
validar y el Secretario no puede cargar: los permisos son `registros.registro:VALIDAR` y
`registros.documento:CREAR`, respectivamente (VAL-015).

### 4.2 Auditoría de operaciones críticas (RF-093)

La auditoría de la corrida tiene las acciones `CARGAR_DOCUMENTO` (3), `VALIDAR_DOCUMENTO` (3),
`VALIDAR_REGISTRO_JUGADOR` (2: ACTIVO y RECHAZADO) y `CONSULTAR_DOCUMENTO` (7). El detalle lleva solo
identificadores, tipo de documento, MIME, tamaño, decisión y estados; **no** lleva nombres, CURP, URL, bucket,
clave S3 ni hash. La consulta que cuenta las filas con URL, firma o hash devuelve **0** (§4 y §5 de
`base-de-datos.txt`).

### 4.3 Requisitos configurables por categoría

Los requisitos no están fijos en el código: provienen de `siglim.requisitos_documento` para el tipo de la
categoría y, si la persona es menor, de los marcados `solo_menores`. En esta corrida la categoría Libre pidió
1 Identificación y 2 Fotografías; cambiar el catálogo cambia la pantalla y la validación (RF-108, DEC-96).

## 5. Defectos encontrados

Ninguno nuevo. La única precisión de lectura es la de CA-4: `documentos.url` guarda la URL del objeto y la URL
prefirmada no se persiste; es el comportamiento acordado en DEC-45 y DEC-95, no un defecto.

## 6. Automatización y verificación

- Backend: `./mvnw verify` → **2011 pruebas** (1742 unitarias + 269 de integración), **0 fallos**, 1 omitida
  (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS), **BUILD SUCCESS**. En H-10:
  `DocumentoRegistroServiceTest` (28), `DocumentoRegistroControllerTest` (38),
  `RequisitosDocumentosTest` (5), `DocumentosRegistroIT` (2), `ValidacionRegistroIT` (4) y
  `RegistrosJugadorIT` (6).
- Frontend: `npm test` → **741 pruebas en 82 archivos, 0 fallos**; `npm run lint` → 0 advertencias y
  0 errores (378 archivos); `npm run build` → compilación correcta.
- Las pruebas de integración del backend usan PostgreSQL 17 con Testcontainers y el doble de S3 en memoria
  (DEC-56); la corrida de aceptación usó PostgreSQL 17 local y S3Mock. El detalle está en
  [`pruebas-automaticas.txt`](evidencias/H-10/pruebas-automaticas.txt).

## 7. Conclusión

H-10 cumple sus cinco criterios de aceptación y las verificaciones complementarias de permisos, errores y
auditoría. El flujo **alta → documentos → validación** funciona de punta a punta en F-18 y F-19: los
documentos quedan pendientes con tipo, tamaño y hash; un archivo con firma no permitida responde 415; el
Secretario activa o rechaza con observaciones; el enlace vence a los 60 s y no se persiste; y sin los
documentos aprobados la activación responde VAL-001. H-10.4 queda terminada; el cierre de H-10 depende de la
prueba cruzada de DEC-73 y de la aprobación del PR.
