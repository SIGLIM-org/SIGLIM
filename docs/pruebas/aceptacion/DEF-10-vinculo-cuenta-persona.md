# Prueba de aceptación · DEF-10 Vincular la cuenta con su persona al invitarla

| Campo | Valor |
| --- | --- |
| Defecto | DEF-10 · subtarea DEF-10.3 · Sprint 4 (DEF-10.1 es SCRUM-450 y DEF-10.2 es SCRUM-451) |
| Origen | Las cuentas `JUGADOR_TUTOR` nacían sin `perfiles.persona_id`, así que «Mi información» (H-24) quedaba vacía (DEC-145, DEC-149) |
| Requisitos | RF-094, RF-106, RF-144, RNF-012, VAL-015, DEC-11, DEC-155 a DEC-159, DEC-167 a DEC-169 |
| Pantallas | F-12 «Usuarios» (invitación y panel de la cuenta) y F-35 «Mi información» |
| Implementó | Leonardo (DEF-10.1 #180 y DEF-10.2 #184) |
| Versión probada | `develop` en `1b5b68f` (incluye `014faf2` y `9ba2168`) |
| Fecha | 2026-11-24 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (5 de 5 criterios); una observación sin defecto |

La corrida valida de punta a punta la corrección del defecto. Al invitar una cuenta con la CURP de
una persona adulta ya registrada, el perfil nace vinculado (`personaId`). Con esa cuenta, el jugador ve
su registro en «Mi información» y la tutora ve al menor bajo su tutela vigente; antes de DEF-10 la
lista llegaba vacía. Las CURP inválidas, sin persona, de un menor o ya vinculadas se rechazan con su
causa **antes** de llamar a Supabase Auth. F-12 pide la CURP solo con el rol «Jugador o tutor», muestra
el error bajo el campo y el panel dice si la cuenta está vinculada. La auditoría guarda el `personaId`
sin la CURP ni otros datos personales.

DEF-10 es un defecto y Jira no trae criterios numerados. Esta prueba los deriva de las decisiones que
lo corrigen (DEC-155 a DEC-159 en el backend, DEC-167 a DEC-169 en F-12) y de su objetivo: que la
cuenta invitada con su CURP vea «Mi información». Quedan numerados de CA-1 a CA-5.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio (DEF-10 no agrega migración: `perfiles.persona_id` ya existía) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS (ES256), responde el inicio de sesión con contraseña y la API de administración que usa el backend al invitar (`POST /auth/v1/invite`, que crea la fila de `auth.users` en la base local como lo hace Supabase, y `DELETE /auth/v1/admin/users/{id}`). A cada cuenta invitada le asigna una contraseña conocida para iniciar sesión con ella, en lugar del correo de invitación |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL`, `SUPABASE_URL` y `SUPABASE_SECRET_KEY` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` apuntando a la autenticación local |
| Navegador | Panel de navegador del entorno de pruebas: 1280 × 800 px para F-12 y 375 × 812 px (móvil) para F-35 |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2252 + 359 pruebas, 0 fallos). Frontend: 40 pruebas de F-12 y F-35 en verde. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/DEF-10/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase** ni se enviaron correos reales. El arnés de autenticación, la semilla y los guiones de la API
viven fuera del repositorio. Aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos son ficticios (dominio `.test`) y sus contraseñas
no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador de prueba** | Administrador general · LIGA (tiene `seguridad.usuario:CREAR`) | Invita las cuentas, consulta F-12 |
| **Jorge Jugador** (invitada en CA-1) | Jugador o tutor · PROPIO, vinculada a la persona «Jorge Jugador» | CA-4: ve su registro |
| **Teresa Tutora** (invitada en CA-1) | Jugador o tutor · PROPIO, vinculada a la persona «Teresa Tutora» | CA-4: ve al menor bajo su tutela |
| **Secretario de prueba** (invitada en CA-3) | Secretario · LIGA, sin CURP | CA-3: la invitación sin CURP no cambia |

Personas ficticias (CURP con formato válido que no corresponde a nadie): **Jorge Jugador** (adulto,
registro `ACTIVO` en «Halcones Libre»), **Teresa Tutora** (adulta, sin registro propio) y **Mateo
Menor** (nacido en 2014, registro `ACTIVO` y tutela vigente de Teresa). La temporada, el club y el
equipo se sembraron por SQL como dueño del esquema.

## 3. Escenarios

### CA-1 · Invitar con la CURP de una persona adulta deja la cuenta vinculada (DEC-155, DEC-167, DEC-169)

> Dada una persona adulta registrada, cuando el Administrador la invita con su CURP y el rol «Jugador
> o tutor», entonces la cuenta nace con `personaId`; la CURP se normaliza (recorte y mayúsculas); el
> detalle y el listado de `GET /usuarios` devuelven `personaId`, y el panel de F-12 dice «Vinculada con
> su persona».

**Resultado obtenido** ([respuestas](evidencias/DEF-10/respuestas-api.txt)):

| Petición | Respuesta |
| --- | --- |
| Jorge con la CURP en minúsculas (`xexj960101hmnxxx01`) | `201` con `personaId` de Jorge |
| Teresa con su CURP | `201` con `personaId` de Teresa |
| `GET /usuarios/{id}` de Jorge | `200` con su `personaId` |
| `GET /usuarios` | Jorge y Teresa con `personaId`; el Administrador y el Secretario con `null` |

En F-12, con el rol «Jugador o tutor» aparece el campo obligatorio «CURP de la persona» con su ayuda
([captura](evidencias/DEF-10/CA1-modal-jugador-pide-curp.png)), y el panel de Jorge muestra «Vinculada
con su persona» sin datos de la persona ([captura](evidencias/DEF-10/CA1-panel-cuenta-vinculada.png)).

**Resultado:** ✅ Aprobado.

### CA-2 · Una CURP que no se puede vincular se rechaza con su causa y sin invitar (DEC-156, DEC-168)

> Dada una CURP con formato inválido, sin persona registrada, de un menor o ya vinculada a otra
> cuenta, cuando se invita, entonces responde el error con `campo: "curp"` y **no** se llama a Supabase
> Auth: no se envía correo ni queda cuenta huérfana. F-12 muestra la causa bajo el campo.

**Resultado obtenido** ([respuestas](evidencias/DEF-10/respuestas-api.txt), [bitácora de Auth](evidencias/DEF-10/base-y-auditoria.txt)):

| Petición | Respuesta |
| --- | --- |
| CURP `ABC123` | `400 VALIDACION` «La CURP debe tener 18 caracteres y el formato oficial.» con `campo: "curp"` |
| CURP con formato válido sin persona | `422 REFERENCIA_INVALIDA` «No hay una persona registrada con esa CURP. Regístrala primero…» con `campo: "curp"` |
| CURP de Mateo (menor) | `422 RF-106` «Un menor de edad no tiene cuenta propia. Invita a su tutor con la CURP del tutor.» con `campo: "curp"` |
| CURP de Jorge con otro correo | `409 DUPLICADO` con `campo: "curp"` y el `perfilId` de la cuenta de Jorge |
| Correo ya registrado (sin CURP) | `409 DUPLICADO` con `campo: "correo"` (el correo se revisa antes que la CURP) |

El simulador de Supabase Auth registró **3** invitaciones (Jorge, Teresa y el Secretario) antes y
después de los cinco errores, y `auth.users` terminó con 4 cuentas (las tres invitadas y el
Administrador). En F-12, la CURP de Mateo escrita en minúsculas y con espacios se recortó, se envió y
mostró «Esa CURP es de un menor de edad, que no tiene cuenta propia. Invita a su tutor con la CURP del
tutor.» bajo el campo, sin cerrar el modal ni invitar
([captura](evidencias/DEF-10/CA2-modal-error-curp-menor.png)).

**Resultado:** ✅ Aprobado.

### CA-3 · Sin CURP la invitación no cambia (DEC-155, DEC-167, DEC-169)

> Dado un rol que no es «Jugador o tutor», cuando se invita, entonces F-12 no pide la CURP, la cuenta
> se crea como antes con `personaId: null` y el panel dice «Sin persona vinculada».

**Resultado obtenido:** el Secretario se invitó sin CURP: `201` con `personaId: null`. En F-12, con el
rol «Secretario (liga)» el modal no muestra el campo de la CURP
([captura](evidencias/DEF-10/CA3-modal-secretario-sin-curp.png)) y el panel de la cuenta dice «Sin
persona vinculada» ([captura](evidencias/DEF-10/CA3-panel-sin-persona-vinculada.png)).

**Resultado:** ✅ Aprobado.

### CA-4 · La cuenta vinculada ve «Mi información» (H-24, RF-144, DEC-75)

> Dada una cuenta invitada con su CURP, cuando inicia sesión, entonces «Mi información» trae a su
> persona con sus registros; la cuenta de una tutora trae también al menor bajo su tutela vigente, y
> nadie ve a quien no es su titular.

**Resultado obtenido** ([respuestas](evidencias/DEF-10/respuestas-api.txt)):

| Cuenta | `GET /mi-informacion` y detalle |
| --- | --- |
| Jorge | `titulares`: Jorge (`esPropia: true`). El detalle trae su registro `ACTIVO` en «Halcones Libre» |
| Teresa | `titulares`: Teresa y Mateo (`esMenor: true`). El detalle de Mateo trae su registro `ACTIVO` |
| Jorge pide el detalle de Mateo | `404 NO_ENCONTRADO` |
| Secretario (sin vínculo) | `titulares: []`, como antes de DEF-10 |

En F-35 (móvil, 375 px), Jorge ve su registro con «Puede jugar» y la CURP enmascarada
([captura](evidencias/DEF-10/CA4-mi-informacion-jugador.png)); Teresa elige a «Mateo Menor Prueba
(menor)» en «Ver la información de» y ve su registro
([captura](evidencias/DEF-10/CA4-mi-informacion-tutora-menor.png)).

**Resultado:** ✅ Aprobado.

### CA-5 · La invitación se audita con el vínculo y sin datos personales (DEC-158, RF-093)

> Dada una invitación, cuando se audita `INVITAR_USUARIO`, entonces el detalle conserva las
> asignaciones y agrega `personaId` solo cuando hay vínculo; nunca la CURP, el nombre ni el correo.

**Resultado obtenido** ([base y auditoría](evidencias/DEF-10/base-y-auditoria.txt)): tres eventos
`INVITAR_USUARIO` a nombre del Administrador. Los de Jorge y Teresa llevan
`{"personaId": "…", "asignaciones": [{"rol": "JUGADOR_TUTOR", "ambito": "PROPIO"}]}`; el del
Secretario, solo `asignaciones`. Ningún detalle contiene CURP, nombre ni correo. Las cinco peticiones
rechazadas de CA-2 no dejaron evento.

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Invitación con CURP y cuenta vinculada | ✅ Aprobado |
| CA-2 Errores de la CURP antes de Supabase Auth | ✅ Aprobado |
| CA-3 Invitación sin CURP | ✅ Aprobado |
| CA-4 «Mi información» de la cuenta vinculada | ✅ Aprobado |
| CA-5 Auditoría sin datos personales | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `1b5b68f` el 24-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/DEF-10/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2252 pruebas unitarias y de controlador, 0 fallos; 359 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `UsuarioServiceTest` (30), `UsuarioControllerTest` (48), `UsuarioRepositoryIT` (8), `InvitacionConPersonaIT` (2) y `MiInformacionIT` (6) |
| Frontend | `npx vitest run src/features/usuarios src/features/registros/mi-informacion` | 3 archivos, 40 pruebas, 0 fallos |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `UsuarioServiceTest` «DEF-10.1: con CURP, recorta y vincula el perfil a la persona, y audita solo su personaId»; `UsuarioControllerTest` «DEF-10.1: POST con CURP responde 201 con el personaId vinculado»; `PantallaUsuarios.test` «DEF-10.2: envía la CURP normalizada con ámbito PROPIO» y «DEF-10.2: el panel muestra el estado del vínculo sin datos de la persona» |
| CA-2 | `UsuarioServiceTest` «DEF-10.1: una CURP con mal formato responde 400 VALIDACION y no invita», «DEF-10.1: una CURP sin persona responde 422 REFERENCIA_INVALIDA…», «RF-106 · DEC-11: la CURP de un menor responde 422 RF-106…», «DEF-10.1: una persona que ya tiene cuenta responde 409 DUPLICADO con el perfilId», «DEC-156: el correo duplicado se revisa antes que la CURP…» y «DEC-157: si una carrera vincula antes a la persona, responde 409 y elimina la cuenta de Auth»; `InvitacionConPersonaIT` «DEF-10.1: una segunda invitación con la misma CURP responde 409 sin llamar a Supabase Auth»; `PantallaUsuarios.test` «DEF-10.2: 422 RF-106 explica que un menor no tiene cuenta propia» y los de `REFERENCIA_INVALIDA` y `DUPLICADO` |
| CA-3 | `UsuarioServiceTest` «DEC-155: una CURP en blanco se trata como ausente y no busca persona»; `PantallaUsuarios.test` «DEF-10.2: con un rol que no es JUGADOR_TUTOR no pide la CURP ni la envía» y «DEF-10.2: al cambiar de JUGADOR_TUTOR a otro rol el campo desaparece…» |
| CA-4 | `InvitacionConPersonaIT` «DEF-10.1 · H-24: tras invitar con CURP, persona_usuario() y es_titular() funcionan con la cuenta nueva»; `MiInformacionIT`; `PantallaMiInformacion.test` «DEF-10 · DEC-149: sin titulares avisa que la cuenta no está vinculada». La pantalla F-35 con una cuenta invitada de verdad solo se comprueba en esta corrida |
| CA-5 | `UsuarioServiceTest` «DEF-10.1: con CURP, … y audita solo su personaId» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · Por la API, una CURP con espacios alrededor se rechaza antes de recortarla.** DEC-155 dice
que la CURP «se recorta y se pasa a mayúsculas». Las minúsculas se normalizan, pero
`" XEXZ990101HMNXXX09 "` (20 caracteres) responde `400 VALIDACION` porque la anotación `@Size(max = 18)`
de `CrearUsuarioRequest` se evalúa sobre el texto sin recortar; la prueba de controlador «DEF-10.1:
una CURP de más de 18 caracteres responde 400 y no llega al servicio» lo fija así a propósito. No
afecta al usuario: F-12 recorta la CURP antes de enviarla (se comprobó en CA-2). Se anota por si otro
cliente de la API llega a enviar la CURP sin recortar.

## 7. Conclusión

DEF-10 está corregido: la invitación con CURP vincula la cuenta con su persona, rechaza con su causa y
sin invitar las CURP que no se pueden vincular, no cambia las invitaciones sin CURP, deja a la cuenta
vinculada ver «Mi información» (la propia y la del menor bajo su tutela) y audita el vínculo sin datos
personales. **DEF-10 puede cerrarse.** La observación O-1 no bloquea el cierre.
