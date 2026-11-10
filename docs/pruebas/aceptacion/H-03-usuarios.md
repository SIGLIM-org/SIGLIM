# Prueba de aceptación · H-03 Invitar usuarios y asignarles roles con ámbito

| Campo | Valor |
| --- | --- |
| Historia | H-03 (SCRUM-179) · subtarea H-03.3 (SCRUM-262) |
| Requisitos | RF-094, VAL-022, UI-029; relacionados RF-093, RN-005, RN-021, RN-054, ADR-0003 |
| Pantallas | F-12 (usuarios y roles) y pantalla de auditoría |
| Versión probada | Corrida inicial: `develop` en `1455acd` (incluye H-03.1 #66 y H-03.2 #69); sin migraciones nuevas. Re-ejecución: `develop` en `c725e01` (incluye las correcciones DEF-04 #72 y DEF-03 #73) |
| Fecha | 2026-11-05 (corrida inicial y re-ejecución, esta de 21:50 a 22:25, hora de México) |
| Ejecutó | López Meza Leonardo (Scrum Master y responsable de calidad), con apoyo de un agente de IA |
| Resultado global | **Aprobado** tras la re-ejecución (4 de 4 criterios; DEF-03 y DEF-04 corregidos y verificados, sección 9). Corrida inicial: rechazado (CA-4 rechazado; defectos DEF-03 y DEF-04) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Proyecto **compartido** de Supabase (SIGLIM, `us-east-1`), PostgreSQL 17, esquema `siglim` |
| Autenticación | Supabase Auth del mismo proyecto. Site URL: `http://localhost:3000`; Redirect URLs: `http://localhost:5173/restablecer-contrasena` |
| Correo | **Correo integrado de Supabase** (SMTP propio desactivado; el proveedor de ADR-0010 sigue pendiente) |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, con `SUPABASE_SECRET_KEY` en `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` |
| Navegador | Google Chrome de escritorio en Windows (sesión del Capturista en una segunda ventana) |

A diferencia de H-04, esta prueba **no puede correr en una base efímera**: el criterio 1 exige que
Supabase Auth cree la cuenta y envíe el correo real. Por eso se ejecuta contra el proyecto
compartido y la sección 7 lista la limpieza.

**Limitación del correo integrado.** Supabase solo envía unos cuantos correos por hora con su
servicio integrado y, según su documentación, solo a direcciones de miembros del equipo de la
organización. Para que la invitación pudiera llegar se agregó un buzón de prueba del ejecutor como
miembro del equipo (rol *Developer*, el mínimo que ofrece el plan Free). Esa condición es del
entorno de desarrollo, no de SIGLIM: con el SMTP propio de ADR-0010 desaparece.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | Ejecuta todas las operaciones de F-12 |
| **Capturista** | Capturista · Liga | Cuenta que se desactiva en CA-3 y se reactiva al final |
| **Invitado** | Se crea en CA-1 con rol Secretario (liga) · Liga | Buzón de prueba del ejecutor, miembro del equipo de Supabase y sin cuenta previa en Auth (verificado con `auth.users` antes de la corrida) |
| **Cuenta huérfana de Auth** | Existe en `auth.users` desde el 22 de octubre, sin perfil en SIGLIM | Escenario V-1 (correo ya registrado) |

Estado de la base antes de la corrida (consulta de solo lectura):

- Perfiles: Administrador, Capturista y el perfil técnico `SISTEMA`.
- Roles asignables: 11 (todos menos `SISTEMA` y `USUARIO_PUBLICO`).
- Clubes: **ninguno**. CA-2 no lo necesita, porque prueba justamente la asignación `CLUB` sin club.

## 3. Escenarios

### CA-1 · La invitación crea el perfil, la asignación y envía el correo

> Dado el Administrador, cuando invita un correo con rol y ámbito, entonces se crea el perfil, la
> asignación y el usuario recibe la invitación.

**Pasos**

1. Iniciar sesión como Administrador y abrir F-12 (`/app/administracion/usuarios`).
2. Pulsar «Invitar» y capturar: nombre «Prueba Aceptación H-03», el correo del buzón de prueba y
   el rol **Secretario (liga)** con ámbito **Liga**. Enviar.
3. Comprobar en la tabla la fila nueva con su chip de rol y estado activo; abrir su panel lateral.
4. Comprobar en la base el perfil, la asignación activa y el evento `INVITAR_USUARIO` en
   `auditoria`, sin nombre ni correo en el detalle.
5. Revisar el buzón de prueba: debe llegar el correo de invitación de Supabase.
6. Abrir el enlace del correo y anotar a qué dirección llega y si permite fijar la contraseña.

**Resultado esperado**

- `POST /api/v1/usuarios` responde `201`; la fila aparece en F-12 con el rol y el ámbito.
- Existen el perfil (`activo = true`), la asignación `SECRETARIO · LIGA` activa y el evento de
  auditoría.
- El correo de invitación llega al buzón de prueba.

**Resultado obtenido:** **Aprobado, con defecto D-01.** El modal «Invitar usuario» anuncia «Se
creará la cuenta y recibirá un correo para establecer su contraseña» y ofrece el ámbito Liga para el
rol Secretario (liga). Al enviar, la tabla pasó a **3 cuentas** con la fila «Prueba Aceptación
H-03» y el chip «Secretario (liga) · Liga», y el panel «Detalle de usuario» mostró la cuenta
**ACTIVO**, con alta el 5 nov 2026 y la asignación **VIGENTE**. En la base quedaron el perfil «Prueba Aceptación H-03» (`activo = true`, vinculado a su
cuenta de Auth), la asignación `SECRETARIO · LIGA` activa y el evento `INVITAR_USUARIO` (id 20) del
módulo M-01 con el detalle `{"asignaciones": [{"rol": "SECRETARIO", "ambito": "LIGA"}]}`, sin nombre
ni correo, registrado por el Administrador. Supabase Auth creó la cuenta a las 17:57:25 con
`invited_at` y **el correo de invitación llegó** al buzón de prueba. Al abrir el enlace, Supabase
confirmó la cuenta (`email_confirmed_at` y `last_sign_in_at` a las 18:05:35), pero redirigió a la
Site URL `http://localhost:3000`, donde no corre el frontend: el navegador mostró «This site can't
be reached» y **el invitado nunca pudo fijar su contraseña**. El criterio, que pide crear el perfil
y la asignación y que el usuario reciba la invitación, se cumple; la cuenta queda inutilizable por
D-01, lo que además contradice lo que promete el propio modal.

**Evidencia:** [`CA1-modal-invitar.png`](evidencias/H-03/CA1-modal-invitar.png),
[`CA1-fila-invitado.png`](evidencias/H-03/CA1-fila-invitado.png),
[`CA1-correo-recibido.png`](evidencias/H-03/CA1-correo-recibido.png),
[`CA1-enlace-invitacion.png`](evidencias/H-03/CA1-enlace-invitacion.png) (fragmento con tokens
tapado) y [`CA1-base-y-auditoria.txt`](evidencias/H-03/CA1-base-y-auditoria.txt) (consulta directa a la base)

**Pruebas automáticas relacionadas**

- `UsuarioServiceTest` · «H-03: invita, crea el perfil con su asignación, audita y devuelve el
  detalle» y «RF-094: si se omite el ámbito, usa el predeterminado del rol».
- `UsuarioControllerTest` · «H-03: POST invita y responde 201 con Location».
- `InvitacionSupabaseTest` · «H-03.1: invita con la clave secreta en «apikey» y «Authorization» y
  devuelve el id de Auth».
- `PantallaUsuarios.test.tsx` · «H-03.2: invita una cuenta con rol y ámbito de club».
- Ninguna prueba automática cubre el envío real del correo ni el enlace: solo esta prueba manual.

### CA-2 · Una asignación con ámbito CLUB sin club se rechaza con 400

> Dada una asignación con ámbito CLUB, cuando se guarda sin club, entonces se rechaza con 400.

**Pasos**

1. En F-12, pulsar «Invitar», elegir el rol **Directiva de club** con ámbito **Club** y no elegir
   club. Intentar enviar y anotar qué hace el formulario.
2. Enviar la misma petición directo a la API para comprobar que el servidor también la rechaza
   (el token se copia de la pestaña *Network* del navegador y **no se pega en ningún chat ni
   documento**):

   ```bash
   curl -s -i -X POST http://localhost:8080/api/v1/usuarios \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"nombreMostrado":"Prueba CA-2","correo":"ca2.h03@ejemplo.invalid",
          "asignaciones":[{"rolId":"0aaa4ea7-3cad-4647-a2f5-1c28e21f0ee1","ambito":"CLUB"}]}'
   ```

3. Comprobar en la base que no se creó ningún perfil ni cuenta de Auth con ese correo.

**Resultado esperado**

- El formulario no deja enviar sin club y explica por qué.
- La API responde `400` con `codigo: "VALIDACION"` y no llama a Supabase Auth.

**Resultado obtenido:** **Aprobado.** Al elegir **Directiva de club** con ámbito **Club**, el
formulario agregó el campo obligatorio «Club» y, al intentar enviar sin elegirlo, lo marcó con
«Elige el club de la asignación.» y no envió la petición (en la captura también aparecen marcados el
nombre y el correo, que se dejaron vacíos). La petición directa a
`POST /api/v1/usuarios` respondió `400` con `codigo: "VALIDACION"`, el detalle «Revisa los campos
marcados.» y el error de `asignaciones[0].clubCoherente`: «El club es obligatorio con ámbito CLUB y
debe omitirse en los demás ámbitos». La consulta posterior a la base no encontró cuenta de Auth ni
perfil con el correo de la petición, ni eventos de auditoría nuevos: el servidor rechazó la
asignación antes de llamar a Supabase Auth.

**Evidencia:** [`CA2-formulario-sin-club.png`](evidencias/H-03/CA2-formulario-sin-club.png),
[`CA2-respuesta-400.json`](evidencias/H-03/CA2-respuesta-400.json) y
[`CA2-terminal-curl.png`](evidencias/H-03/CA2-terminal-curl.png)

**Pruebas automáticas relacionadas**

- `UsuarioServiceTest` · «H-03 criterio 2: una asignación CLUB sin club responde 400 y no invita».
- `UsuarioControllerTest` · «H-03 criterio 2: una asignación CLUB sin club responde 400 y no llega
  al servicio».
- `PantallaUsuarios.test.tsx` · «H-03 criterio 2: la invitación con ámbito de club exige elegir el
  club».

### CA-3 · Una cuenta desactivada con motivo ya no opera y queda auditada

> Dada una cuenta, cuando el Administrador la desactiva con motivo, entonces ya no puede operar y
> el cambio queda auditado.

**Pasos**

1. En otro navegador o ventana privada, iniciar sesión como **Capturista** y dejar abierto su panel.
2. Como Administrador, abrir el panel de la cuenta Capturista en F-12 y pulsar «Desactivar».
3. Intentar confirmar con un motivo de menos de 10 caracteres; después capturar «Desactivación
   para la prueba de aceptación H-03» y confirmar.
4. En la sesión del Capturista, recargar o navegar a otro módulo.
5. Abrir la pantalla de auditoría y buscar el evento `DESACTIVAR_CUENTA` del módulo M-01.

**Resultado esperado**

- Sin motivo válido no se confirma (`400 RN-021`); con motivo, la cuenta queda inactiva.
- La sesión del Capturista, aunque su token siga vigente, se rechaza con `401 NO_AUTENTICADO` y
  no puede operar (ADR-0003 punto 6).
- La auditoría muestra `DESACTIVAR_CUENTA` con el motivo y el usuario Administrador.

**Resultado obtenido:** **Aprobado.** El modal «Desactivar cuenta» explicó que «Capturista de
prueba» no podrá operar, que la cuenta y su historial se conservan y que puede volver a activarse;
el motivo es obligatorio, con «Mínimo 10 caracteres. Quedará en el historial.» y el contador 47/500.
Con el motivo «prueba» (6/500) el modal marcó «Escribe un motivo de al menos 10 caracteres.» y
no desactivó la cuenta; se canceló y la base confirmó que la cuenta siguió activa y sin eventos
nuevos. Al confirmar, la tabla mostró la cuenta
**INACTIVO** con su asignación «Capturista · Liga» conservada. En la sesión del Capturista, abierta
en otra ventana con su token todavía vigente, la siguiente navegación lo regresó a «Iniciar sesión»
con el aviso «Tu sesión venció. Inicia sesión de nuevo.»: ya no pudo operar. La pantalla de
auditoría mostró el evento `DESACTIVAR_CUENTA` del módulo M-01 a las 20:04:28, ejecutado por el
Administrador sobre `perfiles` con el motivo completo; en la base es el registro id 21, con el
detalle `{"activo": false}` y sin datos personales.

**Evidencia:** [`CA3-modal-desactivar.png`](evidencias/H-03/CA3-modal-desactivar.png),
[`CA3-cuenta-inactiva.png`](evidencias/H-03/CA3-cuenta-inactiva.png),
[`CA3-capturista-rechazado.png`](evidencias/H-03/CA3-capturista-rechazado.png),
[`CA3-auditoria.png`](evidencias/H-03/CA3-auditoria.png) y
[`CA3-motivo-corto.png`](evidencias/H-03/CA3-motivo-corto.png)

**Pruebas automáticas relacionadas**

- `UsuarioServiceTest` · «H-03 criterio 3: desactivar sin motivo responde 400 RN-021 y no cambia la
  cuenta» y «H-03 criterio 3: desactivar con motivo deja la cuenta inactiva y auditoría».
- `UsuarioControllerTest` · «RF-094: PATCH activa o desactiva la cuenta».
- `PantallaUsuarios.test.tsx` · «H-03.2: el panel muestra las asignaciones vigentes y revocadas, y
  desactiva con motivo».

### CA-4 · El Administrador no puede cambiar las asignaciones de su propio rol

> Dado el Administrador, cuando intenta cambiar las asignaciones de su propio rol, entonces se
> rechaza (VAL-022).

**Precaución.** El Administrador es la **única** cuenta con ese rol. No se intenta revocar su
asignación de Administrador general ni desactivar su propia cuenta: si el sistema lo permitiera,
la liga se quedaría sin administrador y habría que restaurarlo con SQL. La prueba usa una
operación reversible.

**Pasos**

1. Como Administrador, abrir su **propio** panel en F-12.
2. Revisar si la pantalla ofrece «Agregar asignación», «Revocar» o «Desactivar» sobre su cuenta.
3. Intentar **agregarse** el rol Capturista (liga).
4. Si el sistema lo acepta, revocar de inmediato esa asignación nueva con el motivo «Reversión de
   la prueba de aceptación H-03 CA-4».

**Resultado esperado**

- La pantalla no ofrece cambiar sus propias asignaciones, o el servidor las rechaza con el código
  `VAL-022`.

**Resultado obtenido:** **Rechazado (defecto D-02).** El panel propio del Administrador ofrece
las mismas acciones que el de cualquier otra cuenta: «Agregar asignación», el interruptor «Cuenta
activa» y el botón «Revocar» en su asignación de **Administrador general**, la única de la liga. Al
agregarse el rol Capturista (liga), el sistema **lo aceptó** sin ningún aviso: la base registró la
asignación nueva y el evento `ASIGNAR_ROL` (id 23, 20:17:29). La asignación se revocó de inmediato
con el motivo «Reversión de la prueba de aceptación H-03 CA-4» y el sistema también lo aceptó
(`REVOCAR_ASIGNACION`, id 24, 20:18:07); el panel muestra la confirmación «Asignación «Capturista ·
Liga» revocada; la cuenta conserva el historial.» y la asignación como **REVOCADA**. No hubo rechazo
con `VAL-022` en ninguna de las dos operaciones. Por la precaución descrita no se intentó revocar la
asignación de Administrador general ni desactivar la propia cuenta, pero el código no las distingue
de las operaciones que sí se aceptaron.

**Evidencia:** [`CA4-panel-propio.png`](evidencias/H-03/CA4-panel-propio.png) y
[`CA4-base-y-auditoria.txt`](evidencias/H-03/CA4-base-y-auditoria.txt) (consulta directa a la
base) y [`CA4-auditoria.png`](evidencias/H-03/CA4-auditoria.png) (pantalla de auditoría filtrada
por M-01)

**Pruebas automáticas relacionadas**

- Ninguna. Ni el backend ni el frontend de H-03 tienen una prueba con «criterio 4» (ver la nota
  previa de la sección 6).

### V-1 · Escenario adicional: invitar un correo que ya tiene cuenta en Auth

**Pasos**

1. En F-12, invitar la **cuenta huérfana de Auth** (existe en `auth.users`, sin perfil) con
   cualquier rol de liga.

**Resultado esperado**

- `409 DUPLICADO` con «Ya existe una cuenta de acceso con ese correo.»; no se crea perfil.

**Resultado obtenido:** **Aprobado.** El modal no se cerró y marcó el campo «Correo electrónico»
con «Ya existe una cuenta de acceso con ese correo.». La consulta posterior confirmó que no se creó
perfil con ese correo (siguen 4 perfiles y 5 cuentas de Auth), que la cuenta de Auth existente no
recibió una invitación nueva (`invited_at` sigue vacío) y que no hubo eventos nuevos de auditoría
(el último sigue siendo el id 24). Como el correo no tenía perfil en SIGLIM, el rechazo vino de
Supabase Auth y `InvitacionSupabase` lo tradujo a `409 DUPLICADO`.

**Evidencia:** [`V1-correo-registrado.png`](evidencias/H-03/V1-correo-registrado.png)

**Pruebas automáticas relacionadas**

- `InvitacionSupabaseTest` · «RF-094: un correo ya registrado en Auth responde 409 DUPLICADO».

## 4. Resumen

| Criterio | Corrida inicial (`1455acd`) | Re-ejecución (`c725e01`) |
| --- | --- | --- |
| CA-1 La invitación crea el perfil, la asignación y envía el correo | Aprobado, con defecto D-01 | **Aprobado**: el enlace lleva a `/restablecer-contrasena` y el invitado fija su contraseña e inicia sesión (D-01 corregido) |
| CA-2 Una asignación CLUB sin club se rechaza con 400 | Aprobado | No se repite (sin cambios en ese flujo) |
| CA-3 Una cuenta desactivada con motivo ya no opera y queda auditada | Aprobado | No se repite (sin cambios en ese flujo) |
| CA-4 El Administrador no puede cambiar las asignaciones de su propio rol | **Rechazado** (D-02) | **Aprobado**: F-12 no ofrece las acciones y la API responde `403 VAL-022` (D-02 corregido) |
| V-1 Correo ya registrado en Auth | Aprobado | No se repite manualmente; cubierto por prueba automática con la respuesta real de Supabase (sección 9) |

Pruebas automáticas de la misma versión, ejecutadas el 2026-11-05 a las 20:28: backend
`./mvnw test -Dtest=UsuarioServiceTest,UsuarioControllerTest,InvitacionSupabaseTest,RolControllerTest`
con **68/68** pruebas sin fallos (17 de servicio, 41 de controlador, 4 de invitación y 6 de roles);
frontend `npx vitest run src/features/usuarios` con **14/14** pruebas en dos archivos. Todas pasan
porque ninguna cubre el envío de `redirect_to` ni el criterio 4: los dos defectos están fuera de lo
que las pruebas automáticas verifican. Las pruebas automáticas de la re-ejecución están en la
sección 9.

## 5. Defectos encontrados

Los dos defectos se detectaron al preparar la prueba revisando el código y se confirmaron en la
corrida:

| ID | Defecto | Severidad | Afecta un criterio | Seguimiento |
| --- | --- | --- | --- | --- |
| D-01 (confirmado en CA-1) | La invitación no envía `redirect_to`: el enlace del correo confirma la cuenta y redirige a la Site URL (`localhost:3000`), donde no corre el frontend; el invitado no puede fijar su contraseña y su cuenta queda inutilizable. Corrección propuesta: enviar `redirect_to = <origen del frontend>/restablecer-contrasena`, que ya está en las Redirect URLs y ya acepta el evento `SIGNED_IN` | Alta | No de forma literal (el usuario sí recibe la invitación), pero impide el propósito de la historia | DEF-03 (SCRUM-398), Sprint 2. **Corregido** en #73 (`c725e01`) y verificado en la re-ejecución (sección 9) |
| D-02 (confirmado en CA-4) | VAL-022 no está implementado para la propia cuenta: el Administrador puede agregarse y revocarse asignaciones (y, por el mismo código, revocar su asignación de Administrador general o desactivarse, con lo que la liga se quedaría sin administrador). `UsuarioService.asignar`, `revocar` y `actualizar` no comparan la cuenta objetivo con el usuario actual, F-12 ofrece esas acciones en el panel propio y `convenciones-api.md` §6.1 difiere VAL-022 a H-20, aunque el criterio 4 de H-03 lo pide. Corrección propuesta: `403` con `codigo: "VAL-022"` en esas tres operaciones cuando la cuenta objetivo es la del usuario actual, ocultar las acciones en el panel propio y agregar pruebas «H-03 criterio 4» | Alta | Sí: CA-4 | DEF-04 (SCRUM-399), Sprint 2. **Corregido** en #72 y verificado en la re-ejecución (sección 9) |

## 6. Observaciones

1. **Correo integrado de Supabase.** Ver la sección 1. Si el correo no llega por los límites del
   servicio integrado, se registra aquí y no como defecto, con la evidencia de que la cuenta sí se
   creó en Auth.
2. **Respuestas 400 y 422 de Supabase.** `InvitacionSupabase` interpreta cualquier `400`, `409` o
   `422` de Supabase Auth como «correo duplicado». Si Supabase rechaza un correo por otra causa
   (por ejemplo, una dirección no autorizada para el correo integrado), SIGLIM respondería «Ya
   existe una cuenta» sin que sea cierto. **No se presentó en esta corrida**, porque todas las
   invitaciones usaron buzones del equipo de Supabase; el riesgo sigue abierto y se recomienda
   distinguir el código de error de Supabase (`email_exists` frente a los demás) en la corrección de
   D-01. **Resuelta en DEF-03 (#73):** solo `email_exists` y `user_already_exists` responden
   `409 DUPLICADO`; cualquier otro rechazo de Supabase Auth responde `503 SERVICIO_NO_DISPONIBLE`
   (convenciones de API §6.1, versión 3.5).
3. **Nota previa sobre CA-4.** Al preparar la prueba se revisó el código de H-03.1 y H-03.2 y no se
   encontró ninguna validación del propio usuario ni pruebas del criterio 4 (ver D-02, DEF-04).
4. **No hay clubes registrados**, así que solo se pudo probar el caso negativo de la asignación
   `CLUB`. El caso positivo queda para cuando H-06 registre clubes.
5. **Mensaje engañoso para una cuenta desactivada** (CA-3). El backend rechaza la cuenta inactiva
   con `401 NO_AUTENTICADO` (decisión del 22 de octubre: token válido sin perfil activo) y el
   frontend lo muestra como «Tu sesión venció. Inicia sesión de nuevo.». El criterio se cumple,
   pero el usuario no sabe que su cuenta fue desactivada y puede intentar entrar una y otra vez.
   Se propone, para un sprint posterior, un código propio (por ejemplo `CUENTA_INACTIVA`) con el
   mensaje «Tu cuenta está desactivada; comunícate con la Liga». Severidad baja; no bloquea H-03.
6. **La re-ejecución de D-01 necesita otro buzón.** El enlace de la invitación es de un solo uso y
   la cuenta Invitado ya quedó confirmada en Auth, así que volver a invitarla respondería
   `409 DUPLICADO`. Para repetir el paso 6 de CA-1 tras la corrección hará falta otro buzón que sea
   miembro del equipo de Supabase y no tenga cuenta en Auth.
7. Las capturas no muestran correos, tokens ni contraseñas; las cuentas se identifican por su rol.
   La barra de direcciones del paso 6 de CA-1 contiene el `access_token` en el fragmento (`#…`), por
   lo que en la captura se tapa todo lo que sigue a `localhost:3000/`.

## 7. Limpieza del entorno compartido

| Paso | Estado |
| --- | --- |
| Reactivar la cuenta Capturista (genera `ACTIVAR_CUENTA` en la auditoría) | Hecho a las 20:08:12: evento `ACTIVAR_CUENTA` id 22, sin motivo (opcional al activar) |
| Revocar la asignación Capturista del Administrador si CA-4 la permitió | Hecho en CA-4 a las 20:18:07 (`REVOCAR_ASIGNACION` id 24) |
| Desactivar con motivo la cuenta Invitado (no se borra, RN-005) | Hecho a las 20:29:08: `DESACTIVAR_CUENTA` id 25 con el motivo «Cuenta de prueba de H-03 sin uso tras la prueba de aceptación» |
| Quitar el buzón de prueba del equipo de la organización en Supabase | Hecho por el ejecutor al terminar la corrida |
| Re-ejecución de CA-4: revertir cambios | No hizo falta: las tres operaciones se rechazaron con `403` y no hubo eventos de auditoría nuevos |
| Re-ejecución de CA-1: desactivar con motivo la cuenta Invitado nueva | Hecho a las 22:22:26: `DESACTIVAR_CUENTA` id 27 con el motivo «Cuenta de prueba de H-03 sin uso tras la re-ejecucion» |
| Re-ejecución de CA-1: quitar el buzón nuevo del equipo de la organización en Supabase | Hecho por el ejecutor al terminar la re-ejecución |

## 8. Conclusión

La historia **H-03 no cumple todavía sus criterios de aceptación** sobre `develop` en `1455acd`.
Se aprobaron CA-1 (la invitación crea el perfil y la asignación, audita sin datos personales y el
correo llega), CA-2 (una asignación `CLUB` sin club se rechaza en el formulario y con `400` en la
API) y CA-3 (una cuenta desactivada con motivo deja de operar aunque su token siga vigente y el
cambio queda auditado), además del escenario adicional V-1. Se **rechazó CA-4**: el Administrador
puede cambiar las asignaciones de su propia cuenta sin el rechazo VAL-022 (DEF-04). Además, aunque
CA-1 se cumple al pie de la letra, el enlace de la invitación no lleva al frontend y el invitado no
puede fijar su contraseña (DEF-03).

**Actualización tras la re-ejecución (sección 9).** Con DEF-04 (#72) y DEF-03 (#73) fusionados en
`develop` (`c725e01`), se repitieron el CA-4 completo y el paso 6 del CA-1. CA-4 se **aprobó**: F-12
no ofrece cambiar las asignaciones ni el estado de la propia cuenta y la API rechaza las tres
operaciones con `403 VAL-022` sin tocar la base. El paso 6 del CA-1 también se **aprobó**: el enlace
del correo llevó al invitado a `/restablecer-contrasena`, donde fijó su contraseña, y después inició
sesión con ella. **H-03 cumple sus cuatro criterios de aceptación** y puede pasar a «Finalizado» junto
con DEF-03 (SCRUM-398) y DEF-04 (SCRUM-399) cuando se fusione esta actualización.

## 9. Re-ejecución

| Campo | Valor |
| --- | --- |
| Versión probada | `develop` en `c725e01`: incluye DEF-04 (#72, VAL-022 sobre la propia cuenta) y DEF-03 (#73, `redirect_to` y clasificación de errores de Supabase); sin migraciones nuevas |
| Fecha | 2026-11-05, de 21:50 a 22:25 (hora de México) |
| Ejecutó | López Meza Leonardo, con apoyo de un agente de IA (consultas de solo lectura a la base y a la bitácora de Supabase Auth) |
| Entorno | El de la sección 1, sin cambios: Site URL `http://localhost:3000` y Redirect URLs `http://localhost:5173/restablecer-contrasena`; backend con `SIGLIM_FRONTEND_ORIGEN=http://localhost:5173` en `backend/.env` |
| Alcance | CA-4 completo y paso 6 de CA-1 con un buzón nuevo (observación 6). CA-2, CA-3 y V-1 no se repiten porque las correcciones no cambian esos flujos de forma visible; el cambio de V-1 (clasificar por `error_code`) lo cubren pruebas automáticas con la respuesta real que Supabase dio en la corrida inicial (`422`, `error_code: "email_exists"`) |
| Resultado | **Aprobado**: CA-4 y paso 6 de CA-1 |

### CA-4 · Re-ejecución

**Precaución.** Igual que en la corrida inicial, no se intentó revocar la asignación de
Administrador general ni desactivar la propia cuenta. Las tres peticiones directas a la API se
eligieron para que, si VAL-022 fallara, no cambiaran nada: asignar el rol Capturista (reversible),
revocar la asignación Capturista **ya revocada** en la corrida inicial (sin la regla respondería
`409 RF-094`) y enviar `activo: true` sobre la propia cuenta, que ya estaba activa (sin la regla
respondería `200`).

**Pasos**

1. Como Administrador, abrir F-12 y comprobar la acción de la fila propia y el panel propio.
2. Como control, abrir el panel del Capturista y comprobar que ahí sí aparecen las acciones.
3. Enviar a la API, con el token de la sesión del Administrador (leído con `read -rs` y borrado al
   terminar, nunca pegado en un chat ni en un archivo):
   - `POST /api/v1/usuarios/{propio}/asignaciones` con el rol Capturista y ámbito `LIGA`;
   - `POST /api/v1/asignaciones-rol/{asignación propia ya revocada}/revocacion` con un motivo válido;
   - `PATCH /api/v1/usuarios/{propio}` con `{"activo": true}`.
4. Consultar la base antes y después.

**Resultado esperado**

- F-12 no ofrece agregar, revocar ni cambiar el estado en la cuenta propia.
- Las tres peticiones responden `403` con `codigo: "VAL-022"` y la base no cambia.

**Resultado obtenido:** **Aprobado.** En la tabla, la fila propia ofrece solo «Ver detalle». El
panel propio muestra el aviso «Esta es tu cuenta. Por separación de funciones (VAL-022) no puedes
cambiar tus asignaciones ni el estado de tu cuenta.», el texto «Tu cuenta» en lugar del interruptor
«Cuenta activa», la asignación Administrador general **VIGENTE** y la Capturista **REVOCADA**, sin
«Agregar asignación» ni «Revocar». En el panel del Capturista (control) sí aparecen esas acciones.
Las tres peticiones directas (22:05:12 a 22:05:15) respondieron `403` con `codigo: "VAL-022"`: las
dos de asignaciones con «No puedes cambiar las asignaciones de rol de tu propia cuenta. Otra cuenta
con permiso para gestionar usuarios debe hacerlo.» y el `PATCH` con «No puedes activar ni
desactivar tu propia cuenta. Otra cuenta con permiso para gestionar usuarios debe hacerlo.». La
revocación de la asignación ya revocada respondió `403` y no `409`: la regla se evalúa antes que el
estado de la asignación. La base quedó igual: las mismas dos asignaciones, el perfil activo y el
último evento de auditoría sigue siendo el id 25.

**Evidencia:** [`CA4R-panel-propio.png`](evidencias/H-03/CA4R-panel-propio.png),
[`CA4R-terminal-curl.png`](evidencias/H-03/CA4R-terminal-curl.png),
[`CA4R-respuestas-api.txt`](evidencias/H-03/CA4R-respuestas-api.txt) (respuestas completas, sin
token) y [`CA4R-base-y-auditoria.txt`](evidencias/H-03/CA4R-base-y-auditoria.txt) (consulta directa
a la base, antes y después)

### CA-1 paso 6 · Re-ejecución

**Pasos**

1. Agregar como miembro del equipo de Supabase un buzón **nuevo**, sin cuenta previa en Auth
   (comprobado en *Authentication → Users*).
2. Como Administrador, invitarlo desde F-12 con el nombre «Prueba Reejecucion H-03» y el rol
   Secretario (liga) con ámbito Liga.
3. Abrir el enlace del correo en una ventana de incógnito (para no reemplazar la sesión del
   Administrador), fijar la contraseña e iniciar sesión con ella.
4. Revisar la bitácora de Supabase Auth y la base.

**Resultado esperado**

- La invitación lleva `redirect_to` a `/restablecer-contrasena` del frontend.
- El enlace del correo abre `http://localhost:5173/restablecer-contrasena`, el invitado fija su
  contraseña y después inicia sesión con ella.

**Resultado obtenido:** **Aprobado.** La invitación (22:11:48) creó el perfil vinculado a su cuenta
de Auth, la asignación `SECRETARIO · LIGA` activa y el evento `INVITAR_USUARIO` (id 26) sin nombre ni
correo, y el correo llegó. La bitácora de Supabase Auth registró la invitación con destino
`http://localhost:5173/restablecer-contrasena` (en la corrida inicial era `http://localhost:3000`).
Al abrir el enlace (22:13:36), Supabase confirmó la cuenta y el navegador llegó a
`localhost:5173/restablecer-contrasena` con el formulario «Restablecer contraseña»; el frontend ya
había quitado los tokens del fragmento. El invitado fijó su contraseña (22:15:57) y, tras cerrar la
sesión, inició sesión con ella (22:16:58): la aplicación lo mostró como «Prueba Reejecucion H-03 ·
Secretario (liga) · Liga». Como la Site URL sigue en `localhost:3000` y el destino registrado incluye
la ruta `/restablecer-contrasena`, el resultado se debe al `redirect_to` que ahora envía el backend.

**Evidencia:** [`CA1R-correo-recibido.png`](evidencias/H-03/CA1R-correo-recibido.png),
[`CA1R-restablecer-contrasena.png`](evidencias/H-03/CA1R-restablecer-contrasena.png),
[`CA1R-sesion-invitado.png`](evidencias/H-03/CA1R-sesion-invitado.png) y
[`CA1R-base-y-auditoria.txt`](evidencias/H-03/CA1R-base-y-auditoria.txt) (base y bitácora de
Supabase Auth)

### Pruebas automáticas de la re-ejecución

- `./mvnw verify` sobre la rama de DEF-03, con el mismo contenido que `c725e01`: **932/932** pruebas
  unitarias y de controlador y **148** de integración sin fallos (una omitida: la prueba contra el
  S3 real, que solo corre si se pide).
- `InvitacionSupabaseTest` · «DEF-03: la invitación envía redirect_to con /restablecer-contrasena del
  origen configurado», «RF-094: un correo ya registrado en Auth (email_exists) responde 409
  DUPLICADO» (con la respuesta real de V-1) y las pruebas «DEF-03» de los demás errores, que responden `503`.
- `PropiedadesInvitacionTest` · validación de `SIGLIM_FRONTEND_ORIGEN`.
- Las pruebas «H-03 criterio 4» de DEF-04 (#72) en el servicio, el controlador y F-12.

### Observaciones de la re-ejecución

1. **Primer intento de la revocación por la API.** Se envió con un motivo con acentos desde Git Bash
   en Windows y respondió `400 VALIDACION` con el mensaje genérico de un cuerpo ilegible: el texto no
   llegó en UTF-8. La petición no llegó al servicio y la base no cambió (último evento id 25). Se
   repitió con un motivo sin acentos y las tres peticiones se volvieron a ejecutar para que la
   evidencia quede completa. Es un problema de la herramienta de prueba, no de SIGLIM.
2. **Recarga de la configuración de Supabase Auth.** La bitácora registra una recarga de la
   configuración a las 22:10:11, poco antes de la invitación. El ejecutor no cambió ni guardó nada en
   *Authentication*. No afecta al resultado: el destino registrado incluye la ruta
   `/restablecer-contrasena`, que solo puede venir del `redirect_to` porque la Site URL no la lleva.
3. **Correo integrado de Supabase.** Sigue vigente la limitación de la sección 1. Con DEF-03, una
   invitación a un buzón que no sea del equipo responde `503` (`email_address_not_authorized` en la
   bitácora del backend) en lugar del falso «Ya existe una cuenta»; desaparece con el SMTP propio de
   ADR-0010.
