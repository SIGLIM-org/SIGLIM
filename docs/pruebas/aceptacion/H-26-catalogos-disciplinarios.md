# Prueba de aceptación · H-26 Configurar los catálogos de incidencias y artículos

| Campo | Valor |
| --- | --- |
| Historia | H-26 (SCRUM-204) · subtarea H-26.3 (SCRUM-346) |
| Requisitos | RF-128, RF-129, RF-060, UI-026, PEN-021, RF-093, VAL-015 |
| Pantallas | F-32 (catálogos disciplinarios) |
| Versión probada | `develop` en `2605860`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-04 |
| Ejecutó | López Meza Leonardo (Scrum Master y responsable de calidad) |
| Resultado global | **Aprobado** (3 de 3 criterios y 2 de 2 verificaciones complementarias) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Proyecto compartido de Supabase (PostgreSQL 17) con las cuatro migraciones de `develop` aplicadas por la integración de GitHub (DEC-52) |
| Autenticación | Supabase Auth del mismo proyecto, inicio de sesión con correo |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Google Chrome en Windows, con las herramientas de desarrollador (F12) para las respuestas de la API |

Estado inicial del catálogo, comprobado en la base antes de la corrida: 3 tipos de incidencia
(`AMONESTACION`, `EXPULSION`, `RINA`) y 3 artículos (`DEMO-1`, `DEMO-2`, `DEMO-3`), todos activos y
marcados como demostración (`es_demo = true`), sembrados por la migración inicial (§12.7, DEC-22,
DEC-30, DEC-31).

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `disciplina.catalogo:EDITAR` y `auditoria.registro:CONSULTAR` (LIGA) |
| **Capturista** | Capturista | Sin `disciplina.catalogo:EDITAR` |

Datos que se crean durante la corrida (todos llevan «aceptación H-26» para distinguirlos):

| Elemento | Datos |
| --- | --- |
| Tipo de incidencia T1 | Código `AGRESION_VERBAL_H26`, nombre «Agresión verbal (aceptación H-26)», **abre expediente** |
| Artículo A1 | Número `ACEP-H26-1`, «Suspensión por agresión verbal al árbitro (aceptación H-26)», suspensión por **3 partidos**, sujeto **Jugador**, efecto **Hacia adelante**, **aplicación automática** |
| Artículo A2 | Número `ACEP-H26-2`, «Multa por retraso en la presentación del equipo (aceptación H-26)», multa de **$500.00**, sujeto **Equipo**, efecto **Retroactivo**, **sin** aplicación automática |

Al terminar, los tres elementos se **desactivan** (baja lógica, RN-005): el catálogo no permite
borrar y así no quedan como opciones vigentes en la base compartida.

## 3. Escenarios

### CA-1 · Un tipo marcado «abre expediente» queda configurado para abrir uno al aprobarse la cédula

> Dado un tipo de incidencia, cuando se marca «abre expediente», entonces la aprobación de una
> cédula con esa incidencia abrirá uno.

**Alcance de la verificación.** La H-26 entrega la **configuración**: guardar y editar la marca
`abre_expediente` de cada tipo. La apertura del expediente al aprobar la cédula pertenece a la
**H-27** (su criterio 1) y depende de la cédula (H-16, H-17), que aún no existen; esa parte se
verificará en la prueba de aceptación de la H-27. Aquí se comprueba que la marca se guarda, se
puede cambiar y queda disponible en la API y en la base, que es el dato que la H-27 consultará.

**Pasos**

1. Iniciar sesión como **Administrador** y abrir **Disciplina → Catálogos disciplinarios**
   (`/app/disciplina/catalogos`), pestaña **Tipos de incidencia**.
2. Pulsar **Nuevo tipo**, capturar el código y el nombre de T1, marcar **Abre expediente** y
   pulsar **Crear tipo**.
3. Recargar la página (F5) y localizar T1 con el buscador.
4. Con las herramientas de desarrollador (F12) → **Red**, revisar la respuesta de la petición
   `tipos-incidencia` (pestaña *Respuesta*).
5. Pulsar **Editar** en T1, desmarcar **Abre expediente** y pulsar **Guardar cambios**.
6. Volver a editar T1, marcar de nuevo **Abre expediente** y guardar.

**Resultado esperado**

- Tras crear, aparece el mensaje de confirmación y T1 se muestra con **Abre expediente: Sí** y
  estado **Activo**; tras recargar conserva esos valores.
- La respuesta de `GET /api/v1/tipos-incidencia` incluye T1 con `"abreExpediente": true` y
  `"esDemo": false`.
- Al desmarcar, la fila cambia a **No**; al volver a marcar, regresa a **Sí**.

**Resultado obtenido:** **Aprobado.** T1 se creó con **Abre expediente: Sí** y estado **Activo**,
y conservó esos valores al recargar. La respuesta de `GET /api/v1/tipos-incidencia` lo devolvió con
`"id": 4`, `"abreExpediente": true`, `"activo": true` y `"esDemo": false`. Al desmarcar la casilla, la
API devolvió T1 con `"abreExpediente": false` y la fila cambió a **No**. La bitácora
registra el alta con `abreExpediente: true` y tres ediciones del campo `abreExpediente` (21:03,
21:04 y 21:08): la marca se cambió en ambos sentidos y la última edición la dejó en **No**, el
valor con el que T1 aparece en la captura del CA-3. Esto no afecta el criterio, porque se
comprobó que los dos valores se guardan y se pueden cambiar; además, T1 se desactivó al cierre.

**Evidencia:** [`CA1-tipo-creado.png`](evidencias/H-26/CA1-tipo-creado.png),
[`CA1-respuesta-tipos.png`](evidencias/H-26/CA1-respuesta-tipos.png) y
[`CA1-tipo-sin-expediente.png`](evidencias/H-26/CA1-tipo-sin-expediente.png)

**Pruebas automáticas relacionadas**

- `TiposIncidenciaServiceTest` · «RF-128: registra el tipo y lo audita con el módulo M-09» y
  «RF-128: la edición conserva lo ausente y audita los campos cambiados».
- `TipoIncidenciaControllerTest` · «RF-128: POST responde 201 con Location» y «RF-128: PATCH edita
  el tipo y devuelve el resultado».
- `CatalogosDisciplinariosIT` · «RF-128: un tipo creado aparece en el catálogo activo y la baja
  lógica lo oculta sin borrarlo».
- `PantallaCatalogosDisciplinarios.test.tsx` · «H-26.2: crea un tipo de incidencia y lo anuncia» y
  «H-26.2: edita un tipo de incidencia en el panel lateral».

### CA-2 · Los artículos guardan sanción, sujeto, efecto y aplicación automática

> Los artículos guardan sanción, sujeto, efecto y aplicación automática.

**Pasos**

1. En la pestaña **Artículos del reglamento**, pulsar **Nuevo artículo** y capturar A1:
   tipo de sanción **Suspensión por partidos**, **Partidos de suspensión** = 3, sujeto
   **Jugador**, efecto **Hacia adelante** y marcar **Aplicación automática**. Pulsar
   **Crear artículo**.
2. Crear A2: tipo **Multa**, **Monto de la multa** = 500, sujeto **Equipo**, efecto
   **Retroactivo** y **Aplicación automática** sin marcar.
3. Recargar la página y revisar las filas de A1 y A2 en la tabla.
4. En **Red**, revisar la respuesta de la petición `articulos`.
5. **Validación:** pulsar **Nuevo artículo**, elegir **Suspensión por partidos**, dejar vacío
   **Partidos de suspensión**, llenar lo demás e intentar crear. Después cerrar el panel sin
   guardar.
6. **Cambio de tipo (DEC-30):** editar A1, cambiar el tipo a **Pérdida de puntos** con
   **Puntos** = 2 y guardar; revisar la fila y la respuesta de `articulos`.

**Resultado esperado**

- A1 se muestra con sanción «Suspensión · 3 partidos», aplica a Jugador, efecto Adelante y
  automática **Sí**; A2 con «Multa · $500.00», aplica a Equipo, efecto Retroactivo y automática
  **No**. Ambos activos y sin la insignia «No oficial», y conservan los valores al recargar.
- La respuesta de `GET /api/v1/articulos` trae para A1 `tipoSancion: "SUSPENSION_PARTIDOS"`,
  `partidosSuspension: 3`, `sujeto: "JUGADOR"`, `efecto: "HACIA_ADELANTE"`,
  `aplicacionAutomatica: true`; para A2 `tipoSancion: "MULTA"`, `montoMulta: 500`,
  `sujeto: "EQUIPO"`, `efecto: "RETROACTIVO"`, `aplicacionAutomatica: false`.
- La suspensión sin partidos no se guarda: el formulario muestra el error y el panel sigue
  abierto; la tabla no cambia.
- Tras el cambio de tipo, A1 muestra «Pérdida de puntos · 2 puntos» y la respuesta trae
  `puntos: 2` y `partidosSuspension: null` (el valor del tipo anterior se limpió).

**Resultado obtenido:** **Aprobado.**

- **A1** se mostró con «Suspensión · 3 partidos · Automática», aplica a **Jugador**, efecto
  **Adelante**, **Activo** y sin la insignia «No oficial». La respuesta de `GET /api/v1/articulos`
  trajo `tipoSancion: "SUSPENSION_PARTIDOS"`, `partidosSuspension: 3`, `puntos: null`,
  `montoMulta: null`, `sujeto: "JUGADOR"`, `efecto: "HACIA_ADELANTE"`, `aplicacionAutomatica: true`,
  `otorgaDefaultRestantes: true` (valor por defecto, DEC-32) y `esDemo: false`.
- **A2** se mostró con «Multa · $500.00», aplica a **Equipo**, efecto **Retroactivo** y sin la
  marca «Automática» (visible en la captura del CA-3, tomada después de crearlo; la de la tabla del
  CA-2 se tomó antes del alta de A2). La base confirma `MULTA`, `EQUIPO`, `RETROACTIVO`,
  `aplicacion_automatica = false` y `monto_multa = 500.00`.
- **Validación:** la suspensión sin partidos no se guardó. El panel siguió abierto con el campo
  marcado y el mensaje «Indica un número entero de partidos entre 1 y 32767.», y la tabla no
  cambió. En la captura también aparece «Escribe la descripción.» porque la descripción quedó
  vacía. Ambas validaciones son del formulario, así que la petición no llegó al backend; la
  validación del servidor (`400 VALIDACION`) está cubierta por las pruebas automáticas.
- **Cambio de tipo (DEC-30):** A1 pasó a «Pérdida de puntos · 2 puntos» y la respuesta trajo
  `tipoSancion: "PERDIDA_PUNTOS"`, `puntos: 2` y `partidosSuspension: null`; el sujeto, el efecto y
  la aplicación automática se conservaron. La bitácora registra la edición con los campos
  `tipoSancion`, `partidosSuspension` y `puntos`.

**Evidencia:** [`CA2-articulos-tabla.png`](evidencias/H-26/CA2-articulos-tabla.png),
[`CA2-respuesta-articulos.png`](evidencias/H-26/CA2-respuesta-articulos.png),
[`CA2-validacion-partidos.png`](evidencias/H-26/CA2-validacion-partidos.png) y
[`CA2-cambio-tipo.png`](evidencias/H-26/CA2-cambio-tipo.png)

**Pruebas automáticas relacionadas**

- `ArticulosServiceTest` · «RF-129: aplica los valores por defecto de efecto, aplicación
  automática y partidos restantes», «RF-129: una suspensión sin partidos responde 400
  VALIDACION», «RF-129: una multa sin monto responde 400 VALIDACION» y «RF-129: cambiar de tipo
  limpia los valores del anterior».
- `ArticuloControllerTest` · «RF-129: GET lista el catálogo con sanción, sujeto, efecto y
  aplicación automática» y «RF-129: POST responde 201 con Location».
- `CatalogosDisciplinariosIT` · «RF-129: un artículo creado aparece en el catálogo activo y la
  baja lógica lo oculta» y «DEC-30: cambiar el tipo de sanción limpia el valor del tipo anterior
  en la base».
- `PantallaCatalogosDisciplinarios.test.tsx` · «H-26.2: edita un artículo y guarda solo el valor
  de la sanción elegida» y «H-26.2: muestra el error del backend y conserva el panel abierto».

### CA-3 · Los elementos de demostración llevan la insignia «No oficial»

> Los elementos de demostración llevan la insignia «No oficial».

**Pasos**

1. En la pestaña **Tipos de incidencia**, revisar las filas `AMONESTACION`, `EXPULSION`, `RINA` y
   la de T1.
2. En la pestaña **Artículos del reglamento**, revisar `DEMO-1`, `DEMO-2`, `DEMO-3`, A1 y A2.
3. Revisar el aviso «Valores de demostración» en la parte superior de la pantalla.

**Resultado esperado**

- Los 3 tipos y los 3 artículos sembrados muestran la insignia **«No oficial»**.
- T1, A1 y A2 (creados por el usuario) **no** la muestran.
- El aviso superior explica que los elementos «No oficial» aún no están aprobados por la Liga
  (PEN-021).

**Resultado obtenido:** **Aprobado.** En **Tipos de incidencia** (4), `AMONESTACION`,
`EXPULSION` y `RINA` muestran la insignia **«No oficial»** y T1 no la muestra. En **Artículos del
reglamento** (5), `DEMO-1` y `DEMO-2` la muestran (la captura recorta `DEMO-3`, que ya aparecía con
la insignia en la captura de la tabla del CA-2) y A1 y A2 no la muestran. El aviso «Valores de
demostración» aparece arriba de las pestañas con su propia insignia y explica que la Liga aún no
aprueba esos elementos.

**Evidencia:** [`CA3-tipos-no-oficial.png`](evidencias/H-26/CA3-tipos-no-oficial.png) y
[`CA3-articulos-no-oficial.png`](evidencias/H-26/CA3-articulos-no-oficial.png)

**Pruebas automáticas relacionadas**

- `PantallaCatalogosDisciplinarios.test.tsx` · «H-26.2: muestra los dos catálogos y marca solo
  los valores de demostración».

## 4. Verificaciones complementarias (Definición de Terminado)

No son criterios de la historia, pero la Definición de Terminado (`CONTRIBUTING.md` §4) pide
permisos y auditoría de las operaciones críticas.

### V-1 · Sin el permiso de catálogo no se accede a F-32 (VAL-015)

**Pasos:** cerrar sesión, entrar como **Capturista**, revisar la barra lateral y escribir en la
barra de direcciones `http://localhost:5173/app/disciplina/catalogos`.

**Resultado esperado:** la barra lateral no muestra **Disciplina** y la ruta muestra la página
**403** sin datos de los catálogos.

**Resultado obtenido:** **Aprobado.** Con la cuenta Capturista, la barra lateral solo muestra
**Inicio** y **Partidos**. Al abrir `/app/disciplina/catalogos` aparece la página **Error 403 · No
tienes permiso para esta acción**, sin datos de los catálogos.

**Evidencia:** [`V1-capturista-sin-menu.png`](evidencias/H-26/V1-capturista-sin-menu.png) y
[`V1-capturista-403.png`](evidencias/H-26/V1-capturista-403.png)

**Pruebas automáticas relacionadas:** `TipoIncidenciaControllerTest` y `ArticuloControllerTest` ·
«RNF-003: seguridad de …» (401/403 de cada endpoint); `TiposIncidenciaServiceTest` y
`ArticulosServiceTest` · «VAL-015: sin permiso de catálogo no registra nada»;
`PantallaCatalogosDisciplinarios.test.tsx` · «H-26.2: sin permiso de catálogos muestra la página
403».

### V-2 · Cada alta y edición queda en la bitácora del módulo M-09 (RF-093)

**Pasos:** como **Administrador**, abrir **Administración → Auditoría**
(`/app/administracion/auditoria`) y filtrar por el módulo **M-09**.

**Resultado esperado:** aparecen, con la cuenta Administrador y la fecha de la corrida,
`REGISTRAR_TIPO_INCIDENCIA` (T1), `EDITAR_TIPO_INCIDENCIA` (las ediciones de T1),
`REGISTRAR_ARTICULO` (A1 y A2) y `EDITAR_ARTICULO` (cambio de tipo de A1).

**Resultado obtenido:** **Aprobado.** El filtro **M-09 · Disciplina** mostró 7 eventos de la
cuenta Administrador del 4 de noviembre de 2026: `REGISTRAR_TIPO_INCIDENCIA` (21:01, entidad 4),
tres `EDITAR_TIPO_INCIDENCIA` (21:03, 21:04 y 21:08), `REGISTRAR_ARTICULO` de A1 (21:13) y de A2
(21:16) y `EDITAR_ARTICULO` del cambio de tipo de A1 (21:25). Cada registro guarda solo los datos
del catálogo o los nombres de los campos cambiados, sin datos personales.

**Evidencia:** [`V2-auditoria-m09.png`](evidencias/H-26/V2-auditoria-m09.png)

## 5. Cierre de la corrida

Como **Administrador**, editar T1, A1 y A2, desmarcar **Activo** y guardar. Los tres quedan con
estado **Inactivo** y siguen en la base (RN-005).

**Resultado obtenido:** T1, A1 y A2 quedaron **Inactivos** y conservan sus datos. La bitácora
registra tres ediciones del campo `activo` (21:35 y 21:36). Los 6 elementos de demostración
siguen activos.

**Evidencia:** [`cierre-elementos-inactivos.png`](evidencias/H-26/cierre-elementos-inactivos.png)

## 6. Defectos y observaciones

No se encontraron defectos.

| Id | Observación | Seguimiento |
| --- | --- | --- |
| O-1 | La apertura del expediente al aprobar una cédula con un tipo «abre expediente» (segunda mitad del CA-1) no existe todavía; depende de la cédula (H-16, H-17) y de la H-27. | Se verifica en la prueba de aceptación de la H-27, criterio 1 |

## 7. Conclusión

La H-26 cumple sus tres criterios de aceptación con el frontend, el backend y la base de datos
compartida. Los tipos de incidencia guardan la marca «abre expediente», los artículos guardan
sanción, sujeto, efecto y aplicación automática (y limpian el valor anterior al cambiar el tipo de
sanción), y solo los elementos sembrados como demostración llevan la insignia «No oficial».
También se comprobó que una cuenta sin `disciplina.catalogo:EDITAR` no accede a F-32 y que cada
alta y edición queda en la bitácora del módulo M-09. La historia puede cerrarse.
