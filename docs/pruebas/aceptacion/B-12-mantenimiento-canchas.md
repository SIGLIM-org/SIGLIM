# Prueba de aceptación · B-12 Registrar mantenimiento tipificado de canchas

| Campo | Valor |
| --- | --- |
| Historia | B-12 (SCRUM-221) · subtarea B-12.3 (SCRUM-384) |
| Requisitos | RF-031, RF-030, RN-021, VAL-005, RF-093, PEN-007 |
| Pantallas | F-21 (canchas, disponibilidad y bloqueos) |
| Versión probada | `develop` en `f8f86ac`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-05 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (3 de 3 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 18.6 local en un clúster efímero (puerto 55432), con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor JWKS local que publica una llave ES256 y responde a los endpoints de Supabase Auth que usa el frontend; la cuenta se creó en `auth.users` y se dio de alta con `siglim.registrar_administrador_inicial` |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, Java 25.0.5.0.3 y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://127.0.0.1:5173` (Vite 8.3.5 y proxy hacia el backend) |
| Navegador | Chromium 156 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para la
corrida: no se alteró el proyecto compartido de Supabase. El entorno no tenía Docker, así que las
pruebas de integración con Testcontainers (`CanchasIT`) no se ejecutaron; la verificación manual sí
usó PostgreSQL real con las mismas migraciones, y el trigger `VAL-005` se comprobó sobre esa base.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `canchas.cancha:CONSULTAR` y `canchas.cancha:EDITAR` (LIGA) |

Datos creados durante la corrida, en la semana del **2 al 8 de noviembre de 2026**:

- Cancha **«Cancha de aceptación B-12»**, de la Liga, ubicación Unidad deportiva, dirección
  Av. Universidad 123, Morelia, activa.
- Mantenimiento del **miércoles 4 de noviembre, 08:00–10:00**, motivo «Riego y mantenimiento del
  césped» (CA-1).
- Mantenimiento del **jueves 5 de noviembre, 14:00–16:00**, motivo «Mantenimiento por resiembra del
  área chica», sobre un partido programado (CA-2).
- Partido **Halcones B-12 vs Toros B-12**, jueves 5 de noviembre, **15:00–17:00**, estado
  `PROGRAMADO`. Como la programación manual es la historia H-12 (Sprint 3) y todavía no existe, el
  partido se sembró con SQL directo para poder comprobar el criterio; no se usó ninguna pantalla de
  programación.

## 3. Escenarios

### CA-1 · El mantenimiento queda como bloqueo tipificado y se distingue del bloqueo

> Dado un mantenimiento con motivo, cuando se registra desde F-21, entonces queda como un bloqueo
> tipificado que se distingue del bloqueo en el calendario.

**Pasos**

1. Iniciar sesión como **Administrador** y abrir `/app/programacion/canchas`; seleccionar la cancha
   «Cancha de aceptación B-12».
2. Pulsar «Bloquear intervalo» y elegir el tipo de restricción **Mantenimiento**.
3. Capturar inicio `2026-11-04T08:00`, fin `2026-11-04T10:00` y motivo «Riego y mantenimiento del
   césped».
4. Guardar y revisar el aviso y el calendario semanal.

**Resultado esperado**

- El modal ofrece «Mantenimiento» junto a «Bloqueo» y, al elegirlo, explica que bloquea la cancha
  igual que un bloqueo y se distingue en el calendario.
- El alta responde `201` con `tipo: "MANTENIMIENTO"`, el periodo y el motivo.
- La pantalla confirma «Mantenimiento registrado.» y el calendario pinta el intervalo como
  **Mantenimiento**, con su motivo y distinto del bloqueo.

**Resultado obtenido:** **Aprobado.** El modal mostró «Tipo de restricción» con «Mantenimiento» y
la ayuda «Bloquea la cancha igual que un bloqueo y se distingue en el calendario.». La API respondió
`201` con `id: 2c6e7157-81a2-4bbf-a389-5f9153f2253e`, `tipo: "MANTENIMIENTO"`, periodo
`2026-11-04T08:00:00-06:00`–`2026-11-04T10:00:00-06:00` y el motivo capturado. La pantalla confirmó
«Mantenimiento registrado.» y el calendario pintó el bloque rayado ámbar con la etiqueta
**Mantenimiento** y el motivo, separado del bloqueo y del partido.

**Evidencia**

- [`CA1-modal-mantenimiento.png`](evidencias/B-12/CA1-modal-mantenimiento.png)
- [`CA1-calendario-mantenimiento.png`](evidencias/B-12/CA1-calendario-mantenimiento.png)
- [`CA1-mantenimiento.json`](evidencias/B-12/CA1-mantenimiento.json)

**Pruebas automáticas relacionadas**

- `CanchaControllerTest` · «RF-031: POST con tipo MANTENIMIENTO responde 201 y llega al servicio
  como MANTENIMIENTO».
- `CanchasIT` · «RF-031: en la ocupación, el mantenimiento y el bloqueo salen con su tipo y su
  motivo» y «RF-031: un mantenimiento se guarda aunque se traslape con una disponibilidad y un
  bloqueo de la misma cancha».
- `ModalIntervalo.test.tsx` · «RF-031 (criterio 1): registra un mantenimiento con su motivo» y
  «RF-031 (E1/E3): el modo bloqueo ofrece «Tipo de restricción» con «Bloqueo» por defecto; el de
  disponibilidad no».
- `PantallaCanchas.test.tsx` · «RF-031 (E6): tras registrar un mantenimiento avisa de los partidos
  que quedaron dentro».

### CA-2 · Los partidos afectados se listan y no se reprograman; el mantenimiento bloquea la programación

> Dado un mantenimiento que cubre partidos programados, cuando se intenta guardar, entonces se
> listan los partidos afectados y no se reprograma ninguno; un partido nuevo en ese horario se
> rechaza con `VAL-005`.

**Pasos**

1. Con el partido de 15:00 a 17:00 del jueves 5 ya programado, pulsar «Bloquear intervalo» y elegir
   **Mantenimiento**.
2. Capturar `2026-11-05T14:00` a `2026-11-05T16:00` con motivo «Mantenimiento por resiembra del
   área chica» y pulsar «Guardar».
3. Revisar el aviso con el partido afectado; pulsar «Registrar de todos modos» y revisar el
   calendario.
4. Consultar en la base el estado y el inicio del partido.
5. Insertar con SQL un partido nuevo el miércoles 4 a las 09:00 en la misma cancha, dentro del
   mantenimiento de CA-1, para comprobar el trigger `VAL-005`.

**Resultado esperado**

- El primer envío responde `409` con `codigo: "RF-030"`, `aceptable: true` y la lista
  `partidosAfectados` con el partido, su periodo y sus equipos; no guarda nada.
- El aviso indica que los partidos **no se reprogramarán ni se cancelarán** y no ofrece
  reprogramarlos.
- Al confirmar, el mantenimiento se guarda (`201`), se avisa de «1 partido(s) que quedaron dentro
  del mantenimiento» y el calendario marca el partido **Dentro de un bloqueo**.
- El partido conserva su estado `PROGRAMADO` y su horario.
- El partido nuevo se rechaza con `VAL-005` y el motivo del mantenimiento.

**Resultado obtenido:** **Aprobado.** El primer envío respondió `409` con `codigo: "RF-030"`,
`aceptable: true`, el detalle «El mantenimiento afecta a 1 partido(s), que no se reprogramarán
solos.» y un `partidosAfectados` con «Halcones B-12 vs Toros B-12», estado `PROGRAMADO` y periodo
15:00–17:00. El aviso del modal listó ese partido y advirtió que no se reprogramaría. Tras
«Registrar de todos modos» la API respondió `201` con `partidosAfectados` de tamaño 1; la pantalla
mostró «Mantenimiento registrado.», el aviso «1 partido(s) quedaron dentro del mantenimiento y no se
reprogramaron: reprográmalos manualmente.» y el partido se pintó como **Dentro de un bloqueo**. La
consulta a la base devolvió `estado = PROGRAMADO` e `inicio = 2026-11-05 15:00:00-06`: el partido no
cambió. La inserción del partido nuevo falló con `VAL-005: La cancha tiene mantenimiento en ese
horario: Riego y mantenimiento del césped.`.

**Evidencia**

- [`CA2-aviso-partidos.png`](evidencias/B-12/CA2-aviso-partidos.png) (aviso del `409`)
- [`CA2-respuesta-409.json`](evidencias/B-12/CA2-respuesta-409.json) (respuesta cruda de la API)
- [`CA2-mantenimiento-confirmado.png`](evidencias/B-12/CA2-mantenimiento-confirmado.png)
- [`CA2-mantenimiento-confirmado.json`](evidencias/B-12/CA2-mantenimiento-confirmado.json)
- [`base-mantenimientos.txt`](evidencias/B-12/base-mantenimientos.txt) (registros y partido sin
  cambios)
- [`CA2-val-005-mantenimiento.txt`](evidencias/B-12/CA2-val-005-mantenimiento.txt) (salida real del
  trigger)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-031: un mantenimiento con partidos afectados y sin confirmación responde
  409 RF-030 aceptable y no inserta» y «RF-031: con confirmación guarda el tipo MANTENIMIENTO y
  devuelve los partidos afectados».
- `CanchasIT` · «RF-031: un mantenimiento sobre un partido responde 409 RF-030 con el partido; con
  confirmación se guarda y el partido no cambia» y «VAL-005: el trigger rechaza un partido nuevo que
  cae en un mantenimiento».
- `ModalIntervalo.test.tsx` · «RF-031 / RF-030 (criterio 2): un mantenimiento sobre partidos se
  confirma con «Registrar de todos modos»» y «PEN-007 (E4): cambiar el tipo en el segundo paso vuelve
  al primero y no confirma».
- `PantallaCanchas.test.tsx` · «RF-031 (E6): tras registrar un mantenimiento avisa de los partidos
  que quedaron dentro».
- `calendario.test.ts` · «PEN-007: marca el partido que cae dentro de un bloqueo; [) no cuenta el
  contiguo».

### CA-3 · El motivo es obligatorio y de al menos 10 caracteres

> Sin motivo o con un motivo de menos de 10 caracteres, el mantenimiento no se registra.

**Pasos**

1. Pulsar «Bloquear intervalo» y elegir **Mantenimiento**.
2. Capturar `2026-11-06T08:00` a `2026-11-06T10:00` y el motivo «Corto».
3. Pulsar «Guardar» y revisar el error de la pantalla.
4. Repetir la petición por API con el mismo motivo corto y consultar la base.

**Resultado esperado**

- La pantalla no envía la petición y muestra «Escribe un motivo de al menos 10 caracteres.».
- La API responde `400` con `codigo: "RN-021"` y no guarda nada.

**Resultado obtenido:** **Aprobado.** La pantalla marcó el motivo como inválido y mostró «Escribe un
motivo de al menos 10 caracteres.»; no se envió ninguna petición (el registro de peticiones de la
corrida tiene tres `POST` a `disponibilidad`: el de CA-1 y los dos de CA-2, ninguno de CA-3). La
petición directa a la API respondió `400` con `codigo: "RN-021"` y el detalle «Escribe un motivo de
al menos 10 caracteres.». La consulta a la base confirmó **0** registros para el intervalo del 6 de
noviembre.

**Evidencia**

- [`CA3-motivo-corto.png`](evidencias/B-12/CA3-motivo-corto.png)
- [`CA3-respuesta-400.json`](evidencias/B-12/CA3-respuesta-400.json)
- [`peticiones.txt`](evidencias/B-12/peticiones.txt) (peticiones `POST` de la corrida)
- [`base-mantenimientos.txt`](evidencias/B-12/base-mantenimientos.txt) (consulta del 6 de noviembre:
  0 registros)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RN-021: sin motivo o con motivo de menos de 10 caracteres, el mantenimiento
  responde 400 RN-021 y no inserta».
- `CanchasIT` · «RN-021: un mantenimiento sin motivo o con motivo corto se rechaza y no queda
  guardado».
- `ModalIntervalo.test.tsx` · «RF-031 / RN-021 (criterio 3): no envía un mantenimiento con motivo de
  menos de 10 caracteres».

### Trazabilidad de la auditoría (RF-093)

Cada alta quedó en la auditoría del módulo M-06 con su motivo y el detalle de la cancha, el tipo, el
periodo y los partidos afectados. La consulta devolvió dos eventos `REGISTRAR_MANTENIMIENTO` del
perfil **Administrador de aceptación**, con `partidosAfectados` 0 y 1 respectivamente.

**Evidencia:** [`auditoria-mantenimientos.txt`](evidencias/B-12/auditoria-mantenimientos.txt)

**Pruebas automáticas relacionadas**

- `CanchaServiceTest` · «RF-093: un mantenimiento se audita como REGISTRAR_MANTENIMIENTO con su
  motivo y los partidos afectados».
- `CanchasIT` · «RF-093: el mantenimiento se audita como REGISTRAR_MANTENIMIENTO con su motivo».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Mantenimiento tipificado y visible en el calendario | Aprobado |
| CA-2 Partidos afectados sin reprogramar y `VAL-005` en la programación | Aprobado |
| CA-3 Motivo obligatorio de al menos 10 caracteres | Aprobado |

Verificación automática de la misma versión: backend `./mvnw test` con **653/653** pruebas (0
errores, 0 fallos); frontend `npm test -- --run` con **312/312** pruebas en 46 archivos,
`npm run lint` sin hallazgos y `npm run build` correcto. El detalle está en
[`resumen-ejecucion.txt`](evidencias/B-12/resumen-ejecucion.txt). Las pruebas de integración con
Testcontainers (`CanchasIT`) no se ejecutaron por falta de Docker; el trigger y la auditoría se
comprobaron contra PostgreSQL real en esta corrida.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de B-12 ni una regresión de
RF-030, RN-021 o VAL-005.

## 6. Observaciones

1. **PEN-007 sigue abierto.** Lo probado es la propuesta del ERS: el mantenimiento se confirma en dos
   pasos y los partidos afectados se reprograman a mano. La formalidad del bloqueo debe validarse
   con la Liga; si cambia, cambian el `409` y el aviso, no la validación `VAL-005`.
2. El `409` conserva el código **`RF-030`** porque la regla de confirmación es de RF-030 y RF-031 la
   hereda ([PROPUESTA] B-12.1 D2); el mensaje sí dice «El mantenimiento afecta a N partido(s)».
3. El partido de la prueba se sembró con SQL directo porque la programación manual es H-12 (Sprint
   3). La comprobación de `VAL-005` se hizo, por tanto, en el trigger del esquema, que es el que usará
   la historia de programación.
4. `MANTENIMIENTO` puede traslaparse con otros registros ([PROPUESTA] B-12.1 D3); los dos
   mantenimientos de la corrida conviven en la misma cancha sin error, como estaba previsto.
5. Las capturas no muestran correos, tokens ni contraseñas; la cuenta se identifica por su rol y el
   nombre «Administrador de aceptación» es una etiqueta de la base efímera.
6. Los datos de la corrida quedaron solo en la base local efímera; no se escribió en el proyecto
   compartido de Supabase.

## 7. Conclusión

La historia **B-12 cumple sus tres criterios de aceptación** sobre `develop` en `f8f86ac` con
frontend, backend y base de datos reales: el mantenimiento se registra como bloqueo tipificado y se
distingue en el calendario, los partidos afectados se listan sin reprogramarse y la programación en
ese horario se rechaza con `VAL-005`, y el motivo es obligatorio. B-12 puede pasar a «Finalizado».
