# Prueba de aceptación · H-22 Registrar la directiva del club y sus cambios

| Campo | Valor |
| --- | --- |
| Historia | H-22 · subtarea H-22.3 · Sprint 2 · Should · 2 SP |
| Requisitos | RF-102, UI-005, RF-093, RN-054, VAL-015, VAL-024 |
| Pantallas | F-15 (detalle del club, pestaña Directiva) |
| Versión probada | `develop` en `43a12ed`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000`, `20261106160000` y `20261108100000` |
| Fecha | 2026-11-08 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado (2 de 2 criterios; V1 aprobada)** |

La corrida valida de punta a punta la historia H-22: la Directiva registra un cambio de cargo desde
la pestaña Directiva de F-15 y el cambio queda pendiente de autorización; después la Presidencia lo
autoriza y el cargo anterior termina su vigencia el día previo al inicio del nuevo. La corrida usó
el frontend, el backend y PostgreSQL reales, con una base local efímera y cuentas ficticias.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17-alpine en un contenedor Docker local (puerto 55433), con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las seis migraciones del repositorio aplicadas |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias; el backend valida el token contra ese JWKS |
| Backend | `java -jar target/siglim-0.1.0-SNAPSHOT.jar` en `http://127.0.0.1:8081`, Java 25.0.5.0.3 y Spring Boot 4.1.1, contra la base local y la autenticación local |
| Frontend | `npm run dev -- --mode h22` en `http://127.0.0.1:5174` (Vite 8.3.5), con `VITE_API_URL` hacia el backend local |
| Navegador | Chromium 156.0.8078.4 sin interfaz (Playwright 1.65.0), ventana de 1440 × 1100 px, idioma `es-MX` y zona `America/Mexico_City` |

La prueba se ejecutó sobre una **base local efímera** creada desde la simulación de Supabase y las
migraciones del repositorio: no se alteró el proyecto compartido de Supabase. El arnés
(autenticación local, cuentas y recorrido de Playwright) vive fuera del repositorio; aquí solo se
versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Directiva Halcones H-22** | Directiva de club · ámbito CLUB | `organizacion.directiva:EDITAR`, `organizacion.club:CONSULTAR` y `organizacion.persona:CONSULTAR`, todos de su club |
| **Presidente de aceptación H-22** | Presidente del Comité Ejecutivo · ámbito LIGA | `organizacion.directiva:APROBAR` y `organizacion.club:CONSULTAR` |

Datos sembrados en la base local; todos son ficticios:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Deportivo Halcones H-22** | Club `ACTIVO` con un solo cargo de directiva registrado | CA-1 y CA-2 |
| **Ana Ruiz Hernández** | Presidenta vigente desde el 15 de enero de 2024, autorizada por la Presidencia | CA-1 y CA-2: su vigencia debe terminar al autorizarse el cambio |
| **Bruno Salas López** | Persona registrada por la Directiva del club (RF-010) y sin cargo vigente | CA-1: se propone como nuevo presidente desde el 1 de diciembre de 2026 |
| **Club Vecino H-22** | Club `ACTIVO` fuera del ámbito de la Directiva | V1: el ámbito CLUB no alcanza otro club |

La persona propuesta no tenía cargo previo. Su registro fue creado por la propia Directiva, que es
una de las condiciones con las que `persona_visible()` permite al club consultar el dato personal
(RN-054); el modal de H-22.2 solo busca personas ya registradas y, si no existen, indica que la Liga
debe registrarlas antes.

El estado completo de la base al cerrar la corrida, con vigencias y auditoría, está en
[`CA1-CA2-estado-base.txt`](evidencias/H-22/CA1-CA2-estado-base.txt). Las respuestas crudas de la API
están en [`CA1-CA2-peticiones-api.json`](evidencias/H-22/CA1-CA2-peticiones-api.json).

## 3. Escenarios

### CA-1 · El cambio se registra con vigencia y queda pendiente de autorización

> Dado un cambio de cargo, cuando se registra, entonces queda con vigencia desde y pendiente de
> autorización.

**Pasos**

1. Iniciar sesión como **Directiva Halcones H-22** y abrir el detalle del Deportivo Halcones H-22 en
   Organización › Clubes.
2. Abrir la pestaña **Directiva** y comprobar que el cargo de Presidente muestra a Ana Ruiz Hernández
   como vigente desde el 15 de enero de 2024.
3. Pulsar **Cambiar** en la fila de Presidente, buscar la CURP de Bruno Salas López, confirmar su
   nombre y fijar el inicio de la vigencia el **1 de diciembre de 2026**.
4. Pulsar **Registrar cambio** y revisar el aviso, la tabla y la respuesta de la API.
5. Consultar en la base los cargos del club y la auditoría.

**Resultado esperado**

- El `POST /api/v1/clubes/{clubId}/directiva` responde `201` con `Location` al cargo nuevo.
- El cargo nuevo nace `PENDIENTE`, con `desde = 2026-12-01` y sin `hasta`; el cargo de Ana sigue
  vigente y sin fecha de fin.
- La pestaña muestra el cargo pendiente, indica a quién reemplazará y avisa que la Presidencia debe
  autorizarlo; la auditoría registra `REGISTRAR_CARGO_DIRECTIVA` sin datos personales.

**Resultado obtenido:** **Aprobado.** La API respondió `201` con
`Location: /api/v1/cargos-directiva/4114e7e6-be05-4d94-ae2b-af8959e9225f`; el cuerpo devolvió a
«Bruno Salas López» como `PRESIDENTE`, `desde: "2026-12-01"` y `estado: "PENDIENTE"`, sin `hasta`.
La pantalla mostró «Cambio de Presidente registrado. Queda pendiente de autorización.», el aviso
«Hay un cambio de directiva pendiente de autorización por la Presidencia.» y la fila pendiente con la
nota «Al autorizar, Ana Ruiz Hernández termina el 30 nov 2026.». En la base, Ana conservó su fila con
`hasta = NULL` y la auditoría registró `REGISTRAR_CARGO_DIRECTIVA` en `M-03`, con el perfil de la
Directiva y solo club, cargo, fecha y estado en el detalle.

**Evidencia**

- [`CA1-directiva-antes.png`](evidencias/H-22/CA1-directiva-antes.png)
- [`CA1-modal-registrar.png`](evidencias/H-22/CA1-modal-registrar.png)
- [`CA1-pendiente-de-autorizacion.png`](evidencias/H-22/CA1-pendiente-de-autorizacion.png)
- [`CA1-CA2-peticiones-api.json`](evidencias/H-22/CA1-CA2-peticiones-api.json)
- [`CA1-CA2-estado-base.txt`](evidencias/H-22/CA1-CA2-estado-base.txt)

**Pruebas automáticas relacionadas**

- [`CargoDirectivaServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaServiceTest.java) · «RF-102: registra un cargo pendiente y audita solo sus datos no
  personales».
- [`CargoDirectivaControllerTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaControllerTest.java) · «RF-102: POST registra un cambio pendiente con Location».
- [`CargoDirectivaIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaIT.java) · «H-22.1 · RF-102: registra pendiente, requiere autorización y termina el cargo
  previo».
- [`PestanaDirectiva.test.tsx`](../../../frontend/src/features/organizacion/PestanaDirectiva.test.tsx) · «criterio 1: registra un cambio buscando a la persona por CURP».
- [`PantallaClubes.test.tsx`](../../../frontend/src/features/organizacion/PantallaClubes.test.tsx) · «H-22.2: la pestaña Directiva se abre por URL y consulta su endpoint».

### CA-2 · Al autorizar, el cargo anterior termina su vigencia

> Dada la autorización, cuando se aprueba, entonces el cargo anterior termina su vigencia.

**Pasos**

1. Iniciar sesión como **Presidente de aceptación H-22** y abrir el detalle del Deportivo Halcones
   H-22 › pestaña **Directiva**.
2. Comprobar que el cambio de Presidente aparece como pendiente y que la sesión ofrece **Autorizar**.
3. Pulsar **Autorizar**, revisar el aviso con el titular propuesto, la vigencia nueva y la fecha en
   que terminará Ana Ruiz Hernández, y confirmar con **Autorizar cambio**.
4. Revisar la tabla, el historial y la respuesta de la API.
5. Consultar en la base las vigencias y la auditoría.

**Resultado esperado**

- El `POST /api/v1/cargos-directiva/{id}/autorizacion` responde `200` con el cargo en
  `AUTORIZADO`, `autorizadoPor` de la Presidencia y `hasta = NULL`.
- El cargo anterior conserva su fila y termina el **30 de noviembre de 2026**, el día previo al
  inicio de la vigencia nueva.
- La tabla muestra a Bruno Salas López como vigente y a Ana Ruiz Hernández como concluida al abrir
  el historial; la auditoría registra `AUTORIZAR_CARGO_DIRECTIVA` con el cargo anterior y su fecha
  de fin.

**Resultado obtenido:** **Aprobado.** El modal mostró «Titular propuesto: Bruno Salas López»,
«Vigencia desde: 1 dic 2026» y «Al autorizar, Ana Ruiz Hernández terminará su cargo el 30 nov 2026.».
El `POST` respondió `200` con el cargo `AUTORIZADO` y `autorizadoPor` del perfil Presidente. La
pantalla confirmó «Cambio de Presidente autorizado.» y, al abrir el historial, mostró a Bruno como
`VIGENTE` desde el 1 de diciembre de 2026 y a Ana como `CONCLUIDO` del 15 de enero de 2024 al 30 de
noviembre de 2026. En la base, Ana conservó su fila con `hasta = 2026-11-30`, el cargo nuevo quedó
sin `hasta`, el club siguió con dos filas (no se borró ninguna) y la auditoría registró
`AUTORIZAR_CARGO_DIRECTIVA` con `cargoAnteriorId` y `cargoAnteriorHasta = "2026-11-30"`.

**Evidencia**

- [`CA2-pendiente-para-autorizar.png`](evidencias/H-22/CA2-pendiente-para-autorizar.png)
- [`CA2-modal-autorizar.png`](evidencias/H-22/CA2-modal-autorizar.png)
- [`CA2-cargo-autorizado.png`](evidencias/H-22/CA2-cargo-autorizado.png)
- [`CA2-historial-vigencia-anterior.png`](evidencias/H-22/CA2-historial-vigencia-anterior.png)
- [`CA1-CA2-peticiones-api.json`](evidencias/H-22/CA1-CA2-peticiones-api.json)
- [`CA1-CA2-estado-base.txt`](evidencias/H-22/CA1-CA2-estado-base.txt)

**Pruebas automáticas relacionadas**

- [`CargoDirectivaServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaServiceTest.java) · «RF-102: al autorizar, termina el cargo anterior el día antes de la
  nueva vigencia» y «RF-102: la vigencia nueva debe ser posterior a la anterior».
- [`CargoDirectivaControllerTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaControllerTest.java) · «RF-102: POST autorización devuelve el cargo autorizado» y
  «RF-102: POST autorización informa el cambio no vigente».
- [`CargoDirectivaIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaIT.java) · «H-22.1 · RF-102: registra pendiente, requiere autorización y termina el cargo
  previo».
- [`PestanaDirectiva.test.tsx`](../../../frontend/src/features/organizacion/PestanaDirectiva.test.tsx) · «criterio 2: autoriza el cambio y explica la vigencia anterior» y «el
  historial se muestra solo al pedirlo».

### V1 · El ámbito CLUB no consulta ni modifica la directiva de otro club

> Dada la Directiva de un club, cuando consulta o intenta registrar un cargo en otro club, entonces
> responde `404` y no se revela si el club existe (VAL-024).

**Pasos**

1. Con la sesión de **Directiva Halcones H-22**, llamar `GET /api/v1/clubes/{clubVecino}/directiva`.
2. Llamar `POST /api/v1/clubes/{clubVecino}/directiva` con una persona y una vigencia válidas.
3. Revisar los códigos y los cuerpos de ambas respuestas.

**Resultado esperado**

- Ambas peticiones responden `404 NO_ENCONTRADO` con el mensaje genérico «El recurso no existe o no
  tienes acceso a él.», sin distinguir entre club inexistente y club fuera de ámbito.
- No se crea ningún cargo en el club vecino ni se registra auditoría.

**Resultado obtenido:** **Aprobado.** La consulta y el registro respondieron `404 NO_ENCONTRADO` con
el mismo cuerpo de [`V1-respuesta-otro-club.json`](evidencias/H-22/V1-respuesta-otro-club.json). La
Directiva solo vio su propio club en la pantalla (sin lista de clubes) y no pudo alcanzar el Club
Vecino H-22.

**Evidencia**

- [`V1-respuesta-otro-club.json`](evidencias/H-22/V1-respuesta-otro-club.json)

**Pruebas automáticas relacionadas**

- [`CargoDirectivaIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaIT.java) · «H-22.1 · VAL-024: una directiva no consulta ni modifica cargos de otro club».
- [`CargoDirectivaServiceTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaServiceTest.java) · «VAL-015: no modifica directiva sin permiso».
- [`CargoDirectivaControllerTest`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaControllerTest.java) · «RNF-003: seguridad de GET /api/v1/clubes/{clubId}/directiva»,
  «RNF-003: seguridad de POST /api/v1/clubes/{clubId}/directiva» y «RNF-003: seguridad de POST
  /api/v1/cargos-directiva/{id}/autorizacion».
- [`PestanaDirectiva.test.tsx`](../../../frontend/src/features/organizacion/PestanaDirectiva.test.tsx) · «sin permisos de edición ni autorización no ofrece acciones».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Cambio registrado con vigencia y pendiente de autorización | Aprobado |
| CA-2 Autorización con fin de la vigencia anterior | Aprobado |
| V1 Ámbito CLUB limitado al propio club | Aprobado |

## 5. Verificación automática

| Comando | Dónde | Resultado |
| --- | --- | --- |
| `./mvnw verify` | `backend/` | **BUILD SUCCESS** · 1215 pruebas unitarias y de controlador, 204 de integración, 0 fallos; 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS) |
| `npm run lint` | `frontend/` | 0 advertencias y 0 errores en 282 archivos |
| `npm test` | `frontend/` | 66 archivos y 536 pruebas aprobadas |
| `npm run build` | `frontend/` | compilación de producción correcta |

La corrida automática incluye [`CargoDirectivaIT`](../../../backend/src/test/java/mx/tecnm/morelia/siglim/organizacion/CargoDirectivaIT.java) sobre PostgreSQL real (Testcontainers), que cubre
el registro pendiente, la autorización con fin de la vigencia anterior, el ámbito restringido al
club y la auditoría, además de las pruebas de servicio, controlador y de la pestaña Directiva.

## 6. Defectos encontrados

Ninguno que incumpla los criterios de aceptación. El registro pendiente, la autorización, la
vigencia histórica, el ámbito y la auditoría funcionaron como describen RF-102, RN-054 y el contrato
de [`convenciones-api.md`](../../arquitectura/convenciones-api.md).

Observaciones que no son defectos bloqueantes:

1. **La búsqueda de personas depende de su visibilidad para el club.** El modal busca personas ya
   registradas; la Directiva solo puede encontrar a quienes tengan un vínculo con su club (registro,
   tutela, cargo, arbitraje) o cuya ficha haya creado ella misma (RN-054). Si la persona aún no
   existe o no es visible, el propio modal indica que la Liga debe registrarla antes. Es coherente
   con RF-010 y con la protección de datos personales de RNF-012.
2. **La prueba usó una base local efímera y cuentas ficticias**; las capturas no muestran correos,
   tokens ni contraseñas.
3. **H-22.3 no cambió código de producción**: solo agrega este documento y sus evidencias. Aun así
   se ejecutó la verificación completa de ambos subproyectos.

## 7. Conclusión

Los dos criterios de aceptación de H-22 quedaron **aprobados** contra una base local efímera con el
frontend, el backend y la base de datos reales: un cambio de directiva nace pendiente, con su fecha
de vigencia, y al autorizarlo el cargo anterior termina el día previo al inicio del nuevo sin perder
su historial. La verificación V1 (ámbito CLUB) también pasó.

Con esto, **H-22.3 queda terminada** y la historia H-22 puede cerrarse una vez que el PR se apruebe.

> **Actualización 2026-11-10 (DEF-07).** Esta corrida usó una base local efímera. La migración
> `20261108100000_cargo_directiva_pendiente` no está aplicada en el proyecto compartido, así que la
> repetición de H-22 contra el compartido queda pendiente hasta reconectar la integración de GitHub;
> ver [DEF-07](DEF-07-migraciones-compartido.md).
