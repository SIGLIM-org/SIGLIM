# Prueba de aceptación · H-05 Configurar categorías, grupos y parámetros deportivos versionados

| Campo | Valor |
| --- | --- |
| Historia | H-05 · subtarea H-05.4 (prueba de aceptación) |
| Requisitos | RF-004, RF-005, RF-006, RF-050, RF-093, RF-099, RF-100, RN-021, RNF-005, UI-004, DEC-25, DEC-26, DEC-27 |
| Pantallas | F-11 (estructura y parámetros de la temporada) |
| Versión probada | `develop` en `d188542` (incluye H-05.1 #65, H-05.2 #68 y H-05.3 #70); migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-05 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (4 de 4 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 18.6 local en un clúster efímero (puerto 55432), con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas desde cero |
| Autenticación | Servidor local que publica un JWKS ES256 y responde a los endpoints de Supabase Auth que usa el frontend; el backend validó la firma, el emisor `<SUPABASE_URL>/auth/v1`, la audiencia `authenticated` y el `sub` UUID en cada petición |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, OpenJDK 25.0.5 (el proyecto compila para Java 21) y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5 y proxy hacia el backend) |
| Navegador | Chromium sin interfaz (revisión 1248, Playwright 1.65), ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para la
corrida: no se alteró el proyecto compartido de Supabase. El equipo de pruebas no tenía Docker
disponible, así que las pruebas de integración con Testcontainers (`ParametrosIT`,
`CategoriasGruposIT`) no se ejecutaron en esta corrida; la verificación manual sí usó PostgreSQL
real con las mismas migraciones.

**Limitación de la autenticación.** El entorno local simula Supabase Auth con un JWKS propio porque
la prueba no depende del proveedor de identidad: el backend validó los tokens con el mismo
`DecodificadorJwtConfig` de producción. La diferencia con el proyecto real es solo quién emite el
token; H-05 no usa invitaciones, correo ni sesiones de Supabase.

## 2. Cuenta y datos de prueba

La cuenta se identifica por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `temporadas.temporada:CONSULTAR`, `temporadas.temporada:EDITAR`, `temporadas.parametros:EDITAR` (LIGA) |

Datos creados durante la corrida:

- Temporada **«Temporada 2026-2027 (aceptación H-05)»**, id
  `778a2bd5-c1a6-40df-8ebe-89e2c566c2b2`, del **1 de agosto de 2026** al **31 de mayo de 2027**, en
  estado `CONFIGURACION`.
- Categoría **«Primera Fuerza (aceptación H-05)»** (tipo Libre), id
  `4ec5a98d-d708-4f7f-b7b4-2e617a4adc5f`, tope de su rama y asignada a la temporada.
- Categoría **«Segunda Fuerza (aceptación H-05)»** (tipo Libre), id
  `89aa8284-ccd8-41bb-9756-240f50bcf8af`, con **Primera Fuerza** como categoría superior inmediata y
  asignada a la temporada.
- Versión **1** de parámetros: 3 puntos por victoria, 1 por empate, 0 por derrota, plazo de la
  cédula de 48 h y los criterios de desempate predeterminados, con `esDemo = true`. Motivo:
  «Versión inicial acordada para la aceptación H-05».
- Versión **2** de parámetros: 4 puntos por victoria, plazo de la cédula de 36 h y el orden de
  desempate cambiado (Goles a favor antes de Diferencia de goles), con `esDemo = true`. Motivo:
  «Ajuste de puntos, plazo y desempates para la temporada».

## 3. Escenarios

### CA-1 · La jerarquía de categorías se guarda sin cambios de código

> Dada una categoría, cuando se define su categoría superior inmediata, entonces la jerarquía se
> guarda sin cambios de código (RF-099).

**Pasos**

1. Iniciar sesión como Administrador y abrir F-11 de la temporada
   (`/app/administracion/temporadas/{id}/estructura?pestana=categorias`).
2. Pulsar «Nueva categoría» y crear «Primera Fuerza (aceptación H-05)» con tipo **Libre** y sin
   categoría superior (tope de su rama).
3. Crear «Segunda Fuerza (aceptación H-05)» con tipo **Libre** y **Primera Fuerza** como categoría
   superior.
4. Asignar las dos categorías a la temporada con el botón «Asignar».
5. Recargar la página y comprobar que la columna «Categoría superior» sigue mostrando la jerarquía.
6. Consultar el catálogo por la API (`GET /api/v1/categorias`) y la base directamente.

**Resultado esperado**

- La categoría superior se elige en la pantalla, se guarda y sobrevive a recargar.
- La API y la base conservan la referencia a la categoría superior; no hay ninguna regla fijada en
  el código para esta jerarquía.
- Ambas categorías quedan asignadas a la temporada.

**Resultado obtenido:** **Aprobado.** La tabla de F-11 mostró «Primera Fuerza (aceptación H-05)»
como **tope de su rama** y «Segunda Fuerza (aceptación H-05)» con **Primera Fuerza (aceptación
H-05)** en «Categoría superior»; ambas aparecieron como **ACTIVA** y **ASIGNADA**, y la jerarquía
siguió igual después de recargar. La API devolvió la segunda categoría con
`categoriaSuperior: { id: 4ec5a98d-…, nombre: "Primera Fuerza (aceptación H-05)" }` y la consulta a
la base confirmó la misma referencia y las dos filas de `categorias_temporada`.

**Evidencia:** [`CA1-jerarquia.png`](evidencias/H-05/CA1-jerarquia.png),
[`CA1-categorias.json`](evidencias/H-05/CA1-categorias.json) (respuesta de la API) y
[`CA1-jerarquia.txt`](evidencias/H-05/CA1-jerarquia.txt) (consulta directa a la base)

**Pruebas automáticas relacionadas**

- `CategoriaServiceTest` · «RF-099: asigna una superior válida y audita EDITAR_CATEGORIA con los
  campos cambiados», «RF-099, D2: A → B → A forma un ciclo y responde 409 RF-099» y «RF-099, D2:
  una categoría no puede ser su propia superior (409 RF-099)».
- `CategoriaControllerTest` · «RF-099: GET lista sin paginar, con la superior anidada y omitida si
  no tiene».
- `CategoriasGruposIT` · «RF-004, RF-005, RF-099: crear categoría, asignarla y crear un grupo
  cumplen la lista de activación» y «RF-099, D2: A → B → C → A se rechaza con 409 RF-099 y la
  jerarquía no cambia».
- `PestanaCategorias.test.tsx` · «RF-099: crear con superior hace el POST correcto» y «RF-004:
  «Asignar» hace POST temporadas/{id}/categorias».

### CA-2 · Guardar parámetros crea una versión nueva y conserva la anterior

> Dados parámetros vigentes, cuando se guardan cambios, entonces se crea una versión nueva y la
> anterior se conserva (RF-006).

**Pasos**

1. En la pestaña **Parámetros** de F-11, guardar la **versión 1** con los valores predeterminados y
   el motivo indicado. El modal pide el motivo (RN-021).
2. Cambiar «Puntos por victoria» de 3 a 4 y «Plazo de validación de la cédula» de 48 a 36 horas.
3. Pulsar «Guardar como nueva versión» y confirmar con el motivo. La pantalla avisa que la versión
   1 se conserva y que la tabla de posiciones se recalculará.
4. Revisar el historial: versión 2 vigente y versión 1 conservada con sus «Valores registrados» y
   los campos que cambiaron.
5. Abrir «Ver instantánea» de la versión 1.
6. Consultar por la API la versión vigente, la versión 1 y el historial; y la base directamente.

**Resultado esperado**

- El segundo guardado crea la versión 2 y no modifica la 1.
- El historial muestra ambas versiones, de la mayor a la menor, con el motivo, el autor, la fecha y
  los campos cambiados.
- La instantánea de la versión 1 conserva 3 puntos y 48 h.
- La API y la base devuelven las dos versiones; no se ofrece editar ni borrar una versión.

**Resultado obtenido:** **Aprobado.** La versión 1 se registró con 3 puntos, 48 h y los criterios
predeterminados. Al guardar la versión 2, la pantalla avisó «Se guardó la versión 2.» y el historial
mostró **Versión 2 · VIGENTE** (4 puntos, 36 h, motivo nuevo) y **Versión 1** con su motivo, sus
«Valores registrados» y la leyenda «Cambió: Puntos por victoria, Criterios de desempate, Plazo de
validación de la cédula». La instantánea de la versión 1 mostró 3 puntos, 1 por empate, 0 por
derrota, 48 h y el orden original de desempates. La API devolvió `vigente.version = 2` y el
historial `[2, 1]` con los mismos `camposCambiados`; la base conservó dos filas
(`1 | 3 | 48:00:00` y `2 | 4 | 36:00:00`) y la auditoría registró `REGISTRAR_PARAMETROS` para ambas
versiones, con `versionAnterior: 1` en la segunda. Las versiones no tienen `PATCH` ni `DELETE` (la
prueba automática recibe `405`).

**Evidencia:** [`CA2-historial-versiones.png`](evidencias/H-05/CA2-historial-versiones.png),
[`CA2-instantanea-version-1.png`](evidencias/H-05/CA2-instantanea-version-1.png),
[`CA2-parametros.json`](evidencias/H-05/CA2-parametros.json),
[`CA2-version-1.json`](evidencias/H-05/CA2-version-1.json),
[`CA2-versiones.txt`](evidencias/H-05/CA2-versiones.txt) y
[`CA2-auditoria.txt`](evidencias/H-05/CA2-auditoria.txt) (consultas directas a la base)

**Pruebas automáticas relacionadas**

- `ParametrosServiceTest` · «RF-006, D10: una versión nueva audita solo los campos que cambiaron
  respecto de la vigente», «RF-100, D2: la primera versión sin categorías se registra con esDemo =
  true por omisión» y «D2, PEN-021: con esDemo = false los valores son oficiales y camposDemo va
  vacío».
- `ParametrosControllerTest` · «RF-006, RF-100: POST responde 201 con Location a la versión y el
  cuerpo completo», «D8: GET con versiones omite predeterminados y trae el historial con
  camposCambiados», «RF-006: GET /{version} responde la versión pedida» y «RF-006: las versiones no
  se editan ni se borran (405)».
- `ParametrosIT` · «RF-006, RF-100: cada POST crea una versión nueva, la anterior se conserva y el
  historial compara versiones».
- `PestanaParametros.test.tsx` · «RF-006: guardar la versión 1 envía el juego completo con motivo y
  esDemo» y «RF-006: el historial lista camposCambiados con etiquetas y abre la instantánea».

### CA-3 · Los valores de demostración se muestran con la insignia «No oficial»

> Los valores de demostración se muestran con la insignia «No oficial».

**Pasos**

1. Antes de guardar la primera versión, revisar el formulario con los valores predeterminados
   ([DEMO]): puntos, desempates, marcador por default, plazo de la cédula, bloque de cancha,
   liguilla y ascensos.
2. Comprobar la insignia «No oficial» junto a cada campo [DEMO] y en el encabezado de la versión
   guardada.
3. Consultar la respuesta de la API: `esDemo` y el arreglo `camposDemo`.

**Resultado esperado**

- Mientras los valores no estén confirmados por la Liga, la interfaz rotula «No oficial» en cada
  campo [DEMO] y en el encabezado de la versión.
- La API declara `esDemo: true` y enumera en `camposDemo` los campos que no son oficiales.

**Resultado obtenido:** **Aprobado.** El formulario mostró la insignia **NO OFICIAL** en Puntos por
victoria, Puntos por empate, Puntos por derrota, Goles del ganador por default, Goles del perdedor
por default, Plazo de validación de la cédula, Criterios de desempate, Bloque de cancha, los tres
campos de liguilla y los dos de ascensos/descensos; la Duración del juego (90 min, valor estándar
de DEC-25) no se rotula. Tras guardar, el encabezado mostró «Versión 1 vigente · desde 5 nov 2026 ·
NO OFICIAL» y el historial la insignia en cada versión. La API devolvió `esDemo: true` y
`camposDemo` con los 13 campos [DEMO] (puntos, criterios, marcador, plazo, bloque, liguilla y
ascensos/descensos).

**Evidencia:** [`CA3-formulario-no-oficial.png`](evidencias/H-05/CA3-formulario-no-oficial.png),
[`CA3-version-1-no-oficial.png`](evidencias/H-05/CA3-version-1-no-oficial.png),
[`CA3-encabezado-no-oficial.png`](evidencias/H-05/CA3-encabezado-no-oficial.png) y
[`CA2-parametros.json`](evidencias/H-05/CA2-parametros.json) (`esDemo` y `camposDemo`)

**Pruebas automáticas relacionadas**

- `ParametrosServiceTest` · «RF-100, D2: la primera versión sin categorías se registra con esDemo =
  true por omisión» y «D2, PEN-021: con esDemo = false los valores son oficiales y camposDemo va
  vacío».
- `ParametrosControllerTest` · «RF-006, RF-100: POST responde 201 con Location a la versión y el
  cuerpo completo».
- `PestanaParametros.test.tsx` · «RF-100: sin vigente prellena los predeterminados, avisa y rotula
  «No oficial»» y «PEN-021: el interruptor de oficiales oculta las insignias y envía esDemo:false».

### CA-4 · Los desempates se ordenan y nunca incluyen el nombre del equipo

> Los criterios de desempate se ordenan y nunca incluyen el nombre del equipo (RF-050).

**Pasos**

1. En la sección «Criterios de desempate», comprobar que **Puntos** está fijo primero y que
   **Sorteo**, si aparece, va al final.
2. Pulsar «Subir Goles a favor» para dejarlo antes de «Diferencia de goles» y guardar la versión 2.
3. Revisar la lista numerada y los «Criterios disponibles».
4. Consultar por la API los criterios de la versión 2 y la base.
5. Enviar por la API un cuerpo con `criteriosDesempate: ["PUNTOS", "NOMBRE_EQUIPO"]` para comprobar
   que el servidor rechaza un criterio por nombre de equipo.

**Resultado esperado**

- El orden se guarda tal como se configuró y se conserva en la versión nueva; Puntos sigue primero y
  Sorteo al final.
- La lista de criterios es solo la del catálogo (puntos, diferencia de goles, goles a favor, menos
  goles en contra, partidos ganados, enfrentamiento directo, menos expulsiones y sorteo): no existe
  una opción por nombre de equipo.
- Un criterio desconocido como `NOMBRE_EQUIPO` responde `400 VALIDACION` y no crea versión.

**Resultado obtenido:** **Aprobado.** La lista quedó en el orden **1 Puntos, 2 Goles a favor, 3
Diferencia de goles, 4 Enfrentamiento directo, 5 Menos expulsiones, 6 Sorteo**; «Puntos» mostró
«Siempre primero» y «Sorteo» «Último recurso, siempre va al final», y en «Criterios disponibles»
solo quedaron «Menos goles en contra» y «Partidos ganados». La API y la base guardaron exactamente
`{PUNTOS,GOLES_FAVOR,DIFERENCIA_GOLES,ENFRENTAMIENTO_DIRECTO,MENOS_EXPULSIONES,SORTEO}`. La petición
con `NOMBRE_EQUIPO` respondió `400` con `codigo: "VALIDACION"` y no creó una tercera versión (la
base siguió con dos filas). En toda la pantalla no aparece ninguna opción de desempate por nombre
de equipo.

**Evidencia:** [`CA4-orden-criterios.png`](evidencias/H-05/CA4-orden-criterios.png),
[`CA4-criterios-disponibles.png`](evidencias/H-05/CA4-criterios-disponibles.png),
[`CA4-criterio-nombre-equipo-400.json`](evidencias/H-05/CA4-criterio-nombre-equipo-400.json),
[`CA2-parametros.json`](evidencias/H-05/CA2-parametros.json) y
[`CA2-versiones.txt`](evidencias/H-05/CA2-versiones.txt)

**Pruebas automáticas relacionadas**

- `ParametrosServiceTest` · «RF-050, D6: los criterios deben empezar en PUNTOS», «RF-050, D6: el
  SORTEO solo puede ser el último criterio», «RF-050, D6: un criterio repetido responde 400
  VALIDACION» y «RF-050, D6: sin criterios responde 400 VALIDACION».
- `ParametrosControllerTest` · «RF-050: un criterio de desempate desconocido, como el nombre del
  equipo, responde 400 VALIDACION».
- `PestanaParametros.test.tsx` · «RF-050: subir un desempate cambia el orden enviado y PUNTOS sigue
  primero».
- `desempates.test.ts` · «RF-050: ningún criterio sube por encima de PUNTOS», «RF-050: SORTEO queda
  anclado al final» y «RF-050: criteriosDisponibles lista los que faltan en el orden del catálogo».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 La jerarquía de categorías se guarda sin cambios de código | Aprobado |
| CA-2 Guardar parámetros crea una versión nueva y conserva la anterior | Aprobado |
| CA-3 Los valores de demostración se muestran con la insignia «No oficial» | Aprobado |
| CA-4 Los desempates se ordenan y nunca incluyen el nombre del equipo | Aprobado |

Pruebas automáticas de la misma versión: backend
`./mvnw test -Dtest=ParametrosServiceTest,ParametrosControllerTest` con **56/56** pruebas (33 de
servicio y 23 de controlador, sin fallos); frontend `npx vitest run src/features/temporadas` con
**88/88** pruebas en 8 archivos. Las pruebas `*IT` que usan Testcontainers se omitieron por falta de
Docker; la corrida manual cubrió PostgreSQL real con las mismas migraciones.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-05 ni una regresión de
RF-004, RF-006, RF-050, RF-099 o RF-100.

## 6. Observaciones

1. **La base fue efímera.** El clúster local se creó desde cero para la corrida y no se tocó el
   proyecto compartido de Supabase (DEC-52).
2. **La autenticación se simuló en local.** Un servidor propio publicó el JWKS ES256 y respondió a
   los endpoints de GoTrue que usa el frontend; el backend validó los tokens con el decodificador de
   producción. H-05 no depende del proveedor de identidad, pero una corrida completa contra Supabase
   Auth real sigue siendo parte de la validación de la historia.
3. **La barra de acciones del formulario es fija al pie.** Al desplazarse por el formulario, la
   barra «Guardar como nueva versión» cubre temporalmente el inicio de la sección siguiente (por
   ejemplo, el título de «Criterios de desempate»). No impide guardar ni cambia el resultado, pero
   conviene revisarlo en la pantalla de H-05.3.
4. **El rechazo de un criterio desconocido no nombra el campo.** La API responde `400 VALIDACION`
   con el detalle genérico «La solicitud no es válida. Revisa los datos enviados.»; la interfaz
   nunca ofrece ese valor, así que el criterio se cumple. Es una mejora de mensaje para el contrato
   de errores, no un defecto de H-05.
5. Las capturas no muestran correos, tokens ni contraseñas; la cuenta se identifica por su rol.

## 7. Conclusión

La historia **H-05 cumple sus cuatro criterios de aceptación** sobre `develop` en `d188542` con
frontend, backend y PostgreSQL reales: la jerarquía de categorías se configura y persiste sin
cambios de código, guardar parámetros crea una versión nueva y conserva la anterior con su
historial y auditoría, los valores [DEMO] se rotulan «No oficial» y los criterios de desempate se
ordenan sin admitir el nombre del equipo. H-05 puede pasar a «Finalizado» una vez que el PR de
H-05.4 se fusione.
