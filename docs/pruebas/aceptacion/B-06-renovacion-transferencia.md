# Prueba de aceptación · B-06 Renovar registros y transferir jugadores entre temporadas

| Campo | Valor |
| --- | --- |
| Historia | B-06 · subtarea B-06.3 · Sprint 3 · 3 SP (claves de Jira por confirmar) |
| Requisitos | RF-015, RF-016, RF-111, VAL-026, VAL-017, VAL-025, VAL-001, RN-039, DEC-13, DEC-146, DEC-153, DEC-154, VAL-015, VAL-024, RF-093 |
| Pantallas | F-18 (asistente de alta de jugador) y F-11 (parámetros de la temporada) |
| Implementó | Geovani (B-06.1 #161, B-06.2 #165 y la corrección #166) |
| Versión probada | `develop` en `04e9949` (incluye `6e40eb1`, `bd50d5f` y `ba390bc`) |
| Fecha | 2026-11-19 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (5 de 5 criterios); una observación sin defecto |

La corrida valida de punta a punta la historia B-06. Cuando la Directiva busca por CURP a un jugador
de una temporada anterior, el asistente de alta (F-18) le ofrece **renovarlo** si jugó en su club o
**transferirlo** si jugó en otro. La renovación hereda los documentos aprobados. La transferencia no
los hereda y depende de que la temporada destino permita el cambio de club, un parámetro que el
Administrador configura en F-11. Las reglas de la temporada (VAL-017, VAL-026), del menor (VAL-025) y
del tipo de movimiento se explican con su código, todo queda auditado y el registro nuevo se enlaza
con el anterior. Se usaron el frontend, el backend y PostgreSQL reales sobre una base local efímera.

Los criterios salen de RF-015, RF-016 y RF-111, de las decisiones DEC-13, DEC-146, DEC-153 y DEC-154,
y del contrato de las convenciones de API §6.4 («Renovación y transferencia entre temporadas» y
«Opción para el asistente de alta»). B-06 es una historia B y el backlog no trae criterios numerados,
así que esta prueba los numera de CA-1 a CA-5.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 14 migraciones del repositorio aplicadas, incluidas `20261117190000` (enlace del registro anterior) y `20261117220000` (RF-111 en el alta normal) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS con una llave ES256 y responde el inicio de sesión con contraseña de las cuentas ficticias |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` hacia la autenticación local; Vite reenvía `/api` al backend |
| Navegador | Panel de navegador del entorno de pruebas, ventana emulada de 1024 × 768 px |
| Automatización | Backend `./mvnw verify`: **BUILD SUCCESS** (2113 + 333 pruebas, 0 fallos). Frontend: lint y build correctos; resultados de `vitest` en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/B-06/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase**. La renovación necesita una temporada anterior cerrada y otra activa, y en el compartido
no hay ninguna activa (ver B-08). El arnés de autenticación, las cuentas, la semilla y los guiones de
la API viven fuera del repositorio. Aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones de prueba** | Directiva de club · CLUB (Club Halcones) | CA-1 a CA-5: renueva, transfiere y recibe los rechazos |
| **Secretario de prueba** | Secretario · LIGA | CA-1 y CA-2: valida los registros nuevos (VAL-001) |
| **Administrador de prueba** | Administrador general · LIGA | CA-3: desactiva el cambio de club en F-11 |
| **Tesorero de prueba** | Tesorero · LIGA | CA-5: sin `registros.registro:EDITAR` |

Datos ficticios sembrados en la base local:

| Dato | Situación inicial | Uso en la prueba |
| --- | --- | --- |
| **Temporada local 2025-2026** | `CERRADA` | Temporada anterior |
| **Temporada local 2026-2027** | `ACTIVA`, parámetros v1 con `permite_cambio_club = true` | Temporada destino |
| **Club Halcones** y **Club Toros** | Afiliación `APROBADA` en las dos temporadas; un equipo por club y temporada en Libre Primera B-06 · A | Clubes de destino y de origen |
| **Renato Renueva** | `ACTIVO` en Halcones 2025, con sus 3 documentos `APROBADO` | CA-1: renovación |
| **Tadeo Transfiere** | `ACTIVO` en Toros 2025, con sus documentos `APROBADO` | CA-2: transferencia |
| **Simón Sincambio** | `ACTIVO` en Toros 2025 | CA-3: transferencia no permitida |
| **Nicolás Nuevo** | Sin registros | CA-4: sin registro anterior |
| **Mateo Menor** | Menor; `ACTIVO` en Halcones 2025; tutela vigente y consentimiento **revocado** | CA-4: VAL-025 y movimiento que no corresponde |
| **Andrés Activo** | `ACTIVO` en Toros 2026 | CA-5: VAL-017 |
| **Bruno Baja** | `BAJA` en Toros 2026 | CA-5: VAL-026 |

Las personas son ficticias y sus CURP tienen formato válido pero no corresponden a nadie.

## 3. Escenarios

### CA-1 · La renovación en el mismo club reutiliza la identidad y los documentos aprobados

> Dado un jugador que jugó en el club en una temporada anterior, cuando la Directiva lo busca por
> CURP en F-18, entonces el asistente ofrece «Renovar en mi club» (sin el alta normal). Al confirmar,
> el registro nuevo queda `PENDIENTE_VALIDACION`, enlazado al anterior, con sus documentos aprobados
> contando para VAL-001, y la liga lo puede activar sin cargar documentos nuevos.

**Resultado obtenido.** Al buscar a Renato, F-18 mostró «Persona encontrada» y «Jugó en tu club en la
temporada «Temporada local 2025-2026»», con el botón **Renovar en mi club** y sin la opción de alta
normal ([captura](evidencias/B-06/CA1-ofrece-renovar-en-mi-club.png)). Tras elegir el equipo
Halcones 2026 y **Renovar registro**, el asistente saltó a los documentos: el registro quedó en
**PENDIENTE DE VALIDACIÓN** en la Temporada local 2026-2027 ([captura](evidencias/B-06/CA1-renovacion-pendiente.png))
con «3/3 cargados · 3/3 aprobados» heredados del registro anterior, sin volver a cargarlos
([captura](evidencias/B-06/CA1-documentos-heredados.png)). El historial dice «Renovación desde la
temporada «Temporada local 2025-2026»». El Secretario lo validó sin documentos nuevos y respondió
`200` con `estado: ACTIVO`. En la base, el registro quedó enlazado a Halcones 2025 y la auditoría
registra `RENOVAR_REGISTRO` ([auditoría](evidencias/B-06/historial-auditoria.txt)).

**Resultado:** ✅ Aprobado.

### CA-2 · La transferencia a otro club se permite según la temporada y no hereda documentos

> Dado un jugador que jugó en otro club en una temporada anterior y una temporada destino que permite
> el cambio de club, cuando la Directiva lo busca en F-18, entonces el asistente ofrece «Registrar en
> este equipo» sin nombrar el club de origen; el registro nuevo queda `PENDIENTE_VALIDACION`,
> enlazado al anterior, y los documentos del club de origen no cuentan.

**Resultado obtenido.** Para Tadeo, F-18 mostró «Jugó en otro club en la temporada «Temporada local
2025-2026»», sin decir cuál, con **Registrar en este equipo** ([captura](evidencias/B-06/CA2-ofrece-transferencia.png)).
Tras **Registrar transferencia**, el registro quedó en **PENDIENTE DE VALIDACIÓN** con «0/3
cargados · 0/3 aprobados» y los botones **Cargar** ([captura](evidencias/B-06/CA2-transferencia-sin-documentos-heredados.png)).
El historial dice «Transferencia desde el club «Club Toros» de la temporada «Temporada local
2025-2026»». El Secretario intentó validarlo y recibió `422 VAL-001` con los faltantes
«Identificación» y «Fotografía». En la base, el registro quedó enlazado a Toros 2025 y la auditoría
registra `TRANSFERIR_REGISTRO` ([respuestas](evidencias/B-06/respuestas-api.txt)).

**Resultado:** ✅ Aprobado.

### CA-3 · El Administrador configura en F-11 si la temporada permite el cambio de club (RF-111)

> Dado F-11, cuando el Administrador desactiva «Permitir cambio de club entre temporadas» y guarda
> una versión nueva con motivo, entonces el asistente muestra la transferencia deshabilitada con la
> explicación, y la transferencia y el alta normal del mismo jugador responden `409 RF-111`.

**Resultado obtenido.** F-11 muestra el interruptor activado con el rótulo **No oficial** y la ayuda
«Si lo desactivas, los clubes no pueden registrar como transferencia…»
([captura](evidencias/B-06/CA3-F11-interruptor-activo.png)). Al desactivarlo, «Guardar como
nueva versión» pidió un motivo de al menos 10 caracteres ([captura](evidencias/B-06/CA3-F11-nueva-version-con-motivo.png))
y dejó la versión 2 con el interruptor apagado ([captura](evidencias/B-06/CA3-F11-version-2-sin-cambio-de-club.png)),
auditada como `REGISTRAR_PARAMETROS`. Después, al buscar a Simón (que jugó en Toros), F-18 muestra
**Registrar en este equipo** deshabilitado (`disabled`) con «La temporada «Temporada local
2026-2027» no permite cambios de club entre temporadas. La liga puede habilitarlo en los parámetros
de la temporada.» ([captura](evidencias/B-06/CA3-transferencia-deshabilitada.png)). Por API, la opción
responde `TRANSFERENCIA` con `permitido: false` y el mismo motivo; la transferencia y el alta normal
(DEC-154) responden `409 RF-111` con ese texto, y no se creó ningún registro.

**Resultado:** ✅ Aprobado.

### CA-4 · El movimiento debe corresponder al registro anterior y respetar al menor

> Sin registro anterior no hay movimiento: el asistente sigue con el alta normal y la API responde
> `422 RF-015`/`RF-016`. Renovar a alguien que jugó en otro club o transferir a alguien que ya era del
> club se rechaza con el código del movimiento. Un menor sin consentimiento vigente no se renueva
> (`422 VAL-025`).

**Resultado obtenido** ([respuestas](evidencias/B-06/respuestas-api.txt)):

| Caso | Respuesta |
| --- | --- |
| Opción para Nicolás (sin registro anterior) | `200` con `tipo: null`: el asistente sigue con el alta normal |
| Renovar a Nicolás | `422 RF-015` «…no hay nada que renovar. Da de alta su registro en el equipo.» |
| Transferir a Nicolás | `422 RF-016` «…no hay nada que transferir…» |
| Renovar a Simón (su anterior es de Toros) | `422 RF-015` «El registro anterior es del club «Club Toros»: para cambiarlo de club usa la transferencia.» (ver O-1) |
| Transferir a Mateo (su anterior ya es de Halcones) | `422 RF-016` «El registro anterior ya es de tu club: para conservar al jugador usa la renovación.» |
| Renovar a Mateo, menor con el consentimiento revocado | `422 VAL-025` con `requisito: CONSENTIMIENTO` |

**Resultado:** ✅ Aprobado.

### CA-5 · Dentro de la temporada no hay cambio de club, y se respetan el permiso y el ámbito

> Un jugador con registro activo en la temporada destino responde `409 VAL-017` con su club y
> equipo; uno de baja en otro club de la misma temporada, `409 VAL-026` (RN-039, DEC-13). Un equipo
> fuera del ámbito responde `404`, un dato faltante `400` y una cuenta sin permiso `403`.

**Resultado obtenido** ([respuestas](evidencias/B-06/respuestas-api.txt)):

| Caso | Respuesta |
| --- | --- |
| Transferir a Andrés (`ACTIVO` en Toros 2026) | `409 VAL-017` «…ya está registrada en esta temporada en el club «Club Toros», equipo «Toros 2026»…» |
| Transferir a Bruno (`BAJA` en Toros 2026) | `409 VAL-026` «…estuvo registrada en otro club en esta temporada…», sin nombrar el club |
| Directiva de Halcones hacia el equipo Toros 2026 | `404 NO_ENCONTRADO` |
| Renovación sin `equipoId` | `400 VALIDACION` «Indica la persona y el equipo de destino.» |
| Tesorero (sin `registros.registro:EDITAR`) | `403 VAL-015` |

Los intentos rechazados no crearon registros ni dejaron eventos en la auditoría: solo quedan
`RENOVAR_REGISTRO`, `TRANSFERIR_REGISTRO`, `REGISTRAR_PARAMETROS` y `VALIDAR_REGISTRO_JUGADOR`, sin
CURP ni nombres en `detalle` ([auditoría](evidencias/B-06/historial-auditoria.txt)).

**Resultado:** ✅ Aprobado.

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Renovación con documentos heredados | ✅ Aprobado |
| CA-2 Transferencia sin documentos heredados | ✅ Aprobado |
| CA-3 Cambio de club configurable en F-11 (RF-111, DEC-154) | ✅ Aprobado |
| CA-4 Movimiento que corresponde y menor con consentimiento | ✅ Aprobado |
| CA-5 Sin cambio dentro de la temporada; permiso y ámbito | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `04e9949` el 19-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/B-06/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 2113 pruebas unitarias y de controlador, 0 fallos; 333 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `RegistroJugadorServiceTest` (81), `RegistroJugadorControllerTest` (66), `RenovacionesTransferenciasIT` (10) y `ParametrosServiceTest` (38) |
| Frontend | `npm run lint` | Sin errores |
| Frontend | `npx vitest run --maxWorkers=2` | **90 archivos y 839 pruebas, 0 fallos** en la segunda corrida. En la primera, con la máquina cargada, fallaron 13 por tiempo de espera (5 s) en `PantallaAltaJugador`, `PestanaParametros` y `PantallaClubes`; repetidos solos, esos 3 archivos pasan 68/68 |
| Frontend | `npm run build` | Correcto |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `RenovacionesTransferenciasIT` «RF-015 · DEC-146: renueva en el mismo club, enlaza el registro anterior y deja historial» y «RF-015 · VAL-001: la renovación reutiliza los documentos aprobados del mismo club y se activa»; `RegistroJugadorServiceTest` «RF-015 · DEC-146: renueva en el mismo club, enlaza el registro anterior y audita» y «RF-015 · DEC-153: ofrece la renovación si el registro anterior es del mismo club»; `PantallaAltaJugador.test.tsx` «RF-015: con un registro anterior en el club ofrece «Renovar en mi club» y crea la renovación…» |
| CA-2 | `RenovacionesTransferenciasIT` «RF-016 · RF-111: transfiere a otro club cuando la temporada lo permite y conserva el enlace» y «RF-016 · VAL-001: la transferencia no hereda los documentos aprobados del club de origen»; `RegistroJugadorServiceTest` «RF-016 · RF-111 · DEC-153: ofrece la transferencia permitida…»; `PantallaAltaJugador.test.tsx` «RF-016: con un registro anterior en otro club ofrece «Registrar en este equipo»…» |
| CA-3 | `RenovacionesTransferenciasIT` «RF-111 · DEC-13: con permite_cambio_club=false la transferencia responde 409 y no crea registro», «RF-111 · DEC-154: el alta normal con registro anterior de otro club y cambio no permitido responde 409» y «RF-111 · DEC-154: el trigger rechaza un alta sin registro_anterior_id que cambia de club sin permiso»; `RegistroJugadorServiceTest` «RF-111 · DEC-153: la transferencia queda no permitida con la explicación y sin datos del otro club»; `PantallaAltaJugador.test.tsx` «RF-111: si la Liga no permite el cambio de club, «Registrar en este equipo» queda deshabilitado con la explicación»; `PestanaParametros.test.tsx` «RF-111: el interruptor de cambio de club se rotula «No oficial» y envía permiteCambioClub:false» |
| CA-4 | `RegistroJugadorServiceTest` «RF-015 · RF-016: sin registro anterior responde 422 con el código del movimiento», «RF-015 · RF-016: una renovación con registro de otro club y una transferencia del mismo club se rechazan» y «DEC-153: sin registro anterior no hay movimiento que ofrecer»; `PantallaAltaJugador.test.tsx` «DEC-153: sin registro anterior no se ofrece movimiento y sigue el alta normal» |
| CA-5 | `RenovacionesTransferenciasIT` «VAL-026: no se puede transferir a otro club dentro de la misma temporada»; `RegistroJugadorServiceTest` «VAL-026: una baja en otro club de la temporada destino se rechaza antes de buscar el anterior», «VAL-017 · DEC-153: no ofrece movimiento si la persona ya está registrada en la temporada destino» y «VAL-015 · VAL-024 · DEC-153: sin permiso, sin datos o con un equipo fuera de ámbito no responde la opción» |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · La renovación rechazada nombra el club de origen.** Cuando la Directiva de Halcones intenta
*renovar* a Simón, el `422 RF-015` responde «El registro anterior es del club «Club Toros»…». La
opción del asistente (DEC-153) y el `409 RF-111` del alta normal (DEC-154) evitan a propósito nombrar
al otro club, y VAL-026 tampoco lo nombra (DEC-88). El contrato de DEC-146 no fija el texto de este
`422`, así que no se registra como defecto. En la interfaz el caso no se alcanza, porque el asistente
ofrece la transferencia y no la renovación. Se recomienda que el equipo decida si el mensaje debe
decir «otro club», como las demás respuestas. El historial «Transferencia desde el club «…»» sí
nombra el club de origen, como lo pide DEC-146, y solo lo ve quien consulta el registro nuevo.

## 7. Conclusión

B-06 cumple sus criterios: la Directiva renueva o transfiere desde el asistente de alta según dónde
jugó la persona, la renovación conserva los documentos aprobados y la transferencia no, el
Administrador decide en F-11 si la temporada admite el cambio de club, y las reglas de la temporada,
del menor y del movimiento se explican con su código y sin crear registros. **B-06 puede cerrarse.**
La observación O-1 no bloquea el cierre.
