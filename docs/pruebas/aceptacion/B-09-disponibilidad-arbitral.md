# Prueba de aceptación · B-09 Registrar la disponibilidad arbitral

| Campo | Valor |
| --- | --- |
| Historia | B-09 (SCRUM-218) · subtarea B-09.3 (SCRUM-427) · Sprint 3 · Could · Low · 2 SP |
| Requisitos | RF-022, UI-007, RF-093, VAL-015, VAL-024, D-12 |
| Pantallas | F-20 (árbitros: detalle con la sección «Disponibilidad») y F-14 (auditoría) |
| Implementó | Geovani (B-09.1 #155, B-09.2 #162) |
| Versión probada | `develop` en `bd50d5f` (incluye B-09.1 `29fb6b9` y B-09.2 `fc665e5`) |
| Fecha | 2026-11-17 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | _Pendiente_ |

Los criterios salen de la descripción de SCRUM-427 y del contrato de las convenciones de API
§6.5 («Disponibilidad arbitral (B-09.1, RF-022)»). B-09 es una historia B y no trae criterios
numerados en SCRUM-218, así que esta prueba los numera CA-1 a CA-5. V-1 no es un criterio, pero se
verifica en la misma corrida.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52). B-09 no trae migración: la tabla `disponibilidad_arbitral` y el permiso `arbitraje.disponibilidad:EDITAR` vienen del esquema inicial |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite reenvía `/api` al backend) |
| Navegador | Google Chrome, ventana de 1440 px; dos sesiones a la vez (ventana normal e incógnito) |

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Comisión de Arbitraje** | Comisión de Arbitraje · LIGA (`arbitraje.disponibilidad:EDITAR`) | CA-1 a CA-3 y V-1: registra, retira e intenta los casos rechazados |
| **Directiva Real Patzcuaro** | Directiva de club · CLUB *Real Patzcuaro* (**sin** `arbitraje.disponibilidad:EDITAR`) | CA-4: ve la lista de árbitros, pero no la acción ni la API de disponibilidad |
| **Administrador general** | Administrador general · LIGA (`auditoria.registro:CONSULTAR`) | CA-5: consulta la auditoría en F-14 |

Árbitros que dejó la prueba de H-11 (H-11.3) en el Supabase compartido:

| Id | Nombre | Club | Estado | Uso en la prueba |
| --- | --- | --- | --- | --- |
| A1 | Árbitro Prueba Uno | Real Patzcuaro | `VALIDADO` | CA-1, CA-2 y V-1 |
| A2 | Árbitro Prueba Dos | Real Patzcuaro | `RECHAZADO` | CA-3 (opcional, segundo estado no validado) |
| A3 | Árbitra Prueba Tres | Real Patzcuaro | `PROPUESTO` | CA-3 |

Estado inicial (llenar antes de empezar, con [`base-antes.txt`](evidencias/B-09/base-antes.txt)):

- Los estados de A1, A2 y A3: _pendiente_.
- Filas en `siglim.disponibilidad_arbitral`: _pendiente_ (se espera 0).
- Eventos `REGISTRAR_DISPONIBILIDAD_ARBITRAL` y `ELIMINAR_DISPONIBILIDAD_ARBITRAL` en la
  auditoría: _pendiente_ (se espera 0).

Consulta sugerida para el SQL Editor:

```sql
select p.nombre || ' ' || p.apellido_paterno as arbitro, a.estado, a.id as arbitro_id
from siglim.arbitros a join siglim.personas p on p.id = a.persona_id
order by 1;

select count(*) as periodos from siglim.disponibilidad_arbitral;

select accion, count(*) from siglim.auditoria
where accion in ('REGISTRAR_DISPONIBILIDAD_ARBITRAL', 'ELIMINAR_DISPONIBILIDAD_ARBITRAL')
group by accion;
```

### Preparación (no es criterio; se documenta para poder repetirla)

1. **Ayudante de consola para los pasos de API.** En DevTools → **Red**, recargar la pantalla de
   árbitros (`/app/programacion/arbitros`), abrir cualquier petición `/api/v1/…` y copiar el valor
   de `Authorization`. En la **Consola**, pegar el bloque siguiente y **limpiar la consola**
   (Ctrl+L) para que el token no aparezca en las capturas. Las variables se pierden al recargar:
   después de cada recarga hay que volver a pegar el bloque. Si una llamada responde `401`, la
   sesión expiró: recargar y copiar el token de nuevo.

   ```js
   const T = '<valor de Authorization>'
   const api = (metodo, ruta, cuerpo) =>
     fetch('/api/v1' + ruta, {
       method: metodo,
       headers: { Authorization: T, 'Content-Type': 'application/json' },
       body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
     }).then(async (r) => {
       const j = await r.json().catch(() => null)
       console.log(metodo, ruta, '→', r.status, j)
       return j
     })
   const A1 = '<arbitro_id de Árbitro Prueba Uno>'
   const A3 = '<arbitro_id de Árbitra Prueba Tres>'
   const disp = (id) => '/arbitros/' + id + '/disponibilidad'
   ```

2. **Periodos de la prueba.** Se usan fechas del Sprint 4 para no chocar con otras pruebas, en hora
   de Morelia (`-06:00`):

   | Periodo | Fecha | Horario | Registrado como | Destino |
   | --- | --- | --- | --- | --- |
   | P1 | 2026-11-28 | 09:00–13:00 | Disponible | CA-1: se registra en pantalla y se retira |
   | P2 | 2026-11-29 | 16:00–20:00 | No disponible | V-1: se registra y **se queda** (la puede usar H-14) |

## 3. Escenarios

### CA-1 · La Comisión registra y retira disponibilidad de un árbitro validado

> Dado un árbitro `VALIDADO`, cuando la Comisión de Arbitraje registra un periodo con fecha y
> horario, entonces aparece en su detalle; cuando lo retira tras confirmarlo, entonces desaparece.

**Pasos**

1. Como **Comisión de Arbitraje**, abrir **Árbitros** (`/app/programacion/arbitros`) → pestaña
   **Validados** → en la fila de A1 pulsar **Disponibilidad**.
2. En el detalle, revisar la sección «Disponibilidad» antes de registrar.
3. Registrar P1: fecha 2026-11-28, hora inicial 09:00, hora final 13:00, «Registrar como»
   **Disponible**, y guardar.
4. Con el ayudante de consola: `api('GET', disp(A1))`.
5. Pulsar retirar sobre P1, revisar el modal y confirmar.
6. Repetir `api('GET', disp(A1))`.

**Resultado esperado**

- Paso 2: estado vacío «Sin disponibilidad registrada» con «Registra la fecha y el horario en que
  este árbitro puede ser designado.» y el formulario de alta, con la nota «Fecha y horario en hora
  de Morelia.».
- Paso 3: se anuncia que se registró el periodo; aparece agrupado por fecha (28 nov) con su horario
  09:00–13:00 y «Disponible», y el contador dice «1 periodo registrado».
- Paso 4: `200` con un elemento `{ id, arbitroId: A1, periodo: { desde: "2026-11-28T09:00:00-06:00",
  hasta: "2026-11-28T13:00:00-06:00" }, disponible: true, creadoEn }`.
- Paso 5: modal del sistema de diseño (D-12) con Árbitro, Horario y «Registrado como: Disponible»;
  al confirmar se anuncia que se retiró y la sección vuelve al estado vacío.
- Paso 6: `200` con la lista vacía (`[]`); el `DELETE` del paso 5 respondió `204` sin cuerpo (se ve
  en DevTools → Red).

**Resultado obtenido:** _Pendiente._

**Evidencia:** `CA1-detalle-vacio.png`, `CA1-periodo-registrado.png`, `CA1-consola-get.png`,
`CA1-modal-retirar.png`, `CA1-red-delete-204.png`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralIT` («B-09.1 · RF-022: la Comisión
registra, filtra por rango, retira y audita»); `DisponibilidadArbitralServiceTest` («RF-022: la
Comisión registra un periodo y lo audita con la persona del árbitro», «RF-022: retirar un periodo
lo borra y lo audita»); `DisponibilidadArbitralControllerTest` («RF-022: POST responde 201 con el
periodo registrado», «RF-022: DELETE del periodo responde 204 sin cuerpo»);
`DisponibilidadArbitro.test.tsx` («RF-022: registra el periodo en hora de Morelia y lo anuncia»,
«RF-022: lista los periodos agrupados por fecha con su horario y si está disponible», «D-12: retira
un periodo solo tras confirmarlo en el modal»).

### CA-2 · Un periodo vacío o invertido se rechaza y no se guarda

> Dado un periodo cuyo fin no es posterior a su inicio, cuando se guarda, entonces se rechaza
> (`400 VALIDACION`) y no se guarda.

**Pasos**

1. En el detalle de A1, escribir fecha 2026-11-28, hora inicial 13:00 y hora final 09:00, y
   guardar.
2. Cambiar la hora final a 13:00 (igual que la inicial) y guardar.
3. Dejar vacía la fecha o una de las horas y guardar.
4. Con el ayudante de consola, enviar los mismos casos a la API:

   ```js
   api('POST', disp(A1), { periodo: { desde: '2026-11-28T13:00:00-06:00', hasta: '2026-11-28T09:00:00-06:00' } })
   api('POST', disp(A1), { periodo: { desde: '2026-11-28T09:00:00-06:00', hasta: '2026-11-28T09:00:00-06:00' } })
   api('POST', disp(A1), { periodo: { desde: '2026-11-28T09:00:00-06:00' } })
   api('POST', disp(A1), {})
   api('GET', disp(A1))
   ```

**Resultado esperado**

- Pasos 1 y 2: en el campo de la hora final, «La hora final debe ser posterior a la inicial.»; no se
  envía ninguna petición (DevTools → Red).
- Paso 3: «Indica la fecha.», «Indica la hora inicial.» o «Indica la hora final.» según el campo
  vacío; no se envía nada.
- Paso 4: las cuatro respuestas son `400` con `codigo: "VALIDACION"`: «El inicio del periodo debe
  ser anterior a su fin.» (invertido y vacío), «Indica el fin del periodo» (sin `hasta`, en
  `errores`) e «Indica el periodo de disponibilidad.» (sin `periodo`). El `GET` final devuelve la
  lista vacía.

**Resultado obtenido:** _Pendiente._

**Evidencia:** `CA2-invertido-pantalla.png`, `CA2-campos-vacios-pantalla.png`,
`CA2-consola-400.png`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralIT` («B-09.1 · RF-022: un periodo
vacío o invertido responde 400 y no se guarda»); `DisponibilidadArbitralServiceTest` («RF-022: un
periodo vacío o invertido responde 400 VALIDACION y no se guarda»);
`DisponibilidadArbitralControllerTest` («RF-022: POST sin el fin del periodo responde 400
VALIDACION», «RF-022: POST sin periodo responde 400 VALIDACION»);
`DisponibilidadArbitro.test.tsx` («RF-022: rechaza una hora final que no es posterior a la inicial
sin llamar al API», «RF-022: exige la fecha y las dos horas»).

### CA-3 · Un árbitro pendiente (no validado) no admite disponibilidad (`409 RF-022`)

> Dado un árbitro `PROPUESTO` (o cualquier estado distinto de `VALIDADO`), cuando se intenta
> registrar su disponibilidad, entonces se rechaza con `409 RF-022` y no se guarda.

**Pasos**

1. Como **Comisión de Arbitraje**, pestaña **Por validar** → en la fila de A3 pulsar
   **Disponibilidad**.
2. Revisar el detalle de A3.
3. Con el ayudante de consola:

   ```js
   api('POST', disp(A3), { periodo: { desde: '2026-11-28T09:00:00-06:00', hasta: '2026-11-28T13:00:00-06:00' } })
   api('GET', disp(A3))
   ```

4. (Opcional) Repetir el `POST` con A2 (`RECHAZADO`).

**Resultado esperado**

- Paso 2: el detalle muestra «Este árbitro no tiene periodos registrados.» y **no** ofrece el
  formulario de alta (solo consulta y retiro).
- Paso 3: `409` con `codigo: "RF-022"` y «Solo se registra disponibilidad de un árbitro validado;
  este está …» con su estado; el `GET` devuelve `[]`.
- Paso 4: el mismo `409 RF-022` con el estado rechazado.

**Resultado obtenido:** _Pendiente._

**Evidencia:** `CA3-detalle-propuesto.png`, `CA3-consola-409.png`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralIT` («B-09.1 · RF-022: solo un
árbitro VALIDADO recibe disponibilidad»); `DisponibilidadArbitralServiceTest` («RF-022: un árbitro
que no está VALIDADO responde 409 y no se guarda»); `DisponibilidadArbitralControllerTest`
(«RF-022: POST de un árbitro no validado responde 409 RF-022»); `DisponibilidadArbitro.test.tsx`
(«RF-022: un árbitro no validado no ofrece el alta, pero sí el retiro», «RF-022: explica el 409 si
el árbitro ya no está validado»).

### CA-4 · Sin `arbitraje.disponibilidad:EDITAR` no hay acción y la API responde `403` (VAL-015)

> Dado un usuario sin el permiso de disponibilidad, cuando consulta o registra la disponibilidad de
> un árbitro, entonces recibe `403` con `codigo` VAL-015.

**Pasos**

1. En la ventana de incógnito, iniciar sesión como **Directiva Real Patzcuaro** y abrir
   **Árbitros**.
2. Preparar el ayudante de consola con el token de la Directiva (A1 es de su club) y ejecutar:

   ```js
   api('GET', disp(A1))
   api('POST', disp(A1), { periodo: { desde: '2026-11-28T09:00:00-06:00', hasta: '2026-11-28T13:00:00-06:00' } })
   ```

**Resultado esperado**

- Paso 1: ve sus árbitros, pero ninguna fila ofrece **Disponibilidad**.
- Paso 2: las dos respuestas son `403` con `codigo: "VAL-015"` («Tu cuenta no tiene el permiso
  necesario…»). No se registra auditoría.

**Resultado obtenido:** _Pendiente._

**Evidencia:** `CA4-directiva-sin-accion.png`, `CA4-consola-403.png`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralServiceTest` («VAL-015: sin el
permiso de disponibilidad responde 403 y no consulta la base»); `DisponibilidadArbitralControllerTest`
(arnés «RNF-003: seguridad de …» de los tres endpoints); `DisponibilidadArbitro.test.tsx`
(«RF-022: sin el permiso de disponibilidad no se ofrece el detalle»).

### CA-5 · El alta y el retiro quedan en la auditoría sin datos personales

> Cada alta y cada retiro quedan en la auditoría de M-05 sobre `disponibilidad_arbitral`, con
> `{ arbitroId, desde, hasta, disponible }` y sin nombres, CURP ni datos de contacto.

**Pasos**

1. Como **Administrador general**, abrir **Administración → Auditoría** (F-14,
   `/app/administracion/auditoria`) y filtrar por el módulo **M-05**.
2. Expandir el `REGISTRAR_DISPONIBILIDAD_ARBITRAL` y el `ELIMINAR_DISPONIBILIDAD_ARBITRAL` de P1.
3. Contar en la base los eventos de disponibilidad.

**Resultado esperado**

- Paso 1: aparecen los eventos de la Comisión de Arbitraje con entidad `disponibilidad_arbitral`.
- Paso 2: el detalle muestra `arbitroId`, `desde`, `hasta` y `disponible`, sin nombre, CURP ni
  correo.
- Paso 3: **2** `REGISTRAR_DISPONIBILIDAD_ARBITRAL` (P1 y P2 de V-1) y **1**
  `ELIMINAR_DISPONIBILIDAD_ARBITRAL` (P1): ningún evento por los rechazos de CA-2, CA-3 y CA-4.

**Resultado obtenido:** _Pendiente._

**Evidencia:** `CA5-auditoria.png`, `CA5-detalle-registro.png`, `CA5-detalle-eliminacion.png`,
`auditoria-disponibilidad.txt`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralIT` («B-09.1 · RF-022: la Comisión
registra, filtra por rango, retira y audita»); `DisponibilidadArbitralServiceTest` («RF-022: la
Comisión registra un periodo y lo audita con la persona del árbitro», «RF-022: retirar un periodo
lo borra y lo audita»).

### V-1 · «No disponible» declara que el árbitro no puede ser designado, y el rango filtra por traslape

**Pasos**

1. En el detalle de A1, registrar P2: fecha 2026-11-29, 16:00 a 20:00, «Registrar como» **No
   disponible**.
2. Con el ayudante de consola:

   ```js
   api('GET', disp(A1))
   api('GET', disp(A1) + '?desde=2026-11-29T18:00:00-06:00&hasta=2026-11-29T22:00:00-06:00')
   api('GET', disp(A1) + '?desde=2026-11-30T00:00:00-06:00&hasta=2026-12-01T00:00:00-06:00')
   api('GET', disp(A1) + '?desde=2026-11-29T18:00:00-06:00')
   ```

**Resultado esperado**

- Paso 1: el periodo aparece el 29 nov, 16:00–20:00, como «No disponible». **No se retira**: queda
  para la designación de H-14 (Sprint 4).
- Paso 2: el primer `GET` devuelve P2 con `disponible: false`; el segundo (se traslapa) también lo
  devuelve; el tercero (no se traslapa) devuelve `[]`; el cuarto (una sola fecha) responde `400
  VALIDACION` con «Indica las fechas desde y hasta del rango, o ninguna de las dos.».

**Resultado obtenido:** _Pendiente._

**Evidencia:** `V1-no-disponible.png`, `V1-consola-rango.png`

**Pruebas automáticas relacionadas:** `DisponibilidadArbitralServiceTest` («RF-022: disponible en
falso declara que el árbitro no puede ser designado», «RF-022: el rango del listado filtra por
traslape con el periodo», «RF-022: con una sola fecha del rango responde 400 VALIDACION»).

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 La Comisión registra y retira disponibilidad de un árbitro validado | _Pendiente_ |
| CA-2 Periodo vacío o invertido → `400 VALIDACION` | _Pendiente_ |
| CA-3 Árbitro no validado → `409 RF-022` | _Pendiente_ |
| CA-4 Sin `arbitraje.disponibilidad:EDITAR`: sin acción y `403 VAL-015` | _Pendiente_ |
| CA-5 Auditoría del alta y del retiro sin datos personales | _Pendiente_ |
| V-1 «No disponible» y filtro por rango | _Pendiente_ |

## 5. Defectos encontrados

_Ninguno por ahora._

## 6. Conclusión

_Pendiente._ Las pruebas automáticas citadas ya corrieron en los PR de B-09.1 (#155) y B-09.2
(#162), fusionados en `develop`. Esta subtarea no cambia código.
