# Prueba de aceptación · B-05 Registrar la venta de un equipo y la conservación de plaza

| Campo | Valor |
| --- | --- |
| Historia | B-05 (SCRUM-214) · subtarea B-05.4 (SCRUM-378) |
| Requisitos | RF-103, RF-104, RN-040, UI-034, RN-021, RN-041, RF-003, RF-093, VAL-024, DEC-14, DEC-132 |
| Pantallas | B-09 (Organización → Ventas y plazas: vistas Club y Presidencia), F-10 y F-11 (temporadas), F-15 (clubes y equipos), F-14 (auditoría) |
| Implementó | Geovani (B-05.1, B-05.2 #141, B-05.3 #144) |
| Versión probada | `develop` en `4bd6ddb` |
| Fecha | 2026-11-16 |
| Ejecutó | Leonardo López (prueba cruzada, DEC-50) |
| Resultado global | **Aprobado** (5 de 5 criterios de SCRUM-214 y la regla DEC-132), sin defectos |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | Supabase compartido (proyecto `SIGLIM`, rama `develop` según DEC-52) |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` |
| Navegador | Google Chrome, ventana de 1440 px; dos sesiones a la vez (ventana normal e incógnito) |

El criterio 3 exige **cerrar una temporada**, y eso no se puede deshacer (RF-003). Para no tocar la
temporada de trabajo del equipo («Temporada 2026–2027», en `CONFIGURACION`), la prueba usa una
**temporada de prueba aparte** que se activa y se cierra solo para esta corrida. En el Supabase
compartido queda una temporada cerrada con nombre de prueba.

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; su correo y contraseña no se publican.

| Cuenta | Rol y ámbito | Uso en la prueba |
| --- | --- | --- |
| **Administrador general** | Administrador general · LIGA | Prepara y cierra la temporada de prueba; consulta la auditoría |
| **Secretario** | Secretario de la Liga · LIGA | Registra los equipos de prueba |
| **Directiva Real Patzcuaro** | Directiva de club · CLUB *Real Patzcuaro* | CA-1 y CA-4: registra las operaciones |
| **Presidente** | Presidente del Comité Ejecutivo · LIGA | CA-2: autoriza y rechaza |
| **Directiva CamiloSextos** | Directiva de club · CLUB *CamiloSextos* | CA-5: ámbito de otro club |

Datos que se crean para la corrida:

| Id | Dato | Destino en la prueba |
| --- | --- | --- |
| T | Temporada **«Temporada prueba B-05»** (1-ago-2025 a 31-may-2026), con parámetros v1, 2 categorías y 2 grupos (los equipos A y C en «Libre Demostración / Grupo A») | Se activa y se cierra (CA-3) |
| A | Equipo **«B05 Equipo A»** de Real Patzcuaro | Venta a CamiloSextos autorizada y conservación de plaza autorizada (CA-1 a CA-4) |
| B | Equipo **«B05 Equipo B»** de Real Patzcuaro | Venta rechazada (CA-2) |
| C | Equipo **«B05 Equipo C»** de Real Patzcuaro | Venta que se queda SOLICITADA y no se aplica (CA-3) |

Estado inicial verificado el 2026-11-16: una sola temporada («Temporada 2026–2027», en
`CONFIGURACION`), ninguna operación de equipo, clubes Real Patzcuaro y CamiloSextos `ACTIVO`.

### Preparación (no es criterio; se documenta para poder repetirla)

1. Como **Administrador general**, abrir **Administración → Temporadas** (F-10) y crear la
   temporada **T**.
2. En su configuración (F-11), guardar los parámetros deportivos (v1), las categorías y los grupos.
3. Comprobar que la lista de verificación de **T** está completa y pulsar **Activar**.
   **Antes de confirmar, verificar que la fila es «Temporada prueba B-05» y no «Temporada
   2026–2027».**
4. Como **Secretario**, abrir **Organización → Clubes → Real Patzcuaro** (F-15) y registrar los
   equipos **A**, **B** y **C** en el grupo de **T**.

La preparación quedó registrada en la auditoría (`REGISTRAR_TEMPORADA`, `REGISTRAR_PARAMETROS`,
`ASIGNAR_CATEGORIA_TEMPORADA`, `REGISTRAR_GRUPO`, `ACTIVAR_TEMPORADA` y tres `REGISTRAR_EQUIPO`), visible
en `CA3-auditoria.png`; no se tomaron capturas aparte.

## 3. Escenarios

### CA-1 · La Directiva registra una venta con club destino y queda SOLICITADA

> Dada la Directiva, cuando registra una venta con club destino, entonces la operación queda
> SOLICITADA.

**Pasos**

1. Iniciar sesión como **Directiva Real Patzcuaro** y abrir **Organización → Ventas y plazas**
   (`/app/organizacion/operaciones-equipo`).
2. Pulsar **Registrar operación**, elegir **Venta**, el equipo **A** y el club destino
   **CamiloSextos**; escribir en observaciones `Venta acordada en asamblea del club.` y pulsar
   **Enviar a la Presidencia**.
3. Repetir el paso 2 con los equipos **B** y **C** (venta a CamiloSextos, sin observaciones).
4. Revisar el selector de club destino: no debe ofrecer a Real Patzcuaro.

**Resultado esperado**

- Mensaje «La operación de «B05 Equipo A» quedó SOLICITADA. La Presidencia ya puede revisarla.».
- La bandeja muestra las tres ventas con la insignia **Solicitada**, los clubes «Real Patzcuaro →
  CamiloSextos», la temporada **T** «Abierta» y quién la solicitó.
- El club destino solo ofrece clubes activos distintos del club actual del equipo.

**Resultado obtenido:** **Aprobado.** El formulario ofreció como destino solo a CamiloSextos (único club activo distinto de Real Patzcuaro). Las tres ventas se registraron y la bandeja mostró «3 operaciones en este filtro», cada una con la insignia **Solicitada**, «Real Patzcuaro → CamiloSextos», la temporada **T** «Abierta», la fecha y quien la solicitó, con el texto «Espera el dictamen de la Presidencia.». La auditoría registró tres `SOLICITAR_OPERACION_EQUIPO` en M-03; la de A lleva las observaciones en el detalle.

**Evidencia:** `CA1-formulario-venta.png`, `CA1-ventas-solicitadas.png`

**Pruebas automáticas relacionadas:** `OperacionEquipoServiceTest` («RF-103: la Directiva registra
la venta y queda SOLICITADA, auditada», «RF-103: la venta con el mismo club de origen responde 400
VALIDACION»); `OperacionEquipoControllerTest` («RF-103: POST registra la venta y responde 201 con la
operación SOLICITADA»); `PantallaOperacionesEquipo.test.tsx` («la Directiva registra una venta con
su club destino y queda SOLICITADA»); `OperacionesEquipoIT`.

### CA-2 · El Presidente autoriza o rechaza; el rechazo exige observaciones

> Dado el Presidente, cuando la autoriza o la rechaza, entonces cambia de estado; el rechazo exige
> observaciones.

**Pasos**

1. En la ventana de incógnito, iniciar sesión como **Presidente** y abrir **Ventas y plazas**.
   Comprobar que la vista es de Presidencia y que no aparece **Registrar operación**.
2. **Autorizar la venta de A:** pulsar **Autorizar**, dejar la nota vacía y confirmar con
   **Autorizar operación**.
3. **Rechazo inválido (B):** pulsar **Rechazar** en la venta de B, escribir `Falta` (menos de 10
   caracteres) y pulsar **Rechazar operación**.
4. **Rechazo válido (B):** escribir `La asamblea del club no aprobó la venta del equipo.` y
   confirmar.
5. Revisar la fila de A y de B con el filtro **Todos**.

**Resultado esperado**

- Paso 2: mensaje «… quedó AUTORIZADA. Se aplicará al cerrar la temporada.»; A con insignia
  **Autorizada** y «Resuelta el …».
- Paso 3: el rechazo no se envía; se pide un mínimo de 10 caracteres (RN-021).
- Paso 4: mensaje «… se rechazó con observaciones.»; B con insignia **Rechazada** y las
  observaciones visibles.
- Paso 5: A y B ya no ofrecen **Autorizar** ni **Rechazar** («Sin acciones»): una operación
  resuelta no se vuelve a dictaminar (por API respondería `409 RF-103`).

**Resultado obtenido:** **Aprobado.** La venta de A pasó a **Autorizada** con «Se aplicará al cerrar la temporada.» y «Resuelta el 16 nov 2026». El rechazo de B con «Falta» (5/500) no se envió y mostró «Escribe observaciones de al menos 10 caracteres para rechazar la operación.». Con las observaciones completas, B pasó a **Rechazada** mostrándolas. A y B quedaron con «Sin acciones»; solo C conserva **Autorizar** y **Rechazar**. Auditoría: `AUTORIZAR_OPERACION_EQUIPO` sin motivo y `RECHAZAR_OPERACION_EQUIPO` con las observaciones como motivo.

**Evidencia:** `CA2-autorizar.png`, `CA2-rechazo-minimo.png`, `CA2-rechazada.png`,
`CA2-sin-acciones.png`

**Pruebas automáticas relacionadas:** `OperacionEquipoControllerTest` («RN-040: la autorización
responde 200…», «RN-021: el rechazo sin observaciones suficientes conserva el 400 y el código»,
«RN-040: resolver una operación ya resuelta responde 409 RF-103»); `PantallaOperacionesEquipo.test.tsx`
(«la Presidencia autoriza una solicitud con una nota», «el rechazo exige observaciones y después
cambia el estado»).

### CA-3 · Al cerrar la temporada, la venta autorizada se aplica y el equipo cambia de club

> Dada una operación AUTORIZADA, cuando se cierra la temporada, entonces pasa a APLICADA, el equipo
> cambia de club y se conserva el historial de propietarios (RN-040).

Se ejecuta **después del CA-4**, para que la conservación de plaza autorizada también se aplique.

**Pasos**

1. Como **Administrador general**, abrir **Administración → Temporadas**, pulsar **Cerrar** en
   **«Temporada prueba B-05»** (verificar el nombre) y escribir el motivo `Cierre de la temporada
   de prueba de aceptación B-05.`
2. Como **Presidente**, abrir **Ventas y plazas** con el filtro **Todos**.
3. Abrir **Organización → Clubes → CamiloSextos** y **→ Real Patzcuaro** y revisar sus equipos.
4. Como **Administrador general**, abrir **Auditoría** (F-14) y filtrar por módulo **M-03** y por
   **M-02**; expandir los registros.

**Resultado esperado**

- Paso 1: **T** pasa a **CERRADA** y «Solo lectura».
- Paso 2: la venta de A y la conservación de plaza de A tienen la insignia **Aplicada** y «Aplicada
  el …»; el texto de la venta dice «El equipo ya pertenece al club destino.». La venta de C sigue
  **Solicitada** y explica «La temporada está cerrada: esta solicitud ya no se puede dictaminar.».
  La de B sigue **Rechazada**.
- Paso 2 (historial): la venta de A sigue mostrando «Real Patzcuaro → CamiloSextos»; el club de
  origen no se reescribe (RN-040).
- Paso 3: el equipo **A** aparece en CamiloSextos y ya no en Real Patzcuaro; B y C siguen en Real
  Patzcuaro.
- Paso 4: hay `CERRAR_TEMPORADA` (M-02) con el motivo y dos `APLICAR_OPERACION_EQUIPO` (M-03),
  uno por cada operación de A, con estado anterior `AUTORIZADA` y estado `APLICADA`; ninguno para C.

**Resultado obtenido:** **Aprobado.** **T** quedó **CERRADA** y «Solo lectura» (2 categorías, 2 grupos, parámetros v1). En la bandeja (4 operaciones), la venta y la conservación de plaza de A quedaron **Aplicada** el 16 nov 2026, con «El equipo ya pertenece al club destino.» y «La plaza quedó conservada en su club.»; la venta de A conserva «Real Patzcuaro → CamiloSextos». La venta de C siguió **Solicitada** con el candado y «La temporada está cerrada: esta solicitud ya no se puede dictaminar.»; B siguió **Rechazada**. En Clubes, CamiloSextos muestra «B05 Equipo A» (Temporada prueba B-05) y B y C siguen en Real Patzcuaro. La auditoría registró a la misma hora (17:20:31) `CERRAR_TEMPORADA` con el motivo y `operacionesAplicadas: 2`, y dos `APLICAR_OPERACION_EQUIPO` (venta y conservación de A) con `estadoAnterior: AUTORIZADA`, `estado: APLICADA` y el motivo del cierre; no hay ninguno para C. Consulta directa al Supabase compartido: `equipos.club_id` de A es CamiloSextos y `operaciones_equipo.club_origen_id` sigue siendo Real Patzcuaro (historial de propietarios, RN-040).

**Evidencia:** `CA3-temporada-cerrada.png`, `CA3-operaciones-aplicadas.png`,
`CA3-equipo-en-destino.png`, `CA3-auditoria.png`

**Pruebas automáticas relacionadas:** `TemporadaServiceTest` (cierre que aplica las operaciones
autorizadas); `OperacionesEquipoIT` (aplicación en la transacción del cierre);
`PantallaOperacionesEquipo.test.tsx` («una solicitud de una temporada cerrada no ofrece dictamen y
explica la causa»).

### CA-4 · La conservación de plaza es una operación independiente de la venta

> La conservación de plaza se registra como operación independiente de la venta (RF-104).

**Pasos**

1. Como **Directiva Real Patzcuaro**, pulsar **Registrar operación**, elegir **Venta** y el equipo
   **A** (que ya tiene una venta vigente) con destino CamiloSextos, y enviar.
2. Pulsar **Registrar operación**, elegir **Conservación de plaza** y el equipo **A**; comprobar
   que no aparece el campo de club destino y enviar.
3. Como **Presidente**, autorizar la conservación de plaza de A.

**Resultado esperado**

- Paso 1: se rechaza con «El equipo «B05 Equipo A» ya tiene una venta …: espera a que se resuelva o
  se aplique antes de registrar otra.» (`409 RF-103`, DEC-132).
- Paso 2: la conservación se registra **Solicitada** aunque A tenga una venta vigente: una no
  bloquea a la otra (DEC-14).
- Paso 3: la conservación queda **Autorizada**, en una fila distinta de la venta.

**Resultado obtenido:** **Aprobado.** Con la venta de A ya **Autorizada**, el segundo intento de venta mostró en el formulario «El equipo «B05 Equipo A» ya tiene una venta autorizada: espera a que se resuelva o se aplique antes de registrar otra.» y no se creó otra operación. Al elegir **Conservación de plaza**, el campo de club destino desapareció; la conservación de A se registró **Solicitada** pese a la venta vigente y el Presidente la autorizó en una fila distinta.

**Evidencia:** `CA4-segunda-venta-409.png`, `CA4-conservacion-sin-destino.png`,
`CA4-conservacion-autorizada.png`

**Pruebas automáticas relacionadas:** `OperacionEquipoServiceTest` («RF-104: la conservación de
plaza se registra sin club destino», «RF-103, DEC-132: una segunda venta vigente del mismo equipo
responde 409 RF-103», «DEC-14, DEC-132: una venta vigente no impide registrar la conservación de
plaza»); `PantallaOperacionesEquipo.test.tsx` («la Directiva registra una conservación de plaza sin
club destino»).

### CA-5 · Un usuario de otro club no ve la operación (404)

> Dado un usuario de otro club, cuando consulta la operación, entonces recibe 404.

Hacer este escenario **antes del CA-3**: después del cierre, el equipo A ya es de CamiloSextos.

**Pasos**

1. Como **Presidente**, abrir las herramientas del navegador (pestaña **Red**), recargar **Ventas
   y plazas** y copiar el `id` de la venta de **B** de la respuesta de
   `GET /api/v1/operaciones-equipo`.
2. Iniciar sesión como **Directiva CamiloSextos** y abrir **Ventas y plazas**. Revisar la bandeja.
3. En la pestaña **Red**, clic derecho sobre la petición `GET /api/v1/operaciones-equipo…` →
   **Copy → Copy as fetch**. Pegarla en la **Consola**, cambiar la URL por 
   `/api/v1/operaciones-equipo/<id de B>` y agregar `.then(r => console.log(r.status))` al final.

**Resultado esperado**

- Paso 2: la bandeja aparece vacía («Aún no hay operaciones registradas»): todas las operaciones
  tienen como origen a Real Patzcuaro, y la Directiva solo ve las de su club, aunque CamiloSextos sea
  el destino de las ventas.
- Paso 3: la consola imprime `404`, igual que con un id inexistente (VAL-024).

**Resultado obtenido:** **Aprobado.** La Directiva CamiloSextos vio la bandeja con «0 operaciones en este filtro» y «Aún no hay operaciones registradas», aunque es destino de las tres ventas. La consulta `GET /api/v1/operaciones-equipo/9ae7a6d0-a2fc-4b76-a579-dba10e88e512` (venta de B) con su sesión respondió **404 (Not Found)**. El token se ocultó en la captura.

**Evidencia:** `CA5-bandeja-otro-club.png`, `CA5-consola-404.png` (recortar el encabezado
`Authorization` antes de guardar la captura)

**Pruebas automáticas relacionadas:** `OperacionEquipoServiceTest` («VAL-024: un equipo de otro club
responde 404», «B-05.3, VAL-024: el detalle de otro club responde 404»);
`OperacionEquipoControllerTest` («B-05.3, VAL-024: GET del detalle responde 404…»).

## 4. Defectos encontrados

| Clave | Severidad | Descripción | Estado |
| --- | --- | --- | --- |
| — | — | Ninguno | — |

## 5. Conclusión

B-05 cumple sus cinco criterios de aceptación y la regla de una operación vigente por tipo (DEC-132):
la Directiva solicita, la Presidencia autoriza o rechaza con observaciones, y el cierre de la
temporada aplica en la misma transacción solo las operaciones autorizadas, mueve el equipo vendido al
club destino y conserva el club de origen como historial. No se encontraron defectos. La historia
puede pasar a **Finalizado** al fusionar este documento.

Observaciones (no son defectos):

- El formulario sigue ofreciendo un equipo con venta vigente en el selector; el bloqueo lo da el
  backend con `409 RF-103` y el mensaje se muestra en el mismo formulario.
- La venta de C queda `SOLICITADA` para siempre en una temporada cerrada; es el comportamiento
  esperado y la pantalla explica por qué ya no se puede dictaminar.
- En el Supabase compartido quedan como datos de demostración la «Temporada prueba B-05» (`CERRADA`),
  sus categorías y grupos, y los equipos B05 A (ahora de CamiloSextos), B y C.

## Orden de ejecución recomendado

Preparación → CA-1 → CA-2 → CA-4 → CA-5 → CA-3 (el cierre va al final porque es irreversible y
cambia el club del equipo A).
