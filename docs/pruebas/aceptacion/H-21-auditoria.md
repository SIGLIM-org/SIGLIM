# Prueba de aceptación · H-21 Consultar la auditoría con filtros

| Campo | Valor |
| --- | --- |
| Historia | H-21 · subtarea H-21.3 (prueba de aceptación) |
| Requisitos | RF-134, UI-022, RNF-015, RNF-012 |
| Pantallas | F-14 (auditoría) |
| Versión probada | `develop` en `bb6fadd`, con las migraciones `20261020090000`, `20261022143000` y `20261027140000` |
| Fecha | 2026-10-31 |
| Ejecutó | Agente de IA (OpenCode) en el entorno local del equipo |
| Resultado global | **Aprobado** (3 de 3 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos y autenticación | Supabase local (`supabase start`): PostgreSQL 17.11 y GoTrue, con las tres migraciones del repositorio aplicadas |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, conectado a la base local y a Supabase Auth local |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium 156 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |
| Automatización | `./mvnw verify` (backend) y `npm test`, `npm run lint`, `npm run build` (frontend) sobre la misma versión |

La prueba se ejecutó contra un **proyecto local efímero**, no contra el proyecto compartido de
Supabase: así no se altera la base que usa el equipo y se puede reproducir desde cero.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Presidente** | Presidente del Comité Ejecutivo · Liga | `auditoria.registro:CONSULTAR` (LIGA) |
| **Administrador** | Administrador general · Liga | `auditoria.registro:CONSULTAR` (LIGA) |
| **Capturista** | Capturista · Liga | Sin `auditoria.registro:CONSULTAR` (se usa para la comprobación de denegación) |

Para poder observar filtros y paginación con volumen representativo se cargaron **120 registros
sintéticos** de auditoría, además del registro real del alta del Administrador general:

- 60 registros del módulo **M-06 · Canchas y recursos**, acción `BLOQUEAR_CANCHA`, entidad
  `disponibilidad_canchas`, del Administrador.
- 60 registros del módulo **M-08 · Partidos, cédula y resultados**, acción `APROBAR_CEDULA`, entidad
  `cedulas`, del Presidente.
- Fechas del 20 de octubre de 2026, con motivo y `detalle` JSON (incluido un motivo largo y un
  objeto anidado) para comprobar la fila expandible.

Los datos se cargaron en la base local, no en una migración ni en el proyecto compartido.

## 3. Escenarios

### CA-1 · Listado paginado con filtros

> Dados filtros de usuario, módulo, acción y fecha, cuando se consulta, entonces se listan los
> registros paginados.

**Pasos**

1. Iniciar sesión como **Presidente** y abrir `/app/administracion/auditoria`.
2. Observar el listado sin filtros: total de eventos, cantidad por página y paginación.
3. Elegir **Usuario** = «Ana Administradora», **Módulo** = «M-06 · Canchas y recursos», **Acción** =
   «BLOQUEAR_CANCHA» y **Desde** = **Hasta** = `2026-10-20`.
4. Pulsar «Aplicar filtros» y revisar el total y la primera página.
5. Pulsar «Siguiente» y comprobar que los filtros se conservan.

**Resultado esperado**

- Sin filtros se ven **121 eventos** en páginas de **50**: 3 páginas, del más reciente al más antiguo.
- Con los cuatro filtros se ven **60 eventos** (los 60 registros de M-06 del Administrador en esa
  fecha), en 2 páginas de 50 y 10.
- La página 2 mantiene los filtros y muestra «Página 2 de 2».
- Los filtros quedan en la URL y se restauran al recargar o usar «Atrás».

**Resultado obtenido:** **Aprobado.** El listado inicial mostró 121 eventos y «Página 1 de 3». Al
aplicar los cuatro filtros mostró 60 eventos y «Página 1 de 2»; «Siguiente» llevó a «Página 2 de 2»
sin perder los filtros. La URL conservó `perfilId`, `modulo`, `accion`, `desde`, `hasta`, `pagina`,
`tamano` y `orden`.

**Evidencia:** [`CA1-lista-general.png`](evidencias/H-21/CA1-lista-general.png),
[`CA1-filtros-aplicados.png`](evidencias/H-21/CA1-filtros-aplicados.png),
[`CA1-pagina-2.png`](evidencias/H-21/CA1-pagina-2.png)

**Pruebas automáticas relacionadas**

- `AuditoriaControllerTest` · «RF-134: sin parámetros pide 50 filas de la página 0, del más reciente
  al más antiguo, sin caché» y «RF-134: módulos y acciones separados por coma llegan como listas;
  perfil y fechas, ya convertidos».
- `ConsultaAuditoriaIT` · «RF-134: filtra por perfil», «filtra por uno o varios módulos», «filtra por
  acción», «filtra por rango» y «120 registros dan 3 páginas de 50, del más reciente al más antiguo
  y sin repetir filas».
- `PantallaAuditoria.test.tsx` · «RF-134: aplica filtros multivalor separados por coma y fechas
  convertidas», «los filtros de la URL se restauran en el formulario y en la petición» y «paginar
  conserva los filtros y deshabilita los extremos».
- `filtros.test.ts` y `fechas.test.ts` · conversión entre URL, días locales y parámetros de la API.

### CA-2 · Detalle y motivo completo al expandir un registro

> Al expandir un registro se ve el detalle y el motivo completo.

**Pasos**

1. Con la sesión del **Presidente**, abrir
   `/app/administracion/auditoria?modulo=M-06&accion=BLOQUEAR_CANCHA&desde=2026-10-20&hasta=2026-10-20`.
2. En el primer registro, pulsar el botón «Ver detalle del registro».
3. Revisar el motivo completo, la entidad, el ID de la entidad, el ID del registro y la sección
   **Detalle**.

**Resultado esperado**

- El botón cambia a «Ocultar detalle» y el panel se abre bajo la fila.
- El motivo se muestra completo, con saltos de línea y sin recortes.
- El `detalle` JSON se presenta como pares clave-valor; los objetos y arreglos se ven como texto
  (`{"lista":[1,2,3]}`), nunca como HTML.
- Un registro sin motivo o sin detalle no muestra esas secciones.

**Resultado obtenido:** **Aprobado.** El primer registro mostró el motivo completo («Se bloqueó la
cancha por riego programado y mantenimiento del césped… reprogramar sus partidos.»), la entidad
`disponibilidad_canchas`, los IDs y la sección **Detalle** con `nota`, `goles`, `anidado` y
`estadoNuevo`. El objeto anidado se mostró como texto. No se renderizó HTML del motivo.

**Evidencia:** [`CA2-detalle-expandido.png`](evidencias/H-21/CA2-detalle-expandido.png)

**Pruebas automáticas relacionadas**

- `PantallaAuditoria.test.tsx` · «RF-134: la fila se expande con motivo, IDs y detalle, solo como
  texto» y «RNF-008: la tarjeta etiqueta sus campos y omite «Motivo» si no viene».
- `ConsultaAuditoriaIT` · «RF-134: el detalle jsonb y el motivo completo llegan íntegros» y
  «RF-134: un registro sin perfil y sin detalle los devuelve como null».
- `AuditoriaControllerTest` · «RF-134: una fila lleva el perfil anidado, el detalle como objeto y el
  motivo; los campos vacíos se omiten».

### CA-3 · La auditoría no se puede modificar ni borrar

> Ningún usuario puede modificar ni borrar registros.

**Pasos**

1. En la pantalla F-14, revisar los controles disponibles: filtros, orden, expansión de fila y
   paginación.
2. Con la misma sesión del **Presidente**, enviar `POST /api/v1/auditoria` y `PUT`/`DELETE
   /api/v1/auditoria/1` con el token de la sesión.
3. Repetir la consulta con la cuenta **Capturista**, que no tiene el permiso.
4. Comprobar en la base que `siglim_app` no puede ejecutar `UPDATE`, `DELETE` ni `TRUNCATE` sobre
   `siglim.auditoria`.

**Resultado esperado**

- La pantalla no ofrece botones de editar, borrar, exportar ni imprimir; solo permite consultar.
- `POST /api/v1/auditoria` responde **405** con `Allow: GET` y código `METODO_NO_PERMITIDO`.
- `PUT` y `DELETE /api/v1/auditoria/1` responden **404** `NO_ENCONTRADO`: no existe un recurso de
  escritura por identificador.
- El Capturista ve la página **403** y el servicio responde `VAL-015`.
- La base rechaza `UPDATE`, `DELETE` y `TRUNCATE` incluso para el dueño del esquema, y conserva las
  filas.

**Resultado obtenido:** **Aprobado.** La pantalla solo mostró filtros, orden, expansión y paginación.
Las respuestas reales del backend fueron `405` (`METODO_NO_PERMITIDO`, `Allow: GET`) para `POST` y
`404` (`NO_ENCONTRADO`) para `PUT` y `DELETE`. El Capturista recibió la página 403. En la base,
`siglim_app` obtuvo «permission denied for table auditoria» y el dueño del esquema obtuvo
«RNF-015: La tabla auditoria no admite modificaciones ni borrado»; las pruebas de integración
confirman además el rechazo de `TRUNCATE` directo y por cascada.

**Evidencia:** [`CA3-sin-acciones-de-edicion.png`](evidencias/H-21/CA3-sin-acciones-de-edicion.png),
[`CA3-api-solo-lectura.png`](evidencias/H-21/CA3-api-solo-lectura.png),
[`CA3-sin-permiso.png`](evidencias/H-21/CA3-sin-permiso.png),
[`CA3-api-solo-lectura.json`](evidencias/H-21/CA3-api-solo-lectura.json) (respuestas crudas) y
[`CA3-base-inmutable.txt`](evidencias/H-21/CA3-base-inmutable.txt) (salida real de `psql`).

**Pruebas automáticas relacionadas**

- `AuditoriaControllerTest` · «RNF-015: escribir en /api/v1/auditoria responde 405 con Allow: GET y no
  toca el servicio», «RNF-015: /api/v1/auditoria/{id} no existe: responde 404 NO_ENCONTRADO»,
  «RNF-015: el controlador solo declara métodos GET» y «RNF-015: el servicio de consulta solo expone
  listar y opcionesFiltro».
- `AuditoriaServiceIT` · «RNF-015: siglim_app no puede modificar la auditoría (42501)», «no puede
  borrar la auditoría (42501)», «ni el dueño del esquema puede modificarla / borrarla (P0001 con HINT
  RNF-015)» y las variantes de `TRUNCATE` directo y por cascada.
- `ConsultaAuditoriaIT` · «VAL-015: un rol sin auditoria.registro:CONSULTAR recibe 403 en el listado
  y en las opciones».
- `PantallaAuditoria.test.tsx` · «VAL-015: un 403 de la API muestra ErrorConsulta sin formulario» y
  «VAL-015: sin el permiso muestra la 403 y no pide datos».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Listado paginado con filtros | Aprobado |
| CA-2 Detalle y motivo completo | Aprobado |
| CA-3 Sin modificación ni borrado | Aprobado |

Pruebas automáticas de la misma versión: backend `./mvnw verify` con **299/299** pruebas de surefire
y **87** de failsafe (1 omitida por falta de credenciales de AWS); frontend `npm test` con
**236/236**, `npm run lint` sin hallazgos y `npm run build` correcto.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-21 ni una regresión de
RF-134, RNF-012 o RNF-015.

## 6. Observaciones

1. La prueba se hizo contra Supabase local con datos sintéticos; el proyecto compartido no se tocó.
   La migración y las cuentas se reproducen desde cero con `supabase start` y el alta de usuarios.
2. La consulta no ofrece exportación ni impresión: están fuera del alcance de H-21 (D-06 de
   [`docs/diseno-ui/pantallas/README.md`](../../diseno-ui/pantallas/README.md)).
3. La auditoría muestra el nombre para mostrar del perfil, nunca su correo ni su persona (RNF-012).

## 7. Conclusión

La historia **H-21 cumple sus tres criterios de aceptación** sobre `develop` en `bb6fadd`, con
frontend, backend y base de datos reales, filtros de usuario/módulo/acción/fecha, paginación de 50,
detalle expandible con motivo completo y una API estrictamente de solo lectura. Las pruebas
automáticas de la misma versión pasan en su totalidad y la H-21 puede pasar a «Finalizado».
