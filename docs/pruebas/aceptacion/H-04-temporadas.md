# Prueba de aceptación · H-04 Gestionar temporadas con una sola activa y proteger las cerradas

| Campo | Valor |
| --- | --- |
| Historia | H-04 · subtarea H-04.3 (prueba de aceptación) |
| Requisitos | RF-001, RF-003, RF-004, RF-005, RF-100, RN-005, RN-021, RN-058, RF-093, UI-003 |
| Pantallas | F-10 (temporadas) |
| Versión probada | `develop` en `7d3e703`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-03 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 18.6 local en un clúster efímero (puerto 55432), con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica una llave ES256 y responde a los endpoints de Supabase Auth que usa el frontend; la cuenta se creó en `auth.users` y se dio de alta con `siglim.registrar_administrador_inicial` |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 21.0.13 y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5 y proxy hacia el backend) |
| Navegador | Microsoft Edge 156.0.4314.58 sin interfaz, Playwright, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para la
corrida: no se alteró el proyecto compartido de Supabase. El equipo de pruebas no tenía Docker
disponible, así que las pruebas de integración con Testcontainers (`TemporadasIT`) no se ejecutaron
en esta corrida; la verificación manual sí usó PostgreSQL real con las mismas migraciones.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `temporadas.temporada:CONSULTAR`, `temporadas.temporada:EDITAR` y `auditoria.registro:CONSULTAR` (LIGA) |

Datos creados durante la corrida:

- Temporada **«Temporada 2026-2027 (aceptación H-04)»**, id
  `78b9b73a-3267-4266-bafa-cfb7ba8b5929`, del **1 de agosto de 2026** al **31 de mayo de 2027**.
- Temporada **«Temporada 2027-2028 (aceptación H-04)»**, id
  `7b7a1b04-5c83-4e9c-b22e-b41b65e25e1a`, del **1 de agosto de 2027** al **31 de mayo de 2028**.
- Para cada temporada, una versión de parámetros deportivos (v1), la categoría «Primera Fuerza
  (aceptación H-04)» y el «Grupo Único (aceptación H-04)». Como la historia H-05 (categorías,
  grupos y parámetros) todavía no está implementada, estos datos se sembraron con SQL directo para
  poder comprobar la lista de verificación; no se usó ninguna pantalla de configuración.
- Motivo del cierre: «Cierre del ciclo 2026-2027 por término del calendario deportivo».

## 3. Escenarios

### CA-1 · Una segunda temporada no se activa si ya hay una activa

> Dada una temporada ACTIVA, cuando se intenta activar otra, entonces se rechaza con el mensaje de
> la temporada activa.

**Pasos**

1. Crear la temporada 2026-2027 desde F-10 y sembrar sus parámetros, categoría y grupo.
2. Abrir la lista de verificación (4 de 4) y pulsar «Activar»; la temporada queda **ACTIVA**.
3. Crear la temporada 2027-2028 desde F-10 y sembrar sus parámetros, categoría y grupo.
4. Volver a la pantalla: la fila de 2027-2028 muestra la lista en 3 de 4, el botón «Activar»
   deshabilitado y el motivo con el nombre de la temporada activa.
5. Intentar la activación por la API (`POST /api/v1/temporadas/{id}/activacion`), que es la misma
   acción que dispara el botón cuando está habilitado.

**Resultado esperado**

- La activación se rechaza y el mensaje nombra la temporada que ya está activa.
- La temporada 2027-2028 conserva el estado `CONFIGURACION`.

**Resultado obtenido:** **Aprobado.** La fila mostró «La temporada «Temporada 2026-2027 (aceptación
H-04)» está activa; ciérrala antes de activar esta.» y el botón quedó deshabilitado. La API respondió
`409` con `codigo: "RN-058"`, el detalle «La temporada «Temporada 2026-2027 (aceptación H-04)» ya
está activa. Ciérrala antes de activar «Temporada 2027-2028 (aceptación H-04)».» y el requisito
incumplido. La temporada 2027-2028 siguió en `CONFIGURACION`.

**Evidencia:** [`CA1-temporada-activa.png`](evidencias/H-04/CA1-temporada-activa.png),
[`CA1-otra-temporada-activa.png`](evidencias/H-04/CA1-otra-temporada-activa.png) y
[`CA1-respuesta-409.json`](evidencias/H-04/CA1-respuesta-409.json)

**Pruebas automáticas relacionadas**

- `TemporadaServiceTest` · «RN-058: no activa si otra temporada ya está activa y la nombra en el
  mensaje» y «RN-058: la lista de verificación nombra la temporada que ya está activa».
- `TemporadaControllerTest` · «RF-100: el 409 de activación lleva los requisitos que faltan» y las
  pruebas de seguridad de `POST /api/v1/temporadas/{id}/activacion`.
- `TemporadasIT` · «RF-001, RF-003, RF-100, RN-058: crear, verificar, activar, cerrar y proteger la
  cerrada».
- `PantallaTemporadas.test.tsx` · «H-04 criterio 1: si el servidor rechaza la activación, muestra la
  causa».

### CA-2 · Sin parámetros no se activa y la lista muestra lo que falta

> Dada una temporada sin parámetros, cuando se intenta activar, entonces se rechaza (RF-100) y la
> lista de verificación muestra lo que falta.

**Pasos**

1. Con la base recién migrada, crear la temporada 2026-2027 desde F-10 sin configurarla.
2. Revisar la fila y el panel «Lista de verificación para activar».
3. Intentar la activación por la API, ya que el botón está deshabilitado por la lista incompleta.

**Resultado esperado**

- El rechazo lleva el código `RF-100` y el detalle de los parámetros que faltan.
- La lista de verificación enumera cada requisito pendiente y la temporada no cambia de estado.

**Resultado obtenido:** **Aprobado.** La lista mostró **1 de 4 requisitos cumplidos**: `RN-058`
cumplido y `RF-100`, `RF-004` y `RF-005` pendientes, cada uno con su descripción. La API respondió
`409` con `codigo: "RF-100"`, el detalle «La temporada necesita parámetros deportivos antes de
activarse.» y el arreglo `requisitos` con los tres pendientes. La temporada siguió en
`CONFIGURACION`.

**Evidencia:** [`CA2-lista-requisitos.png`](evidencias/H-04/CA2-lista-requisitos.png) y
[`CA2-respuesta-409.json`](evidencias/H-04/CA2-respuesta-409.json)

**Pruebas automáticas relacionadas**

- `TemporadaServiceTest` · «RF-100: no activa sin parámetros deportivos y devuelve lo que falta»,
  «RF-004: no activa sin categorías asignadas» y «RF-005: no activa sin grupos creados».
- `TemporadaControllerTest` · «RF-100: el 409 de activación lleva los requisitos que faltan».
- `TemporadasIT` · «RF-004, RF-005, RF-100: la activación pide parámetros, después categoría y
  después grupo».
- `PantallaTemporadas.test.tsx` · «H-04 criterio 2: activar deshabilitado explica qué falta y la
  lista lo detalla».

### CA-3 · Una temporada cerrada no se edita y conserva sus datos

> Dada una temporada CERRADA, cuando se intenta editar, entonces se rechaza y sus datos se
> conservan.

**Pasos**

1. Tras cerrar la temporada 2026-2027 (CA-4), revisar su fila en F-10.
2. Intentar cambiarle el nombre por la API (`PATCH /api/v1/temporadas/{id}`), la operación que usa
   el formulario de edición.
3. Consultar la temporada por la API y en la base para comprobar que no cambió.

**Resultado esperado**

- La fila se muestra como **CERRADA** y **Solo lectura**, sin acciones de edición ni cierre.
- La edición responde `409` con el código `RF-003` y los datos originales permanecen.

**Resultado obtenido:** **Aprobado.** La fila mostró la insignia **CERRADA**, el texto «Solo
lectura» y ninguna acción de escritura. El `PATCH` respondió `409` con `codigo: "RF-003"` y el
detalle «La temporada «Temporada 2026-2027 (aceptación H-04)» está cerrada: sus datos son de solo
lectura.». La consulta posterior devolvió el mismo nombre, las fechas `2026-08-01` y `2027-05-31`,
el estado `CERRADA`, 1 categoría, 1 grupo y parámetros v1. La fila de la base conservó esos mismos
valores.

**Evidencia:** [`CA3-temporada-cerrada.png`](evidencias/H-04/CA3-temporada-cerrada.png),
[`CA3-respuesta-409.json`](evidencias/H-04/CA3-respuesta-409.json),
[`CA3-datos-conservados.json`](evidencias/H-04/CA3-datos-conservados.json) y
[`CA3-datos-conservados.txt`](evidencias/H-04/CA3-datos-conservados.txt) (consulta directa a la base)

**Pruebas automáticas relacionadas**

- `TemporadaServiceTest` · «RF-003: una temporada cerrada es de solo lectura» y «RF-003: un PATCH sin
  cambios no escribe ni audita».
- `TemporadasIT` · «RF-001, RF-003, RF-100, RN-058: crear, verificar, activar, cerrar y proteger la
  cerrada».
- `PantallaTemporadas.test.tsx` · «H-04 criterio 3: una temporada cerrada es de solo lectura y no
  ofrece acciones».

### CA-4 · El cierre pide motivo y queda en auditoría

> El cierre pide motivo y queda en auditoría.

**Pasos**

1. Con la temporada 2026-2027 activa, pulsar «Cerrar temporada».
2. Revisar el modal: explica que la temporada quedará de solo lectura y que el motivo es
   obligatorio, con mínimo de 10 caracteres.
3. Capturar el motivo «Cierre del ciclo 2026-2027 por término del calendario deportivo» y confirmar.
4. Abrir la pantalla de auditoría y consultar el evento del cierre.

**Resultado esperado**

- Sin motivo no se confirma; con un motivo válido, la temporada pasa a `CERRADA`.
- La auditoría registra la acción `CERRAR_TEMPORADA` con el motivo y el usuario.

**Resultado obtenido:** **Aprobado.** El modal mostró el motivo como obligatorio, el contador
63/500 y el aviso de que quedaría en la auditoría. Al confirmar, la pantalla avisó «Temporada
«Temporada 2026-2027 (aceptación H-04)» cerrada.» y la fila pasó a **CERRADA** con «Solo lectura»;
la temporada 2027-2028 quedó «Lista para activar» al no haber otra activa. La pantalla de auditoría
mostró el evento `CERRAR_TEMPORADA` del módulo M-02 a las 18:41:45, con el motivo completo, y la
consulta a la base devolvió el mismo registro (id 5) con el usuario administrador.

**Evidencia:** [`CA4-modal-cierre.png`](evidencias/H-04/CA4-modal-cierre.png),
[`CA4-temporada-cerrada.png`](evidencias/H-04/CA4-temporada-cerrada.png),
[`CA4-auditoria.png`](evidencias/H-04/CA4-auditoria.png) y
[`CA4-auditoria.txt`](evidencias/H-04/CA4-auditoria.txt) (consulta directa a la base)

**Pruebas automáticas relacionadas**

- `TemporadaServiceTest` · «RN-021: cerrar exige un motivo de al menos 10 caracteres» y «RF-003:
  cerrar fija el motivo, cambia el estado y lo deja en auditoría».
- `TemporadaControllerTest` · «RF-003: el cierre pasa el motivo al servicio» y «RN-021: el 400 por
  falta de motivo conserva el código».
- `TemporadasIT` · «RN-021: cerrar sin motivo responde 400 y la temporada sigue activa».
- `PantallaTemporadas.test.tsx` · «H-04 criterio 4: el cierre pide motivo y lo envía con la
  temporada» y «H-04 criterio 4: un cierre rechazado conserva el modal y explica el error».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Una segunda temporada no se activa si ya hay una activa | Aprobado |
| CA-2 Sin parámetros no se activa y la lista muestra lo que falta | Aprobado |
| CA-3 Una temporada cerrada no se edita y conserva sus datos | Aprobado |
| CA-4 El cierre pide motivo y queda en auditoría | Aprobado |

Pruebas automáticas de la misma versión: backend
`./mvnw test -Dtest=TemporadaServiceTest,TemporadaControllerTest` con **60/60** pruebas (22 de
servicio y 38 de controlador, sin fallos); frontend `npx vitest run src/features/temporadas` con
**15/15** pruebas en un archivo.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-04 ni una regresión de
RF-001, RF-003, RF-004, RF-005, RF-100 o RN-058.

## 6. Observaciones

1. **El botón de activar se deshabilita cuando falta un requisito** (H-04.2). Por eso CA-1 y CA-2
   intentaron la activación por la API, que es la misma acción del botón; la pantalla muestra la
   causa y la lista de verificación el detalle. La prueba confirma que el servidor también rechaza
   la operación, no solo la interfaz.
2. **H-05 todavía no está implementada.** Los parámetros, la categoría y el grupo se sembraron con
   SQL directo. Cuando exista la pantalla de configuración (Sprint 2), esos pasos se harán desde
   F-11 sin cambiar los criterios de H-04.
3. **La lista de verificación usa los códigos del ERS:** `RN-058` para la temporada activa, `RF-100`
   para los parámetros, `RF-004` para las categorías y `RF-005` para los grupos. El rechazo siempre
   nombra el primer requisito incumplido y devuelve todos en `requisitos`.
4. **La base fue efímera.** El clúster local se creó desde cero para la corrida y no se tocó el
   proyecto compartido de Supabase.
5. Las capturas no muestran correos, tokens ni contraseñas; la cuenta se identifica por su rol.

## 7. Conclusión

La historia **H-04 cumple sus cuatro criterios de aceptación** sobre `develop` en `7d3e703` con
frontend, backend y base de datos reales: una segunda temporada no se activa mientras otra esté
activa y el mensaje la nombra, una temporada sin parámetros no se activa y la lista de verificación
explica qué falta, una temporada cerrada queda en solo lectura sin perder sus datos y el cierre pide
un motivo que queda registrado en la auditoría. H-04 puede pasar a «Finalizado».
