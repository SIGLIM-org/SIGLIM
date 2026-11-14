# Prueba de aceptación · H-11 Presentar árbitros del club y validarlos

| Campo | Valor |
| --- | --- |
| Historia | H-11 (SCRUM-188) · subtarea H-11.3 (SCRUM-292) |
| Requisitos | RF-021, RF-112, RF-113, UI-007, RF-093, RF-010, VAL-024 |
| Pantallas | F-20 (árbitros: vista de la Comisión y vista del club) |
| Implementó | Geovani (H-11.1 #119, H-11.2 #121) |
| Versión probada | `develop` en `007ed9b` |
| Fecha | 2026-11-14 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (3 de 3 criterios y 2 complementarios), con un defecto menor (DEF-09) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52) |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` |
| Navegador | Google Chrome, ventana de 1440 px; dos sesiones a la vez (ventana normal e incógnito) |

A diferencia de H-08, esta prueba corre contra el Supabase compartido: los árbitros se crean desde
la interfaz y quedan como datos de demostración.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Real Patzcuaro** | Directiva de club · CLUB *Real Patzcuaro* | CA-1: presenta árbitros; CA-4: duplicado |
| **Comisión de Arbitraje** | Comisión de Arbitraje · LIGA | CA-2: valida y rechaza; CA-3: catálogo |
| **Directiva CamiloSextos** | Directiva de club · CLUB *CamiloSextos* | CA-5: ámbito de otro club |
| **Administrador general** | Administrador general · LIGA | CA-2: consulta la auditoría (F-14) |

Personas ficticias que se registran desde el modal de F-20 (CURP con formato válido, sin
correspondencia con personas reales):

| Id | CURP | Nombre | Nacimiento | Destino en la prueba |
| --- | --- | --- | --- | --- |
| A1 | `ARPR800101HMNRRB01` | Árbitro Prueba Uno | 1980-01-01 | Se valida (CA-2) |
| A2 | `ARPD820202HMNRRC02` | Árbitro Prueba Dos | 1982-02-02 | Se rechaza (CA-2) |
| A3 | `ARPT850303MMNRRD03` | Árbitra Prueba Tres | 1985-03-03 | Se queda PROPUESTO (CA-3) |

Estado inicial verificado el 2026-11-13: no hay árbitros registrados en ningún club.

## 3. Escenarios

### CA-1 · La Directiva presenta un árbitro y queda PROPUESTO ligado a su club

> Dada la Directiva, cuando presenta un árbitro, entonces queda PROPUESTO ligado a su club.

**Pasos**

1. Iniciar sesión como **Directiva Real Patzcuaro** y abrir **Programación → Árbitros**
   (`/app/programacion/arbitros`).
2. Comprobar que la pantalla no menciona «Colegio» y que aparece «Presentar árbitro».
3. Pulsar «Presentar árbitro». El club aparece fijo como *Real Patzcuaro* (único de su ámbito).
4. Escribir la CURP de **A1** y pulsar «Buscar persona». Como no existe, aparece «Registrar
   persona».
5. Capturar nombre, apellido y fecha de nacimiento de A1 y pulsar «Registrar y presentar».
6. Repetir los pasos 3 a 5 con **A2** y **A3**.

**Resultado esperado**

- Mensaje «… quedó PROPUESTO como árbitro de «Real Patzcuaro»…».
- La pestaña **Por validar** muestra A1, A2 y A3 con la insignia «Propuesto», el club y quién los
  presentó; su conteo es 3.

**Resultado obtenido:** **Aprobado.** Se registraron A1, A2 y A3 desde el modal (la CURP no existía y se capturó la identidad una sola vez). Cada uno quedó «Propuesto», ligado a *Real Patzcuaro* y «Presentado por» la Directiva; la pestaña Por validar mostró 3. La pantalla no menciona «Colegio».

**Evidencia:** `evidencias/H-11/CA1-modal-registrar-persona.png`,
`evidencias/H-11/CA1-propuestos-directiva.png`

**Pruebas automáticas relacionadas:** `ArbitrosIT#directivaPresentaYComisionValida`;
`PantallaArbitros.test.tsx` (RF-112: persona existente y persona nueva).

### CA-2 · La Comisión valida o rechaza con observaciones y queda auditado

> Dada la Comisión, cuando valida o rechaza con observaciones, entonces cambia su estado y queda
> auditado.

**Pasos**

1. En la ventana de incógnito, iniciar sesión como **Comisión de Arbitraje** y abrir Árbitros.
   Comprobar que ve A1, A2 y A3 en **Por validar** y que **no** aparece «Presentar árbitro».
2. **Validar A1:** pulsar «Validar», escribir la nota `Documentación revisada en ventanilla.` y
   confirmar.
3. **Rechazo inválido (A2):** pulsar «Rechazar», escribir `Falta` (menos de 10 caracteres) e
   intentar confirmar.
4. **Rechazo válido (A2):** escribir `No presentó constancia del curso de arbitraje vigente.` y
   confirmar.
5. Iniciar sesión como **Administrador general**, abrir **Auditoría (F-14)** y filtrar por módulo
   **M-05**. Expandir los registros.

**Resultado esperado**

- Paso 2: A1 pasa a **Validados** con la nota, quién validó y la fecha.
- Paso 3: el rechazo no se envía; se pide un mínimo de 10 caracteres.
- Paso 4: A2 pasa a **Rechazados** y muestra las observaciones.
- Paso 5: hay tres registros `PROPONER_ARBITRO` y uno de cada uno de `VALIDAR_ARBITRO` y
  `RECHAZAR_ARBITRO` sobre `arbitros`. El rechazo lleva las observaciones como motivo. Ningún
  registro muestra nombre ni CURP.

**Resultado obtenido:** **Aprobado.** A1 pasó a Validados con la nota, la fecha y quien validó. El rechazo de A2 con «Falta» (5 caracteres) no se envió y pidió al menos 10. Con las observaciones completas, A2 pasó a Rechazados mostrándolas. En F-14, filtrando M-05, hay 5 eventos: 3 `PROPONER_ARBITRO`, 1 `VALIDAR_ARBITRO` y 1 `RECHAZAR_ARBITRO`, este con las observaciones como motivo; el detalle solo lleva identificadores (club, persona y estados), sin nombres ni CURP. Observación: la nota de validación no quedó en la auditoría, aunque el modal lo indica (DEF-09).

**Evidencia:** `CA2-validar-nota.png`, `CA2-rechazo-minimo.png`, `CA2-rechazados.png`,
`CA2-auditoria-m05.png`

**Pruebas automáticas relacionadas:** `ArbitrosIT#comisionRechazaConObservaciones`,
`ArbitroServiceTest`; `PantallaArbitros.test.tsx` (RF-113: valida con nota; rechazo exige
observaciones).

### CA-3 · Solo los árbitros VALIDADO forman el catálogo para designar

> Solo los árbitros VALIDADO aparecen en el catálogo para designar.

La pantalla de designación (F-24) llega con H-14 (Sprint 4). Hoy el criterio se comprueba con el
filtro por estado de F-20, el endpoint que usará F-24 y el trigger de la base que respalda la regla.

**Pasos**

1. Como **Comisión**, abrir la pestaña **Validados**.
2. Abrir las herramientas del navegador (pestaña Red) y revisar la petición
   `GET /api/v1/arbitros?estado=VALIDADO…`.
3. Abrir las pestañas **Por validar** y **Rechazados**.

**Resultado esperado**

- **Validados** muestra solo a A1 (conteo 1), y la respuesta trae `totalElementos: 1`.
- A3 sigue en **Por validar** y A2 en **Rechazados**; ninguno aparece entre los validados.
- Respaldo en la base: el trigger `tg_designaciones_validar` (BEFORE INSERT OR UPDATE en
  `designaciones`) rechaza un árbitro que no esté `VALIDADO` (RF-113). Lo demuestra
  `ArbitrosIT` y se probará de punta a punta en H-14.3.

**Resultado obtenido:** **Aprobado.** Validados muestra solo a A1 (conteo 1). La petición `GET /api/v1/arbitros?estado=VALIDADO&pagina=0&tamano=20` respondió 200 con un elemento `estado: "VALIDADO"` y `totalElementos: 1`. A3 sigue en Por validar y A2 en Rechazados. El trigger `tg_designaciones_validar` existe en el Supabase compartido.

**Evidencia:** `CA3-validados.png`, `CA3-red-estado-validado.png`

### CA-4 · Una persona no puede estar dos veces en el catálogo (complementario)

Escenario que deriva de RF-010 y DEC-10, implementado en H-11.1.

**Pasos**

1. Como **Directiva Real Patzcuaro**, pulsar «Presentar árbitro», buscar la CURP de **A1**
   (ahora sí la encuentra) y pulsar «Presentar árbitro».

**Resultado esperado**

- La búsqueda reutiliza la persona sin pedir sus datos otra vez.
- Al presentar se muestra «La persona ya está registrada como árbitro del club «Real
  Patzcuaro».» y no se crea otro registro.

**Resultado obtenido:** **Aprobado.** Al buscar la CURP de A1 se reutilizó la persona sin volver a pedir datos. Al presentarla, el sistema respondió «La persona ya está registrada como árbitro del club «Real Patzcuaro».» y no se creó otro registro (Por validar se mantuvo en 1).

**Evidencia:** `CA4-duplicado.png`

### CA-5 · La Directiva solo ve los árbitros de su club (complementario, VAL-024)

**Pasos**

1. Iniciar sesión como **Directiva CamiloSextos** y abrir Árbitros.
2. Revisar las tres pestañas y sus conteos.

**Resultado esperado**

- Las tres pestañas muestran el estado vacío con conteo 0: no aparecen A1, A2 ni A3, que son de
  Real Patzcuaro.
- «Presentar árbitro» aparece con el club fijo en *CamiloSextos*.
- No aparecen «Validar» ni «Rechazar» (la Directiva no tiene `arbitraje.arbitro:VALIDAR`).

**Resultado obtenido:** **Aprobado.** La Directiva CamiloSextos ve Por validar, Validados y Rechazados con conteo 0 y el estado vacío; no aparecen A1, A2 ni A3. Ofrece «Presentar árbitro» para su club y no muestra acciones de validación.

**Evidencia:** `CA5-otro-club-vacio.png`

**Pruebas automáticas relacionadas:** `ArbitrosIT#directivaSoloVeSuClub`; `ArbitroControllerTest`
(401, 403 y 404 por endpoint).

## 4. Defectos encontrados

| Clave | Severidad | Descripción | Estado |
| --- | --- | --- | --- |
| DEF-09 (SCRUM-435) | Menor | Al validar un árbitro con nota, `ArbitroService.validar` registra `VALIDAR_ARBITRO` con `motivo` nulo y el `detalle` sin `observaciones`, aunque el modal de F-20 dice «La nota es opcional y también queda en la auditoría» y el Javadoc del servicio dice que la auditoría lleva las observaciones. La nota sí se guarda en `arbitros.observaciones`. Corrección propuesta: agregar `observaciones` al `detalle` cuando la validación lleva nota, con su prueba en `ArbitroServiceTest`. | Abierto · Geovani |

El defecto no invalida el CA-2: el cambio de estado queda auditado con usuario, fecha, módulo,
acción y entidad. Afecta a un dato complementario y a la coherencia del texto de la pantalla.

## 5. Conclusión

H-11 cumple sus tres criterios de aceptación y los dos escenarios complementarios (identidad única
y ámbito por club). La historia puede pasar a **Finalizado** en cuanto DEF-09 quede registrado en
Jira; su corrección no bloquea a H-14, que usará el catálogo de validados ya comprobado aquí.
