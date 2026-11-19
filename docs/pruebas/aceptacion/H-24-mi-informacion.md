# Prueba de aceptación · H-24 Consultar mi información como jugador o tutor

| Campo | Valor |
| --- | --- |
| Historia | H-24 · subtarea H-24.3 (prueba de aceptación de la consulta propia) |
| Requisitos | RF-144, RF-019, RF-020, UI-032, RNF-012, RNF-018, VAL-015, VAL-024 (DEC-139 a DEC-152) |
| Pantallas | F-35 (Mi información) |
| Implementó | H-24.1 (#159) y H-24.2 (#163): Leonardo López |
| Versión probada | `develop` en `bdc1cfc` (base de la rama `test/m04-h24.3-aceptacion-consulta-propia`) |
| Fecha | 2026-11-18 |
| Ejecutó | Agente de IA (OpenCode), sin acompañamiento humano en esta corrida; la revisión cruzada de DEC-73 y la aprobación del PR quedan pendientes |
| Resultado global | **Aprobado** (3 de 3 criterios y verificaciones complementarias) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 (`postgres:17-alpine`) en un contenedor local, con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las catorce migraciones del repositorio aplicadas en orden |
| Autenticación | Servidor local que publica el JWKS ES256 y responde el inicio de sesión con contraseña; el backend validó firma, emisor, audiencia y `sub` como en Supabase Auth |
| Backend | `siglim-0.1.0-SNAPSHOT.jar` compilado de esta rama (`bdc1cfc`), en `http://localhost:8081`, contra la base local y el JWKS local |
| Frontend | `vite` en `http://localhost:5174`, con `VITE_API_URL` apuntando al backend de 8081 (los puertos 8080 y 5173 estaban ocupados por otros procesos locales) |
| Navegador | Chromium controlado con Playwright (`channel: msedge`), ventana de 1280 × 1200 px, `es-MX` y zona `America/Mexico_City` |

La corrida se hizo sobre una **base local efímera**: no se alteró el proyecto compartido de Supabase. El
arnés (JWKS local, siembra y recorrido de Playwright) vive fuera del repositorio; aquí quedan las capturas,
las respuestas crudas de la API y las consultas a la base. La consulta propia no usa S3: los documentos de
la siembra son filas de `siglim.documentos` sin archivo, suficientes para que los requisitos aparezcan
aprobados.

> La prueba no sustituye a las pruebas automáticas: las complementa. `MiInformacionIT` cubre los tres
> criterios con PostgreSQL 17 en Testcontainers; `MiInformacionServiceTest`,
> `MiInformacionControllerTest` y `ReglaElegibilidadTest`, las reglas y el contrato HTTP; y
> `PantallaMiInformacion.test.tsx`, la interfaz.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Tutora de Ana y Luis** | Jugador o tutor · PROPIO | CA-1 y CA-2: la pantalla y su API |
| **Jugador de otro club** | Jugador o tutor · PROPIO | CA-3: otro usuario de ámbito PROPIO (404) |
| **Secretaría de la Liga** | Secretario · LIGA | Verificación complementaria: tiene el permiso, pero «mi» información es personal (404, DEC-140) |
| **Tesorería de la Liga** | Tesorero · LIGA | Verificación complementaria: sin `registros.registro:CONSULTAR` (403, VAL-015) |

Datos ficticios sembrados en la base local:

- Temporada **Temporada 2026-2027** (ACTIVA) con la categoría **Infantil** y el grupo **Único**.
- **María López Ruiz**, CURP ficticia `LORA850101MMNPRR07`, persona de la cuenta y tutora vigente de sus
  dos hijos. No tiene registros de jugadora: su pantalla lo dice y aun así muestra Sanciones y Privacidad.
- **Ana López Ruiz** (menor, 2012), CURP ficticia `LORA120514MMNPRN09`, registro
  `80000000-0000-4000-8000-000000000020` **ACTIVO** en **Halcones Infantil** desde el 1 sep 2026, con sus
  cinco documentos aprobados (4 de 4 requisitos) y una **suspensión VIGENTE** de 3 partidos (1 cumplido),
  por el artículo **12 «Juego brusco»**, vigente del 10 nov al 20 dic 2026, expediente `EXP-2026-014`.
  Se usa en CA-1 y CA-2.
- **Luis López Ruiz** (menor, 2014), CURP ficticia `LORA140903HMNPRS08`, registro
  `80000000-0000-4000-8000-000000000021` **ACTIVO** en el mismo equipo, con cuatro documentos aprobados:
  le falta la **credencial INE del tutor**. Se usa en CA-1.
- **Jorge Ramírez Ortiz**, CURP ficticia `RAOJ900202HMNRRJ03`, cuenta de ámbito PROPIO que no tutela a
  nadie más. Se usa en CA-3.
- Jornada **2** **PUBLICADO** con dos partidos **PROGRAMADO** de Halcones Infantil: el 22 nov como local
  frente a Lobos Infantil y el 29 nov como visitante en casa de Pumas Infantil, ambos en la Unidad
  Deportiva 1.

Los datos, las tutelas y el ámbito tal como los ve cada cuenta están en
[`base-de-datos.txt`](evidencias/H-24/base-de-datos.txt).

## 3. Escenarios

### CA-1 · Un tutor con dos menores puede elegir a cada uno

> Dado un tutor con dos menores, cuando abre la pantalla, entonces puede elegir a cada uno.

**Pasos**

1. Iniciar sesión como **Tutora de Ana y Luis** y abrir **Mi información** desde el menú
   (`/app/mi-informacion`).
2. Comprobar la lista **Ver la información de**: debe ofrecer a la propia persona y a los dos menores.
3. Elegir a **Ana** y comprobar sus datos, documentos, elegibilidad y partidos.
4. Elegir a **Luis** y comprobar que la información cambia y que le falta la credencial del tutor.

**Resultado esperado**

- La lista ofrece exactamente `María López Ruiz (tú)`, `Ana López Ruiz (menor)` y `Luis López Ruiz (menor)`.
- Al elegir a cada menor, la URL lleva `?persona=<id>`, la cabecera muestra su nombre y su CURP enmascarada,
  y el contenido corresponde a esa persona.
- Ana tiene **4 de 4 completos**; Luis, **3 de 4 completos** y **Falta 1** (la credencial INE del tutor).

**Resultado obtenido:** **Aprobado.** La lista ofreció las tres opciones en el orden esperado. Al elegir a
Ana, la URL quedó en `…?persona=aaaaaaaa-1111-4111-8111-000000000002`, la cabecera mostró **Ana López
Ruiz** `LORA******MMN***09` y el bloque **Halcones Infantil** con **4 de 4 completos**. Al elegir a Luis,
la URL cambió a `…?persona=…0003`, la cabecera mostró **Luis López Ruiz** `LORA******HMN***08` y los
documentos pasaron a **3 de 4 completos** con **Falta 1** en **Credencial INE del tutor**. El detalle de
cada uno es el que devolvió la API para esa persona (los datos crudos están en
[`resultados-recorrido.json`](evidencias/H-24/resultados-recorrido.json)).

**Evidencia**

- [`CA1-selector-titulares.png`](evidencias/H-24/CA1-selector-titulares.png): la pantalla con la lista de titulares.
- [`CA1-ana-seleccionada.png`](evidencias/H-24/CA1-ana-seleccionada.png): Ana elegida, con sus cuatro requisitos completos.
- [`CA1-luis-seleccionado.png`](evidencias/H-24/CA1-luis-seleccionado.png): Luis elegido, con **3 de 4** y **Falta 1**.
- [`resultados-recorrido.json`](evidencias/H-24/resultados-recorrido.json): textos de las opciones, URL y cifras que leyó el recorrido.

**Pruebas automáticas relacionadas**

- `MiInformacionIT` · «H-24 criterio 1 · DEC-139: un tutor con dos menores ve a ambos por nombre y el
  detalle de cada uno».
- `MiInformacionServiceTest` · «RF-144 · DEC-139: primero la persona de la cuenta y después los menores que
  tutela».
- `PantallaMiInformacion.test.tsx` · «DEC-148: con la persona propia y dos menores, el selector cambia
  `?persona=` y pide su detalle» y «DEC-148 · DEC-151: con un titular no hay selector y muestra el
  registro, “Puede jugar” y los documentos».

### CA-2 · Un jugador suspendido ve la causa y la vigencia

> Dado un jugador suspendido, cuando consulta, entonces ve la causa y la vigencia.

**Pasos**

1. Con la misma cuenta, elegir a **Ana** en la lista de titulares.
2. Leer la tarjeta roja de elegibilidad y la sección **Sanciones**.

**Resultado esperado**

- La pantalla dice **No puede jugar** y muestra la tarjeta **Suspensión vigente** con el mensaje del API.
- La sección **Sanciones** muestra la suspensión **Vigente**, los partidos cumplidos (1 de 3), la vigencia,
  el artículo y el folio del expediente.

**Resultado obtenido:** **Aprobado.** La pantalla mostró **Suspensión vigente** con el mensaje exacto
«Suspensión de 3 partidos por el artículo 12 («Juego brusco»): faltan 2 partidos, vigente hasta el
20/12/2026.» y debajo **No puede jugar**. La sección **Sanciones** mostró **Suspensión por partidos** con
la insignia **VIGENTE**, **Partidos cumplidos: 1 de 3**, **Vigencia: 10 nov 2026 – 20 dic 2026**,
**Artículo: 12 · Juego brusco** y **Expediente: EXP-2026-014**. La respuesta cruda del detalle
(`200`, `Cache-Control: no-store`) está en [`CA2-detalle-ana.json`](evidencias/H-24/CA2-detalle-ana.json).

**Evidencia**

- [`CA2-tarjeta-suspension.png`](evidencias/H-24/CA2-tarjeta-suspension.png): la tarjeta con la causa y la fecha de término.
- [`CA2-sanciones.png`](evidencias/H-24/CA2-sanciones.png): la sanción con estado, partidos, vigencia, artículo y folio.
- [`CA2-detalle-ana.json`](evidencias/H-24/CA2-detalle-ana.json): detalle de la API con `elegible: false` y la sanción.

**Pruebas automáticas relacionadas**

- `MiInformacionIT` · «H-24 criterio 2 · RF-020: un jugador con suspensión VIGENTE ve `elegible=false` con
  artículo, folio y vigencia».
- `ReglaElegibilidadTest` · «RF-020: una suspensión VIGENTE impide jugar con el artículo y los partidos que
  faltan» y «RF-020: la vigencia lleva los partidos restantes y la fecha de término si ambos existen».
- `PantallaMiInformacion.test.tsx` · «RF-020: una causa de suspensión muestra la tarjeta roja con su
  mensaje» y «DEC-151: cada sanción muestra partidos cumplidos, vigencia, artículo y folio».

### CA-3 · Ningún otro usuario de ámbito PROPIO ve esta información (404)

> Ningún otro usuario de ámbito PROPIO ve esta información (404).

**Pasos**

1. Iniciar sesión como **Jugador de otro club** (ámbito PROPIO) y abrir **Mi información**: solo debe
   aparecer su propia persona, sin selector.
2. Abrir `/app/mi-informacion?persona=<Ana>` y comprobar que no se muestra a la menor.
3. Pedir por API `GET /api/v1/mi-informacion/<Ana>` con el token de esa cuenta.
4. Repetir la petición con la **Secretaría de la Liga** (tiene el permiso, ámbito LIGA), con la
   **Tesorería** (sin el permiso) y sin token.

**Resultado esperado**

- Otro usuario PROPIO recibe `404` igual que un recurso inexistente (VAL-024), sin datos de la persona.
- La pantalla muestra **No encontramos esa información** y no pide el detalle de un titular que no está en
  su lista (DEC-148).
- Una cuenta de liga con el permiso también recibe `404` (DEC-140); sin el permiso, `403 VAL-015`; sin
  sesión, `401`.

**Resultado obtenido:** **Aprobado.** La cuenta del **Jugador de otro club** abrió **Mi información** con
**Jorge Ramírez Ortiz** y sin selector. Al pedir `?persona=<Ana>`, la pantalla mostró **No encontramos esa
información** / «Puede que ya no tengas acceso a este registro.» y no mostró el nombre de la menor. La API
respondió `404` con «El recurso no existe o no tienes acceso a él.» (`NO_ENCONTRADO`), idéntico a un
recurso inexistente. La **Secretaría** recibió el mismo `404`; la **Tesorería**, `403 VAL-015` («Tu cuenta
no tiene el permiso necesario para esta operación.») y la pantalla **No tienes permiso para esta acción**;
sin token, `401 NO_AUTENTICADO`.

**Evidencia**

- [`CA3-otro-jugador-su-informacion.png`](evidencias/H-24/CA3-otro-jugador-su-informacion.png): la cuenta PROPIO solo se ve a sí misma.
- [`CA3-otro-jugador-sin-acceso.png`](evidencias/H-24/CA3-otro-jugador-sin-acceso.png): la pantalla al pedir a la menor.
- [`CA3-respuesta-404.json`](evidencias/H-24/CA3-respuesta-404.json): el `404` de la API para otro usuario PROPIO.
- [`CA3-respuesta-liga-404.json`](evidencias/H-24/CA3-respuesta-liga-404.json): el `404` de la cuenta de liga con permiso.
- [`CA3-respuesta-403.json`](evidencias/H-24/CA3-respuesta-403.json): el `403 VAL-015` de la cuenta sin permiso.
- [`CA3-respuesta-401.json`](evidencias/H-24/CA3-respuesta-401.json): el `401` sin sesión.
- [`CA3-tesorero-403.png`](evidencias/H-24/CA3-tesorero-403.png): la página 403 de la Tesorería.

**Pruebas automáticas relacionadas**

- `MiInformacionIT` · «H-24 criterio 3 · VAL-024: otro usuario PROPIO y uno con ámbito LIGA reciben 404 por
  quien no tutelan».
- `MiInformacionServiceTest` · «VAL-024 · DEC-140: una persona de la que no es titular responde 404 aun con
  ámbito LIGA» y «DEC-140: primero el permiso y después la titularidad».
- `MiInformacionControllerTest` · «RNF-003 · VAL-015 · VAL-024: seguridad de
  `GET /api/v1/mi-informacion/{personaId}`» (401, 403 y 404 con el arnés de autorización).
- `PantallaMiInformacion.test.tsx` · «VAL-024 · DEC-148: un detalle con 404 muestra “No encontramos esa
  información”» y «VAL-015: un 403 muestra la página sin permiso».

## 4. Verificaciones complementarias

### 4.1 Permisos, ámbito y errores 401/403/404

| Caso | Petición | Respuesta |
| --- | --- | --- |
| Tutora (persona propia y menores) | `GET /mi-informacion/{Ana}` | `200` con el detalle y `Cache-Control: no-store` |
| Otro usuario PROPIO | `GET /mi-informacion/{Ana}` | `404 NO_ENCONTRADO` (VAL-024) |
| Secretario (ámbito LIGA, con permiso) | `GET /mi-informacion/{Ana}` | `404 NO_ENCONTRADO` (DEC-140) |
| Tesorero (ámbito LIGA, sin permiso) | `GET /mi-informacion/{Ana}` | `403 VAL-015` |
| Sin token | `GET /mi-informacion/{Ana}` | `401 NO_AUTENTICADO` |
| Tesorero (sin permiso) | `GET /mi-informacion` | `403 VAL-015` |

Las respuestas crudas están en [`CA3-respuesta-404.json`](evidencias/H-24/CA3-respuesta-404.json),
[`CA3-respuesta-liga-404.json`](evidencias/H-24/CA3-respuesta-liga-404.json),
[`CA3-respuesta-403.json`](evidencias/H-24/CA3-respuesta-403.json) y
[`CA3-respuesta-401.json`](evidencias/H-24/CA3-respuesta-401.json). El orden de errores es el de DEC-140:
primero el permiso (403) y después la titularidad (404); una cuenta de liga nunca ve «mi» información por
esta ruta, aunque tenga el permiso.

### 4.2 Datos personales mínimos y sin caché (RNF-012, RNF-018)

- La respuesta de detalle lleva `curpEnmascarada` (`LORA******MMN***09`), nunca la CURP completa.
- No incluye `s3Bucket`, `s3Clave`, `url`, `sha256`, `tipoMime` ni el nombre del archivo: solo el estado, las
  observaciones y la fecha de carga de cada documento.
- Las respuestas `200` llevan `Cache-Control: no-store`; el frontend descarta la consulta al salir de la
  pantalla (`gcTime: 0`).

### 4.3 Ámbito propio en la base

Con el rol `siglim_app` y los claims del perfil de la tutora, `siglim.persona_usuario()` devuelve a María y
`siglim.es_titular(...)` es verdadero para María, Ana y Luis, y falso para Jorge. Con los claims del jugador
de otro club, `es_titular(Ana)` es falso: la misma regla que usa el servicio para responder `404` está
también en la base (RF-144, VAL-024). El detalle está en
[`base-de-datos.txt`](evidencias/H-24/base-de-datos.txt).

## 5. Defectos encontrados

Ninguno.

## 6. Automatización y verificación

| Verificación | Resultado real |
| --- | --- |
| `./mvnw verify` (backend) | **BUILD SUCCESS** en 6:09: 2 113 pruebas de surefire y 333 de failsafe, 0 fallos; 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS) |
| `MiInformacionIT` (Testcontainers, PostgreSQL 17) | **6 de 6**, con los tres criterios de H-24 |
| `MiInformacionControllerTest`, `MiInformacionServiceTest` y `ReglaElegibilidadTest` | **39 de 39** (13 + 7 + 19) |
| `npm run lint` (frontend) | oxlint sin advertencias ni errores |
| `npm test` (frontend) | **90 archivos y 839 pruebas en verde**, incluidas las 13 de `PantallaMiInformacion.test.tsx` |
| `npm run build` (frontend) | `tsc -b && vite build` correcto |

La suite completa del frontend se ejecutó con `--maxWorkers=2 --testTimeout=30000`: la máquina de la
corrida tenía alrededor de 0.6 GB de RAM libre y, con todos los workers, Vitest abortaba por falta de
memoria (código 134 del worker), no por fallos de prueba. Con dos workers y el tiempo límite ampliado,
las 839 pruebas pasan. El detalle de comandos y salidas está en
[`pruebas-automaticas.txt`](evidencias/H-24/pruebas-automaticas.txt).

Las pruebas de integración del backend usan PostgreSQL 17 con Testcontainers; la corrida de aceptación
usó PostgreSQL 17 local con las catorce migraciones del repositorio.

## 7. Conclusión

H-24 cumple sus tres criterios de aceptación y las verificaciones complementarias de permisos, ámbito y
datos personales. Un tutor con dos menores elige a cada uno y ve la información de esa persona; una jugadora
suspendida ve la causa, los partidos que faltan y la fecha de término; y ningún otro usuario de ámbito
PROPIO —ni una cuenta de liga por esta ruta— recibe la información: la API responde `404` igual que con un
recurso inexistente. H-24.3 queda terminada. La prueba cruzada de DEC-73 y el cierre de H-24 dependen de la
aprobación del PR.
