# Prueba de aceptación · B-07 Consultar el historial de cambios de un registro

| Campo | Valor |
| --- | --- |
| Historia | B-07 · subtarea B-07.3 · Sprint 3 · 1 SP (claves de Jira por confirmar) |
| Requisitos | RF-018, RNF-015, VAL-015, VAL-024, UI-006, RN-021 |
| Pantallas | F-17 (plantilla del equipo, «Ver historial») y la pantalla «Validación de registros» (para generar los cambios) |
| Implementó | Geovani (B-07.1 #164, B-07.2 #168) |
| Versión probada | `develop` en `04e9949` (incluye `e0689bc` y `b531ffc`) |
| Fecha | 2026-11-19 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (5 de 5 criterios); sin defectos ni observaciones |

La corrida valida de punta a punta la historia B-07. Cada cambio de estado de un registro de jugador
(alta, validación y baja) queda en su historial con estado anterior y nuevo, motivo, usuario y
fecha. La Directiva lo consulta desde la plantilla del equipo con «Ver historial», del alta al
cambio más reciente. La consulta respeta el permiso y el ámbito del club, es de solo lectura, no se
audita, y la tabla del historial no se puede modificar desde la aplicación. Se usaron el frontend,
el backend y PostgreSQL reales sobre una base local efímera.

Los criterios salen de RF-018 («conservar cada cambio de estado del registro con usuario, fecha y
motivo»), de RNF-015, de las descripciones de B-07.1 y B-07.2 y del contrato de las convenciones de
API §6.4 («Historial de un registro»). B-07 es una historia B y el backlog no trae criterios
numerados, así que esta prueba los numera de CA-1 a CA-5.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas (`registro_estado_historial` y el trigger `tg_registros_historial` ya existían; B-07 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` hacia la autenticación local; Vite reenvía `/api` al backend |
| Navegador | Panel de navegador del entorno de pruebas, ventana emulada de 1024 × 768 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Frontend: lint, **839 pruebas, 0 fallos** y build correctos. Detalle en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-07/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase**. El entorno local no tiene S3, así que los tres documentos aprobados que exige la
validación de Héctor se sembraron directamente en la base; el resto del flujo (alta, consentimiento,
validación y baja) se hizo en la interfaz. El arnés de autenticación, las cuentas, la semilla y los
guiones de la API viven fuera del repositorio. Aquí solo se versionan este documento y sus
evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican. En la matriz
inicial, `registros.registro:CONSULTAR` lo tienen la Directiva de club, el Secretario, la Comisión
Disciplinaria y el jugador o tutor (ámbito `PROPIO`, que es de H-24); el Tesorero no lo tiene.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones de prueba** | Directiva de club · CLUB (Club Halcones) | Da de alta y de baja a Héctor; consulta el historial en F-17 |
| **Secretario de prueba** | Secretario · LIGA | Valida a Héctor; consulta historiales de cualquier club |
| **Tesorero de prueba** | Tesorero · LIGA | Sin `registros.registro:CONSULTAR` (`403`) |

Datos ficticios en la base local (Temporada local B-07 `ACTIVA`, Libre Primera B-07 · A, equipos
Halcones Primera y Toros Primera):

| Registro | Cómo se generó su historial | Uso en la prueba |
| --- | --- | --- |
| **Héctor Historial** (Halcones) | En la interfaz durante la corrida: alta por la Directiva, validación por el Secretario y baja por la Directiva | CA-1, CA-2, CA-3 y CA-5 |
| **Iván Importado** (Halcones) | Sembrado sin sesión, como una importación: su alta no guarda usuario | CA-3: «Sin usuario registrado» |
| **Omar Otroclub** (Toros) | Sembrado: alta y validación por el Secretario | CA-4: registro de otro club |

Las personas son ficticias y sus CURP tienen formato válido pero no corresponden a nadie.

## 3. Escenarios

### CA-1 · La plantilla ofrece «Ver historial» y muestra los cambios del alta al más reciente

> Dado un registro del club, cuando la Directiva pulsa «Ver historial» en la plantilla (F-17),
> entonces ve una línea de tiempo del alta al cambio más reciente, cada cambio con su estado (insignia
> con ícono y texto), el estado anterior, la fecha y hora, quién lo hizo y el motivo.

**Pasos.** La Directiva da de alta a Héctor en F-18 (paso de consentimiento incluido) y el Secretario
lo activa en «Validación de registros» con las observaciones «Documentos revisados en ventanilla». La Directiva abre la
plantilla de Halcones Primera.

**Resultado obtenido.** Cada fila de la plantilla tiene **Ver historial**, además de sus acciones
(«Dar de baja», «Cargar documentos»). El modal «Historial de Héctor Historial Prueba» muestra el
equipo, la categoría y la temporada, y dos cambios en orden: **PENDIENTE DE VALIDACIÓN** («Estado
inicial», «19 nov 2026, 20:42 · Por Directiva Halcones de prueba», «Alta del registro») y **ACTIVO**
(«Antes: Pendiente de validación», «19 nov 2026, 20:43 · Por otro usuario», «Documentos revisados en
ventanilla») ([captura](evidencias/B-07/CA1-historial-alta-y-validacion.png)).

**Resultado:** ✅ Aprobado.

### CA-2 · Cada cambio de estado nuevo se agrega con su usuario y su motivo (RF-018)

> Dado un registro activo, cuando la Directiva lo da de baja con motivo, entonces el historial agrega
> el cambio `ACTIVO → BAJA` con ese motivo, su usuario y la hora, sin alterar los anteriores.

**Resultado obtenido.** La Directiva dio de baja a Héctor con «El jugador se muda a otra ciudad por
trabajo». Al volver a abrir **Ver historial** aparece un tercer cambio: **BAJA** («Antes: Activo»,
«19 nov 2026, 20:44 · Por Directiva Halcones de prueba», con el motivo), y los dos anteriores siguen
iguales ([captura](evidencias/B-07/CA2-historial-con-baja.png)). En la base, el historial de Héctor
tiene las tres filas con usuario y motivo, y la auditoría registra por separado
`REGISTRAR_REGISTRO_JUGADOR`, `VALIDAR_REGISTRO_JUGADOR` y `DAR_BAJA_REGISTRO_JUGADOR`
([auditoría](evidencias/B-07/historial-auditoria.txt)).

**Resultado:** ✅ Aprobado.

### CA-3 · Quién hizo el cambio se muestra según lo que la sesión puede ver

> `cambiadoPor` lleva el nombre mostrado del perfil; si la sesión no puede ver ese perfil (una cuenta
> de club no ve los de la liga), el nombre va nulo y la interfaz dice «Por otro usuario»; si el
> historial no guardó usuario, el campo es nulo y la interfaz dice «Sin usuario registrado».

**Resultado obtenido** ([respuestas](evidencias/B-07/respuestas-api.txt)):

| Consulta del historial de Héctor | Validación (`PENDIENTE_VALIDACION → ACTIVO`) | Alta y baja |
| --- | --- | --- |
| Secretario (LIGA) | `cambiadoPor.nombre: "Secretario de prueba"` | «Directiva Halcones de prueba» |
| Directiva de Halcones (CLUB) | `cambiadoPor.nombre: null` → «Por otro usuario» en F-17 | «Directiva Halcones de prueba» |

El historial de Iván, cuya alta se cargó sin sesión, muestra «19 nov 2026, 20:40 · Sin usuario
registrado» ([captura](evidencias/B-07/CA3-sin-usuario-registrado.png)).

**Resultado:** ✅ Aprobado.

### CA-4 · La consulta respeta el permiso y el ámbito del club

> Una cuenta de ámbito `LIGA` ve el historial de cualquier registro; una de ámbito `CLUB`, solo los de
> su club: el de otro club responde `404`, igual que un registro inexistente (VAL-024). Sin
> `registros.registro:CONSULTAR`, `403 VAL-015`.

**Resultado obtenido** ([respuestas](evidencias/B-07/respuestas-api.txt)):

| Consulta | Respuesta |
| --- | --- |
| Secretario: historial de Omar (Club Toros) | `200` con el alta y la validación |
| Directiva de Halcones: historial de Omar (Club Toros) | `404 NO_ENCONTRADO` |
| Directiva de Halcones: registro inexistente | `404 NO_ENCONTRADO`, la misma respuesta |
| Tesorero: historial de Héctor | `403 VAL-015` |

**Resultado:** ✅ Aprobado.

### CA-5 · El historial es de solo lectura y consultarlo no se audita (RNF-015)

> La ruta no admite escritura (`405`), responde con `Cache-Control: no-store`, la consulta no deja
> eventos en la auditoría y la aplicación no puede modificar ni borrar el historial en la base.

**Resultado obtenido** ([respuestas](evidencias/B-07/respuestas-api.txt)):

- `POST`, `PATCH`, `DELETE` y `PUT` sobre `/registros-jugador/{id}/historial` responden
  `405 METODO_NO_PERMITIDO`.
- El `GET` responde `200` con `Cache-Control: no-store`.
- La auditoría tenía 4 eventos antes de las consultas por API y siguió con 4 después.
- Con el rol de la aplicación (`SET ROLE siglim_app`), un `UPDATE` y un `DELETE` sobre
  `registro_estado_historial` fallan con «permission denied for table registro_estado_historial».
  El rol solo tiene `INSERT` y `SELECT` sobre la tabla.

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 «Ver historial» en la plantilla, del alta al más reciente | ✅ Aprobado |
| CA-2 Cada cambio nuevo se agrega con usuario y motivo | ✅ Aprobado |
| CA-3 Quién lo hizo según lo visible para la sesión | ✅ Aprobado |
| CA-4 Permiso y ámbito del club | ✅ Aprobado |
| CA-5 Solo lectura, sin auditar la consulta, tabla inmutable | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `04e9949` el 19-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-07/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `HistorialRegistroIT` (3), `BajaRegistroIT` (5), `RegistroJugadorControllerTest` (66) y `RegistroJugadorServiceTest` (81) |
| Frontend | `npm run lint` | Sin errores |
| Frontend | `npx vitest run --maxWorkers=2` | **90 archivos y 839 pruebas, 0 fallos**, incluidas las del historial en `ModalHistorialRegistro.test.tsx` y `PantallaPlantilla.test.tsx` |
| Frontend | `npm run build` | Correcto |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `HistorialRegistroIT` «RF-018 · B-07.1: el Secretario ve el alta, la validación y la baja en orden, con motivo y usuario»; `ModalHistorialRegistro.test.tsx` «RF-018: muestra alta → validación → baja en orden, con estado, fecha, quién y motivo»; `PantallaPlantilla.test.tsx` «B-07.2 · RF-018: «Ver historial» de la fila abre el historial de ese registro y se cierra» |
| CA-2 | `BajaRegistroIT` «H-23 · RF-014 · RN-021: la Directiva da de baja un registro ACTIVO; queda el historial con su motivo y usuario» y «RF-017 · DEC-118: una segunda baja responde 409 y no agrega historial» |
| CA-3 | `ModalHistorialRegistro.test.tsx` (textos «Por otro usuario» y «Sin usuario registrado») |
| CA-4 | `HistorialRegistroIT` «RF-018 · VAL-024: la Directiva ve el historial de su club; la de otro club recibe 404 como uno inexistente» y «VAL-015: una cuenta sin registros.registro:CONSULTAR recibe 403»; `RegistroJugadorControllerTest` «RNF-003 · VAL-015 · VAL-024: seguridad de GET /api/v1/registros-jugador/{id}/historial»; `ModalHistorialRegistro.test.tsx` «VAL-024: un 404 muestra el error de consulta sin reintentar» |
| CA-5 | `RegistroJugadorControllerTest` «RF-018 · B-07.1: GET /registros-jugador/{id}/historial responde 200 con no-store y el arreglo en orden» y «RNF-015 · B-07.1: el historial es de solo lectura: POST, PATCH y DELETE responden 405»; la inmutabilidad en la base y la ausencia de auditoría solo se comprueban en esta corrida |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**Observaciones:** ninguna. El texto «Por otro usuario» del cambio que hizo la liga, visto por la
Directiva, es el comportamiento que fija el contrato (RLS `perfiles_leer`) y que B-07.2 marcó como
**[PROPUESTA]** de redacción.

## 7. Conclusión

B-07 cumple sus criterios: cada cambio de estado del registro queda con estado anterior y nuevo,
motivo, usuario y fecha; la Directiva lo consulta desde la plantilla en orden cronológico; la
consulta respeta el permiso y el ámbito del club, no se audita y no puede modificar el historial.
**B-07 puede cerrarse.**
