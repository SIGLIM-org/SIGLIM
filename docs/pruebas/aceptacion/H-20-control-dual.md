# Prueba de aceptación · H-20 Editar los permisos de un rol con control dual

| Campo | Valor |
| --- | --- |
| Historia | H-20 · subtarea H-20.3 |
| Requisitos | RF-095, RF-096, VAL-022, VAL-023, RN-053 |
| Pantallas | F-13 (administración: roles y permisos) |
| Versión probada | `develop` en `b76d578`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000` y `20261106160000` |
| Fecha | 2026-11-08 |
| Ejecutó | Geovani (equipo de desarrollo) con un agente de IA en el entorno local |
| Resultado global | **Aprobado (4 de 4 criterios)** |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local (contenedor Docker efímero) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cinco migraciones del repositorio aplicadas |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS y firma con ES256 los tokens de sesión; el inicio de sesión de la pantalla se hace con cuentas ficticias |
| Backend | `java -jar` del paquete de `develop` en `http://localhost:8081`, contra la base y la autenticación locales (variables de entorno, sin tocar `backend/.env`) |
| Frontend | `npm run dev -- --mode h20` en `http://localhost:5174`, con `VITE_API_URL` hacia el backend local |
| Navegador | Chromium sin interfaz, controlado con Playwright, ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase. El arnés (autenticación local, cuentas, roles de prueba y script de Playwright) vive
fuera del repositorio; aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador de aceptación** | `ADMINISTRADOR_GENERAL` · LIGA | CA-1, CA-2 y CA-4 (`seguridad.rol:EDITAR` y `seguridad.rol:CONSULTAR`) |
| **Presidente de aceptación** | `PRESIDENTE` · LIGA | CA-3 (`seguridad.permisos:APROBAR_INCOMPATIBLE` y `seguridad.rol:CONSULTAR`) |

Roles creados para la prueba en la liga local, todos asignables y con ámbito `LIGA`:

| Rol | Permisos al iniciar | Uso |
| --- | --- | --- |
| Coordinador de canchas (`COORDINADOR_CANCHAS`) | `canchas.cancha:CONSULTAR` | CA-1: cambio compatible |
| Auxiliar de programación (`AUXILIAR_PROGRAMACION`) | `programacion.jornada:CONSULTAR` | CA-2 y CA-3: par incompatible que se aprueba |
| Auxiliar de comunicados (`AUXILIAR_COMUNICADOS`) | `publicacion.comunicado:REDACTAR` | CA-2 y CA-3: par incompatible que se rechaza |

Todos los datos son ficticios. El catálogo tiene **73 permisos** y el par incompatible de
programación (`programacion.jornada:EDITAR` con `programacion.jornada:APROBAR`) está en la
semilla de la migración, no se agregó para la prueba.

## 3. Escenarios

### CA-1 · Un cambio sin pares incompatibles se aplica y se audita

> Dado un cambio sin pares incompatibles, cuando se guarda con motivo, entonces se aplica y se
> audita.

**Pasos**

1. Iniciar sesión como **Administrador de aceptación** y abrir `/app/administracion/roles`.
2. Elegir el rol **Coordinador de canchas** y comprobar que la matriz muestra 1 de 73 permisos.
3. Marcar «Capturar Formato 1 u oferta Formato 2 (RF-116, RF-117)» (`canchas.solicitud:CREAR`),
   que es compatible con «Consultar canchas y disponibilidad» (`canchas.cancha:CONSULTAR`).
4. Pulsar «Guardar cambios», escribir el motivo `La Liga autorizó que este rol capture solicitudes
   de cancha` y confirmar.
5. Revisar la pantalla y la base de datos (matriz del rol y auditoría).

**Resultado esperado**

- El `PATCH /api/v1/roles/{id}/permisos` responde **200** con el rol ya actualizado.
- La matriz queda con **2 de 73** permisos y la pantalla confirma que el cambio se aplicó.
- La auditoría registra `CAMBIAR_PERMISOS_ROL` con el motivo, el perfil que lo hizo y el permiso
  agregado.

**Resultado obtenido:** **Aprobado.**

- La pantalla pasó de «1 de 73» a «2 de 73 permisos seleccionados» y el botón «Guardar cambios»
  quedó deshabilitado porque ya no había cambios pendientes.
- El `PATCH` respondió **200** con `COORDINADOR_CANCHAS` y sus dos permisos
  (`canchas.cancha:CONSULTAR` y `canchas.solicitud:CREAR`); el cuerpo crudo está en la evidencia.
- La pantalla mostró «Cambios aplicados a la matriz del rol y registrados en la auditoría.».
- La base confirmó los dos permisos y **una** fila de auditoría `CAMBIAR_PERMISOS_ROL` con motivo
  «La Liga autorizó que este rol capture solicitudes de cancha», `detalle.permisosAgregados =
  ["canchas.solicitud:CREAR"]` y el perfil del Administrador.

**Evidencia:** [`CA1-matriz-antes.png`](evidencias/H-20/CA1-matriz-antes.png),
[`CA1-modal-guardar.png`](evidencias/H-20/CA1-modal-guardar.png),
[`CA1-cambios-aplicados.png`](evidencias/H-20/CA1-cambios-aplicados.png),
[`CA1-CA4-peticiones-api.txt`](evidencias/H-20/CA1-CA4-peticiones-api.txt),
[`CA1-CA4-base-de-datos.txt`](evidencias/H-20/CA1-CA4-base-de-datos.txt).

**Pruebas automáticas relacionadas:**
[`RolesPermisosIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolesPermisosIT.java)
(«H-20 criterio 1: un cambio sin pares incompatibles responde 200, se aplica y se audita»),
[`RolServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolServiceTest.java)
(«RF-095: un cambio sin pares incompatibles se aplica y se audita con su motivo») y
[`PantallaRolesPermisos.test.tsx`](../../../frontend/src/features/roles/PantallaRolesPermisos.test.tsx)
(«RF-095: un cambio compatible se guarda con su motivo y confirma la aplicación»).

### CA-2 · Un par incompatible responde 202 y queda pendiente

> Dado un cambio con un par incompatible, cuando se guarda, entonces responde 202 y queda
> pendiente.

**Pasos**

1. Con la misma sesión de Administrador, elegir el rol **Auxiliar de programación** (1 de 73
   permisos).
2. Marcar «Registrar y ajustar jornadas y partidos (RF-033, RF-036, RF-040)»
   (`programacion.jornada:EDITAR`) y «Aprobar o devolver la jornada (RF-121)»
   (`programacion.jornada:APROBAR`).
3. Comprobar el aviso de control dual y pulsar «Solicitar aprobación».
4. Escribir el motivo `Se requiere que el auxiliar también apruebe la programación` y pulsar
   «Enviar solicitud».
5. Repetir con **Auxiliar de comunicados**: agregar
   `publicacion.comunicado:APROBAR` (par incompatible con `publicacion.comunicado:REDACTAR`) y
   enviar la solicitud con su motivo.
6. Revisar la pantalla y la base de datos.

**Resultado esperado**

- El `PATCH` responde **202** con la solicitud en estado `SOLICITADO` y los permisos que se
  agregan y quitan; la matriz del rol **no** cambia.
- La pantalla avisa que el cambio no se aplica y que el Presidente debe aprobarlo o rechazarlo.
- La auditoría registra `SOLICITAR_CAMBIO_PERMISOS` con el motivo y el par incompatible; **no**
  registra `CAMBIAR_PERMISOS_ROL`.

**Resultado obtenido:** **Aprobado.**

- El primer `PATCH` respondió **202** con
  `estado: "SOLICITADO"`, `permisosAgregados: ["programacion.jornada:APROBAR",
  "programacion.jornada:EDITAR"]`, `permisosQuitados: []` y
  `solicitadoPor: Administrador de aceptación`.
- La pantalla mantuvo **1 de 73** permisos en la matriz y mostró «El cambio quedó pendiente: la
  matriz del rol no se modificó y el Presidente debe aprobarlo o rechazarlo.».
- El segundo `PATCH` (comunicados) respondió **202** con
  `permisosAgregados: ["publicacion.comunicado:APROBAR"]`.
- La base confirmó la matriz sin cambios y **dos** solicitudes `SOLICITADO`; la auditoría tiene
  **dos** eventos `SOLICITAR_CAMBIO_PERMISOS` con su motivo y los pares incompatibles, y **ningún**
  `CAMBIAR_PERMISOS_ROL` para esos roles.

**Evidencia:** [`CA2-par-incompatible.png`](evidencias/H-20/CA2-par-incompatible.png),
[`CA2-modal-solicitud.png`](evidencias/H-20/CA2-modal-solicitud.png),
[`CA2-solicitud-pendiente.png`](evidencias/H-20/CA2-solicitud-pendiente.png),
[`CA1-CA4-peticiones-api.txt`](evidencias/H-20/CA1-CA4-peticiones-api.txt),
[`CA1-CA4-base-de-datos.txt`](evidencias/H-20/CA1-CA4-base-de-datos.txt).

**Pruebas automáticas relacionadas:**
[`RolesPermisosIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolesPermisosIT.java)
(«H-20 criterio 2: un par incompatible queda SOLICITADO y no toca la matriz»),
[`RolServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolServiceTest.java)
(«H-20 criterio 2: un par incompatible responde 202 y deja la solicitud pendiente») y
[`PantallaRolesPermisos.test.tsx`](../../../frontend/src/features/roles/PantallaRolesPermisos.test.tsx)
(«VAL-023: al crear un par incompatible avisa y envía una solicitud en lugar de aplicar»).

### CA-3 · El Presidente aprueba o rechaza con motivo

> Dado el Presidente, cuando aprueba o rechaza con motivo, entonces se aplica o descarta.

**Pasos**

1. Cerrar la sesión del Administrador e iniciar sesión como **Presidente de aceptación**.
2. Abrir `/app/administracion/roles` y la pestaña **Solicitudes pendientes**: debe haber 2.
3. En la solicitud de **Auxiliar de programación**, pulsar «Aprobar», escribir el motivo
   `Procede: la separación de funciones se revisó` y confirmar «Aprobar cambio».
4. En la solicitud de **Auxiliar de comunicados**, pulsar «Rechazar», escribir el motivo
   `No procede: concentraría redacción y aprobación` y confirmar «Rechazar cambio».
5. Volver a la pestaña **Matriz de permisos** y comprobar ambos roles; revisar la base de datos.

**Resultado esperado**

- Cada dictamen responde **200** con la solicitud `APROBADO` o `RECHAZADO`, el resolutor y el
  motivo en `observaciones`.
- La aprobación aplica la matriz solicitada (3 de 73 permisos en programación); el rechazo la deja
  como estaba (1 de 73 en comunicados).
- La auditoría registra `APROBAR_CAMBIO_PERMISOS` y `RECHAZAR_CAMBIO_PERMISOS` con el motivo del
  dictamen; las solicitudes resueltas no se reabren.

**Resultado obtenido:** **Aprobado.**

- La pestaña mostró «2 solicitudes pendientes», con el rol, quién solicitó, los permisos que se
  agregan y el motivo de cada una. Tras aprobar, el contador pasó a 1 y la pantalla confirmó
  «Solicitud aprobada: la matriz de «Auxiliar de programación» se actualizó y el dictamen quedó en
  la auditoría.».
- Los dos `POST` de resolución respondieron **200**: la solicitud de programación quedó
  `APROBADO`, con `resueltoPor` el Presidente y `observaciones` con el motivo; la de comunicados
  quedó `RECHAZADO` con su motivo.
- La matriz de **Auxiliar de programación** quedó con **3 de 73** permisos
  (`CONSULTAR`, `EDITAR` y `APROBAR`), y la de **Auxiliar de comunicados** siguió con **1 de 73**
  (`REDACTAR`), como se ve en las capturas y en la base.
- La auditoría registró **un** `APROBAR_CAMBIO_PERMISOS` y **un** `RECHAZAR_CAMBIO_PERMISOS` con
  los motivos del dictamen y el perfil del Presidente.

**Evidencia:** [`CA3-solicitudes-pendientes.png`](evidencias/H-20/CA3-solicitudes-pendientes.png),
[`CA3-modal-aprobar.png`](evidencias/H-20/CA3-modal-aprobar.png),
[`CA3-solicitud-aprobada.png`](evidencias/H-20/CA3-solicitud-aprobada.png),
[`CA3-modal-rechazar.png`](evidencias/H-20/CA3-modal-rechazar.png),
[`CA3-solicitud-rechazada.png`](evidencias/H-20/CA3-solicitud-rechazada.png),
[`CA3-matriz-aprobada.png`](evidencias/H-20/CA3-matriz-aprobada.png),
[`CA3-matriz-rechazada.png`](evidencias/H-20/CA3-matriz-rechazada.png),
[`CA1-CA4-peticiones-api.txt`](evidencias/H-20/CA1-CA4-peticiones-api.txt),
[`CA1-CA4-base-de-datos.txt`](evidencias/H-20/CA1-CA4-base-de-datos.txt).

**Pruebas automáticas relacionadas:**
[`RolesPermisosIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolesPermisosIT.java)
(«H-20 criterio 3: el Presidente aprueba y el cambio pendiente se aplica» y «… rechaza y el cambio
se descarta»),
[`RolServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolServiceTest.java)
(las dos pruebas de criterio 3) y
[`PantallaRolesPermisos.test.tsx`](../../../frontend/src/features/roles/PantallaRolesPermisos.test.tsx)
(«RF-096: el Presidente aprueba una solicitud con motivo y ve el resultado»).

### CA-4 · El Administrador no puede editar su propio rol (VAL-022)

> Dado el Administrador, cuando edita su propio rol, entonces se rechaza (VAL-022).

**Pasos**

1. Cerrar la sesión del Presidente e iniciar sesión como **Administrador de aceptación**.
2. Abrir `/app/administracion/roles`: la pantalla selecciona **Administrador general**, el rol del
   propio usuario.
3. Comprobar el aviso de VAL-022 y que las casillas de la matriz estén deshabilitadas.
4. Con el token de esa misma sesión, intentar el cambio por la API:
   `PATCH /api/v1/roles/{id-del-rol-propio}/permisos` con la matriz sin
   `auditoria.registro:CONSULTAR` y un motivo.
5. Revisar la respuesta y la base de datos (matriz y auditoría).

**Resultado esperado**

- La interfaz bloquea la edición y explica que el cambio se rechaza con VAL-022.
- El backend responde **403** con `codigo: "VAL-022"` y no modifica la matriz.
- El intento rechazado no deja auditoría de cambio.

**Resultado obtenido:** **Aprobado.**

- La pantalla mostró el aviso «No puedes cambiar los permisos de tu propio rol. La edición se
  rechaza con el código VAL-022; puedes consultar la matriz y pedir a otra cuenta autorizada que
  la modifique.», con **0** casillas habilitadas y **21 de 73** permisos solo de lectura.
- El `PATCH` directo respondió **403** con
  `codigo: "VAL-022"`, `title: "Acción no permitida"` y `detail: "No puedes cambiar los permisos de
  tu propio rol (VAL-022). Otra cuenta con el permiso debe hacerlo."`.
- La matriz de `ADMINISTRADOR_GENERAL` siguió igual y la consulta de auditoría devolvió **0**
  eventos `CAMBIAR_PERMISOS_ROL` sobre ese rol.

**Evidencia:** [`CA4-rol-propio-bloqueado.png`](evidencias/H-20/CA4-rol-propio-bloqueado.png),
[`CA1-CA4-peticiones-api.txt`](evidencias/H-20/CA1-CA4-peticiones-api.txt),
[`CA1-CA4-base-de-datos.txt`](evidencias/H-20/CA1-CA4-base-de-datos.txt).

**Pruebas automáticas relacionadas:**
[`RolesPermisosIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolesPermisosIT.java)
(«H-20 criterio 4: el Administrador no edita su propio rol (VAL-022) y no se audita»),
[`RolServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/seguridad/RolServiceTest.java)
(«H-20 criterio 4: el Administrador no edita su propio rol (403 VAL-022), sin escribir») y
[`PantallaRolesPermisos.test.tsx`](../../../frontend/src/features/roles/PantallaRolesPermisos.test.tsx)
(«VAL-022: bloquea la edición del propio rol y explica el código»).

## 4. Observaciones

No se encontraron defectos que afecten los criterios de aceptación. Dos observaciones menores, sin
impacto en el resultado:

- El encabezado de F-13 invita a «Ajustar la matriz de cada rol» también cuando la sesión es del
  Presidente, que solo consulta y dictamina. Es un detalle de texto de H-20.2, no de la regla de
  negocio; los permisos se respetan y la matriz aparece deshabilitada.
- El intento de VAL-022 no se audita porque se rechaza antes de escribir, igual que en las pruebas
  automáticas de H-20.1. La denegación no deja rastro de cambio, que es el comportamiento esperado.

## 5. Verificación automática

| Comando | Resultado |
| --- | --- |
| `backend/./mvnw verify` | **Correcto**: `BUILD SUCCESS`, 202 pruebas, 0 fallos, 1 omitida (1:20 min) |
| `frontend/npm run lint` | **Correcto**: sin errores |
| `frontend/npm test` | **Correcto**: 64 archivos y 520 pruebas aprobadas |
| `frontend/npm run build` | **Correcto**: compilación de producción completada |

## 6. Conclusión

Los cuatro criterios de aceptación de H-20 se cumplen en el entorno local: el cambio compatible se
aplica y se audita, el par incompatible queda pendiente con 202, el Presidente lo aprueba o rechaza
con motivo y el Administrador no puede editar su propio rol. La subtarea H-20.3 queda **aprobada**;
la historia H-20 puede cerrarse cuando su PR se fusione en `develop`.
