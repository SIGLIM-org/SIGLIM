# Prueba de aceptación · B-01 Atender solicitudes ARCO

| Campo | Valor |
| --- | --- |
| Historia | B-01 · subtarea B-01.2 · Sprint 3 · 2 SP (claves de Jira por confirmar) |
| Requisitos | RF-098, RF-093, VAL-015, VAL-024, RNF-003, DEC-40, DEC-67, ADR-0007 |
| Pantallas | Ninguna propia: B-01 entrega el contrato y el backend. La pantalla «Auditoría» (H-21) sirve de evidencia visual |
| Implementó | Geovani (B-01 #149) |
| Versión probada | `develop` en `c6deab8` (incluye `d2c37d5`) |
| Fecha | 2026-11-21 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (6 de 6 criterios); dos observaciones sin defecto |

La corrida valida de punta a punta la historia B-01 por su API. Con `seguridad.arco:GESTIONAR`, la
liga registra las solicitudes de acceso, rectificación, cancelación y oposición de una persona
titular y les da seguimiento (`RECIBIDA → EN_ATENCION → ATENDIDA` o `RECHAZADA`, con respuesta
obligatoria al cerrar). El listado se filtra por estado, tipo y titular. El permiso se aplica con su
ámbito: LIGA ve y cambia todo, CLUB no ve filas y PROPIO solo lo del titular. Una cancelación nunca
borra datos (DEC-67) y la auditoría registra las operaciones sin la descripción ni la respuesta. Se
usaron el backend y PostgreSQL reales sobre una base local efímera.

Los criterios salen de RF-098 («registrar las solicitudes de acceso, rectificación, cancelación y
oposición y su seguimiento»), de DEC-40 y DEC-67, de la descripción de B-01 y del contrato de las
convenciones de API §6.1 («Solicitudes ARCO»). B-01 es una historia B y el backlog no trae criterios
numerados, así que esta prueba los numera de CA-1 a CA-6.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas (la tabla `solicitudes_arco` y sus políticas RLS ya existían; B-01 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite), solo para la pantalla «Auditoría» |
| Navegador | Panel de navegador del entorno de pruebas, ventana emulada de 1024 × 768 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-01/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase** ni la matriz de permisos compartida. El arnés de autenticación, las cuentas, la semilla y
los guiones de la API viven fuera del repositorio. Aquí solo se versionan este documento y sus
evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican. En la matriz
inicial, `seguridad.arco:GESTIONAR` solo lo tiene el **Administrador general**.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador de prueba** | Administrador general · LIGA | CA-1 a CA-3, CA-5 y CA-6: registra, da seguimiento y consulta la auditoría; en CA-4 configura la matriz |
| **Secretario de prueba** | Secretario · LIGA | CA-4: sin el permiso |
| **Directiva Halcones de prueba** | Directiva de club · CLUB | CA-4: sin el permiso y, después, con el permiso en ámbito CLUB |
| **Titular de prueba** | Jugador o tutor · PROPIO, perfil enlazado a la persona «Tomás Titular» | CA-4: sin el permiso y, después, con el permiso en ámbito PROPIO |

Personas ficticias titulares (CURP con formato válido que no corresponden a nadie): **Tomás
Titular** (acceso y oposición), **Rosa Rectifica** (rectificación) y **Carlos Cancela** (cancelación,
con correo y teléfono capturados).

## 3. Escenarios

### CA-1 · La liga registra los cuatro tipos de solicitud ARCO

> Dada una persona titular, cuando el Administrador registra una solicitud con `tipo` y `descripcion`,
> entonces queda `RECIBIDA` (`201`, sin `Location`); sin titular, con un tipo desconocido o sin
> descripción responde `400 VALIDACION`, y con una persona inexistente `422 REFERENCIA_INVALIDA`.

**Resultado obtenido** ([respuestas](evidencias/B-01/respuestas-api.txt)):

| Petición | Respuesta |
| --- | --- |
| `ACCESO` de Tomás · `RECTIFICACION` de Rosa · `CANCELACION` de Carlos · `OPOSICION` de Tomás | `201` con `estado: RECIBIDA` en las cuatro; encabezado `Cache-Control: no-store` y sin `Location` |
| Sin `personaId` | `400 VALIDACION` «Indica la persona titular de los datos.» |
| `tipo: BORRADO` | `400 VALIDACION` «El tipo de solicitud debe ser ACCESO, RECTIFICACION, CANCELACION u OPOSICION.» |
| Descripción en blanco | `400 VALIDACION` «Describe lo que pide el titular de los datos.» |
| Persona inexistente | `422 REFERENCIA_INVALIDA` «La persona indicada no existe. Búscala por su CURP…» |

**Resultado:** ✅ Aprobado.

### CA-2 · El seguimiento sigue las transiciones y exige la respuesta al cerrar

> `RECIBIDA → EN_ATENCION`; `RECIBIDA` o `EN_ATENCION` → `ATENDIDA` o `RECHAZADA` (finales, con
> `respuesta` obligatoria, y guardan quién y cuándo atendió). Otra transición responde `422 RF-098`.

**Resultado obtenido** ([respuestas](evidencias/B-01/respuestas-api.txt)):

| Paso | Respuesta |
| --- | --- |
| Acceso: `RECIBIDA → EN_ATENCION` | `200`, sin respuesta ni fecha de atención |
| Acceso: `EN_ATENCION` otra vez | `422 RF-098` «La solicitud ya está EN_ATENCION.» |
| Acceso: `ATENDIDA` sin respuesta | `400 VALIDACION` «Escribe la respuesta que se entrega al titular para cerrar la solicitud.» |
| Acceso: `EN_ATENCION → ATENDIDA` con respuesta | `200` con `respuesta`, `atendidaPor`, `atendidaPorNombre: "Administrador de prueba"` y `atendidaEn` |
| Acceso: `ATENDIDA → RECHAZADA` | `422 RF-098` «La solicitud ya está ATENDIDA y no admite más cambios de estado.» |
| Rectificación: `EN_ATENCION` con respuesta | `400 VALIDACION` «La respuesta al titular se registra al cerrar la solicitud…» |
| Rectificación: `RECIBIDA → RECHAZADA` con respuesta | `200` (cierre directo permitido) |
| Oposición: `estado: RECIBIDA` | `400 VALIDACION` «El nuevo estado debe ser EN_ATENCION, ATENDIDA o RECHAZADA.» |
| Solicitud inexistente | `404 NO_ENCONTRADO` |
| `DELETE /solicitudes-arco/{id}` | `405 METODO_NO_PERMITIDO` |

**Resultado:** ✅ Aprobado.

### CA-3 · El listado se ordena por recepción y se filtra por estado, tipo y titular

**Resultado obtenido** ([respuestas](evidencias/B-01/respuestas-api.txt)). Sin filtros, el
Administrador recibe las 4 solicitudes de la más reciente a la más antigua (oposición, cancelación,
rectificación, acceso). Con `estado=ATENDIDA` recibe 2, con `tipo=RECTIFICACION` 1, con el
`personaId` de Tomás sus 2 solicitudes y con `tamano=2` las 2 más recientes con `totalElementos: 4`.
Un estado desconocido en el filtro responde `400 VALIDACION`.

**Resultado:** ✅ Aprobado.

### CA-4 · El permiso se aplica con su ámbito (ADR-0007)

> Sin `seguridad.arco:GESTIONAR`, `403 VAL-015`; sin sesión, `401`. La matriz es configurable: con el
> permiso en ámbito `CLUB` el listado es una página vacía y no se registra ni se cambia el estado; en
> ámbito `PROPIO` el titular registra y ve solo lo suyo; cambiar el estado exige ámbito `LIGA`.

**Resultado obtenido con la matriz inicial.** El Secretario recibe `403 VAL-015` al listar, registrar y
cambiar estado. La Directiva y el titular también reciben `403` al listar, y sin sesión la respuesta es
`401`.

**Resultado obtenido con la matriz configurada.** El Administrador otorgó el permiso a los roles
Directiva de club y Jugador o tutor con `PATCH /roles/{id}/permisos` (`200`, auditado como
`CAMBIAR_PERMISOS_ROL` con su motivo), es decir, configuración y no código (H-20):

| Cuenta | Listar | Registrar | Cambiar estado |
| --- | --- | --- | --- |
| Directiva (CLUB) | `200` con `totalElementos: 0` | `404` para cualquier titular | `404` |
| Titular Tomás (PROPIO) | `200` con solo sus solicitudes (2, después 3) | `201` para sí mismo; `404` para otra persona | `404` (exige LIGA) |

**Resultado:** ✅ Aprobado.

### CA-5 · Una cancelación se atiende sin borrar datos (DEC-67)

> Atender una solicitud de cancelación registra su seguimiento y nunca borra la persona ni su
> historial; la tabla de solicitudes no admite borrado desde la aplicación.

**Resultado obtenido** ([base y auditoría](evidencias/B-01/base-y-auditoria.txt)). La cancelación de
Carlos pasó por `EN_ATENCION` y quedó `ATENDIDA` con la respuesta «Se bloquearon el correo y el teléfono
del titular; el historial deportivo se conserva (DEC-67).». La persona sigue en la base con su CURP,
nombre, correo y teléfono, y la base conserva las 3 personas sembradas. Con el rol de la aplicación
(`SET ROLE siglim_app`), un `DELETE` sobre `solicitudes_arco` falla con «permission denied for table
solicitudes_arco»: el rol solo tiene `INSERT`, `SELECT` y `UPDATE`, y la política `arco_editar` limita
el `UPDATE` a la liga. Ver la observación O-2.

**Resultado:** ✅ Aprobado.

### CA-6 · Cada operación se audita sin la descripción ni la respuesta (RF-093)

**Resultado obtenido.** La auditoría de `M-01` tiene un `REGISTRAR_SOLICITUD_ARCO` por cada alta, con
el detalle `{"tipo": …}`. Cada cambio de estado deja un `CAMBIAR_ESTADO_SOLICITUD_ARCO` con
`{"estadoAnterior", "estadoNuevo"}`. El alta que hizo el titular aparece con su propio perfil. Ningún
evento contiene el texto de una descripción o respuesta, ni correos, teléfonos o CURP
([base y auditoría](evidencias/B-01/base-y-auditoria.txt)). La pantalla «Auditoría» muestra los
eventos de `M-01 · Administración y seguridad` ([captura](evidencias/B-01/CA6-auditoria-eventos-arco.png)),
y el detalle del cierre de la cancelación solo trae `estadoNuevo: ATENDIDA` y `estadoAnterior:
EN_ATENCION` ([captura](evidencias/B-01/CA6-detalle-sin-respuesta.png)).

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Registro de los cuatro tipos y validaciones | ✅ Aprobado |
| CA-2 Seguimiento y transiciones | ✅ Aprobado |
| CA-3 Listado y filtros | ✅ Aprobado |
| CA-4 Permiso con ámbito LIGA, CLUB y PROPIO | ✅ Aprobado |
| CA-5 Cancelación sin borrado | ✅ Aprobado |
| CA-6 Auditoría sin datos personales | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `c6deab8` el 21-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-01/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `SolicitudArcoServiceTest` (15), `SolicitudArcoControllerTest` (19) y `SolicitudesArcoIT` (4) |
| Frontend | — | No aplica: B-01 no tiene interfaz propia y esta subtarea solo agrega documentación |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `SolicitudArcoServiceTest` «RF-098: registra la solicitud RECIBIDA, autoriza sobre el titular y audita solo el tipo», «RF-098: sin titular, con un tipo desconocido o sin descripción responde 400 VALIDACION» y «RF-098: una persona inexistente responde 422 REFERENCIA_INVALIDA sin insertar»; `SolicitudArcoControllerTest` «RF-098: POST /solicitudes-arco responde 201 sin Location con la solicitud RECIBIDA» |
| CA-2 | `SolicitudArcoServiceTest` «RF-098: RECIBIDA pasa a EN_ATENCION sin respuesta y se audita la transición», «RF-098: al cerrar guarda la respuesta y el perfil que atendió», «RF-098: cerrar sin respuesta, volver a RECIBIDA o responder sin cerrar responde 400 VALIDACION», «RF-098: una transición no permitida responde 422 RF-098 y no cambia nada» y «RF-098: una solicitud inexistente responde 404»; `SolicitudesArcoIT` «RF-098: registrar, poner en atención y atender una solicitud, con su auditoría» |
| CA-3 | `SolicitudArcoServiceTest` «RF-098: con ámbito LIGA lista las solicitudes con el mismo alcance en filas y total»; `SolicitudArcoControllerTest` «RF-098: GET /solicitudes-arco pasa los filtros al servicio y responde la página con no-store» y «RF-098: un estado desconocido en el filtro responde 400»; `SolicitudesArcoIT` «RF-098: el listado filtra por titular y estado, con el total de las filas visibles» |
| CA-4 | `SolicitudArcoServiceTest` «ADR-0007: con ámbito CLUB responde una página vacía sin consultar la tabla [PROPUESTA]» y «VAL-015: sin el permiso no registra ni audita»; `SolicitudArcoControllerTest` «RNF-003: seguridad de GET, POST y PATCH»; `SolicitudesArcoIT` «VAL-015: una cuenta sin seguridad.arco:GESTIONAR no lista ni registra». El ámbito `PROPIO` con un titular real solo se comprueba en esta corrida |
| CA-5 | La ausencia de borrado y los privilegios de la tabla solo se comprueban en esta corrida |
| CA-6 | `SolicitudArcoServiceTest` «RF-098: registra la solicitud RECIBIDA, … y audita solo el tipo»; `SolicitudesArcoIT` «RF-098: registrar, poner en atención y atender una solicitud, con su auditoría» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · Las fechas de la solicitud se responden en UTC.** `recibidaEn` y `atendidaEn` llegan como
`2026-11-21T19:29:17.197739Z`. Es ISO 8601 válido, pero las convenciones de API (§1, «Fechas y horas»)
piden el desplazamiento de la zona de negocio (`2026-10-18T10:00:00-06:00`), como responden otros
endpoints (por ejemplo, `cambiadoEn` del historial del registro). Se recomienda alinear el formato
cuando se construya la interfaz ARCO.

**O-2 · El «bloqueo» de una cancelación no tiene mecanismo en el sistema.** DEC-67 dice que una
cancelación se resuelve como bloqueo y no como borrado. B-01 cumple la mitad que le toca: registra el
seguimiento y nunca borra. Pero atender la solicitud no bloquea nada: el correo y el teléfono de Carlos
siguen visibles y no existe una marca de bloqueo sobre la persona. Hoy el bloqueo queda como un paso
manual fuera del sistema. Como B-01 no lo incluye en su alcance («solo se registra el seguimiento»),
no se registra como defecto; se recomienda que el equipo decida si abre una historia para el bloqueo
de datos.

Además, B-01 no tiene pantalla propia: las solicitudes se gestionan solo por API. No hay una UI-xx del
ERS que la pida, así que tampoco es un defecto de esta historia.

## 7. Conclusión

B-01 cumple sus criterios: la liga registra las cuatro solicitudes ARCO, les da seguimiento con
transiciones controladas y respuesta obligatoria al cerrar, las consulta con filtros, aplica el
permiso con su ámbito, nunca borra datos y lo audita todo sin datos personales. **B-01 puede
cerrarse.** Las observaciones O-1 y O-2 no bloquean el cierre.
