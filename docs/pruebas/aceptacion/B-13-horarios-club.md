# Prueba de aceptación · B-13 Registrar horarios de cancha de club y comodato

| Campo | Valor |
| --- | --- |
| Historia | B-13 (SCRUM-222) · subtarea B-13.3 (prueba de aceptación) |
| Requisitos | RF-119, DEC-33, RF-003, RF-093, VAL-015 |
| Pantallas | F-21 (detalle de la cancha, sección «Horarios por categoría») |
| Versión probada | Rama de trabajo con `develop` al `7dab0e5`; el código de M-06 es el de `7e1b3dd` (sin cambios desde entonces), con las seis migraciones de `supabase/migrations/` |
| Fecha | 2026-11-11 |
| Ejecutó | Geovani, con un agente de IA (DeepSeek Harness) en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios de SCRUM-222 y 1 verificación adicional) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 18.6 en un clúster efímero local (puerto 55432), con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las seis migraciones del repositorio aplicadas |
| Autenticación | Emisor local que publica un JWKS con llave ES256 y responde a los endpoints de Supabase Auth que usa el frontend (contraseña, usuario y cierre de sesión); el backend valida firma, emisor, audiencia y `sub` |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, compilado con `release 21` y ejecutado con Java 25.0.5, Spring Boot 4.1.1 y Spring JDBC, contra la base local y el emisor local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5 y proxy hacia el backend) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

El proyecto declara **Java 21** en `backend/pom.xml`; el entorno local solo tenía Java 25.0.5, así que
la compilación se hizo con `release 21` (el artefacto sigue apuntando a Java 21) y el backend se
ejecutó con ese JDK 25. No hubo diferencias funcionales por ello.

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para la
corrida: no se alteró el proyecto compartido de Supabase. El entorno no tenía Docker disponible,
así que las pruebas de integración con Testcontainers (`CanchasIT`) no se ejecutaron; el esquema
real, los triggers y las restricciones sí se usaron a través del backend y de consultas directas.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `canchas.cancha:CONSULTAR` y `canchas.cancha:EDITAR` (LIGA) |

Datos preparados para la corrida (temporadas, categorías, clubes y canchas se sembraron con SQL;
los horarios de la temporada abierta se capturaron desde la pantalla):

- **Temporada 2026-2027** (`ACTIVA`) y **Temporada 2025-2026** (`CERRADA`), ambas con sus
  parámetros deportivos.
- Categorías **Primera División**, **Segunda División** y **Sub-15**, asignadas a la temporada
  activa; la cerrada solo tiene Primera División.
- Clubes **Halcones B-13** y **Toros B-13**, activos.
- **Cancha de la Liga B-13** (`LIGA`, sin club), **Cancha de Halcones B-13** (`CLUB` de Halcones
  B-13) y **Cancha Externa B-13** (`EXTERNO`).
- Un horario preexistente en la temporada cerrada para la Cancha de la Liga (viernes 08:00–10:00,
  Halcones B-13, comodato), sembrado con SQL porque una temporada cerrada no se puede escribir por
  la API (RF-003). Sirve para comprobar que el reemplazo de la temporada activa no la toca.

## 3. Escenarios

### CA-1 · Criterio 1: horarios por categoría de una cancha de club y de una cancha de la liga en comodato

#### CA-1a · Una cancha de la liga guarda sus horarios en comodato por categoría y club

**Pasos**

1. Iniciar sesión como **Administrador** y abrir Programación › Canchas › Cancha de la Liga B-13.
2. En «Horarios por categoría», con la **Temporada 2026-2027** seleccionada por ser la activa,
   pulsar **Agregar horario** y capturar Primera División, Sábado, 10:00 a 12:00, club Halcones B-13.
3. Pulsar **Agregar horario** otra vez y capturar Segunda División, Sábado, 12:00 a 14:00, club
   Toros B-13.
4. Pulsar **Guardar horarios** y revisar la tabla y el aviso.

**Resultado esperado**

- Cada fila exige el club (cancha de la liga) y se marca con la insignia **Comodato**.
- El `PUT` responde `200` con los dos horarios, `esComodato: true`, el club y la categoría, en
  orden por día y hora de inicio.
- Los intervalos contiguos (10:00–12:00 y 12:00–14:00) no se consideran traslape.
- La pantalla confirma «Horarios guardados.» y la base guarda `es_comodato = true`.
- Queda un evento de auditoría `REGISTRAR_HORARIOS_CANCHA` con el número de horarios guardados.

**Resultado obtenido:** **Aprobado.** El `PUT` respondió `200` con los dos horarios en el orden
esperado: Primera División/Halcones B-13 de 10:00:00 a 12:00:00 y Segunda División/Toros B-13 de
12:00:00 a 14:00:00, ambos con `esComodato: true`. La pantalla mostró la insignia **Comodato** en
cada fila, el aviso «Horarios guardados.» y la base quedó con dos filas `es_comodato = true`. La
auditoría registró la acción de M-06 sobre la cancha con `horarios: 2`.

#### CA-1b · Una cancha de club usa su propio club y no es comodato

**Pasos**

1. Abrir la Cancha de Halcones B-13.
2. Con la Temporada 2026-2027 seleccionada, pulsar **Agregar horario** y capturar Primera División,
   Sábado, 09:00 a 11:00.
3. Guardar y revisar la fila y la respuesta.

**Resultado esperado**

- El formulario no ofrece selector de club: muestra el club propietario y no pinta la insignia
  **Comodato**.
- El `PUT` no envía `clubId`; la respuesta trae el club propietario y `esComodato: false`.
- La base guarda la fila con el club del propietario y `es_comodato = false`.

**Resultado obtenido:** **Aprobado.** La sección describió «Días y horas en que las categorías de
Halcones B-13 usan esta cancha»; la fila mostró Halcones B-13 fijo, sin insignia de comodato. El
`PUT` envió el horario sin `clubId` y respondió `200` con `esComodato: false` y el club Halcones
B-13. La base quedó con la fila correspondiente.

**Evidencia**

- [`CA1a-formulario-comodato.png`](evidencias/B-13/CA1a-formulario-comodato.png)
- [`CA1a-horarios-guardados.png`](evidencias/B-13/CA1a-horarios-guardados.png)
- [`CA1b-formulario-cancha-club.png`](evidencias/B-13/CA1b-formulario-cancha-club.png)
- [`CA1b-horarios-cancha-club.png`](evidencias/B-13/CA1b-horarios-cancha-club.png)
- Peticiones y respuestas de ambos `PUT` en [`CA1-CA4-peticiones-api.json`](evidencias/B-13/CA1-CA4-peticiones-api.json)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «DEC-33: en una cancha de la liga, cada horario es comodato del club
  indicado», «DEC-33: en una cancha de club, el club es su propietario y no hay comodato» y
  «DEC-33: en una cancha de club, otro club responde 422 RF-119».
- `CanchaControllerTest` · «RF-119: PUT de horarios responde 200 con la lista y acepta HH:mm y
  HH:mm:ss».
- `SeccionHorariosClub.test.tsx` · «DEC-33: en LIGA cada fila exige club; con club el cuerpo lleva
  clubId y se ve «Comodato»».
- `CanchasIT` · «RF-119: el PUT guarda los horarios, el GET los lee en orden y otra temporada no
  se toca» (no ejecutada en esta corrida por falta de Docker).

### CA-2 · Criterio 2: los traslapes se rechazan y los intervalos contiguos se aceptan

> Dados dos horarios del mismo día que se traslapan, cuando se guardan, entonces se explica el
> choque y no se guarda nada; dos intervalos contiguos sí se aceptan.

**Pasos**

1. En la Cancha de la Liga B-13, agregar un tercer horario: Segunda División, Sábado, 11:00 a
   13:00, Toros B-13, que se cruza con los dos existentes.
2. Pulsar **Guardar horarios** y revisar el aviso; comprobar con el `GET` que la lista no cambió.
3. Enviar el mismo traslape directo a la API con `PUT /canchas/{id}/horarios-club` (sin pasar por
   la validación del navegador).
4. Cambiar la fila a inicio 15:00 y fin 14:00, guardar y revisar el error del campo.

**Resultado esperado**

- El navegador marca las dos filas que chocan, explica con cuál se traslapan y no llama al `PUT`.
- La API responde `422 RF-119` con el mensaje que nombra el día y las dos horas; la lista guardada
  no cambia.
- Un fin anterior al inicio responde en el navegador con «El fin debe ser posterior al inicio.».
- Los intervalos contiguos del CA-1a siguen guardados (el fin es exclusivo).

**Resultado obtenido:** **Aprobado.** El navegador marcó las tres filas implicadas y mostró «Se
traslapa con el horario 3 (Sábado 11:00–13:00)» en las filas 1 y 2, «Se traslapa con el horario 1
(Sábado 10:00–12:00). Se traslapa con el horario 2 (Sábado 12:00–14:00).» en la fila 3 y el resumen
«Corrige los horarios marcados; no se guardó ningún cambio.»; no se emitió ningún `PUT` (0
peticiones `PUT` desde el clic hasta el aviso). La llamada directa respondió `422` con
`codigo: "RF-119"` y el detalle «Los horarios del sábado de 10:00 a 12:00 y de 11:00 a 13:00 se
traslapan; la cancha no puede tener dos horarios a la vez.», y el `GET` posterior devolvió los
mismos dos horarios del CA-1a. Con inicio 15:00 y fin 14:00, el campo de fin mostró «El fin debe
ser posterior al inicio.» sin enviar la petición.

**Evidencia**

- [`CA2-traslape-en-el-navegador.png`](evidencias/B-13/CA2-traslape-en-el-navegador.png)
- [`CA2-validacion-inicio-fin.png`](evidencias/B-13/CA2-validacion-inicio-fin.png)
- Respuesta `422` y `GET` posterior en [`CA1-CA4-peticiones-api.json`](evidencias/B-13/CA1-CA4-peticiones-api.json)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-119: dos horarios del mismo día que se traslapan responden 422 aunque
  sean de otra categoría» y «RF-119: los intervalos contiguos y los de otro día no chocan».
- `CanchaControllerTest` · «RF-119: el 422 del traslape llega con su código».
- `horariosClub.test.ts` · «RF-119: 10–12 y 12–14 el mismo día no chocan (intervalo semiabierto)» y
  «RF-119: categorías distintas el mismo día sí chocan».
- `SeccionHorariosClub.test.tsx` · «RF-119: un traslape entre categorías distintas marca las dos
  filas y no llama al PUT» y «un 422 RF-119 muestra la alerta con el detalle y sin el código».

### CA-3 · Criterio 3: una cancha de la liga sin comodato se rechaza

> Dada una cancha de la liga sin comodato, cuando se intenta registrar horarios de club, entonces
> se rechaza.

**Pasos**

1. En la Cancha de la Liga B-13, con la Temporada 2026-2027, pulsar **Agregar horario** y capturar
   Segunda División, Sábado, 16:00 a 18:00, **sin elegir club**.
2. Pulsar **Guardar horarios** y revisar el error del campo; comprobar que no se envió la petición.
3. Enviar directo a la API un horario sin `clubId`:
   `PUT /canchas/{id}/horarios-club` con `{ temporadaId, horarios: [{ categoriaId, diaSemana: 6,
   horaInicio: "18:00", horaFin: "20:00" }] }`.
4. Consultar la lista guardada con el `GET`.

**Resultado esperado**

- El navegador exige el club en una cancha de la liga con «Elige el club que usa la cancha.» y no
  llama al `PUT`.
- La API responde `422 RF-119`: en una cancha de la liga cada horario es comodato y el club es
  obligatorio.
- La lista guardada no cambia y no se registra auditoría de esa petición rechazada.

**Resultado obtenido:** **Aprobado.** Al guardar sin club, el navegador marcó el campo con «Elige
el club que usa la cancha.» y el resumen «Corrige los horarios marcados; no se guardó ningún
cambio.», sin emitir ningún `PUT` (0 peticiones `PUT` desde el clic hasta el aviso). La llamada
directa respondió `422` con `codigo: "RF-119"` y el detalle «Una cancha de la liga solo tiene
horarios en comodato: indica el club de cada horario.». El `GET` posterior devolvió los mismos dos
horarios del CA-1a y la auditoría no sumó eventos por esta petición.

**Evidencia**

- [`CA3-sin-club-en-el-navegador.png`](evidencias/B-13/CA3-sin-club-en-el-navegador.png)
- Respuesta `422` y `GET` posterior en [`CA1-CA4-peticiones-api.json`](evidencias/B-13/CA1-CA4-peticiones-api.json)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «DEC-33: en una cancha de la liga, sin club responde 422 RF-119».
- `SeccionHorariosClub.test.tsx` · «DEC-33: en LIGA cada fila exige club; con club el cuerpo lleva
  clubId y se ve «Comodato»».

### CA-4 · Criterio 4: el reemplazo es por temporada y una temporada cerrada es de solo lectura

> Dada una temporada cerrada con horarios, cuando se consulta, entonces se ven en solo lectura;
> cuando se guarda la temporada activa, entonces se reemplaza solo esa y la cerrada no cambia.

**Pasos**

1. En la Cancha de la Liga B-13, cambiar la temporada a **Temporada 2025-2026** (`CERRADA`).
2. Revisar el aviso, la tabla y la ausencia de los controles de edición.
3. Enviar un `PUT` directo a la temporada cerrada.
4. Volver a la **Temporada 2026-2027**, quitar el horario de Toros B-13 y guardar.
5. Consultar la base: horarios de ambas temporadas y auditoría.

**Resultado esperado**

- La temporada cerrada se consulta con la insignia **CERRADA**, el aviso «Temporada cerrada: sus
  horarios son de solo lectura.», sin **Agregar horario** ni **Guardar horarios**.
- El `PUT` a la temporada cerrada responde `409 RF-003` con el mensaje «La temporada «Temporada
  2025-2026» está cerrada: sus horarios son de solo lectura.».
- Guardar la temporada activa reemplaza su lista (queda el horario de Halcones y desaparece el de
  Toros) y deja intacto el horario de la temporada cerrada.
- Cada guardado deja su evento `REGISTRAR_HORARIOS_CANCHA` con el número de horarios resultante.

**Resultado obtenido:** **Aprobado.** La temporada 2025-2026 se mostró como **CERRADA**, con el
aviso de solo lectura, el horario del viernes 08:00–10:00 y sin botones de edición. El `PUT` directo
respondió `409` con `codigo: "RF-003"` y el mensaje esperado. Al guardar la temporada activa quedó
una sola fila (Primera División/Halcones B-13, 10:00–12:00) y la temporada cerrada conservó su
horario. La auditoría registró tres eventos `REGISTRAR_HORARIOS_CANCHA` con `horarios: 2`, `1` y
`1`, todos de M-06 sobre la cancha correspondiente.

**Evidencia**

- [`CA4-temporada-cerrada.png`](evidencias/B-13/CA4-temporada-cerrada.png)
- [`CA4-reemplazo-atomico.png`](evidencias/B-13/CA4-reemplazo-atomico.png)
- Estado final de la base y auditoría en [`CA1-CA4-estado-base.txt`](evidencias/B-13/CA1-CA4-estado-base.txt)
- Respuesta `409` y `GET` de ambas temporadas en [`CA1-CA4-peticiones-api.json`](evidencias/B-13/CA1-CA4-peticiones-api.json)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-003: una temporada cerrada responde 409 antes de revisar categorías,
  tipo y traslapes», «RF-119: el reemplazo borra e inserta solo en la temporada indicada y responde
  la lista guardada» y «RF-119: una lista vacía borra los horarios de la temporada y lo audita con
  0».
- `CanchasIT` · «RF-003: una temporada cerrada no admite horarios pero se consulta» (no ejecutada
  en esta corrida por falta de Docker).
- `SeccionHorariosClub.test.tsx` · «un 409 RF-003 explica que la temporada se cerró, sin código, y
  la deja en solo lectura».

### V1 · Verificación adicional: una cancha externa no ofrece horarios de club

> Dada una cancha que no es de la liga ni de un club, cuando se abre su detalle, entonces no se
> ofrece la sección de horarios.

**Pasos**

1. Abrir la Cancha Externa B-13 y buscar «Horarios por categoría».

**Resultado obtenido:** **Aprobado.** La sección no aparece en el detalle (`haySeccion: false` en la
evidencia) y la pantalla no consulta el endpoint de horarios.

**Evidencia**

- [`V1-cancha-externa-sin-horarios.png`](evidencias/B-13/V1-cancha-externa-sin-horarios.png)
- Resultado de la comprobación en [`CA1-CA4-peticiones-api.json`](evidencias/B-13/CA1-CA4-peticiones-api.json)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «DEC-33: una cancha externa no admite horarios de club (422 RF-119)».
- `PantallaCanchas.test.tsx` · «B-13.2, DEC-33: una cancha EXTERNO no muestra «Horarios por
  categoría» ni los pide».

## 4. Pruebas automáticas ejecutadas en esta corrida

| Comando | Resultado |
| --- | --- |
| `./mvnw -o test -Dtest='CanchaServiceTest,CanchaControllerTest'` | 118 pruebas, 0 fallos |
| `npx vitest run src/features/canchas/horariosClub.test.ts src/features/canchas/SeccionHorariosClub.test.tsx src/features/canchas/PantallaCanchas.test.tsx` | 43 pruebas, 0 fallos |

`CanchasIT` requiere Docker (Testcontainers), que no estaba disponible en el entorno; su cobertura
de RF-119 y RF-003 quedó cubierta en esta corrida por la verificación manual sobre PostgreSQL real
y por las pruebas unitarias y de controlador.

## 5. Observaciones

- **El actor de la historia es el Secretario.** B-13 y RF-119 están asignados al Secretario, pero
  según el Apéndice E del ERS ese rol solo tiene «M-06 C: oficios; ofertas de externos» y no
  `canchas.cancha:EDITAR`; con la matriz inicial, únicamente el Administrador general registra
  horarios. Por eso la corrida manual usó la cuenta de Administrador general en los dos tipos de
  cancha. Queda como pregunta para la Liga si el Secretario (o la Directiva del club) debe registrar
  horarios; si la respuesta es sí, se resuelve configurando la matriz de permisos (H-20), sin
  cambiar B-13. El ámbito CLUB del servicio está cubierto por `CanchaServiceTest`.
- **Validación duplicada.** El navegador rechaza el traslape y la falta de club antes del `PUT` con
  las mismas reglas del servidor; el `422 RF-119` se comprobó llamando a la API directo, que es la
  defensa que protege a otros clientes.
- **Datos sembrados.** Temporadas, categorías, clubes, canchas y el horario de la temporada cerrada
  se prepararon con SQL; los horarios de la temporada abierta se capturaron desde la pantalla.

## 6. Defectos encontrados

Ninguno.

## 7. Conclusión

La historia B-13 cumple los cuatro criterios de SCRUM-222 sobre `develop`: registra horarios por
categoría en una cancha de club y en una de la liga en comodato, distingue el comodato, rechaza los
traslapes —en el navegador y en el servidor—, rechaza los horarios sin club en una cancha de la
liga, reemplaza la lista por temporada sin tocar las demás, impide editar una temporada cerrada con
`RF-003`, audita cada guardado y no ofrece la sección en una cancha externa. Se aprueba.
