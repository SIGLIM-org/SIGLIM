# Prueba de aceptación · B-03 Registrar reglas interpretadas pendientes de autorización

| Campo | Valor |
| --- | --- |
| Historia | B-03 (SCRUM-212) · subtarea B-03.3 (SCRUM-371) |
| Requisitos | RF-007, RF-006, RF-003, RF-093, RN-006, VAL-015 · decisiones DEC-57, DEC-58, DEC-59 |
| Pantallas | F-11 (estructura de la temporada), pestaña **Interpretaciones** [PROPUESTA] y pestaña **Parámetros** · F-14 (auditoría) |
| Versión probada | `develop` en `9644ef5` (incluye B-03.1 #87 y B-03.2 #91), con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-07 |
| Ejecutó | López Meza Leonardo (Scrum Master y responsable de calidad) |
| Resultado global | **Aprobado** (4 de 4 criterios y 2 de 2 verificaciones adicionales) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Proyecto compartido de Supabase (PostgreSQL 17) con las migraciones de `develop` aplicadas por la integración de GitHub (DEC-52) |
| Autenticación | Supabase Auth del mismo proyecto, inicio de sesión con correo |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Google Chrome en Windows, con las herramientas de desarrollador (F12) para las respuestas de la API |

**Peticiones desde la consola.** Los casos que la interfaz impide a propósito (rechazar con
observaciones cortas, resolver sin permiso, volver a resolver) se enviaron desde la consola de F12
**en la misma sesión del navegador**, con el token de la persona logueada y por el mismo proxy
`/api/v1` que usa el frontend (anexo A). Las capturas muestran el estado HTTP y el cuerpo de la
respuesta; el valor del token nunca se imprimió.

**Estado inicial**, comprobado en la base antes de la corrida: una temporada, «Temporada
2026–2027» (`bae83713…`), en estado `CONFIGURACION`, sin parámetros registrados y sin
interpretaciones. En el catálogo inicial `temporadas.interpretacion:REGISTRAR` solo está en
**Administrador general** y `temporadas.interpretacion:APROBAR` solo en **Presidente**.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `temporadas.interpretacion:REGISTRAR`, `temporadas.parametros:EDITAR`, `temporadas.temporada:CONSULTAR` y `auditoria.registro:CONSULTAR`; **sin** `temporadas.interpretacion:APROBAR` |
| **Presidente** | Presidente del Comité Ejecutivo · Liga (cuenta creada para esta prueba con la invitación de H-03) | `temporadas.interpretacion:APROBAR`, `temporadas.temporada:CONSULTAR` y `auditoria.registro:CONSULTAR`; **sin** `…:REGISTRAR` |
| **Capturista** | Capturista · Liga | Ningún permiso de `temporadas.*` |

Preparación (antes de los criterios):

1. El **Administrador** invitó la cuenta del Presidente desde **Administración → Usuarios** con el
   rol **Presidente** y ámbito **Liga** (H-03); la auditoría registró `INVITAR_USUARIO` a las
   18:37 y la cuenta se activó desde el correo.
2. El **Administrador** registró la **versión 1 de los parámetros** de la temporada en F-11 →
   **Parámetros** (H-05) a las 18:43, marcada **No oficial**, para que CA-3 comparara una versión
   real antes y después de autorizar.

Datos creados durante la corrida (todas las descripciones empiezan con `[PRUEBA B-03.3]`):

| Interpretación | Descripción | Destino |
| --- | --- | --- |
| **I1** (`14c34c9c…`) | «[PRUEBA B-03.3] Si un partido se suspende por lluvia después del minuto 60, el marcador al momento de la suspensión se considera resultado final.» | El Presidente la **autorizó** (CA-2, CA-3) |
| **I2** (`a713a1cd…`) | «[PRUEBA B-03.3] Un equipo que al minuto 15 tenga menos de 7 jugadores en la cancha pierde por incomparecencia.» | El Presidente la **rechazó** con observaciones (CA-2) |
| **I3** (`1a4f8cd4…`) | «[PRUEBA B-03.3] Interpretación para comprobar que sin el permiso de aprobar no se puede resolver.» | Quedó pendiente para CA-4 y al cierre el Presidente la rechazó (V2) |

Las interpretaciones no se borran (no hay `DELETE`, RF-003): al terminar, las tres quedaron
resueltas y ninguna quedó pendiente en la base compartida.

## 3. Escenarios

### CA-1 · Una interpretación registrada queda PENDIENTE con quién y cuándo la registró

> Dada una interpretación, cuando se registra, entonces queda PENDIENTE con quién y cuándo la
> registró.

**Pasos**

1. Iniciar sesión como **Administrador**, abrir **Administración → Temporadas**, pulsar
   **Estructura** en «Temporada 2026–2027» y elegir la pestaña **Interpretaciones**.
2. Pulsar **Registrar interpretación**, capturar la descripción de I1 y confirmar.
3. Revisar el aviso y la tarjeta de I1.
4. Registrar I2 e I3 de la misma forma y revisar el filtro **Todas**.
5. Verificar en la base la autoría, la fecha y la auditoría de las tres altas (anexo B).

**Resultado esperado**

- El aviso dice «La interpretación quedó registrada y pendiente de autorización.» y el filtro pasa
  a **Todas**.
- La tarjeta muestra la insignia **Pendiente**, «Registrada el {fecha y hora} por López Meza
  Leonardo (tú)» y «Pendiente de la autorización de la Presidencia.», sin botones de resolución
  (el Administrador no tiene `…:APROBAR`).
- En la base, cada interpretación nace en `PENDIENTE` con `registrada_por` = perfil del
  Administrador, `registrada_en` y sin `resuelta_por` ni `resuelta_en`; el cuerpo del `POST` no
  admite estado ni autoría, que fija el servidor.
- La auditoría registra `REGISTRAR_INTERPRETACION` (módulo M-02) por cada alta.

**Resultado obtenido:** **Aprobado.** El modal mostró el contador «145/2000» y la aclaración de
que la interpretación no cambia los parámetros. Al confirmar apareció el aviso «La interpretación
quedó registrada y pendiente de autorización.» y la tarjeta de I1 con **PENDIENTE**, «Registrada el
7 nov 2026, 18:48 por López Meza Leonardo (tú)» y «Pendiente de la autorización de la
Presidencia.», sin acciones de resolución. Con I2 e I3 el filtro **Todas** mostró «3
interpretaciones», las tres pendientes con su autoría y hora (18:48, 18:56 y 18:56). En la base,
las tres filas nacieron en `PENDIENTE` con `registrada_por` del Administrador y `registrada_en`
18:48:25, 18:56:08 y 18:56:18, y la auditoría registró tres `REGISTRAR_INTERPRETACION` con
`estado: PENDIENTE` y la temporada.

**Evidencia**

- [`CA1-modal-registro.png`](evidencias/B-03/CA1-modal-registro.png) — modal con la descripción de I1.
- [`CA1-interpretacion-pendiente.png`](evidencias/B-03/CA1-interpretacion-pendiente.png) — aviso y tarjeta de I1 en **Pendiente** con autoría y fecha.
- [`CA1-tres-pendientes.png`](evidencias/B-03/CA1-tres-pendientes.png) — filtro **Todas** con I1, I2 e I3 pendientes.
- [`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt) — filas de `interpretaciones_regla` y eventos `REGISTRAR_INTERPRETACION`.

**Pruebas automáticas relacionadas**

- `InterpretacionesIT` · «B-03.1: el Administrador registra en PENDIENTE con su autoría y queda auditado».
- `PestanaInterpretaciones.test.tsx` · «RF-007: registra una interpretación y la deja pendiente».

### CA-2 · El Presidente autoriza o rechaza, queda auditado y el rechazo exige observaciones

> Dado el Presidente, cuando la autoriza o la rechaza, entonces cambia de estado y queda auditada;
> el rechazo exige observaciones.

**Pasos**

1. Iniciar sesión como **Presidente** y abrir F-11 de «Temporada 2026–2027» → **Interpretaciones**.
2. Verificar que la pestaña abre en el filtro **Pendientes**, que no hay botón **Registrar
   interpretación** y que cada tarjeta ofrece **Autorizar** y **Rechazar**.
3. En I1, pulsar **Autorizar**, escribir la nota opcional «Autorizada en la prueba de aceptación
   B-03.3.» y confirmar.
4. En I2, pulsar **Rechazar**, escribir «No aplica» (9 caracteres) y pulsar **Rechazar
   interpretación**.
5. Desde la consola, enviar el rechazo de I2 con `observaciones: "No aplica"` (anexo A).
6. En el modal, cambiar las observaciones a «La regla ya está en el reglamento vigente: no requiere
   interpretación.» y confirmar.
7. Revisar los filtros **Autorizadas** y **Rechazadas**, y en **Administración → Auditoría**
   filtrar el módulo **M-02**.

**Resultado esperado**

- Al autorizar, I1 pasa a **Autorizada** con «Resuelta el {fecha y hora} por {Presidente} (tú)» y
  la nota de autorización.
- Con menos de 10 caracteres el modal marca el campo con «Escribe observaciones de al menos 10
  caracteres para rechazar la interpretación.» y **no envía** la petición (DEC-58).
- La API rechaza las mismas observaciones cortas con `400` y `codigo: "RF-007"`; I2 sigue
  **Pendiente**.
- Con observaciones válidas, I2 pasa a **Rechazada** mostrando sus observaciones.
- La auditoría de M-02 muestra los `REGISTRAR_INTERPRETACION` del Administrador y un
  `RESOLVER_INTERPRETACION` del Presidente por cada resolución, con `estadoAnterior: PENDIENTE` y
  el estado nuevo; el del rechazo lleva las observaciones como motivo. El intento con
  observaciones cortas no aparece.

**Resultado obtenido:** **Aprobado.** La vista del Presidente abrió en **Pendientes** con «3
interpretaciones · Pendientes», sin botón de registro, cada tarjeta con **Rechazar** y
**Autorizar** y la autoría como «otro perfil de la Liga». El modal de autorización mostró la
interpretación propuesta y la nota opcional («45/500»); al confirmar, I1 quedó **AUTORIZADA** con
«Resuelta el 7 nov 2026, 19:07 por Presidente Lio (tú)» y la nota. Con «No aplica» (9/500) el
modal marcó el error de los 10 caracteres sin enviar nada. La consola recibió `400` con
`codigo: "RF-007"`, `title: "Solicitud inválida"` y el `detail` «Escribe observaciones de al menos
10 caracteres para rechazar la interpretación.», e I2 siguió pendiente. Con las observaciones
válidas, I2 quedó **RECHAZADA** con «Resuelta el 7 nov 2026, 19:18 por Presidente Lio (tú)» y sus
observaciones. La auditoría filtrada por M-02 mostró 7 eventos del módulo, encabezados por los
dos `RESOLVER_INTERPRETACION` del Presidente (19:07:38 y 19:18:03), el segundo con el motivo del
rechazo; la captura muestra esas dos filas y el resto de los eventos de la corrida está en el
anexo B. En la base,
los dos eventos llevan `estadoAnterior: PENDIENTE` y el estado nuevo, y no hay rastro del intento
con observaciones cortas.

**Evidencia**

- [`CA2-presidente-pendientes.png`](evidencias/B-03/CA2-presidente-pendientes.png) — vista del Presidente: filtro **Pendientes**, sin registro y con **Autorizar**/**Rechazar**.
- [`CA2-modal-autorizar.png`](evidencias/B-03/CA2-modal-autorizar.png) — modal de autorización de I1 con la nota.
- [`CA2-autorizada.png`](evidencias/B-03/CA2-autorizada.png) — I1 **Autorizada** con quién, cuándo y la nota.
- [`CA2-rechazo-sin-observaciones.png`](evidencias/B-03/CA2-rechazo-sin-observaciones.png) — modal de rechazo con 9 caracteres y el error del campo.
- [`CA2-rechazo-400.png`](evidencias/B-03/CA2-rechazo-400.png) — respuesta `400 RF-007` de la API.
- [`CA2-rechazada.png`](evidencias/B-03/CA2-rechazada.png) — I2 **Rechazada** con sus observaciones.
- [`CA2-auditoria-m02.png`](evidencias/B-03/CA2-auditoria-m02.png) — auditoría del módulo M-02 con las dos resoluciones.
- [`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt) — eventos completos de M-02 con su detalle.

**Pruebas automáticas relacionadas**

- `InterpretacionesIT` · «RF-007: el Presidente autoriza o rechaza; la resolución no se reabre».
- `PestanaInterpretaciones.test.tsx` · «RF-007: el Presidente autoriza con una nota opcional», «RF-007: rechazar exige observaciones de al menos 10 caracteres» y «RF-007: quien solo resuelve ve primero las pendientes y no puede registrar».

### CA-3 · Una interpretación autorizada no modifica los parámetros versionados

> Una interpretación, aun autorizada, nunca modifica los parámetros versionados (RF-007); si debe
> aplicarse, se crea una versión nueva de parámetros por H-05.

**Pasos**

1. Registrar la versión 1 de los parámetros antes de la primera interpretación (preparación).
2. Ejecutar CA-1 y CA-2 (autorización de I1 y rechazo de I2).
3. Como **Administrador**, volver a F-11 → **Parámetros**, recargar (F5) y revisar la versión
   vigente y el historial.
4. Consultar `siglim.parametros_temporada` y la auditoría de parámetros de la temporada (anexo B).

**Resultado esperado**

- Después de autorizar, la temporada sigue con **una sola versión** (versión 1), con la misma fecha
  y el mismo motivo, y el formulario indica que no hay cambios respecto a la versión 1.
- En la base, `parametros_temporada` conserva una sola fila para la temporada y la auditoría no
  registra ninguna acción sobre parámetros después de la preparación.
- La pantalla de Interpretaciones explica que una interpretación no es una regla permanente ni
  modifica los parámetros versionados; aplicarla exigiría registrar la versión 2 por H-05, que
  queda fuera de esta prueba.

**Resultado obtenido:** **Aprobado.** Después de las resoluciones, F-11 mostró «Parámetros:
versión 1», «Versión 1 vigente · desde 7 nov 2026», «Sin cambios respecto a la versión 1» y un
historial con una sola entrada (versión 1, vigente, 7 nov 2026, López Meza Leonardo). La pestaña
**Parámetros** marcó **v1** durante toda la corrida, antes y después de la autorización (capturas
de CA-1 y CA-2). En la base, la temporada tiene una sola fila en `parametros_temporada`, creada a
las 18:43:08, y el único `REGISTRAR_PARAMETROS` de la auditoría es el de la preparación; ni la
autorización de I1 (19:07) ni el rechazo de I2 (19:18) escribieron en esa tabla.

**Nota de evidencia.** La captura de la pestaña **Parámetros** antes de autorizar
(`CA3-parametros-antes.png`) no se tomó. La sustituyen el indicador **v1** de la pestaña en
[`CA1-interpretacion-pendiente.png`](evidencias/B-03/CA1-interpretacion-pendiente.png), tomada
antes de autorizar, y la hora de creación de la versión 1 y la auditoría de
[`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt).

**Evidencia**

- [`CA3-parametros-despues.png`](evidencias/B-03/CA3-parametros-despues.png) — versión 1 e historial sin cambios después de autorizar.
- [`CA1-interpretacion-pendiente.png`](evidencias/B-03/CA1-interpretacion-pendiente.png) y [`CA2-autorizada.png`](evidencias/B-03/CA2-autorizada.png) — pestaña **Parámetros v1** antes y después de autorizar.
- [`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt) — fila única de `parametros_temporada` y auditoría sin acciones de parámetros posteriores.

**Pruebas automáticas relacionadas**

- `InterpretacionReglaServiceTest` (D4: ni el alta ni la resolución escriben `parametros_temporada`).
- `InterpretacionesIT` · «RF-007: el Presidente autoriza o rechaza; la resolución no se reabre».

### CA-4 · Sin el permiso de aprobar, resolver responde 403

> Dado un usuario sin permiso, cuando intenta resolver, entonces recibe 403.

**Pasos**

1. Como **Administrador** (tiene `…:REGISTRAR` pero no `…:APROBAR`), abrir F-11 →
   **Interpretaciones** y revisar la tarjeta de I3 (pendiente).
2. Desde la consola, en esa sesión, enviar `POST /api/v1/interpretaciones-regla/{I3}/resolucion`
   con `estado: "AUTORIZADA"` (anexo A).
3. Recargar la pestaña y verificar que I3 sigue **Pendiente**.
4. Iniciar sesión como **Capturista**: revisar el menú y abrir directamente
   `/app/administracion/temporadas/{id}/estructura`.

**Resultado esperado**

- La tarjeta de I3 no ofrece **Autorizar** ni **Rechazar** al Administrador.
- La API responde `403` con `codigo: "VAL-015"` y el detalle «Tu cuenta no tiene el permiso
  necesario para esta operación.»: el permiso se comprueba **antes** que la autoría, por eso no
  responde `403 RF-007` (DEC-57).
- I3 sigue **Pendiente** y la auditoría no registra una resolución.
- El Capturista no ve **Administración** en el menú y la ruta muestra la página **Error 403** sin
  datos.

**Resultado obtenido:** **Aprobado.** Con el Administrador, I3 apareció **PENDIENTE** con
«Pendiente de la autorización de la Presidencia.» y sin acciones; I1 e I2 se mostraron resueltas
por «otro perfil de la Liga». La consola recibió `403` con `codigo: "VAL-015"`,
`title: "Acción no permitida"` y el `detail` «Tu cuenta no tiene el permiso necesario para esta
operación.», no `RF-007`. I3 siguió pendiente hasta su cierre en V2 y la auditoría no registró
ninguna resolución del Administrador. El Capturista solo vio **Inicio** y **Partidos** en el menú
y la ruta de F-11 mostró «Error 403 · No tienes permiso para esta acción» sin datos de la temporada.

**Evidencia**

- [`CA4-admin-sin-resolucion.png`](evidencias/B-03/CA4-admin-sin-resolucion.png) — I3 pendiente sin acciones de resolución para el Administrador.
- [`CA4-respuesta-403.png`](evidencias/B-03/CA4-respuesta-403.png) — respuesta `403 VAL-015` pedida desde la sesión del Administrador.
- [`CA4-capturista-403.png`](evidencias/B-03/CA4-capturista-403.png) — el Capturista sin acceso a F-11.

**Pruebas automáticas relacionadas**

- `InterpretacionesIT` · «VAL-015: sin el permiso no se registra ni se resuelve, y no queda auditoría».
- `PestanaInterpretaciones.test.tsx` · «VAL-015: un 403 del listado se muestra con ErrorConsulta».

## 4. Verificaciones adicionales

### V1 · Una resolución es definitiva

**Pasos**

1. Como **Presidente**, desde la consola, enviar de nuevo la resolución de I1 con
   `estado: "RECHAZADA"` y observaciones válidas (anexo A).

**Resultado esperado**

- La API responde `409` con `codigo: "RF-007"`; I1 sigue **Autorizada** y la auditoría no registra
  otra resolución.

**Resultado obtenido:** **Aprobado.** La consola recibió `409` con `codigo: "RF-007"`,
`title: "Conflicto con el estado actual"` y el `detail` «La interpretación ya está autorizada: una
resolución no se reabre.». I1 siguió **AUTORIZADA** con la misma resolución de las 19:07 y la
auditoría conserva una sola resolución de I1. Las observaciones del intento tenían más de 10
caracteres a propósito, para que el conflicto se debiera solo al estado.

**Evidencia**

- [`V1-reresolver-409.png`](evidencias/B-03/V1-reresolver-409.png) — respuesta `409 RF-007` junto a I1 autorizada.

**Pruebas automáticas relacionadas**

- `InterpretacionesIT` · «RF-007: el Presidente autoriza o rechaza; la resolución no se reabre».

### V2 · Cierre de los datos de prueba

**Pasos**

1. Como **Presidente**, rechazar I3 con «Cierre de la prueba B-03.3: interpretación usada solo para
   verificar permisos.»

**Resultado esperado**

- I3 queda **Rechazada** y el filtro **Pendientes** queda vacío.

**Resultado obtenido:** **Aprobado.** I3 quedó `RECHAZADA` a las 19:41 con sus observaciones y la
auditoría registró su `RESOLVER_INTERPRETACION`. El filtro **Pendientes** mostró «0
interpretaciones · Pendientes» y el estado vacío «No hay interpretaciones con este filtro».

**Evidencia**

- [`V2-cierre-sin-pendientes.png`](evidencias/B-03/V2-cierre-sin-pendientes.png) — filtro **Pendientes** vacío al cierre.
- [`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt) — estado final de las tres interpretaciones.

**Fuera de alcance de esta corrida.** La separación de funciones (DEC-57: una cuenta con los dos
permisos no resuelve su propia interpretación) y la resolución en temporada cerrada (DEC-59)
requieren dar a un rol los dos permisos o cerrar la única temporada de la base compartida; quedan
cubiertas por `InterpretacionesIT` («RF-007, DEC-57: …» y «RF-003, DEC-59: …») y por
`PestanaInterpretaciones.test.tsx` («DEC-57: …» y «DEC-59: …»).

## 5. Verificación automática

| Comando | Dónde | Resultado |
| --- | --- | --- |
| `./mvnw verify` | `backend/` | **BUILD SUCCESS** · 1100 pruebas unitarias, 0 fallos (incluidas 17 de `InterpretacionReglaServiceTest` y 26 de `InterpretacionReglaControllerTest`). Las 185 de integración se **omitieron** por falta de Docker (Testcontainers) |
| `./mvnw -Dtest=ninguna -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=InterpretacionesIT verify` | `backend/` | **BUILD SUCCESS** · `InterpretacionesIT` con Docker Desktop y PostgreSQL 17 (Testcontainers): 6 pruebas, 0 fallos, 0 omitidas, incluidas las de DEC-57 y DEC-59 |
| `npm run lint` | `frontend/` | 0 advertencias y 0 errores (oxlint, 259 archivos) |
| `npm test` | `frontend/` | 62 archivos y 500 pruebas aprobadas (incluidas las 10 de `PestanaInterpretaciones.test.tsx`) |
| `npm run build` | `frontend/` | `tsc -b` y `vite build` correctos |

Ejecutado el 2026-11-07 en la rama `test/b03-3-prueba-aceptacion`, que no cambia código respecto a
`develop` en `9644ef5`.

## 6. Defectos encontrados

Ninguno en la historia. Observaciones que no bloquean la aceptación:

- **O-1 · Autoría sin nombre.** La tarjeta muestra a la otra cuenta como «otro perfil de la Liga»,
  porque el contrato solo trae `perfiles.id` (decisión de B-03.2). El Presidente no ve **quién**
  registró la interpretación; el dato existe en la base y en la auditoría. Se propone exponer el
  nombre visible en el DTO en una iteración futura, a criterio del PO.
- **O-2 · Versión 1 de parámetros no oficial.** La versión 1 registrada en la preparación quedó en
  la base compartida como **No oficial** (`es_demo = true`) con el motivo «Version 1. Para
  criterios de aceptacion». Las versiones son inmutables: cuando la Liga confirme los valores, se
  registra la versión 2 oficial por H-05.
- **O-3 · Migración pendiente en Supabase, ajena a B-03.** La migración
  `20261106160000_tesorero_consulta_equipos` (DEF-06, PR #85) está en `develop` pero no aparece en
  `supabase_migrations.schema_migrations` y el Tesorero sigue sin `organizacion.equipo:CONSULTAR`.
  Se reporta al equipo para revisar la integración de GitHub (DEC-52).

## 7. Conclusión

La historia **B-03 cumple sus 4 criterios de aceptación** con frontend, backend y la base
compartida, recorridos en el navegador: toda interpretación nace **PENDIENTE** con quién y cuándo
la registró; solo el Presidente la autoriza o la rechaza, el rechazo exige observaciones de al
menos 10 caracteres en la interfaz y en la API, y cada resolución queda auditada; autorizar no
modifica los parámetros versionados; y sin `temporadas.interpretacion:APROBAR` la resolución se
rechaza con `403 VAL-015`. Además, una resolución no se reabre (`409 RF-007`).

La prueba confirma la integración de B-03.1 (endpoints, regla de estados, permisos y auditoría) y
B-03.2 (pestaña Interpretaciones de F-11 con sus modales y filtros). Los casos de separación de
funciones (DEC-57) y de temporada cerrada (DEC-59) quedan cubiertos por las pruebas automáticas.

## Anexo A · Peticiones desde la consola

Con la sesión de la cuenta indicada abierta en `http://localhost:5173`, en F12 → **Consola**. El
snippet busca la interpretación por su descripción en el listado y no imprime el token.

```js
const clave = Object.keys(localStorage).find((k) => k.startsWith('sb-') && k.endsWith('-auth-token'))
const { access_token } = JSON.parse(localStorage.getItem(clave))
const h = { 'Content-Type': 'application/json', Authorization: `Bearer ${access_token}` }
const lista = await (await fetch('/api/v1/interpretaciones-regla?estado=ESTADO&tamano=100', { headers: h })).json()
const objetivo = lista.contenido.find((i) => i.descripcion.includes('TEXTO'))
console.log('Interpretación:', objetivo.id, objetivo.estado)
const r = await fetch(`/api/v1/interpretaciones-regla/${objetivo.id}/resolucion`, {
  method: 'POST',
  headers: h,
  body: JSON.stringify(CUERPO),
})
console.log(r.status, await r.json())
```

| Caso | Cuenta | `ESTADO` | `TEXTO` | `CUERPO` | Respuesta |
| --- | --- | --- | --- | --- | --- |
| CA-2 paso 5 | Presidente | `PENDIENTE` | `menos de 7 jugadores` (I2) | `{ estado: 'RECHAZADA', observaciones: 'No aplica' }` | `400 RF-007` |
| CA-4 paso 2 | Administrador | `PENDIENTE` | `sin el permiso de aprobar` (I3) | `{ estado: 'AUTORIZADA' }` | `403 VAL-015` |
| V1 | Presidente | `AUTORIZADA` | `suspende por lluvia` (I1) | `{ estado: 'RECHAZADA', observaciones: 'Intento de reabrir la resolución.' }` | `409 RF-007` |

## Anexo B · Verificación en la base

Consultas a `siglim.parametros_temporada`, `siglim.interpretaciones_regla` y `siglim.auditoria`
ejecutadas al terminar la corrida, con su lectura:
[`base-y-auditoria.txt`](evidencias/B-03/base-y-auditoria.txt).
