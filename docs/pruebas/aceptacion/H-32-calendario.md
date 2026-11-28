# Prueba de aceptación · H-32 Generar el calendario de la temporada

| Campo | Valor |
| --- | --- |
| Historia | H-32 · subtarea H-32.5 · Sprint 4 (Must, 3 SP) |
| Requisitos | RF-033, RF-034, RF-037, RF-041, RN-012, RN-020, RN-021, DEC-174, DEC-175, DEC-177, DEC-184 a DEC-186, DEC-189, DEC-192, DEC-193, DEC-197, DEC-198, DEC-200, ADR-0015 |
| Pantalla | F-22 «Programación de jornadas», tarjeta y modal «Generar calendario» (`/app/programacion/jornadas`) |
| Implementó | Leonardo (H-32.1 #193, H-32.2 #197, H-32.3 #198 y H-32.4 #199) |
| Versión probada | `develop` en `abd90c2` |
| Fecha | 2026-11-27 |
| Ejecutó | Israel (prueba cruzada, DEC-50), con un agente de IA en el entorno local |
| Resultado global | **Aprobado** (6 de 6 criterios); dos observaciones sin defecto |

La corrida valida la generación del calendario con un **grupo par (A, 4 equipos)** y uno **impar (B,
5 equipos)** y 2 vueltas. El Programador lo genera desde el modal de F-22: cada par se enfrenta
exactamente dos veces, nadie aparece dos veces en una fecha, el grupo impar descansa a un equipo por
fecha y a cada uno dos veces, la localía queda equilibrada y se invierte en la segunda vuelta, y las
10 fechas caen en sábado y domingo, una semana tras otra, desde el primer sábado después del inicio.
Los días de una fecha se cambian con motivo solo mientras está en borrador, un grupo con partidos ya
ligados no se regenera y la generación queda registrada con su autor, fecha y parámetros.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local y efímero (Docker `postgres:17-alpine`), con la simulación de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las 16 migraciones del repositorio (H-32.5 no agrega migración) |
| Autenticación | Servidor local que simula Supabase Auth: publica el JWKS (ES256) y responde el inicio de sesión con contraseña |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25 y Spring Boot 4.1, con `DB_URL` y `SUPABASE_URL` apuntando a la base y a la autenticación locales |
| Frontend | `npm run dev -- --port 5173` (Vite) con `VITE_SUPABASE_URL` apuntando a la autenticación local |
| Navegador | Panel de navegador del entorno de pruebas, 1280 × 800 px |
| Automatización | Backend `./mvnw verify` y frontend `npx vitest run src/features/programacion/calendario`; resultados en la sección 5 y en [`pruebas-automaticas.txt`](evidencias/H-32/pruebas-automaticas.txt) |

La prueba corrió sobre una **base local efímera**: **no se alteró el proyecto compartido de
Supabase**. El arnés de autenticación, la semilla y los guiones de la API viven fuera del repositorio;
aquí solo se versionan este documento y sus evidencias.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos son ficticios (dominio `.test`) y sus contraseñas
no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Programador de prueba** | Programador · LIGA (`programacion.jornada:EDITAR` y `ENVIAR`) | Genera el calendario, cambia días, asigna y envía la fecha 2 |
| **Comisión de prueba** | Comisión de arbitraje · LIGA (solo `programacion.jornada:CONSULTAR`) | Comprueba el `403` |

Semilla por SQL como dueño del esquema: «Temporada prueba H-32», `ACTIVA`, con inicio el **miércoles
6 de enero de 2027** (para comprobar que la fecha 1 cae en el sábado siguiente) y parámetros v1 con
**2 vueltas**. Categoría «Libre (prueba H-32)» (juega el domingo) con el grupo **A** (Halcones, Toros,
Pumas y Leones) y el grupo **B** (Águilas, Lobos, Osos, Zorros y Linces), cada equipo de su propio
club. Dos canchas de la unidad deportiva para asignar la fecha 2 en CA-4 y CA-5.

## 3. Escenarios

Antes de generar se comprobó la seguridad ([respuestas](evidencias/H-32/respuestas-api.txt), S-1 a
S-3): sin sesión `401 NO_AUTENTICADO`; la Comisión `403 VAL-015`; y el calendario de los dos grupos
aparece con `generado: false`.

**Generación desde F-22.** La tarjeta «Calendario de la temporada» decía «Sin generar · 2 grupos». El
modal «Generar calendario» mostró **2 vueltas · parámetros versión 1** (de la versión vigente, solo
lectura), la fecha de inicio con el valor de la temporada (06/01/2027) y en vivo «La fecha 1 será el
sáb 9 y dom 10 ene 2027», con los dos grupos marcados «Sin calendario»
([captura](evidencias/H-32/CA4-modal-generar-fecha-1.png)). Al confirmar respondió `201`: grupo A con
4 equipos y **6 fechas**, grupo B con 5 equipos y **10 fechas**, 10 fechas creadas
([captura](evidencias/H-32/CA1-CA6-calendario-generado.png), G-1 a G-3 en las respuestas). Las
propiedades se comprobaron con consultas a la base ([base y auditoría](evidencias/H-32/base-y-auditoria.txt)).

### CA-1 · Cada par se enfrenta exactamente v veces y nadie aparece dos veces en una fecha (RN-012, DEC-174)

> Dado un grupo con N equipos activos y v vueltas, cuando se genera, entonces cada par se enfrenta
> exactamente v veces y ningún equipo aparece dos veces en una fecha (RN-012).

| Grupo | Pares distintos | Veces por par | Equipos que aparecen dos veces en una fecha | Equipos por fecha |
| --- | --- | --- | --- | --- |
| A (N = 4) | 6 = C(4,2) | 2 en todos | 0 | 4 en las 6 fechas |
| B (N = 5) | 10 = C(5,2) | 2 en todos | 0 | 5 en las 10 fechas (4 juegan y 1 descansa) |

**Resultado: ✅ Aprobado.**

### CA-2 · Con N impar descansa un equipo por fecha y cada uno descansa v veces (RF-037, DEC-184)

> Dado N impar, cuando se genera, entonces en cada fecha descansa un equipo y todos descansan v veces.

En el grupo B hay **exactamente un descanso en cada una de sus 10 fechas** y **cada equipo descansa 2
veces**, una por vuelta: Linces, Zorros, Osos, Lobos y Águilas en las fechas 1 a 5 y el mismo orden en
las 6 a 10. El grupo par A no tiene descansos. En la API, cada fecha del grupo B trae `descansa` y el
listado de fechas cuenta `descansos: 1` (G-2 y G-3).

**Resultado: ✅ Aprobado.**

### CA-3 · Localía equilibrada por vuelta e invertida en la siguiente (DEC-184)

> La diferencia entre partidos de local y de visitante de cada equipo es como máximo 1 por vuelta; en
> la vuelta siguiente se invierte la localía.

| Grupo | Local / visitante por equipo y vuelta | Diferencia máxima | Pares con la misma localía en las dos vueltas |
| --- | --- | --- | --- |
| A (3 partidos por vuelta) | 2/1 o 1/2, y al revés en la vuelta 2 | 1 | 0 de 6 |
| B (4 partidos por vuelta) | 2/2 en las dos vueltas | 0 | 0 de 10 |

**Resultado: ✅ Aprobado.**

### CA-4 · Fechas 1…F en sábado y domingo; sus días se cambian con motivo mientras están en borrador (DEC-175, DEC-186, DEC-193)

> Se crean las fechas 1…F con sábado y domingo consecutivos desde la fecha de inicio; mientras sus
> jornadas estén en BORRADOR, el Programador puede cambiar o agregar días con motivo.

- **Fechas.** Con el inicio en miércoles 6 de enero, la fecha 1 es el **sábado 9 y domingo 10 de
  enero** de 2027 y las fechas 2 a 10 siguen cada 7 días hasta el 13 y 14 de marzo; F = 10, la última
  del grupo de 5 (base, CA-4).
- **Cambiar días en borrador.** `PATCH /fechas/{fecha 1}` con `["2027-01-08", "2027-01-09",
  "2027-01-10"]` y el motivo «Se agrega el viernes por el torneo relámpago» respondió `200` con los
  tres días; quedó `CAMBIAR_DIAS_FECHA` con `diasAnteriores` y `dias` (CA-4a y auditoría).
- **Sin motivo** responde `400 RN-021` «Escribe un motivo de al menos 10 caracteres…» (CA-4b).
- **Fuera de borrador no.** Se asignó la fecha 2 (H-33), se envió a aprobación (`200`, sus dos
  jornadas en `REVISION`) y cambiar sus días respondió `409 RF-090` «Los días de la fecha 2 solo se
  cambian en borrador…» (CA-4c, CA-4d y [captura](evidencias/H-32/CA4-CA5-fecha-2-en-revision.png)).

**Resultado: ✅ Aprobado.**

### CA-5 · Un grupo con partidos ya ligados no se regenera (RN-020, DEC-189)

> Dado un grupo con partidos ya ligados a su calendario, cuando se intenta regenerarlo, entonces se
> rechaza con la causa.

Con la fecha 2 asignada (4 emparejamientos ligados a partidos), `POST /temporadas/{id}/calendario` con
`grupoIds: [A]` y un motivo respondió `409 RN-020` «El calendario de «Libre (prueba H-32) – A» ya
tiene partidos o jornadas programados y no se puede regenerar; quita esos grupos de grupoIds.»
(CA-5b). No escribió nada: siguen 42 emparejamientos vigentes, 0 reemplazados y una sola
`GENERAR_CALENDARIO`. En F-22, el modal «Regenerar calendario» muestra los dos grupos deshabilitados
con la insignia «Con partidos» y la causa «Ya tiene partidos programados; su calendario no se puede
regenerar (RN-020).» ([captura](evidencias/H-32/CA5-regenerar-grupos-con-partidos.png)).

**Resultado: ✅ Aprobado.**

### CA-6 · Se registran usuario, fecha y parámetros usados (RF-041, DEC-192)

> Se registran usuario, fecha y parámetros usados (RF-041).

Los 42 emparejamientos tienen `creado_por` = «Programador de prueba». La auditoría registra
`GENERAR_CALENDARIO` en M-07 sobre `temporadas`, con el usuario, la fecha y hora, y un detalle solo de
identificadores y conteos: `versionParametros: 1`, `vueltas: 2`, `fechaInicio: 2027-01-06`, los dos
grupos, `emparejamientos: 42`, `totalFechas: 10`, `fechasCreadas: 10`, sin reemplazados ni omitidos
(base, CA-6). La respuesta `201` trae los mismos datos (G-1).

**Resultado: ✅ Aprobado.**

### Auditoría (RF-093)

Cada operación dejó su fila en M-07 con el «Programador de prueba»: `GENERAR_CALENDARIO`,
`GENERAR_FECHA` (fecha 2), `ENVIAR_FECHA_A_APROBACION` (fecha 2) y `CAMBIAR_DIAS_FECHA` (fecha 1, con
el motivo). El `409 RN-020` y los rechazos `400` y `409` de los días no dejaron filas. Ningún detalle
lleva nombres ni datos personales.

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Cada par v veces y nadie dos veces por fecha | ✅ Aprobado |
| CA-2 Con N impar, un descanso por fecha y v por equipo | ✅ Aprobado |
| CA-3 Localía equilibrada e invertida en la segunda vuelta | ✅ Aprobado |
| CA-4 Fechas en sábado y domingo; días con motivo solo en borrador | ✅ Aprobado |
| CA-5 Un grupo con partidos no se regenera | ✅ Aprobado |
| CA-6 Usuario, fecha y parámetros registrados | ✅ Aprobado |

## 5. Pruebas automáticas relacionadas

Verificación ejecutada sobre `abd90c2` el 27-nov-2026 (detalle en
[`pruebas-automaticas.txt`](evidencias/H-32/pruebas-automaticas.txt)):

| Subproyecto | Comando | Resultado |
| --- | --- | --- |
| Backend | `./mvnw verify` | **BUILD SUCCESS**: 3326 pruebas unitarias y de controlador, 0 fallos; 414 de integración, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS). Incluye `GeneradorCalendarioTest` (705), `DiasDeFechaTest` (6), `CalendarioServiceTest` (20), `CalendarioControllerTest` (18), `FechaServiceTest` (13) y `CalendarioIT` (5) |
| Frontend | `npx vitest run src/features/programacion/calendario` | 2 archivos, 36 pruebas, 0 fallos (`ModalGenerarCalendario.test` y `reglas.test`) |

| Criterio | Pruebas que ya lo cubren |
| --- | --- |
| CA-1 | `GeneradorCalendarioTest` (exhaustivo con N = 2…30 y 1…4 vueltas) «DEC-174: cada par se enfrenta `vueltas` veces…» y «RN-012: en cada fecha cada equipo aparece exactamente una vez…»; `CalendarioIT` «RF-034, RN-012, RF-037: genera 2 vueltas por grupo…» |
| CA-2 | `GeneradorCalendarioTest` «RF-037: con N impar hay un descanso por fecha y cada equipo descansa `vueltas` veces; con N par, ninguno» y «RF-037 · DEC-184: N = 5, una vuelta, descanso fijo y calendario exacto»; `CalendarioIT` (el mismo de CA-1) |
| CA-3 | `GeneradorCalendarioTest` «H-32 (criterio 3) · DEC-184: en cada vuelta, \|local − visitante\| ≤ 1 por equipo» y «DEC-174: cada par … cambia de localía en vueltas consecutivas» |
| CA-4 | `DiasDeFechaTest` «DEC-186: si el inicio es miércoles, la fecha 1 es el sábado siguiente» y los demás de DEC-186; `FechaServiceTest` «DEC-193: agrega un día ordenado, fija el motivo y audita CAMBIAR_DIAS_FECHA…», «RN-021: sin motivo de al menos 10 caracteres responde 400 RN-021…»; `CalendarioIT` «DEC-193: PATCH de la fecha agrega un día con motivo; traslape 422, sin motivo 400 y en revisión 409»; `ModalGenerarCalendario.test` «criterio 1: sin calendario genera todos los grupos desde la fecha de inicio» |
| CA-5 | `CalendarioServiceTest` «RN-020: un grupo con partidos o jornadas de fecha responde 409 RN-020 con la lista y no escribe»; `CalendarioIT` «RN-020: con un partido ligado a un emparejamiento, regenerar responde 409 y no cambia nada»; `ModalGenerarCalendario.test` «criterio 4 (RN-020): un grupo con partidos no se puede marcar» y «criterio 5: un 409 RN-020 desmarca el grupo y muestra la causa» |
| CA-6 | `CalendarioServiceTest` «RF-034, DEC-187: genera el grupo … crea las fechas y audita»; `CalendarioIT` (auditoría `GENERAR_CALENDARIO` con `totalFechas` y `emparejamientos`) |
| Seguridad | `CalendarioServiceTest` «VAL-015: sin programacion.jornada:EDITAR no lee ni escribe» y `CalendarioControllerTest` (401, 403 y 404 por endpoint) |

## 6. Defectos y observaciones

**Defectos:** ninguno.

**O-1 · Redacción del `409 RF-090` de los días.** El mensaje dice «la jornada 2 está revisión.»: falta
«en» antes del estado. El código y el estado HTTP son correctos. Se anota para pulir el texto en
`FechaService.actualizarDias`.

**O-2 · CA-4a se repitió.** La primera llamada de CA-4a respondió `400 VALIDACION` porque la consola
del arnés no mandó en UTF-8 la «á» de «relámpago»; se repitió con el cuerpo desde un archivo UTF-8 y
respondió `200`. Por eso, en la auditoría, `CAMBIAR_DIAS_FECHA` aparece después del envío de la fecha
2. No es un defecto del sistema: el frontend siempre manda UTF-8. La descripción de H-32 en Jira cita
`PATCH /categorias-temporada/{id}` para los días de juego; la ruta vigente es
`PATCH /temporadas/{id}/categorias/{categoriaTemporadaId}` (DEC-191). Los días de juego no son parte de
los seis criterios y no se probaron aquí.

## 7. Conclusión

H-32 cumple sus seis criterios con un grupo par y uno impar: el método del círculo enfrenta a cada par
exactamente las veces configuradas sin repetir equipos en una fecha, el grupo impar descansa a un
equipo por fecha y a cada uno una vez por vuelta, la localía queda equilibrada e invertida en la
segunda vuelta, las fechas caen en sábado y domingo desde el primer sábado tras el inicio y sus días
se cambian con motivo solo en borrador, un grupo con partidos no se regenera y la generación queda
registrada con su autor, fecha y parámetros. **H-32 puede cerrarse.** Las observaciones O-1 y O-2 no
bloquean el cierre.
