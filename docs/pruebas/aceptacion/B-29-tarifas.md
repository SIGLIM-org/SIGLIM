# Prueba de aceptación · B-29 Configurar tarifas de arbitraje y cancha

| Campo | Valor |
| --- | --- |
| Historia | B-29 (SCRUM-238) · subtarea B-29.3 (SCRUM-393) · Sprint 3 · Could · Low · 2 SP |
| Requisitos | RF-135, RF-003, RF-093, VAL-015, PEN-042, DEC-66 |
| Pantallas | F-33 (tesorería, pestaña «Tarifas» [PROPUESTA]) y F-14 (auditoría) |
| Implementó | Israel (B-29.1 #150, B-29.2 #153) |
| Versión probada | `develop` en `5911a87` (incluye B-29.1 `25cc809` y B-29.2 `e84ee51`) |
| Fecha | 2026-11-17 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (4 de 4 criterios de SCRUM-238 y 3 de 3 casos adicionales), sin defectos |

Los criterios de B-29 se precisaron el 17 de noviembre en SCRUM-238 a partir de las pruebas
automáticas (`TarifaServiceTest`, `TarifaControllerTest`, `TarifasIT`) y de DEC-66. Conservan la
numeración y el sentido de los criterios originales del refinamiento del Sprint 2. Los tres casos
adicionales (V-1 a V-3) no son criterios de la historia, pero se verifican en la misma corrida.

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52), con las 12 migraciones hasta `20261116180000_parametros_localia` |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite reenvía `/api` al backend) |
| Navegador | Google Chrome, ventana de 1440 px |

La prueba usa el Supabase compartido, igual que B-05.4 y H-13.3. Las tarifas que se capturan en la
«Temporada 2026–2027» **no se borran al terminar**: las reutiliza la prueba de cobros automáticos
B-30.2 (SCRUM-446).

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Tesorero** | Tesorero (liga) · LIGA (`tesoreria.tarifa:EDITAR`, único rol con ese permiso) | CA-1 a CA-4, V-2 y V-3: captura, edita e intenta los casos rechazados |
| **Administrador general** | Administrador general · LIGA (`auditoria.registro:CONSULTAR`) | CA-4: consulta la auditoría en F-14 |
| **Directiva** | Directiva de club · CLUB Real Pátzcuaro (`tesoreria.cobro:CONSULTAR`, **sin** `tesoreria.tarifa:EDITAR`) | V-1: entra a tesorería, pero no ve la pestaña ni usa la API de tarifas |

Estado inicial verificado el 2026-11-17 a las 16:38 en el Supabase compartido
([`base-antes.txt`](evidencias/B-29/base-antes.txt)):

| Temporada | Estado | Categorías |
| --- | --- | --- |
| Temporada 2026–2027 | `CONFIGURACION` | Libre Demostración, Veteranos Demostración |
| Temporada prueba B-05 | `CERRADA` | Libre Demostración, Veteranos Demostración |
| Temporada prueba H-13 | `CERRADA` | Libre Demostración |

- `siglim.tarifas` estaba **vacía**: la semilla de DEC-66 no se cargó en el compartido.
- La auditoría no tenía ningún `REGISTRAR_TARIFA` ni `EDITAR_TARIFA` (137 eventos en total).
- La tabla tiene `UNIQUE (temporada_id, categoria_id, concepto)`, `CHECK (monto >= 0)` y monto
  `numeric(12,2)`; no tiene columna `es_demo`: el rótulo «No oficial» sale de la API
  (`TarifaService.TARIFAS_DEMO`).

### Preparación (no es criterio; se documenta para poder repetirla)

1. **Tarifa en una temporada cerrada (para el CA-3).** La API no permite registrar tarifas en una
   temporada `CERRADA` y no había ninguna, así que no se podía probar la **edición** bloqueada. Se
   insertó una fila por SQL en «Temporada prueba H-13»:

   | Id | Temporada | Categoría | Concepto | Monto |
   | --- | --- | --- | --- | --- |
   | `86595834-b0ce-4152-b23c-841ae5ad8a67` | Temporada prueba H-13 (`CERRADA`) | Libre Demostración | `ARBITRAJE` | 250.00 |

   Como no pasa por la API, **no tiene evento de auditoría**: no cuenta en el CA-4.
2. **Ayudante de consola para los pasos de API.** En DevTools → **Red**, recargar la pestaña
   Tarifas, abrir cualquier petición `/api/v1/…` y copiar el valor de `Authorization`. En la
   **Consola**, pegar el bloque siguiente y **limpiar la consola** (Ctrl+L) para que el token no
   aparezca en las capturas. Las variables se pierden al recargar la página: después de cada recarga
   hay que volver a pegar el bloque. Si una llamada responde `401`, la sesión expiró: recargar y
   copiar el token de nuevo.

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
   const TEMP = 'bae83713-394b-4084-b6d8-63d74a3fd59e'      // Temporada 2026–2027
   const CERRADA = '2231c72c-188f-42cf-ac18-c806a98ade47'   // Temporada prueba H-13
   const LIBRE = '214171a4-253a-477c-b379-a4237e71f30b'     // Libre Demostración
   const VETERANOS = 'fe2a0e5e-0cef-4c2e-b466-1369f76f4eff' // Veteranos Demostración
   const TARIFA_CERRADA = '86595834-b0ce-4152-b23c-841ae5ad8a67'
   ```

Montos que quedan al final en la «Temporada 2026–2027» (se reutilizan en B-30.2):

| Categoría | Arbitraje | Cancha |
| --- | --- | --- |
| Libre Demostración | 400.00 (se registra en 350.00 y se edita en el CA-4) | 200.00 |
| Veteranos Demostración | 300.00 | 0.00 (en B-30.2 cubre la omisión `TARIFA_EN_CERO`) |

## 3. Escenarios

### CA-1 · Una tarifa por temporada, categoría y concepto; la repetida responde `409 DUPLICADO`

> Dada una temporada y una categoría, cuando el Tesorero registra una tarifa de arbitraje o de
> cancha, entonces queda una sola por temporada, categoría y concepto. La repetida responde
> `409 DUPLICADO`.

**Pasos**

1. Como **Tesorero**, abrir `/app/tesoreria` → pestaña **Tarifas**.
2. En **Temporada**, elegir «Temporada 2026–2027 · Configuración».
3. En **Libre Demostración**, escribir Arbitraje `350.00` y Cancha `200.00` y pulsar **Guardar**.
4. En **Veteranos Demostración**, escribir solo Arbitraje `300.00` y pulsar **Guardar**.
5. Con el ayudante de consola, intentar registrar otra vez la misma combinación:
   `api('POST', '/tarifas', { temporadaId: TEMP, categoriaId: LIBRE, concepto: 'ARBITRAJE', monto: 999 })`.

**Resultado esperado**

- Paso 1: la pestaña abre en «Temporada prueba H-13 · Cerrada», porque no hay temporada `ACTIVA` y
  se elige la de `fecha_inicio` más reciente (`temporadaInicial`).
- Paso 2: insignias **Configuración** y **No oficial**, el aviso de montos de demostración y las
  dos categorías con «Sin tarifa» en ambos conceptos.
- Paso 3: «Tarifas de «Libre Demostración» guardadas: arbitraje $350.00 y cancha $200.00.»
- Paso 4: «Tarifas de «Veteranos Demostración» guardadas: arbitraje $300.00.»
- Paso 5: `409` con `codigo: "DUPLICADO"` y «Ya existe la tarifa de arbitraje para esa categoría
  en la temporada: edita su monto.» La tarifa sigue en 350.00.

**Resultado obtenido:** **Aprobado.** En «Temporada 2026–2027» la pestaña mostró las insignias
**Configuración** y **No oficial**, el aviso de montos de demostración y las dos categorías con «Sin
tarifa». Al guardar Libre apareció la confirmación de arbitraje $350.00 y cancha $200.00, y al
guardar Veteranos, «Tarifas de «Veteranos Demostración» guardadas: arbitraje $300.00.», con la tabla
en 350.00 / 200.00 y 300.00 / «Sin tarifa». El `POST` repetido de Libre · Arbitraje respondió
**409 (Conflict)** con `type: https://siglim.mx/errores/duplicado` y «Ya existe la tarifa de
arbitraje para esa categoría en la temporada…»; la tarifa siguió en 350.00 y no se creó otra fila
(`base-despues.txt`). La apertura en «Temporada prueba H-13 · Cerrada» (paso 1) no se capturó; la
cubre `tarifas.test.ts` («B-29.2: abre en la temporada activa o, sin ella, en la más reciente»).

**Evidencia:** `CA1-tabla-vacia.png`, `CA1-tarifas-guardadas.png`, `CA1-consola-409.png`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («B-29 criterio 1 · RF-135: la misma
temporada, categoría y concepto responde 409 DUPLICADO»); `TarifasIT` («B-29 criterios 1 y 4 ·
RF-135: el TESORERO registra una tarifa única por temporada, categoría y concepto, auditada»);
`TarifaControllerTest` («RF-135: POST responde 201 con Location»); `PestanaTarifas.test.tsx`
(«RF-135: crea la tarifa que falta y cambia la existente, solo con lo que cambió», «B-29 criterio
1 · RF-135: muestra el 409 DUPLICADO del backend en la fila»).

### CA-2 · Un monto negativo o con más de dos decimales se rechaza con `400 VALIDACION`; 0 es válido

> Dado un monto negativo o con más de dos decimales, cuando se guarda, entonces se rechaza con
> `400 VALIDACION` y no se guarda. Una tarifa de 0 sí es válida.

**Pasos**

1. En **Veteranos Demostración**, escribir Cancha `-50` y pulsar **Guardar**.
2. En la misma celda, escribir `10.555` y pulsar **Guardar**.
3. Con el ayudante de consola, enviar los mismos montos a la API y un `PATCH` negativo:

   ```js
   api('POST', '/tarifas', { temporadaId: TEMP, categoriaId: VETERANOS, concepto: 'CANCHA', monto: -50 })
   api('POST', '/tarifas', { temporadaId: TEMP, categoriaId: VETERANOS, concepto: 'CANCHA', monto: 10.555 })
   const lista = await api('GET', '/tarifas?temporadaId=' + TEMP)
   const libreArb = lista.find((t) => t.categoria.id === LIBRE && t.concepto === 'ARBITRAJE').id
   api('PATCH', '/tarifas/' + libreArb, { monto: -1 })
   ```

4. Recargar la pestaña, escribir Cancha `0` en **Veteranos Demostración** y pulsar **Guardar**.
5. Comprobar en la base que la Cancha de Veteranos solo tiene la fila de 0.00 y que la Arbitraje
   de Libre sigue en 350.00.

**Resultado esperado**

- Paso 1: en la celda, «El monto no puede ser negativo.»; no se envía ninguna petición.
- Paso 2: «Escribe un monto en pesos con hasta dos decimales, por ejemplo 350.00.»; no se envía.
- Paso 3: las tres respuestas son `400` con `codigo: "VALIDACION"` y el mensaje del campo `monto` en
  `errores` («El monto no puede ser negativo» o «El monto admite hasta dos decimales»). El backend
  rechaza aunque la pantalla no haya enviado nada.
- Paso 4: «Tarifas de «Veteranos Demostración» guardadas: cancha $0.00.»
- Paso 5: no hay ninguna fila con `-50` ni con `10.56` (la columna `numeric(12,2)` redondearía en
  silencio, así que el rechazo tiene que venir de la API).

**Resultado obtenido:** **Aprobado.** La pantalla explicó en la celda «El monto no puede ser
negativo.» con `-50` y «Escribe un monto en pesos con hasta dos decimales, por ejemplo 350.00.» con
`10.555`, sin enviar nada. Por la API, los dos `POST` (`-50` y `10.555`) y el `PATCH` con `-1`
respondieron **400 (Bad Request)** con `title: Solicitud inválida`,
`type: https://siglim.mx/errores/validacion` y `detail: Revisa los campos marcados.`; el mensaje del
campo `monto` viaja en la propiedad `errores`, que no se expandió en la captura. Al guardar `0`, «Tarifas de «Veteranos Demostración» guardadas:
cancha $0.00.» y la celda quedó en 0.00. En la base no hay ninguna fila con `-50`, `-1`, `10.555`
ni `10.56`.

**Evidencia:** `CA2-negativo-pantalla.png`, `CA2-decimales-pantalla.png`, `CA2-consola-400.png`,
`CA2-tarifa-cero.png`, `base-despues.txt`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («B-29 criterio 2 · RF-135: un monto
negativo se rechaza con 400 y no se guarda», «RF-135: una tarifa de cero es válida», «B-29 criterio
2 · RF-135: editar con un monto negativo responde 400»); `TarifaControllerTest` («B-29 criterio 2 ·
RF-135: POST con monto negativo responde 400 VALIDACION y no llega al servicio», «RF-135: POST con
más de dos decimales o sin concepto responde 400 VALIDACION», «B-29 criterio 2 · RF-135: PATCH con
monto negativo responde 400 VALIDACION»); `TarifasIT` («B-29 criterio 2 · RF-135: un monto negativo
se rechaza y no se guarda»); `tarifas.test.ts` («B-29 criterio 2 · RF-135: rechaza un monto
negativo», «RF-135: rechaza tres decimales, letras y más de diez dígitos enteros»);
`PestanaTarifas.test.tsx` («B-29 criterio 2 · RF-135: un monto negativo se explica en la celda y no
se envía»).

### CA-3 · En una temporada `CERRADA` las tarifas son de solo lectura (`409 RF-003`)

> Dada una temporada `CERRADA`, cuando se intenta registrar o editar una tarifa, entonces se
> rechaza con `409 RF-003` y la pestaña queda de solo lectura.

**Pasos**

1. En **Temporada**, elegir «Temporada prueba H-13 · Cerrada».
2. Con el ayudante de consola, intentar registrar y editar:

   ```js
   api('POST', '/tarifas', { temporadaId: CERRADA, categoriaId: LIBRE, concepto: 'CANCHA', monto: 100 })
   api('PATCH', '/tarifas/' + TARIFA_CERRADA, { monto: 300 })
   ```

3. Recargar la pestaña y comprobar en la base que la tarifa de H-13 no cambió.

**Resultado esperado**

- Paso 1: insignias **Cerrada** y **No oficial** y el aviso «La temporada «Temporada prueba H-13»
  está cerrada: sus tarifas son de solo lectura.». Libre Demostración muestra Arbitraje **$250.00**
  y Cancha «Sin tarifa» como texto, **sin** campos de captura ni botón **Guardar**.
- Paso 2: las dos respuestas son `409` con `codigo: "RF-003"` y el mismo mensaje del aviso.
- Paso 3: H-13 sigue con una sola tarifa, Arbitraje de Libre en 250.00, y no hay auditoría de esos
  intentos.

**Resultado obtenido:** **Aprobado.** «Temporada prueba H-13 · Cerrada» mostró las insignias
**Cerrada** y **No oficial**, el aviso «La temporada «Temporada prueba H-13» está cerrada: sus
tarifas son de solo lectura.» y Libre Demostración con Arbitraje **$250.00** y Cancha «Sin tarifa»
como texto, sin campos ni botón **Guardar**. El `POST` de Cancha y el `PATCH` de la tarifa sembrada
respondieron **409 (Conflict)** con `title: Conflicto con el estado actual`,
`type: https://siglim.mx/errores/rf-003` y el mismo mensaje del aviso. La temporada cerrada sigue con
su única tarifa en 250.00 y sin auditoría de esos intentos.

**Evidencia:** `CA3-solo-lectura.png`, `CA3-consola-409.png`, `base-despues.txt`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («B-29 criterio 3 · RF-003: en una
temporada cerrada no se registran tarifas», «B-29 criterio 3 · RF-003: en una temporada cerrada la
tarifa es de solo lectura»); `TarifasIT` («B-29 criterio 3 · RF-003: en una temporada cerrada las
tarifas son de solo lectura»); `PestanaTarifas.test.tsx` («B-29 criterio 3 · RF-003: una temporada
cerrada se muestra de solo lectura», «RF-003: si la temporada se cierra al guardar, explica el
bloqueo y relee el contexto»).

### CA-4 · Las altas y los cambios de monto se auditan, y las tarifas se rotulan «No oficial»

> Cada alta y cada cambio de monto quedan en la auditoría (`REGISTRAR_TARIFA`, `EDITAR_TARIFA`, con
> el monto anterior y el nuevo), y las tarifas se rotulan «No oficial» (`esDemo = true`, PEN-042,
> DEC-66).

**Pasos**

1. Como **Tesorero**, en «Temporada 2026–2027», cambiar Arbitraje de **Libre Demostración** de
   `350.00` a `400.00` y pulsar **Guardar**.
2. Con el ayudante de consola, consultar las tarifas: `api('GET', '/tarifas?temporadaId=' + TEMP)`.
3. Como **Administrador general**, abrir **Administración → Auditoría** (F-14,
   `/app/administracion/auditoria`) y filtrar por el módulo **M-13**.
4. Expandir el evento `EDITAR_TARIFA`.
5. Contar en la base los eventos de tarifas.

**Resultado esperado**

- Paso 1: «Tarifas de «Libre Demostración» guardadas: arbitraje $400.00.»
- Paso 2: `200` con 4 tarifas, todas con `esDemo: true`: Libre 400.00 y 200.00, Veteranos 300.00 y
  0.00. La pestaña muestra la insignia **No oficial** en todas las temporadas (CA-1 y CA-3).
- Paso 3: **4** `REGISTRAR_TARIFA` (Libre Arbitraje 350.00, Libre Cancha 200.00, Veteranos
  Arbitraje 300.00, Veteranos Cancha 0.00) y **1** `EDITAR_TARIFA`, todos del Tesorero, módulo
  M-13 y entidad `tarifas`.
- Paso 4: detalle con `concepto: ARBITRAJE`, `montoAnterior: 350.00`, `monto: 400.00` y
  `esDemo: true`, sin datos personales.
- Paso 5: exactamente 4 `REGISTRAR_TARIFA` y 1 `EDITAR_TARIFA`: ningún evento por los intentos
  rechazados (CA-1, CA-2, CA-3, V-1, V-2) ni por el mismo monto (V-3).

**Resultado obtenido:** **Aprobado.** El cambio de Libre · Arbitraje mostró «Tarifas de «Libre
Demostración» guardadas: arbitraje $400.00.» y la tabla quedó en 400.00 / 200.00 y 300.00 / 0.00. El
`GET /tarifas?temporadaId=…` respondió **200** con 4 tarifas, todas con `esDemo: true`. En F-14,
filtrando por **M-13 · Tesorería y cobros**, aparecieron **4 `REGISTRAR_TARIFA`** (16:59:22,
16:59:23, 16:59:43 y 17:05:08) y **1 `EDITAR_TARIFA`** (17:17:29), todos de «Lio tesorero» y con
entidad `tarifas`; el resto de los 11 eventos de M-13 son cobros del 7 de noviembre. El detalle del
`EDITAR_TARIFA` muestra `concepto: ARBITRAJE`, `montoAnterior: 350`, `monto: 400`, `esDemo: true`,
`categoriaId` y `temporadaId`, sin datos personales. En la base hay exactamente esos 5 eventos
(ids 138 a 142) y ninguno por los rechazos ni por el mismo monto.

**Evidencia:** `CA4-edicion.png`, `CA4-consola-esdemo.png`, `CA4-auditoria.png`,
`CA4-detalle-edicion.png`, `auditoria-tarifas.txt`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («RF-135, RF-093: registra la tarifa
[DEMO] con monto BigDecimal y la audita», «RF-135, RF-093: cambia el monto y audita el anterior y
el nuevo»); `TarifasIT` («B-29 criterios 1 y 4 · RF-135: el TESORERO registra una tarifa única por
temporada, categoría y concepto, auditada»); `TarifaControllerTest` («RF-135: GET lista las tarifas
de la temporada con esDemo y el monto con dos decimales», «RF-135: PATCH responde 200 con la
tarifa actualizada»); `PestanaTarifas.test.tsx` («B-29.2: abre en la temporada activa con la tabla
categoría × concepto y la insignia «No oficial»»).

### V-1 · Sin `tesoreria.tarifa:EDITAR` no hay pestaña y la API responde `403` (VAL-015)

**Pasos**

1. Como **Directiva**, abrir `/app/tesoreria`.
2. Preparar el ayudante de consola con el token de la Directiva y ejecutar:

   ```js
   api('GET', '/tarifas/contexto')
   api('POST', '/tarifas', { temporadaId: TEMP, categoriaId: LIBRE, concepto: 'ARBITRAJE', monto: 1 })
   ```

**Resultado esperado**

- Paso 1: entra a tesorería (tiene `tesoreria.cobro:CONSULTAR`), pero **no** aparece la pestaña
  **Tarifas**.
- Paso 2: las dos respuestas son `403` con `codigo: "VAL-015"`.

**Resultado obtenido:** **Aprobado.** La **Directiva** vio F-33 con sus cobros y las pestañas
**Cobros** y **Adeudos por club**, **sin** la pestaña **Tarifas**. `GET /tarifas/contexto` y
`POST /tarifas` respondieron **403 (Forbidden)** con `title: Acción no permitida`,
`type: https://siglim.mx/errores/val-015` y «Tu cuenta no tiene el permiso necesario…». No se
registró auditoría.

**Evidencia:** `V1-directiva-sin-pestana.png`, `V1-consola-403.png`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («VAL-015: sin el permiso no se consulta
el contexto de tarifas», «VAL-015: sin el permiso no se registra la tarifa»); `TarifasIT`
(«VAL-015: un rol sin tesoreria.tarifa:EDITAR no consulta ni registra tarifas»);
`TarifaControllerTest` (arnés «RNF-003: seguridad de …» de los cuatro endpoints);
`PantallaTesoreria.test.tsx` («B-29.2: solo aparece con tesoreria.tarifa:EDITAR»).

### V-2 · Una categoría que no participa en la temporada responde `422 REFERENCIA_INVALIDA`

En el compartido las dos categorías del catálogo participan en la «Temporada 2026–2027», así que no
hay una categoría ajena real; la pantalla tampoco la ofrece, porque arma la tabla con
`/tarifas/contexto`. Se envía un id que no participa, que recorre la misma rama del servicio
(`categoriaEnTemporada` falso).

**Pasos**

1. Como **Tesorero**, con el ayudante de consola:
   `api('POST', '/tarifas', { temporadaId: TEMP, categoriaId: crypto.randomUUID(), concepto: 'ARBITRAJE', monto: 100 })`.

**Resultado esperado:** `422` con `codigo: "REFERENCIA_INVALIDA"` y «La categoría indicada no
participa en la temporada «Temporada 2026–2027».»

**Resultado obtenido:** **Aprobado.** Respondió **422 (Unprocessable Entity)** con
`type: https://siglim.mx/errores/referencia-invalida` y «La categoría indicada no participa en la
temporada…». No se creó ninguna tarifa.

**Evidencia:** `V2-consola-422.png`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («RF-135: temporada inexistente o
categoría ajena a la temporada responden 422 REFERENCIA_INVALIDA»); `TarifasIT` («RF-135: una
categoría que no participa en la temporada responde 422 REFERENCIA_INVALIDA»).

### V-3 · Guardar el mismo monto no audita nada

**Pasos**

1. En la pantalla, sin cambiar ningún monto de **Libre Demostración**, revisar el botón **Guardar**.
2. Con el ayudante de consola, después del paso 1 del CA-4 (si la página se recargó, primero se
   vuelve a pegar el ayudante y se recalcula `libreArb` como en el paso 3 del CA-2):

   ```js
   const lista = await api('GET', '/tarifas?temporadaId=' + TEMP)
   const libreArb = lista.find((t) => t.categoria.id === LIBRE && t.concepto === 'ARBITRAJE').id
   api('PATCH', '/tarifas/' + libreArb, { monto: 400 })
   ```

**Resultado esperado**

- Paso 1: **Guardar** está deshabilitado; la pantalla solo envía lo que cambió.
- Paso 2: `200` con la tarifa en 400.00; no se agrega ningún `EDITAR_TARIFA` (se comprueba en el
  paso 5 del CA-4).

**Resultado obtenido:** **Aprobado.** Después de guardar, **Guardar** queda deshabilitado en las
filas sin cambios (`CA4-edicion.png`, `CA2-tarifa-cero.png`). El `PATCH` con el mismo monto respondió
**200** con la tarifa en 400 y `esDemo: true`; el último `EDITAR_TARIFA` sigue siendo el de las
17:17:29 (id 142), así que no se auditó.

**Evidencia:** `CA4-consola-esdemo.png` (el mismo comando hace el `GET` y el `PATCH`), `auditoria-tarifas.txt`

**Pruebas automáticas relacionadas:** `TarifaServiceTest` («RF-135: el mismo monto no cambia nada
ni se audita»).

## 4. Resumen

| Escenario | Resultado |
| --- | --- |
| CA-1 Una tarifa por temporada, categoría y concepto; la repetida responde `409 DUPLICADO` | Aprobado |
| CA-2 Negativo o con más de dos decimales → `400 VALIDACION`; 0 es válido | Aprobado |
| CA-3 Temporada `CERRADA` de solo lectura (`409 RF-003`) | Aprobado |
| CA-4 Auditoría de altas y cambios, y rótulo «No oficial» | Aprobado |
| V-1 Sin `tesoreria.tarifa:EDITAR`: sin pestaña y `403 VAL-015` | Aprobado |
| V-2 Categoría ajena a la temporada → `422 REFERENCIA_INVALIDA` | Aprobado |
| V-3 El mismo monto no se audita | Aprobado |

Las pruebas automáticas citadas en cada escenario ya corrieron en los PR de B-29.1 (#150) y B-29.2
(#153), fusionados en `develop`. Esta subtarea no cambia código, así que no se volvió a ejecutar
`./mvnw verify` ni la verificación del frontend.

## 5. Defectos encontrados

| Clave | Severidad | Descripción | Estado |
| --- | --- | --- | --- |
| — | — | Ninguno | — |

## 6. Observaciones (no son defectos)

1. **La pestaña abre en una temporada cerrada.** Sin temporada `ACTIVA`, `temporadaInicial` elige la
   de `fecha_inicio` más reciente, que hoy es «Temporada prueba H-13» (`CERRADA`). El Tesorero tiene
   que cambiar a «Temporada 2026–2027» para capturar. Es lo que se diseñó (B-29.2) y desaparece
   cuando haya una temporada activa.
2. **Los mensajes de campo de los `400` van en `errores`.** El `detail` es el genérico «Revisa los
   campos marcados.»; el texto «El monto admite hasta dos decimales» o «El monto no puede ser
   negativo» llega en la propiedad `errores`, que la pantalla usa (`mensajeDe('monto')`). Las
   capturas de consola no la expandieron.
3. **Montos sin ceros finales en el detalle de la auditoría.** El JSONB guarda `350`, `400` y `0`;
   la columna, la API y la pantalla conservan dos decimales (350.00, 0.00). Igual que en la
   observación 8 de B-31.
4. **Tarifa sembrada por SQL en H-13.** Se insertó solo para probar la edición bloqueada del CA-3;
   no tiene auditoría y se queda como dato de demostración de la temporada cerrada.
5. **La semilla [DEMO] de DEC-66 no está en el compartido.** Antes de la prueba la tabla estaba
   vacía; las tarifas actuales son las capturadas en esta corrida, rotuladas «No oficial»
   (PEN-042).
6. **V-2 con un id aleatorio.** No existe una categoría ajena real en la temporada abierta; el id
   generado con `crypto.randomUUID()` recorre la misma rama de `TarifaService.crear`.

## 7. Conclusión

B-29 cumple sus cuatro criterios de aceptación sobre `develop` en `5911a87`, con el frontend, el
backend y el Supabase compartido. El Tesorero registra una tarifa por temporada, categoría y
concepto, y la repetida se rechaza con `409 DUPLICADO`. Los montos negativos o con más de dos
decimales se rechazan en la pantalla y en la API (`400 VALIDACION`), y el cero es válido. Una
temporada cerrada es de solo lectura en la pantalla y en la API (`409 RF-003`). Cada alta y cada
cambio quedan auditados con el monto anterior y el nuevo, y todas las tarifas se rotulan «No
oficial». Los casos adicionales también se cumplen: sin el permiso no hay pestaña ni acceso
(`403 VAL-015`), una categoría ajena responde `422 REFERENCIA_INVALIDA` y el mismo monto no se
audita. No se encontraron defectos.

La subtarea **B-29.3 queda terminada** y **B-29 puede pasar a Finalizado** al fusionar este
documento: B-29.1 (#150) y B-29.2 (#153) ya están en `develop`. Las cuatro tarifas de la «Temporada
2026–2027» (Libre 400.00 / 200.00, Veteranos 300.00 / 0.00) se conservan para B-30.2 (SCRUM-446).

## Orden de ejecución recomendado

Preparación → CA-1 → CA-2 → CA-3 → V-2 → CA-4 (pasos 1 y 2) → V-3 → CA-4 (pasos 3 a 5, como
Administrador general) → V-1 (como Directiva).

Así se cambia de sesión solo dos veces. El CA-4 se revisa en la auditoría al final para contar
todos los eventos de la corrida, incluidos los que no deben existir.
