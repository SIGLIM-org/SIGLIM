# Prueba de aceptación · B-27 Redactar, aprobar y publicar comunicados

| Campo | Valor |
| --- | --- |
| Historia | B-27 (SCRUM-236) · subtarea B-27.3 (SCRUM-390) |
| Requisitos | RF-090, UI-031, VAL-015 |
| Pantallas | B-08 (comunicados) |
| Versión probada | `develop` en `2605860`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-04 |
| Ejecutó | Agente de IA (OpenCode) en el entorno local del equipo, revisado por Olvera Bucio Jose Israel |
| Resultado global | **Aprobado** (5 de 5 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos y autenticación | Supabase local (`supabase start`): PostgreSQL 17 y GoTrue, con las migraciones del repositorio aplicadas |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, conectado a la base local y a Supabase Auth local |
| Frontend | `npm run dev` en `http://localhost:5173` (proxy de Vite hacia el backend) |
| Navegador | Chromium 156 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra un **proyecto local efímero**, no contra el proyecto compartido de
Supabase: no se alteró la base del equipo y la prueba se reproduce desde cero.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Secretaría** | Secretario de la Liga · Liga | `publicacion.comunicado:REDACTAR` |
| **Presidencia** | Presidente del Comité Ejecutivo · Liga | `publicacion.comunicado:APROBAR` |
| **Cuenta con ambos permisos** | Presidente del Comité Ejecutivo · Liga, con `REDACTAR` agregado | `publicacion.comunicado:REDACTAR` y `publicacion.comunicado:APROBAR` (solo para CA-4) |

Comunicados creados durante la corrida:

- **«Arranque oficial de la Copa Morelia 2026»** (`e14ae10e…`): flujo directo hasta publicarse.
- **«Mantenimiento de la cancha principal»** (`833c3d67…`): devuelto con observaciones, corregido y publicado.
- **«Prueba de separación de funciones»** (`7ec46db5…`): redactado por la cuenta con ambos permisos, que intenta aprobarlo.

## 3. Escenarios

### CA-1 · Al enviarse, el borrador pasa a revisión y ya no se edita

> Dado un comunicado en BORRADOR, cuando se envía, entonces pasa a REVISION y ya no se edita.

**Pasos**

1. Iniciar sesión como **Secretaría** y abrir Administración › Comunicados.
2. Pulsar «Redactar comunicado», capturar título y cuerpo y guardar el borrador.
3. Pulsar «Enviar a revisión» y confirmar.
4. Revisar el detalle del comunicado y la respuesta de la API.

**Resultado esperado**

- El comunicado pasa de `BORRADOR` a `REVISION`.
- El detalle ya no ofrece editarlo mientras espera el dictamen.

**Resultado obtenido:** **Aprobado.** El comunicado se guardó en `BORRADOR` y, tras enviarlo, la API
respondió `estadoPublicacion: REVISION`. El detalle muestra la insignia **EN REVISIÓN** y el aviso
«En revisión por la Presidencia. Mientras espera el dictamen, el comunicado no se puede editar».

**Evidencia**

- [`CA1-borrador.png`](evidencias/B-27/CA1-borrador.png)
- [`CA1-en-revision.png`](evidencias/B-27/CA1-en-revision.png)
- [`CA1-comunicado-revision.json`](evidencias/B-27/CA1-comunicado-revision.json)

**Pruebas automáticas relacionadas**

- `ComunicadoServiceTest` · «RF-090: el borrador pasa a revisión y queda auditado», «RF-090: en
  revisión el comunicado ya no se edita» y «RF-090: no se envía a revisión lo que ya no está en borrador».
- `PantallaComunicados.test.tsx` · «B-27.2: enviar a revisión pide confirmación y deja el comunicado en revisión».

### CA-2 · La Presidencia aprueba y el comunicado se publica con quién y cuándo

> Dado el Presidente, cuando lo aprueba, entonces pasa a PUBLICADO con quién y cuándo.

**Pasos**

1. Iniciar sesión como **Presidencia** y abrir el comunicado «Arranque oficial…» en la pestaña «En revisión».
2. Aprobarlo con la observación «Aprobado por unanimidad del Comité Ejecutivo.».
3. Revisar la respuesta de la API y la fila en `siglim.comunicados`.
4. Comprobar por API que la **Secretaría** no puede aprobar, que la **Presidencia** no puede
   redactar y que un comunicado publicado ya no se edita.

**Resultado esperado**

- El comunicado pasa a `PUBLICADO` con `aprobadoPor`, `aprobadoEn` y `publicadoEn`.
- Sin el permiso correspondiente, la API responde **403 VAL-015** (denegación por defecto).
- Editar un comunicado publicado responde **409**.

**Resultado obtenido:** **Aprobado.** La API respondió `PUBLICADO` con `aprobadoPor` igual al perfil
de la Presidencia y `aprobadoEn`/`publicadoEn` a las 19:09:21; la base guarda los mismos valores. La
Secretaría recibió **403 VAL-015** al resolver, la Presidencia **403 VAL-015** al redactar y la
edición del comunicado publicado respondió **409 RF-090**.

**Evidencia**

- [`CA2-revision-presidencia.png`](evidencias/B-27/CA2-revision-presidencia.png)
- [`CA2-publicado.png`](evidencias/B-27/CA2-publicado.png)
- [`CA2-comunicado-publicado.json`](evidencias/B-27/CA2-comunicado-publicado.json)
- [`CA2-secretaria-no-aprueba.json`](evidencias/B-27/CA2-secretaria-no-aprueba.json)
- [`CA2-presidencia-no-redacta.json`](evidencias/B-27/CA2-presidencia-no-redacta.json)
- [`CA2-edicion-publicada-409.json`](evidencias/B-27/CA2-edicion-publicada-409.json)
- [`base-comunicados.txt`](evidencias/B-27/base-comunicados.txt)

**Pruebas automáticas relacionadas**

- `ComunicadoServiceTest` · «RF-090: aprobar publica con el perfil de la sesión y deja la fecha de
  aprobación» y «VAL-015: sin permiso de redacción no se registra ningún comunicado».
- `ComunicadosIT` · «RF-090, CU-027: redactar, editar, enviar a revisión, aprobar y publicar con historial».
- `PantallaComunicados.test.tsx` · «B-27.2: el Presidente aprueba y publica un comunicado en revisión».

### CA-3 · La devolución exige observaciones y regresa el comunicado a borrador

> Dada una devolución sin observaciones, entonces se rechaza; con observaciones, el comunicado
> regresa a BORRADOR.

**Pasos**

1. Como **Secretaría**, redactar «Mantenimiento de la cancha principal» y enviarlo a revisión.
2. Como **Presidencia**, intentar devolverlo sin observaciones.
3. Devolverlo con la observación «Indica la cancha, el horario y el motivo del mantenimiento antes de publicar.».
4. Como **Secretaría**, abrir el borrador devuelto, corregir el cuerpo y reenviarlo.
5. Como **Presidencia**, aprobarlo.

**Resultado esperado**

- Sin observaciones, la devolución se rechaza y el comunicado sigue en `REVISION`.
- Con observaciones, vuelve a `BORRADOR` y la Secretaría ve las observaciones al editarlo.
- Corregido y reenviado, puede aprobarse y publicarse.

**Resultado obtenido:** **Aprobado.** El rechazo sin observaciones lo cubren las pruebas automáticas
citadas abajo (ver Observaciones §1). Con observaciones, la API respondió `BORRADOR` con el texto en
`observaciones`, y el borrador muestra el recuadro «Observaciones de la Presidencia». La Secretaría
lo corrigió, lo reenvió (`REVISION`) y la Presidencia lo publicó.

**Evidencia**

- [`CA3-observaciones-secretaria.png`](evidencias/B-27/CA3-observaciones-secretaria.png)
- [`CA3-devuelto.png`](evidencias/B-27/CA3-devuelto.png)
- [`CA3-comunicado-devuelto.json`](evidencias/B-27/CA3-comunicado-devuelto.json)
- [`CA3-reenviado.png`](evidencias/B-27/CA3-reenviado.png)
- [`CA3-comunicado-reenviado.json`](evidencias/B-27/CA3-comunicado-reenviado.json)
- [`CA3-publicado-tras-correccion.png`](evidencias/B-27/CA3-publicado-tras-correccion.png)
- [`CA3-comunicado-publicado.json`](evidencias/B-27/CA3-comunicado-publicado.json)

**Pruebas automáticas relacionadas**

- `ComunicadoServiceTest` · «RF-090: devolver exige observaciones, fija el motivo y vuelve a borrador».
- `ComunicadoControllerTest` · «RF-090: el 400 por devolver sin observaciones conserva el código».
- `ComunicadosIT` · «RF-090: la devolución exige observaciones, vuelve a borrador y deja el motivo».
- `PantallaComunicados.test.tsx` · «B-27.2: devolver exige observaciones de al menos 10 caracteres»
  y «B-27.2: el borrador devuelto muestra las observaciones en el editor».

### CA-4 · Quien redacta un comunicado no puede aprobarlo

> Quien redacta un comunicado no puede aprobarlo.

**Pasos**

1. Iniciar sesión con la **cuenta con ambos permisos**, redactar «Prueba de separación de funciones» y enviarlo a revisión.
2. Intentar aprobarlo desde la pantalla y por API.
3. Intentar en la base local `UPDATE … SET aprobado_por = redactado_por` para comprobar la última garantía.

**Resultado esperado**

- La pantalla avisa que no puede aprobar su propio comunicado.
- La API responde **403 RF-090** y el comunicado sigue en `REVISION`, sin aprobador.
- La restricción `ck_comunicados_dual` rechaza el cambio directo en la base.

**Resultado obtenido:** **Aprobado.** La pantalla mostró «No puedes aprobar tu propio comunicado
(RF-090)»; la API respondió **403 RF-090**; la fila quedó en `REVISION` sin `aprobado_por`, y el
`UPDATE` directo falló por `ck_comunicados_dual`.

**Evidencia**

- [`CA4-alerta-autor.png`](evidencias/B-27/CA4-alerta-autor.png)
- [`CA4-autoprobacion-403.json`](evidencias/B-27/CA4-autoprobacion-403.json)
- [`CA4-comunicado-sin-aprobar.json`](evidencias/B-27/CA4-comunicado-sin-aprobar.json)
- [`CA4-base-sin-aprobar.txt`](evidencias/B-27/CA4-base-sin-aprobar.txt)
- [`CA4-ck-dual.txt`](evidencias/B-27/CA4-ck-dual.txt)

**Pruebas automáticas relacionadas**

- `ComunicadoServiceTest` · «RF-090: quien redactó no puede aprobar su propio comunicado».
- `TraduccionErroresBdIT` · «23514 real (CHECK ck_comunicados_dual): 400 VALIDACION».
- `PantallaComunicados.test.tsx` · «RF-090: quien redactó no puede aprobar su propio comunicado».

### CA-5 · Cada transición queda en el historial de publicación

> Cada transición queda en el historial de publicación.

**Pasos**

1. Al terminar CA-1 a CA-4, consultar en la base local el historial de publicación y la auditoría del módulo M-11.

**Resultado esperado**

- Una fila de historial por cada transición (`BORRADOR → REVISION`, `REVISION → PUBLICADO`,
  `REVISION → BORRADOR`), con quién y cuándo; la devolución guarda su motivo.
- Cada operación aparece en la auditoría con módulo, acción y perfil.

**Resultado obtenido:** **Aprobado.** El historial tiene las 7 transiciones de la corrida, y la
devolución conserva su motivo. La auditoría registra las 11 operaciones (`REDACTAR_COMUNICADO`,
`EDITAR_COMUNICADO`, `ENVIAR_COMUNICADO_A_REVISION`, `APROBAR_COMUNICADO`, `DEVOLVER_COMUNICADO`) con su perfil.

**Evidencia**

- [`historial-publicacion.txt`](evidencias/B-27/historial-publicacion.txt)
- [`auditoria-comunicados.txt`](evidencias/B-27/auditoria-comunicados.txt)

**Pruebas automáticas relacionadas**

- `ComunicadosIT` · «RF-090, CU-027: redactar, editar, enviar a revisión, aprobar y publicar con historial».
- `ComunicadoServiceTest` · «RF-090: redactar recorta el texto, usa el perfil de la sesión y audita».

## 4. Resumen

| Criterio | Resultado |
| --- | --- |
| CA-1 Envío a revisión y bloqueo de edición | Aprobado |
| CA-2 Aprobación con quién y cuándo | Aprobado |
| CA-3 Devolución con observaciones | Aprobado |
| CA-4 Separación entre redactar y aprobar | Aprobado |
| CA-5 Historial de publicación | Aprobado |

## 5. Defectos encontrados

Ninguno.

## 6. Observaciones

1. La corrida manual no guardó evidencia del intento de devolver **sin** observaciones (CA-3, paso 2);
   esa parte la cubren las pruebas automáticas citadas (servicio, controlador, integración y pantalla).
2. La consulta pública de comunicados en el portal es de B-26 (Sprint 4) y no forma parte de esta prueba.

## 7. Conclusión

La historia **B-27 cumple sus 5 criterios de aceptación** sobre `develop` en `2605860`, con frontend,
backend y base de datos reales. Puede cerrarse con la Definición de Terminado de `CONTRIBUTING.md` §4.
