# Prueba de aceptación · H-08 Solicitar y aprobar la afiliación del club por temporada (tarjetón)

| Campo | Valor |
| --- | --- |
| Historia | H-08 · subtarea H-08.3 (prueba de aceptación) |
| Requisitos | RF-101, UI-023, DEC-09, CU-018 |
| Pantallas | F-16 (tarjetón de afiliación: captura y dictamen) |
| Versión probada | `develop` en `2a23100`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000`, `20261103100000`, `20261106160000` y `20261108100000` |
| Fecha | 2026-11-12 |
| Ejecutó | Agente de IA en el entorno local del equipo |
| Resultado global | **Aprobado** (3 de 3 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 17 local (contenedor Docker) con la simulación del entorno de Supabase ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las seis migraciones del repositorio aplicadas |
| Autenticación | Servidor local que publica el JWKS ES256 y responde el inicio de sesión con contraseña que usa la pantalla de acceso; el backend validó firma, emisor `<SUPABASE_URL>/auth/v1`, audiencia `authenticated` y el `sub` UUID de cada petición |
| Backend | `siglim-0.1.0-SNAPSHOT.jar` en `http://localhost:8080`, contra la base local y el JWKS local (variables de entorno, sin tocar `backend/.env`) |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Microsoft Edge controlado con Playwright, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La corrida se ejecutó de punta a punta sobre una **base local efímera**, recreada desde la
simulación de Supabase y las migraciones del repositorio: no se alteró el proyecto compartido de
Supabase. El arnés (JWKS local, datos de siembra y recorrido de Playwright) vive fuera del
repositorio; en este documento solo quedan las capturas y las respuestas de la API.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican en el repositorio.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Directiva Halcones** | Directiva de club · ámbito CLUB de *Deportivo Halcones de Morelia* | CA-1: captura su tarjetón |
| **Directiva Riviera** | Directiva de club · ámbito CLUB de *Club Riviera Morelia* | CA-2: captura el segundo tarjetón; CA-3: intenta abrir el tarjetón ajeno |
| **Presidencia del Comité** | Presidente del Comité Ejecutivo · Liga | CA-2: aprueba un tarjetón y rechaza el otro |

Datos preparados en la base local antes del recorrido:

- Temporada **«Temporada 2026-27 · aceptación H-08» (ACTIVA)**, con la categoría **Libre**
  asignada.
- Club **«Deportivo Halcones de Morelia»** y club **«Club Riviera Morelia»**, ambos **ACTIVO**.
- Perfiles con sus asignaciones de rol: dos Directivas de club con ámbito `CLUB` y la Presidencia
  con ámbito `LIGA`.

Los tarjetones se capturaron y dictaminaron **desde la interfaz**, no por SQL. La temporada, las
categorías, los clubes y las cuentas se sembraron por SQL porque su alta pertenece a otras
historias (H-04, H-06, H-03), como en las pruebas de aceptación anteriores.

## 3. Escenarios

### CA-1 · La Directiva envía el tarjetón y queda SOLICITADA con folio

> Dada la Directiva, cuando envía el tarjetón completo, entonces queda SOLICITADA con folio.

**Pasos**

1. Iniciar sesión como **Directiva Halcones** y abrir `/app/organizacion/afiliaciones`.
2. Pulsar «Registrar afiliación».
3. Revisar el club (el único de su ámbito) y la temporada activa; marcar la categoría **Libre** y
   capturar los colores `Titular: verde y blanco. Alternativo: azul marino.`.
4. Pulsar «Enviar a aprobación» y revisar el detalle y la respuesta de la API.

**Resultado esperado**

- El formulario se divide en club y temporada, categorías y colores, sin pedir datos que ya viven
  en el expediente del club (UI-023, DEC-09).
- El aviso confirma «El tarjetón **AF-2026-0001** quedó SOLICITADO. La Presidencia ya puede
  revisarlo.».
- El expediente muestra la insignia **SOLICITADA**, el folio, los colores, la categoría y la
  trazabilidad con quién y cuándo lo capturó.
- `POST /api/v1/afiliaciones` responde `201` con `Location`, el folio y el estado `SOLICITADA`.

**Resultado obtenido:** **Aprobado.** El formulario mostró el club fijo y la temporada activa por
defecto; al guardar, la pantalla abrió el expediente con el aviso y el folio **AF-2026-0001** en
estado **SOLICITADA**, capturado por *Directiva Halcones* el 12 nov 2026, 18:13. La API respondió
`201` con `folio: "AF-2026-0001"`, `estado: "SOLICITADA"` y la auditoría
`REGISTRAR_AFILIACION` de esa directiva.

**Evidencia:** [`CA1-bandeja-directiva.png`](evidencias/H-08/CA1-bandeja-directiva.png),
[`CA1-formulario-tarjeton.png`](evidencias/H-08/CA1-formulario-tarjeton.png),
[`CA1-detalle-solicitada.png`](evidencias/H-08/CA1-detalle-solicitada.png) y
[`CA1-respuesta-alta.json`](evidencias/H-08/CA1-respuesta-alta.json) (respuesta cruda del alta)

**Pruebas automáticas relacionadas**

- `AfiliacionServiceTest` · «H-08 criterio 1 · RF-101: la Directiva captura el tarjetón y queda
  SOLICITADA con folio», «RF-101: el club ya afiliado en la temporada responde 409 con folio y
  tarjetón» y «RF-003: no se captura el tarjetón de una temporada cerrada (409)».
- `AfiliacionControllerTest` · «RF-101: el alta responde 201 con el tarjetón SOLICITADA y su
  Location» y «RF-101: sin categorías el tarjetón no se captura (400 VALIDACION)».
- `AfiliacionesIT` · «H-08 criterio 1 · RF-101: la Directiva captura el tarjetón y queda SOLICITADA
  con folio».
- `PantallaAfiliaciones.test.tsx` · «RF-101: la Directiva captura por secciones y el tarjetón queda
  SOLICITADO con folio».

### CA-2 · La Presidencia aprueba o rechaza, con quién y cuándo, y el rechazo exige observaciones

> Dado el Presidente, cuando aprueba o rechaza, entonces el estado cambia, se registran quién y
> cuándo, y el rechazo exige observaciones.

**Pasos**

1. Con la **Directiva Riviera**, capturar un segundo tarjetón para *Club Riviera Morelia* en la
   misma temporada y categoría (folio **AF-2026-0002**).
2. Iniciar sesión como **Presidencia del Comité** y abrir `/app/organizacion/afiliaciones`.
3. Abrir el tarjetón **AF-2026-0001**, pulsar «Aprobar afiliación», escribir la nota
   `Cumple los requisitos de la convocatoria.` y confirmar.
4. Abrir el tarjetón **AF-2026-0002**, pulsar «Rechazar afiliación» y confirmar sin observaciones
   para comprobar la validación; después capturar
   `Faltan las firmas del secretario y la categoría no corresponde a la convocatoria.` y confirmar.
5. Revisar las filas en `siglim.afiliaciones` y los eventos de `siglim.auditoria`.

**Resultado esperado**

- La aprobación deja el tarjetón **APROBADA** y la pantalla muestra la insignia, quién resolvió y
  la fecha, además de la nota de aprobación.
- El rechazo sin observaciones no se envía: el modal exige al menos 10 caracteres; por API responde
  `400` con `codigo: "RF-101"`.
- El rechazo con observaciones deja el tarjetón **RECHAZADA** y muestra el motivo del rechazo con
  quién y cuándo.
- Ambos dictámenes quedan en la base con `resuelta_por`, `resuelta_en` y `observaciones`, y en la
  auditoría como `APROBAR_AFILIACION` y `RECHAZAR_AFILIACION`.

**Resultado obtenido:** **Aprobado.** La aprobación mostró ««Deportivo Halcones de Morelia» quedó
afiliado a Temporada 2026-27 · aceptación H-08.», el tarjetón **APROBADA** con *Presidencia del
Comité* y fecha 12 nov 2026, 18:13. El rechazo sin observaciones respondió `400 RF-101`
(«Escribe observaciones de al menos 10 caracteres para rechazar la afiliación.»); con observaciones,
el tarjetón **AF-2026-0002** quedó **RECHAZADA** y la pantalla mostró el motivo completo con quién y
cuándo (18:14). En la base, ambas filas conservan `resuelta_por`, `resuelta_en` y sus
observaciones, y la auditoría registra los cuatro eventos con el perfil que los ejecutó.

**Evidencia:** [`CA2-detalle-por-dictaminar.png`](evidencias/H-08/CA2-detalle-por-dictaminar.png),
[`CA2-modal-aprobar.png`](evidencias/H-08/CA2-modal-aprobar.png),
[`CA2-tarjeton-aprobado.png`](evidencias/H-08/CA2-tarjeton-aprobado.png),
[`CA2-modal-rechazar.png`](evidencias/H-08/CA2-modal-rechazar.png),
[`CA2-tarjeton-rechazado.png`](evidencias/H-08/CA2-tarjeton-rechazado.png),
[`CA2-respuesta-aprobacion.json`](evidencias/H-08/CA2-respuesta-aprobacion.json),
[`CA2-respuesta-rechazo.json`](evidencias/H-08/CA2-respuesta-rechazo.json),
[`CA2-respuesta-400-sin-observaciones.json`](evidencias/H-08/CA2-respuesta-400-sin-observaciones.json),
[`CA2-filas-en-base.txt`](evidencias/H-08/CA2-filas-en-base.txt) y
[`CA2-auditoria.txt`](evidencias/H-08/CA2-auditoria.txt) (salidas reales de SQL)

**Pruebas automáticas relacionadas**

- `AfiliacionServiceTest` · «H-08 criterio 2 · RF-101: el Presidente aprueba, se guardan perfil y
  fecha, y se audita», «H-08 criterio 2 · RF-101: rechazar sin observaciones suficientes responde
  400» y «H-08 criterio 2 · RF-101: el rechazo guarda observaciones, motivo y auditoría».
- `AfiliacionControllerTest` · «RF-101: la resolución exige APROBAR sobre el club del tarjetón»,
  «RF-101: resolver un tarjetón ya resuelto responde 409 RF-101» y «VAL-015: la Directiva no puede
  resolver su propio tarjetón (403)».
- `AfiliacionesIT` · «H-08 criterios 1 y 2 · RF-101: el SECRETARIO captura con folio y el PRESIDENTE
  aprueba» y «H-08 criterio 2 · RF-101: el rechazo exige observaciones y las guarda con el motivo».
- `PantallaAfiliaciones.test.tsx` · «RF-101: el Presidente aprueba con una nota y el estado cambia»
  y «RF-101: el rechazo exige observaciones y las envía».

> La comprobación del `400` sin observaciones se hizo por API sobre un tarjetón de una temporada en
> `CONFIGURACION`, para no dejar un tarjetón de la temporada activa en un estado inconsistente. El
> modal de la interfaz muestra la misma regla («Mínimo 10 caracteres») y la cubre la prueba de
> `PantallaAfiliaciones.test.tsx`.

### CA-3 · Un usuario de otro club recibe 404

> Dado un usuario de otro club, cuando consulta la afiliación, entonces recibe 404.

**Pasos**

1. Iniciar sesión como **Directiva Riviera**.
2. Abrir directamente la URL del tarjetón **AF-2026-0001**, del club *Deportivo Halcones de
   Morelia*: `/app/organizacion/afiliaciones/c2f5c46f-4866-487f-a3ae-7c6c1e78d437`.
3. Consultar por API `GET /api/v1/afiliaciones/{id}` con su token y revisar el listado
   `GET /api/v1/afiliaciones`.

**Resultado esperado**

- La pantalla muestra «No encontramos esta información. Puede que no exista o que no tengas
  acceso», sin revelar el tarjetón ni su club.
- La API responde **404** con `codigo: "NO_ENCONTRADO"`, idéntico a un recurso inexistente
  (VAL-024), no `403`.
- El listado devuelve solo el tarjetón de su club (**AF-2026-0002**), con `totalElementos: 1`.

**Resultado obtenido:** **Aprobado.** La pantalla mostró el estado vacío «No encontramos esta
información…» con el botón «Volver a la bandeja», y la bandeja solo listó **Club Riviera Morelia**
(**AF-2026-0002**). La API respondió `404` `NO_ENCONTRADO` para el tarjetón de Halcones y el listado
devolvió un único elemento, el de su propio club.

**Evidencia:** [`CA3-detalle-ajeno-404.png`](evidencias/H-08/CA3-detalle-ajeno-404.png),
[`CA3-respuesta-404.json`](evidencias/H-08/CA3-respuesta-404.json) y
[`CA3-listado-directiva.json`](evidencias/H-08/CA3-listado-directiva.json)

**Pruebas automáticas relacionadas**

- `AfiliacionServiceTest` · «VAL-024: un tarjetón de otro club responde 404 y no se convierte en
  403» y «RF-101: un tarjetón inexistente responde 404 antes de autorizar».
- `AfiliacionesIT` · «H-08 criterio 3 · RF-101, VAL-024: la Directiva solo ve y opera el tarjetón de
  su club».
- `PantallaAfiliaciones.test.tsx` · «VAL-024: un tarjetón fuera de ámbito responde 404 y ofrece
  volver».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 El tarjetón queda SOLICITADA con folio | Aprobado |
| CA-2 La Presidencia aprueba o rechaza con quién y cuándo; el rechazo exige observaciones | Aprobado |
| CA-3 Un usuario de otro club recibe 404 | Aprobado |

Pruebas automáticas de la misma versión: backend
`./mvnw -Dtest='AfiliacionServiceTest,AfiliacionControllerTest' -Dit.test='AfiliacionesIT' verify`
con **80/80** pruebas (30 de servicio, 43 de controlador y 7 de `AfiliacionesIT` con PostgreSQL real
en Testcontainers, sin fallos); frontend
`npx vitest run src/features/organizacion/afiliaciones` con **8/8** pruebas.

## 5. Defectos encontrados

Ninguno. No se encontró un comportamiento que incumpla los criterios de H-08 ni una regresión de
RF-101, UI-023, DEC-09 o VAL-024.

## 6. Observaciones

1. **El nombre del perfil que resolvió no viaja en el listado ajeno.** En la respuesta del listado
   de la Directiva Riviera, `resueltaPor` del tarjetón de Halcones no aparece (RLS no le permite leer
   ese perfil); el detalle del propio tarjetón, visto por la Presidencia, sí lo incluye. Es el
   comportamiento documentado de H-08.1, no un defecto.
2. **Los tarjetones no se borran.** Ambos quedan en `siglim.afiliaciones` con su folio, su estado
   final y su trazabilidad (RN-005); la corrida no intentó eliminarlos.
3. **Sin datos personales en las evidencias.** Las capturas no muestran correos, contraseñas ni
   tokens; los tokens de sesión de la corrida no se guardaron en el repositorio.
4. **La base local se recreó desde cero.** El proyecto compartido de Supabase no se tocó y el
   arnés (JWKS local, siembra y recorrido) quedó fuera del repositorio, como en las pruebas de
   aceptación anteriores.

## 7. Conclusión

La historia **H-08 cumple sus tres criterios de aceptación** sobre `develop` en `2a23100` con
frontend, backend y base de datos reales: la Directiva captura el tarjetón y el sistema le asigna
folio y lo deja SOLICITADA; la Presidencia lo aprueba o lo rechaza con observaciones obligatorias y
quedan registrados quién y cuándo en el expediente, la base y la auditoría; y una Directiva de otro
club recibe `404` sin revelar el tarjetón. H-08 puede pasar a «Finalizado».
