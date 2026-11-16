# Prueba de aceptación · H-23 Dar de baja a un jugador conservando su historial

| Campo | Valor |
| --- | --- |
| Historia | H-23 · subtarea H-23.3 (prueba de aceptación de baja) |
| Requisitos | RF-014, RF-017, RN-021, UI-006 |
| Pantallas | F-17 (plantilla del equipo, modal de baja) y la cabecera del registro (documentos del registro) |
| Implementó | H-23.1 (#134) y H-23.2 (#135): Leonardo López |
| Versión probada | `develop` en `fc3a52e` (base de la rama `test/m04-h23.3-aceptacion-baja`) |
| Fecha | 2026-11-15 |
| Ejecutó | Geovani, con un agente de IA (prueba cruzada de DEC-73) |
| Resultado global | **Aprobado** (2 de 2 criterios y verificaciones complementarias) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 (`postgres:17-alpine`) en un contenedor local, con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las diez migraciones del repositorio aplicadas en orden |
| Autenticación | Servidor local que publica el JWKS ES256 y responde el inicio de sesión; el backend validó firma, emisor, audiencia y `sub` como en Supabase Auth |
| Backend | `siglim-0.1.0-SNAPSHOT.jar` compilado de `fc3a52e`, en `http://localhost:8080`, contra la base local y el JWKS local |
| Frontend | `vite` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium 1248 controlado con Playwright, ventana de 1440 × 1000 px y zona `America/Mexico_City` |

La corrida se hizo sobre una **base local efímera**: no se alteró el proyecto compartido de Supabase. El
arnés (JWKS local, datos de siembra y recorrido de Playwright) vive fuera del repositorio; aquí quedan las
capturas, las respuestas crudas de la API y las consultas a la base. La baja no usa S3: los documentos de
la siembra son filas de `siglim.documentos` sin archivo, solo para poder activar los registros (VAL-001).

> La prueba no sustituye a las pruebas automáticas: las complementa. `BajaRegistroIT` cubre las reglas con
> PostgreSQL 17 en Testcontainers; `RegistroJugadorServiceTest` y `RegistroJugadorControllerTest`, la regla y
> el contrato HTTP; `PantallaPlantilla.test.tsx`, `erroresBaja.test.ts` y `PantallaDocumentosRegistro.test.tsx`,
> la interfaz.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones** | Directiva de club · CLUB *Deportivo Halcones* | CA-1 y CA-2: baja desde F-17 y por API |
| **Directiva Águilas** | Directiva de club · CLUB *Club Águilas* | Verificación de ámbito (404) |
| **Secretario de la Liga** | Secretario · LIGA | Validó los registros en la siembra; verificación de permiso (403) |

Datos ficticios sembrados en la base local:

- Temporada **Temporada 2026-2027** (ACTIVA) con la categoría **Libre** y el grupo **Único**.
- Club **Deportivo Halcones** con el equipo **Halcones** y su tarjetón **APROBADA**; club **Club Águilas**
  con el equipo **Águilas**.
- **Carlos Ramírez Ortiz**, CURP ficticia `RAOC940303HMNMRR01`, registro
  `80000000-0000-4000-8000-000000000001`, **ACTIVO**: sus tres documentos (1 Identificación y
  2 Fotografías) aprobados y validado por el Secretario con las observaciones «Documentos revisados en
  ventanilla.». Se usa en CA-1.
- **Luis Pérez Gómez**, CURP ficticia `PEGL960606HMNRMS02`, registro
  `80000000-0000-4000-8000-000000000002`, **ACTIVO** en las mismas condiciones. Se usa en CA-2 y en los
  errores 401/403/404: debe terminar la prueba igual que empezó.
- **Andrés Torres Sánchez**, CURP ficticia `TOSA980808HMNRNN03`, registro
  `80000000-0000-4000-8000-000000000003`, **PENDIENTE_VALIDACION**: se usa para el `409` de un registro que
  no está activo.

El estado inicial (registros, historial con el alta y la validación, y auditoría de bajas vacía) está en la
primera parte de [`base-de-datos.txt`](evidencias/H-23/base-de-datos.txt).

## 3. Escenarios

### CA-1 · Un registro ACTIVO dado de baja con motivo pasa a BAJA y conserva su historial

> Dado un registro ACTIVO, cuando se da de baja con motivo, entonces pasa a BAJA y conserva su historial.

**Pasos**

1. Iniciar sesión como **Directiva Halcones** y abrir **Organización → Jugadores**
   (`/app/organizacion/jugadores`). La plantilla de Halcones muestra a Carlos y a Luis **ACTIVO** con
   **Dar de baja**, y a Andrés **PENDIENTE DE VALIDACIÓN** sin esa acción.
2. Pulsar **Dar de baja** en la fila de Carlos. Comprobar el título, el equipo, la categoría y la temporada
   del registro, y la advertencia de lo que implica la baja.
3. Escribir el motivo `El jugador cambió de residencia a otra ciudad.` y pulsar **Dar de baja**.
4. Abrir los documentos del registro de Carlos
   (`/app/organizacion/jugadores/80000000-0000-4000-8000-000000000001/documentos`).
5. Consultar el registro, su historial y la auditoría en la base.

**Resultado esperado**

- `POST /registros-jugador/{id}/baja` responde `200` con `estado: "BAJA"`, `bajaEn` (fecha de México) y
  `motivoBaja`.
- La plantilla anuncia la baja; la fila queda **BAJA**, **No elegible** y sin la acción.
- La cabecera del registro muestra la fecha y el motivo de la baja, y conserva los documentos y las
  observaciones de la validación.
- El historial conserva las filas anteriores (alta y validación) y agrega `ACTIVO → BAJA` con el motivo y el
  usuario; la operación queda auditada (RF-093).

**Resultado obtenido:** **Aprobado.** El modal mostró «Dar de baja a Carlos Ramírez Ortiz», «El registro en
Halcones (Libre, Temporada 2026-2027) pasará a Baja.» y las cuatro advertencias (deja de ser elegible desde
hoy y no se puede deshacer; el registro y su historial se conservan; para volver a jugar en el equipo hace
falta un alta nueva; en esta temporada no podrá registrarse en otro equipo ni otro club). La baja respondió
`200` con `estado: "BAJA"`, `bajaEn: "2026-11-15"` y `motivoBaja`; las observaciones y la validación del
Secretario no cambiaron. La plantilla anunció «Carlos Ramírez Ortiz quedó dado de baja. Su historial se
conserva.» y la fila pasó a **BAJA** · **No elegible** sin acciones. La cabecera del registro muestra
**Fecha de baja 15 nov 2026**, **Motivo de la baja**, «El registro está dado de baja: sus documentos quedan
como consulta.» y los tres documentos aprobados.

En la base, `registros_jugador` tiene `estado = BAJA` y `baja_en = 2026-11-15` (cumple
`ck_registros_baja`); el historial conserva las filas 1 (alta) y 4 (validación) y agrega la 6,
`ACTIVO → BAJA` con el motivo y **Directiva Halcones** como `cambiado_por`. La auditoría tiene **una** fila
`DAR_BAJA_REGISTRO_JUGADOR` del módulo `M-04` con el usuario, el motivo y un `detalle` de solo identificadores,
estados y fecha, sin nombre ni CURP. Con el rol `siglim_app` del backend, borrar o modificar el historial o
borrar el registro responde `permission denied`: nada con historial se borra (RF-003, RN-005).

**Evidencia**

- [`CA1-plantilla-antes.png`](evidencias/H-23/CA1-plantilla-antes.png): plantilla con Carlos y Luis **ACTIVO**.
- [`CA1-modal-baja.png`](evidencias/H-23/CA1-modal-baja.png): modal con la advertencia y el motivo.
- [`CA1-plantilla-despues.png`](evidencias/H-23/CA1-plantilla-despues.png): aviso y fila **BAJA** · **No elegible**.
- [`CA1-detalle-baja.png`](evidencias/H-23/CA1-detalle-baja.png): cabecera con la fecha y el motivo de la baja.
- [`CA1-respuesta-ui.json`](evidencias/H-23/CA1-respuesta-ui.json): `200` del `POST …/baja` que hizo la pantalla.
- [`CA1-detalle-registro.json`](evidencias/H-23/CA1-detalle-registro.json): `GET /registros-jugador/{id}` después de la baja.
- [`base-de-datos.txt`](evidencias/H-23/base-de-datos.txt): registros, historial y auditoría antes y después, e inmutabilidad.

**Pruebas automáticas relacionadas**

- `BajaRegistroIT` · «H-23 · RF-014 · RN-021: la Directiva da de baja un registro ACTIVO; queda el historial
  con su motivo y usuario».
- `RegistroJugadorServiceTest` · «RF-014 · RN-021: da de baja un registro ACTIVO con el motivo antes del
  UPDATE, la fecha de México y auditoría».
- `RegistroJugadorControllerTest` · «RF-014 · DEC-124: POST /registros-jugador/{id}/baja responde 200 con
  no-store, bajaEn y motivoBaja».
- `PantallaPlantilla.test.tsx` · «DEC-125: «Dar de baja» solo en la fila ACTIVO y con registros.registro:EDITAR»
  y «DEC-127: envía el motivo recortado, anuncia la baja con el foco y vuelve a consultar la lista».
- `PantallaDocumentosRegistro.test.tsx` · «DEC-128: un registro en BAJA muestra la fecha y el motivo de la
  baja y queda de consulta».

### CA-2 · Sin motivo, la baja se rechaza (RN-021)

> Sin motivo, se rechaza (RN-021).

**Pasos**

1. En la plantilla, pulsar **Dar de baja** en la fila de **Luis** y pulsar **Dar de baja** sin escribir
   motivo.
2. Escribir `Se fue` (6 caracteres) y volver a confirmar; después pulsar **Cancelar**.
3. Para comprobar la regla en el servidor, sin la revisión del navegador, enviar por API
   `POST /registros-jugador/{id}/baja` sobre Luis con el motivo vacío, solo con espacios, sin el campo y con
   `Se fue`.
4. Consultar el registro de Luis, su historial y la auditoría.

**Resultado esperado**

- El modal no envía la baja y explica el mínimo de 10 caracteres.
- La API responde `400 RN-021` en los cuatro casos.
- El registro de Luis sigue **ACTIVO**, sin `baja_en`, sin fila nueva en el historial ni en la auditoría.

**Resultado obtenido:** **Aprobado.** En ambos intentos el modal quedó abierto, marcó el campo y mostró
«Escribe un motivo de al menos 10 caracteres.» con el contador `0/500` y `6/500`. Por API, los cuatro casos
respondieron `400` con `codigo: "RN-021"` y «Escribe un motivo de al menos 10 caracteres para dar de baja el
registro.». Al terminar la prueba, Luis sigue **ACTIVO** sin `baja_en`; su historial solo tiene el alta y la
validación, y la única fila `DAR_BAJA_REGISTRO_JUGADOR` de la auditoría es la de Carlos.

**Evidencia**

- [`CA2-sin-motivo.png`](evidencias/H-23/CA2-sin-motivo.png): modal sin motivo con el error.
- [`CA2-motivo-corto.png`](evidencias/H-23/CA2-motivo-corto.png): modal con un motivo de 6 caracteres.
- [`CA2-sin-motivo.json`](evidencias/H-23/CA2-sin-motivo.json): las cuatro respuestas `400 RN-021` en RFC 9457.
- [`base-de-datos.txt`](evidencias/H-23/base-de-datos.txt): Luis **ACTIVO** antes y después, sin historial nuevo.

**Pruebas automáticas relacionadas**

- `BajaRegistroIT` · «RN-021 · DEC-121: sin motivo responde 400 y el registro sigue ACTIVO sin historial
  nuevo».
- `RegistroJugadorServiceTest` · «RN-021 · DEC-121: sin motivo, en blanco o de 9 caracteres responde 400
  RN-021 antes de buscar el registro» y «DEC-121 · DEC-122: el 400 del motivo gana al 404 de un registro
  inexistente».
- `PantallaPlantilla.test.tsx` · «RN-021: con un motivo de menos de 10 caracteres no hace el POST» y
  «DEC-129: un 400 RN-021 deja el modal abierto con el detail».

## 4. Verificaciones complementarias

### 4.1 Permisos, ámbito y errores 401/403/404/409

| Caso | Petición | Respuesta |
| --- | --- | --- |
| Sin token | `POST /registros-jugador/{Luis}/baja` | `401 NO_AUTENTICADO` |
| Secretario (sin `registros.registro:EDITAR`) | `POST /registros-jugador/{Luis}/baja` | `403 VAL-015` |
| Directiva de otro club (Águilas) | `POST /registros-jugador/{Luis}/baja` | `404 NO_ENCONTRADO` (VAL-024) |
| Registro inexistente | `POST /registros-jugador/{uuid-inexistente}/baja` | `404 NO_ENCONTRADO` |
| Segunda baja del registro de Carlos | `POST /registros-jugador/{Carlos}/baja` | `409 RF-017` «El registro ya está dado de baja.» |
| Registro `PENDIENTE_VALIDACION` (Andrés) | `POST /registros-jugador/{Andrés}/baja` | `409 RF-017` «El registro aún no está validado: si no procede, pide a la liga que lo rechace.» |

Las respuestas crudas están en
[`respuestas-401-403-404-409.json`](evidencias/H-23/respuestas-401-403-404-409.json). Todas llevaron un
motivo válido para que el error fuera el del caso y no el de RN-021. Ninguna cambió el estado de los
registros ni agregó historial o auditoría (§ «DESPUÉS» de `base-de-datos.txt`). El 403 del Secretario es el
comportamiento acordado en DEC-119: hoy solo la Directiva tiene `registros.registro:EDITAR`, y la baja por el
Secretario queda **[PROPUESTA]**.

En la interfaz, la fila de Andrés (pendiente) y la de Carlos ya en **BAJA** no ofrecen **Dar de baja**
(DEC-125).

### 4.2 Auditoría de operaciones críticas (RF-093)

La auditoría de la corrida tiene **una** fila `DAR_BAJA_REGISTRO_JUGADOR`: usuario **Directiva Halcones**,
módulo `M-04`, entidad `registros_jugador`, el motivo y el `detalle` con `estadoAnterior`, `estado`, `bajaEn`,
`equipoId`, `personaId` y `temporadaId`. No lleva nombres ni CURP. Los intentos rechazados (400, 401, 403,
404 y 409) no se auditaron.

### 4.3 Historial conservado e inmutable

La baja es un cambio de estado, no un borrado: el registro sigue en `registros_jugador` y el trigger
`tg_registros_historial` agrega la transición sin tocar las anteriores. Con el rol `siglim_app`, `DELETE` y
`UPDATE` sobre `registro_estado_historial` y `DELETE` sobre `registros_jugador` responden
`permission denied` (último bloque de `base-de-datos.txt`).

## 5. Defectos encontrados

Ninguno.

## 6. Automatización y verificación

- Backend: `./mvnw verify` → **2031 pruebas** (1756 unitarias + 275 de integración), **0 fallos**, 1 omitida
  (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS), **BUILD SUCCESS**. En H-23:
  `BajaRegistroIT` (5), `RegistroJugadorServiceTest` (60) y `RegistroJugadorControllerTest` (40).
- Frontend: `npm test` → **741 pruebas en 82 archivos, 0 fallos**; `npm run lint` → sin advertencias ni
  errores; `npm run build` → compilación correcta. Los archivos de H-23.2 (`PantallaPlantilla.test.tsx`,
  `erroresBaja.test.ts`) y `PantallaDocumentosRegistro.test.tsx` pasan.
- Las pruebas de integración del backend usan PostgreSQL 17 con Testcontainers; la corrida de aceptación
  usó PostgreSQL 17 local. El detalle está en
  [`pruebas-automaticas.txt`](evidencias/H-23/pruebas-automaticas.txt).

## 7. Conclusión

H-23 cumple sus dos criterios de aceptación y las verificaciones complementarias de permisos, ámbito,
errores y auditoría. Desde F-17, la Directiva da de baja un registro **ACTIVO** con motivo: pasa a **BAJA**
con la fecha de México, el motivo queda en el historial junto a las transiciones anteriores y la operación se
audita; sin motivo, o con menos de 10 caracteres, la interfaz no envía la baja y la API responde
`400 RN-021` sin tocar el registro. H-23.3 queda terminada. La prueba cruzada de DEC-73 la realizó Geovani con
un agente de IA; el cierre de H-23 depende de la aprobación del PR.
