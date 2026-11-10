# Guion de demostración del avance — SIGLIM

Guion para mostrar el sistema en vivo cuando la Liga, el docente o el equipo pidan ver el avance.
No inventa datos de negocio: cada valor de captura sale de las pruebas de aceptación de
[`aceptacion/`](aceptacion/README.md) y de las reglas del código, y cada «qué debe verse» está
respaldado por un escenario ya aprobado.

| Campo | Valor |
| --- | --- |
| Rama y versión | `develop` en `4199541` (8 nov 2026) |
| Alcance demostrable | 20 historias con prueba de aceptación aprobada + la autorización y la auditoría transversales (H-02, H-21) |
| Duración | 20 min el núcleo; 35 min el extendido; 50 min si se parte de una base vacía |
| Pantallas | Portal, F-07/F-08 (panel), F-10/F-11 (temporadas), F-15 (clubes), F-21 (canchas), F-12/F-13 (usuarios, roles), F-14 (auditoría), F-33/F-34 (tesorería), B-08 (comunicados), F-32 (catálogos) |
| Regla de oro | Si algo no está implementado, se dice. Este guion incluye los límites para no prometer de más |

---

## 1. Qué se puede demostrar hoy

| Historia | Qué se ve | Ruta | Cuenta |
| --- | --- | --- | --- |
| H-01 | Inicio de sesión, menú según permisos, cierre de sesión sincronizado entre pestañas (DEF-02) | `/login` → `/app` | cualquiera |
| H-02 | Denegación por defecto: módulos y rutas ocultas o 403/404 sin filtrar si el recurso existe | `/app/**` | Capturista vs. Administrador |
| H-04 | Temporada con una sola activa, lista de verificación de 4 requisitos, cierre con motivo y solo lectura | `/app/administracion/temporadas` | Administrador |
| H-05 | Parámetros deportivos versionados e inmutables, rotulados «No oficial» | `…/temporadas/{id}/estructura?pestana=parametros` | Administrador |
| B-02 | Convocatoria de la temporada (referencia, versión y enlace) | `…/temporadas` | Secretario |
| B-03 | Reglas interpretadas: se registran pendientes y las autoriza la Presidencia | `…/estructura?pestana=interpretaciones` | Administrador + Presidente |
| H-06 | Alta de club y de equipo; baja con motivo; la Directiva solo ve su club | `/app/organizacion/clubes` | Administrador o Secretario / Directiva |
| B-04 | Cambio de nombre = club nuevo y club anterior cerrado, con «Proviene de» | `/app/organizacion/clubes` | Administrador o Secretario |
| H-22 | Directiva del club: cambio de cargo pendiente y autorización de la Presidencia | `/app/organizacion/clubes` → pestaña Directiva | Directiva + Presidente |
| H-07 | Cancha, disponibilidad y bloqueo con motivo | `/app/programacion/canchas` | Administrador |
| B-11 | Sedes y asignación de una cancha a su sede | `/app/programacion/canchas` → «Sedes» | Administrador |
| B-12 | Mantenimiento tipificado (bloqueo con tipo propio) | `/app/programacion/canchas` | Administrador |
| H-20 | Control dual de permisos: cambio compatible se aplica, par incompatible queda en solicitud | `/app/administracion/roles` | Administrador + Presidente |
| H-26 | Catálogos de incidencias y artículos, con insignia «No oficial» en lo `[DEMO]` | `/app/disciplina/catalogos` | Administrador |
| H-29 | Cobros manuales y adeudos por club | `/app/tesoreria` | Tesorero / Directiva |
| H-30 | Pago en línea **simulado** en 3 pasos, con comprobante | `/app/tesoreria` | Directiva |
| H-31 | Pago recibido fuera del sistema con comprobante en S3 | `/app/tesoreria` | Tesorero |
| B-31 | Vencimiento automático de cobros por fecha límite (tarea como Sistema) | `/app/tesoreria` | Tesorero |
| B-27 | Comunicado: borrador → revisión → publicado; no se aprueba el propio | `/app/administracion/comunicados` | Secretario + Presidente |
| H-21 | Auditoría filtrable con el motivo completo de cada operación | `/app/administracion/auditoria` | Presidente / Administrador |

**Cifras del avance (decirlas, no enseñarlas todas):** 27 controladores REST, 72 tablas en
`siglim`, 6 migraciones, 73 permisos, 107 archivos de prueba de backend y 66 de frontend.
Del Sprint 2 replanificado, **21 de 24 elementos** están implementados; faltan B-05 (venta de
equipo), B-13 (horarios de cancha de club y comodato) y B-29 (tarifas de arbitraje y cancha).

### Lo que todavía **no** existe (decirlo antes de que lo pregunten)

- **Programación de partidos, cédula, resultados y tabla de posiciones** (M-07, M-08, de H-12 a H-19):
  no hay pantalla ni endpoint. Por eso el portal público (`/`, `/resultados`, `/tabla`) muestra
  estados vacíos a propósito: *no se usan datos de ejemplo*.
- **Registro de jugadores, documentos y árbitros** (M-04, M-05): solo permisos y tablas.
- **Competencias (liguilla, ascensos), reportes REP-xxx, avisos y correo** (M-10, M-12, M-14):
  pendientes de sprint.
- **Personas**: no hay pantalla para darlas de alta; la directiva de H-22 solo busca personas ya
  registradas.
- Fuera de alcance por el ERS §2.6: programación automática, pasarela real, reconocimiento facial,
  WhatsApp/SMS, mapas, API pública, login social.

---

## 2. Antes de la demo (checklist de 20 min)

### 2.1 Entorno

| Paso | Comprobación |
| --- | --- |
| Backend | `cd backend && ./mvnw spring-boot:run`; `GET http://localhost:8080/api/v1/salud` → `{"estado":"OK"}` y `GET /actuator/health` → `UP` (la base de datos del proyecto compartido responde) |
| Frontend | `cd frontend && npm run dev` → `http://localhost:5173` |
| Frontend, variables | No existe `frontend/.env.local`: créalo desde `frontend/.env.example` con `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` y `VITE_API_URL=/api/v1` (los dos primeros, del panel del proyecto; solo la clave publicable) |
| Supabase | Authentication → URL Configuration: *Site URL* `http://localhost:3000` y *Redirect URLs* con `http://localhost:5173/restablecer-contrasena` (solo hace falta si se demuestra una invitación) |
| Navegador | Una ventana normal y una de incógnito, para tener dos sesiones a la vez (Administrador y Presidente) |
| Fecha | Los ejemplos usan el 10 de noviembre de 2026 y la regla de vencimiento de `America/Mexico_City`; si la demo es otro día, corre los días hábiles y el vencimiento como se indica en E7 |

> El backend, al arrancar, ejecuta la tarea de vencimiento de cobros (B-31): cualquier cobro
> `PENDIENTE` con fecha límite anterior a hoy pasa a `VENCIDO`. Es correcto y conviene enseñarlo,
> pero hay que saberlo para no confundirlo con un error.

### 2.2 Cuentas

No se inventan correos ni contraseñas: se usan las cuentas que el equipo ya tiene en el proyecto.
Hacen falta **cuatro** (la quinta, Secretario, solo si se demuestran B-02/B-27):

| Cuenta | Rol y ámbito | Para qué | Nota |
| --- | --- | --- | --- |
| **Administrador** | Administrador general · Liga | temporadas, parámetros, canchas, clubes, catálogos, usuarios y roles | No tiene M-13 ni M-11: por eso no ve Tesorería ni Comunicados |
| **Presidente** | Presidente del Comité Ejecutivo · Liga | aprueba control dual, interpretaciones, directiva y comunicados | Segunda pestaña o incógnito |
| **Tesorero** | Tesorero · Liga | cobros, adeudos y pago externo | No puede pagar en línea |
| **Directiva** | Directiva de club · ámbito **CLUB** del club de la demo | paga un cobro en línea (simulado) y ve solo su club | Si no existe, el Administrador le agrega la asignación en F-12 (acto 3, paso 5) |
| (Secretario) | Secretario · Liga | convocatoria y redacción de comunicados | Opcional para el núcleo |

Si falta una cuenta: el Administrador la invita en F-12 con un correo **del equipo** (Supabase solo
envía el correo integrado a miembros de la organización y con límite por hora, así que la invitación
se prepara con días de anticipación, no minutos antes de la demo). La contraseña se establece desde
el enlace del correo; no se escribe en el guion.

**Reparto de permisos que hay que saber de memoria** (es lo que hace creíble la demo):

- Registrar canchas y clubes: Administrador (o Secretario, si la migración `20261103100000` ya dio
  `organizacion.equipo:EDITAR` al Secretario).
- Asociar convocatoria y redactar comunicados: **solo** Secretario.
- Aprobar comunicados y control dual: **solo** Presidente.
- Cobrar/registrar pago: **solo** Tesorero. Pagar en línea: **solo** Directiva.
- Congelar catálogos de disciplina: Administrador.

### 2.3 Datos base mínimos

| Dato | Cómo se consigue |
| --- | --- |
| Una temporada **ACTIVA** | Si ya hay una (de las corridas de aceptación), se usa. Si no, se crea en el acto 1 (ruta «base vacía») |
| Categoría y grupo de esa temporada | Se crean en el acto 1 si faltan |
| Un club con equipo | Se crea en el acto 3 |
| Un cobro pendiente | Se crea en el acto 5 |

> **Cuidado con la base compartida.** Las temporadas, clubes, canchas, cobros y comunicados **no se
> pueden borrar** (RN-005): lo que se cree queda. Usa el sufijo «Demostración» en los nombres y, si
> vas a ensayar varias veces, hazlo en la base local efímera del equipo, no en el proyecto
> compartido.

---

## 3. Hoja de datos (todo lo que se teclea, en un solo lugar)

| Dato | Valor exacto | Dónde |
| --- | --- | --- |
| Temporada | `Temporada Demostración 2026-27` | Inicio `2026-08-01`, fin `2027-05-31` |
| Categoría | `Libre Demostración` · tipo `Libre` | Estructura → Categorías |
| Grupo | `Grupo 1` | Estructura → Grupos |
| Parámetros v1 | Valores predeterminados (3 / 1 / 0, cédula 48 h, 90 min, bloque 120 min) | Motivo `Versión inicial de la temporada de demostración` |
| Parámetros v2 | Puntos por victoria `4`; plazo de cédula `36` | Motivo `Ajuste de puntos y plazo para la demostración` |
| Convocatoria | Referencia `Convocatoria Demostración 2026-27`, versión `1.0`, enlace `https://liga.mx/convocatoria-demostracion.pdf` | Temporadas |
| Interpretación | `[DEMO] Si un partido se suspende por lluvia después del minuto 60, el marcador al momento de la suspensión se considera resultado final.` | Estructura → Interpretaciones |
| Autorización de la interpretación | `Autorizada durante la demostración interna del avance.` | Presidente |
| Sede | `Unidad Deportiva Demostración` · `Av. Universidad 123, Morelia, Mich.` | Canchas → Sedes |
| Cancha | `Cancha 1 · Demostración` · `Unidad deportiva` · propietario `Liga` · `Pasto sintético, iluminación` | Canchas → Nueva cancha |
| Disponibilidad | `08:00` → `20:00` del sábado de esta semana | Cancha → Agregar disponibilidad |
| Bloqueo | `14:00` → `15:00` del mismo sábado | Motivo `Riego y mantenimiento del césped` |
| Club | `Club Fuego Nuevo Demostración` | Organización → Clubes |
| Cambio de nombre | `Club Fuego Nuevo F.C. Demostración` | Motivo `La asamblea del club aprobó el cambio de nombre` |
| Equipo | `Fuego Nuevo A` · temporada activa · categoría del acto 1 · `Grupo 1` | Detalle del club |
| Baja de equipo (opcional) | Motivo `Se retiró del torneo por desintegración de la plantilla` | Detalle del club |
| Cobro 1 | Concepto `Cuota de inscripción temporada 2026-27 · demostración` · `1500.00` | Inicio hoy · límite hoy + 10 días |
| Cobro 2 | Concepto `Uso de cancha para entrenamiento · demostración` · `350.50` | Inicio hoy · límite hoy + 5 días |
| Pago externo | Medio `Transferencia` · referencia `SPEI-DEMO-001` · un PDF de menos de 5 MB | Tesorería → Registrar pago |
| Comunicado | Título `Arranque de la Copa Morelia 2026 (demostración)` | Cuerpo: `La Liga informa el arranque de la Copa Morelia 2026. La programación y las sedes se publicarán en el portal. Cualquier duda se atiende en las oficinas de la Liga en horario de 9:00 a 15:00.` |
| Nota de aprobación | `Aprobado por unanimidad del Comité Ejecutivo.` | Presidente |
| Devolución (opcional) | `Indica la cancha, el horario y el motivo del mantenimiento antes de publicar.` | Presidente |
| Motivos | Cualquier texto de **10 caracteres o más** | El sistema rechaza los más cortos antes de enviar |

---

## 4. Guion de 20 minutos (núcleo)

Regla de conducción: **una frase de contexto, dos clics, un resultado**. Nunca explicar la pantalla
antes de que aparezca.

### Acto 0 · Portal y sesión (2 min)

1. Abrir `http://localhost:5173/` (sin sesión) y decir: «el portal público es de solo lectura y
   **no muestra datos de ejemplo**; hoy no hay programación publicada, así que aparece vacío a
   propósito». Recorrer `/resultados` y `/tabla` (mismo estado vacío).
2. `Iniciar sesión` como **Administrador** → `/app`.
   **Debe verse:** barra superior con nombre y «Administrador general · Liga»; menú con Inicio,
   Organización, Programación, Disciplina y Administración. El panel está casi vacío a propósito:
   el único indicador que existe hoy es la tarjeta de tesorería, y el Administrador no tiene ese
   permiso — se verá en la sesión del Tesorero (acto 5).
3. Cerrar sesión e iniciar como **Capturista**.
   **Debe verse:** solo Inicio y Partidos. Abrir `http://localhost:5173/app/administracion/temporadas`.
   **Debe verse:** «Error 403 · No tienes permiso para esta acción», sin datos.
   *Frase:* «la denegación es por defecto; el menú se arma con los permisos que devuelve `GET /api/v1/yo`, no con el nombre del rol».

### Acto 1 · Temporada, parámetros y estructura (4 min)

Entrar como **Administrador** → Administración → Temporadas.

- **Si ya hay una temporada activa:**
  1. Abrir la temporada **activa** → «Estructura».
  2. Pestaña **Parámetros**: mostrar «Versión 1 · VIGENTE», las insignias **No oficial** y el
     botón «Ver instantánea».
  3. Crear una temporada nueva (nombre `Temporada Demostración 2026-27 (prueba)`, inicio
     `2026-08-01`) y abrir su **lista de verificación**: dirá «0 de 4 requisitos cumplidos», con el
     renglón RN-058 «La temporada «…» está activa; ciérrala antes de activar esta.» y el botón
     «Activar» deshabilitado. *No activarla.*
- **Si no hay ninguna activa (base vacía):**
  1. «Nueva temporada» → `Temporada Demostración 2026-27`, `2026-08-01` → `2027-05-31`.
  2. Abrir su «Estructura» → pestaña **Parámetros** → «Guardar la versión 1» con el motivo.
     **Debe verse:** «Se guardó la versión 1.» y «Versión 1 vigente · desde hoy · NO OFICIAL».
  3. Pestaña **Categorías** → «Nueva categoría»: `Libre Demostración`, tipo `Libre` → «Asignar» a
     la temporada. Pestaña **Grupos** → en la sección de `Libre Demostración`, agregar `Grupo 1`.
  4. Volver a Temporadas → «Activar». **Debe verse:** la temporada en ACTIVA y la lista de
     verificación en 4/4.
  5. Cambiar «Puntos por victoria» a `4` y «Plazo de validación de la cédula» a `36` →
     «Guardar como nueva versión» + motivo. **Debe verse:** «Se guardó la versión 2.», «Versión 2 ·
     VIGENTE» y «Cambió: Puntos por victoria, Plazo de validación de la cédula»; la instantánea de
     la versión 1 sigue en 3 puntos y 48 h.

*Frase:* «los parámetros son configuración versionada, no constantes del código; cada versión es
inmutable y lo `[DEMO]` se rotula “No oficial” porque la Liga no los ha aprobado (PEN-021)».

### Acto 2 · Canchas, sede y bloqueo (2 min)

Administrador → Programación → Canchas.

1. «Sedes» → «Registrar sede»: `Unidad Deportiva Demostración`,
   `Av. Universidad 123, Morelia, Mich.` → **«Sede «Unidad Deportiva Demostración» registrada.»**
2. «Nueva cancha»: `Cancha 1 · Demostración`, ubicación `Unidad deportiva`, sede la anterior,
   propietario `Liga`, características `Pasto sintético, iluminación` → la fila aparece **ACTIVA**.
3. En el detalle, «Agregar disponibilidad»: sábado de esta semana `08:00`–`20:00` →
   **«Disponibilidad registrada.»** y la franja pasa a **Disponible**.
4. «Bloquear intervalo»: mismo sábado `14:00`–`15:00`, tipo `Bloqueo`, motivo
   `Riego y mantenimiento del césped` → **«Bloqueo registrado.»**; el bloque se pinta encima con su
   motivo. Probar un motivo de 5 letras → **«Escribe un motivo de al menos 10 caracteres.»**
5. *(Opcional, 20 s)* «Bloquear intervalo» con tipo `Mantenimiento` y el mismo motivo → el bloque se
   pinta distinto y se rotula **Mantenimiento** (B-12).

*Frase:* «si esta cancha tuviera un partido programado dentro del bloqueo, el sistema no lo mueve:
responde 409 con los partidos afectados y pide confirmación explícita; reprogramarlos es manual».

### Acto 3 · Club, equipo y cambio de nombre (3 min)

Administrador → Organización → Clubes (si la cuenta es Secretario, igual).

1. «Registrar club»: `Club Fuego Nuevo Demostración` → el detalle se abre solo, en **ACTIVO**.
2. «Cambiar nombre»: `Club Fuego Nuevo F.C. Demostración`, motivo
   `La asamblea del club aprobó el cambio de nombre` → **club nuevo ACTIVO** con «Proviene de
   «Club Fuego Nuevo Demostración»»; el anterior queda **CERRADO** con fecha y motivo, y ya no
   admite equipos.
3. En el club nuevo, «Registrar equipo»: temporada activa, la categoría del acto 1
   (`Libre Demostración`, o la que exista) con su `Grupo 1`, nombre `Fuego Nuevo A` →
   **«Equipo Fuego Nuevo A registrado.»** y la fila «Fuego Nuevo A · <categoría> · Grupo 1 ·
   ACTIVO».
4. *(Opcional)* «Editar» el equipo a `Fuego Nuevo F.C.` y luego «Dar de baja» con el motivo de la
   hoja de datos → la fila queda **BAJA**, con la fecha y el motivo, sin botones: el historial se
   conserva, el equipo no se borra (RN-005).
5. Asignar la cuenta de la **Directiva** a ese club: Administración → Usuarios → panel de la
   cuenta → «Agregar asignación»: rol `Directiva de club`, ámbito `Club`, club
   `Club Fuego Nuevo F.C. Demostración`. Es el paso que habilita los actos 3 y 5 con esa cuenta.
6. En otra ventana (incógnito), iniciar sesión como **Directiva** → Organización → Clubes:
   **debe abrir directo su club**, sin catálogo de la liga, sin «Registrar club» ni «Registrar
   equipo», y con «Datos de tu club y sus equipos.». Si se pide por URL un club ajeno, responde
   404 (no 403): no revela que existe.

*Frase:* «el cambio de nombre no reescribe el club: crea uno nuevo y cierra el anterior con fecha y
motivo, para que el historial de la temporada anterior siga apuntando al club correcto».

### Acto 4 · Usuarios, permisos y control dual (3 min)

Administrador → Administración → Usuarios.

1. Listar las cuentas: nombre, rol, ámbito, estado. Abrir el panel de una cuenta **propia**:
   **«Esta es tu cuenta… no puedes cambiar tus asignaciones ni el estado de tu cuenta»** (VAL-022).
   *Frase:* «no hay autoascenso de privilegios; la regla está en el servicio y se audita todo lo
   demás».
2. Ir a **Roles y permisos** (`/app/administracion/roles`): abrir «Administrador general» (rol
   propio) → casillas deshabilitadas y el aviso de VAL-022; 21 de 73 permisos, solo lectura.
3. Elegir el rol **Secretario (liga)**: tiene `publicacion.comunicado:REDACTAR`. Marcar
   `publicacion.comunicado:APROBAR` (par incompatible: redactar y aprobar lo mismo).
   Pulsar «Solicitar aprobación» y escribir el motivo
   `Se requiere que la Secretaría también apruebe sus comunicados`.
   **Debe verse:** `202`, la matriz **no cambia** y el aviso «El cambio quedó pendiente… el
   Presidente debe aprobarlo o rechazarlo».
4. Pasar a la sesión del **Presidente** (incógnito) → Roles y permisos → pestaña
   **«Solicitudes pendientes»** → **Rechazar** con
   `No procede: concentraría redacción y aprobación de comunicados`.
   **Debe verse:** la solicitud en RECHAZADO, la matriz de Secretario intacta (sigue en REDACTAR
   sin APROBAR) y el contador de pendientes en 0.
5. *(Opcional, 20 s)* Editar un rol y añadir un permiso **compatible** (por ejemplo
   `canchas.cancha:CONSULTAR` a `Comisión Disciplinaria`) → se aplica en el acto con motivo y queda
   en auditoría `CAMBIAR_PERMISOS_ROL`. Anotarlo para revertirlo al final.

*Frase:* «un cambio normal se aplica y se audita; si junta dos permisos que se contradicen, no se
aplica: se convierte en una solicitud que resuelve otra persona con el permiso
`seguridad.permisos:APROBAR_INCOMPATIBLE`. La base tiene además la restricción que lo impide por
SQL».

### Acto 5 · Cobros, pago simulado y pago externo (3 min)

**Paso 0 (solo si se saltó el acto 3):** asignar la cuenta de la Directiva al club de la demo
(Usuarios → panel de la cuenta → «Agregar asignación»: rol `Directiva de club`, ámbito `Club`).

1. Como **Tesorero** → Tesorería (`/app/tesoreria`) → «Nuevo cobro»:
   club de la demo, concepto `Cuota de inscripción temporada 2026-27 · demostración`, monto
   `1500.00`, inicio hoy, límite hoy + 10 días → aviso
   **«Cobro «…» registrado para Club Fuego Nuevo F.C. Demostración por $1,500.00.»**
2. Segundo cobro: `Uso de cancha para entrenamiento · demostración`, `350.50`, límite hoy + 5 días.
   Revisar la pestaña **«Adeudos por club»** y la tarjeta del panel de Inicio: los totales de la
   pantalla coinciden con la API.
3. Probar una validación en vivo: monto `0` → «El monto debe ser mayor que cero.»; monto `350.505`
   → «Escribe un monto mayor que cero, con hasta dos decimales.» (no se llama a la API).
4. Como **Directiva** → Tesorería → cobro de $1,500 → **«Pagar en línea (simulado)»**:
   paso 1 cobro a pagar, paso 2 referencia `SIM-AAAAMMDD-XXXXXX`, **«Aprobar pago simulado»** →
   paso 3 **«Pago registrado»** con comprobante. **Debe verse en los tres pasos** la leyenda
   **«Pago simulado: no se realiza ningún cargo»**; tras cerrar, la fila queda **PAGADO** y sin
   acciones.
5. Como **Tesorero** → cobro de $350.50 → «Registrar pago»: importe `$350.50` fijo, medio
   `Transferencia`, fecha hoy, referencia `SPEI-DEMO-001`, adjuntar un PDF → **«Pago de $350.50
   registrado… el cobro quedó pagado.»**; el recibo queda en S3 y la auditoría guarda solo el
   identificador del documento, tipo y tamaño (nunca la URL ni el hash).
6. Por API, reintentar el pago del cobro que ya está pagado →
   **409 VAL-028**: «El cobro está PAGADO; no admite otro pago». (En pantalla la acción ya no
   aparece: eso también es parte de la regla.)

*Frase:* «no hay pasarela real ni datos de tarjeta: es una simulación explícita (DEC-37). El pago
externo sí exige comprobante y queda ligado al cobro; un pago no puede ser parcial».

### Acto 6 · Comunicado y auditoría (3 min)

1. Como **Secretario** → Administración → Comunicados → «Redactar comunicado»: título y cuerpo de
   la hoja de datos → queda **BORRADOR** → «Enviar a revisión» → **«EN REVISIÓN»** y deja de ser
   editable.
2. Cambiar a **Presidente** → pestaña «En revisión» → «Aprobar y publicar» con la nota
   `Aprobado por unanimidad del Comité Ejecutivo.` → **PUBLICADO** con quién y cuándo.
3. *(Opcional, 30 s)* Separación de funciones: volver como **Secretario** y abrir el comunicado que
   ya está en revisión: **no** aparece «Aprobar y publicar» (solo el permiso de la Presidencia lo
   permite), y por API el intento responde **403 VAL-015**. La regla más fina —que quien
   redacta no pueda aprobar **lo suyo** aunque tenga los dos permisos— se demuestra en H-20, y la
   base la garantiza con la restricción `ck_comunicados_dual`.
   El portal todavía no publica comunicados: eso es B-26 (Sprint 4); no prometerlo.
4. Como **Presidente** → Administración → Auditoría: filtrar por módulo **M-13** y por la acción
   `REGISTRAR_COBRO` o `REGISTRAR_PAGO`; abrir «Ver detalle del registro» y leer el motivo
   completo. Añadir el filtro de usuario o de fechas del día.
   **Debe verse:** la operación con su perfil, fecha, módulo, acción, entidad, id y el JSON del
   detalle, sin datos personales.
5. En la API, para cerrar con broche: `POST /api/v1/auditoria` → **405** `METODO_NO_PERMITIDO`;
   `DELETE /api/v1/auditoria/1` → **404** `NO_ENCONTRADO`. *Frase:* «la auditoría es de solo
   lectura por diseño; el rol de la aplicación no puede modificarla ni truncarla».

**Cierre (1 min):** «Este es el flujo central: identidad, permisos y auditoría; configuración
deportiva versionada; organización; canchas; cobranza con pago simulado y externo; comunicación
oficial. Lo que sigue —programación, cédula, resultados y tabla— se apoya en estos cimientos y
es el siguiente sprint». Enseñar la tabla de §1 o el plan de sprints.

---

## 5. Guion extendido (35 min)

Añadir, en este orden:

| # | Demo | Datos | Tiempo |
| --- | --- | --- | --- |
| E1 | Cierre de temporada con motivo y solo lectura (H-04) | Motivo `Cierre del ciclo 2026-2027 por término del calendario deportivo` | 2 min |
| E2 | Convocatoria (B-02) | Referencia y versión de la hoja; reemplazo a `2.0` con el enlace vacío | 2 min |
| E3 | Interpretación (B-03): registrar y autorizar | Texto y nota de autorización de la hoja | 3 min |
| E4 | Directiva del club (H-22) | Requiere una persona ya registrada y visible para el club; si no la hay, **solo mostrar el historial** de la pestaña | 3 min |
| E5 | Catálogos de disciplina (H-26) | Tipo `AGRESION_VERBAL_DEMO` + artículo `DEMO-DIA-1` (suspensión 3 partidos, jugador) | 3 min |
| E6 | Devolución de comunicado (B-27) | Comunicado nuevo → enviar a revisión → devolver sin observaciones (se rechaza) → devolver con la observación de la hoja → corregir → reenviar | 3 min |
| E7 | Vencimiento (B-31) | Cobro con periodo y límite anteriores a hoy; reiniciar el backend y mostrar cómo pasa a **VENCIDO** con evento `VENCER_COBRO` del perfil **Sistema**, y que un vencido no se paga en línea (**VAL-028**) | 3 min |
| E8 | La Liga ve sus permisos | `GET /api/v1/yo` en la pestaña Red, o la matriz de permisos de un rol cualquiera | 1 min |

> E4 y E7 son las más frágiles: E4 depende de que exista una persona registrada (no hay pantalla
> para crearla) y E7 depende de reiniciar el backend o de que ya haya vencidos. Ensáyalas o
> sáltalas sin pena.

---

## 6. Cómo responder lo que van a preguntar

| Pregunta | Respuesta corta |
| --- | --- |
| «¿Ya programa partidos?» | No. La programación manual es H-12 y H-13, el cambio de estado del partido H-15 y la cédula H-16 y H-17; hoy están las bases: temporada, categoría, grupo, equipo, cancha, disponibilidad y bloqueo, que es todo lo que la programación necesita validar. |
| «¿Y la tabla de posiciones?» | Se calcula con `calcular_tabla()` a partir de resultados oficiales; no se guarda en una tabla. Faltan la captura y la oficialización de resultados (H-16 y H-17) y la pantalla de la tabla (H-18). |
| «¿Por qué el portal está vacío?» | Es una decisión: el portal solo muestra lo `PUBLICADO` y no se usan datos de ejemplo (RF-056, VAL-014). |
| «¿Esto es una pasarela de pago?» | No, es una simulación rotulada (DEC-37): no pide tarjeta ni cobra. La pasarela real está fuera de alcance. |
| «¿Quién puede cambiar permisos?» | El Administrador los edita, pero un par incompatible se convierte en solicitud y la resuelve el Presidente; nadie edita su propio rol ni su propia cuenta (VAL-022). |
| «¿Se puede borrar un club, un cobro, un partido?» | No. Todo cambio es de estado con motivo y queda en auditoría (RN-005, RF-093). |
| «¿Dónde están los datos personales?» | En el esquema `siglim`, que no está expuesto por la API de Supabase: solo entra el backend (ADR-0014); el frontend usa Supabase únicamente para iniciar sesión. |
| «¿Qué falta para terminar?» | Programación y cédula, registros de jugadores, árbitros, resultados, competencias, reportes, avisos y el portal con datos; el detalle está en el [plan de sprints](../planning/sprint-plan.md). |
| «¿Es a prueba de errores?» | Cada operación valida en el servicio (mensaje que explica) y el trigger de la base es la última garantía; las transiciones de estado se rechazan por ambos lados. |
| «¿Cómo sé que funciona?» | 20 historias tienen prueba de aceptación de punta a punta con evidencia, más 173 archivos de pruebas automáticas (107 backend, 66 frontend). |

---

## 7. Plan B

| Si falla… | Qué hacer |
| --- | --- |
| La red o Supabase | El backend responde 500/503 y el panel no carga. Tener listas las capturas de [`aceptacion/evidencias/`](aceptacion/evidencias/) y narrar con ellas. No reiniciar la base en vivo. |
| `frontend/.env.local` | Es el error más probable en una máquina recién clonada: sin `VITE_SUPABASE_*` el login falla. Probarlo la noche anterior. |
| El nombre ya existe (409 DUPLICADO) | Es una validación que se puede lucir: cambiar el nombre a `… (2)` y decir que la unicidad la garantiza la base. |
| No hay temporada activa y no quieres crear una | Mostrar solo lectura de la estructura y los parámetros de la temporada existente; la activación se explica con la lista de verificación. |
| La cuenta Directiva no está asignada | El Administrador la asigna en F-12 (30 s). Si no hay una quinta cuenta, asignar `Directiva de club` a la cuenta del Capturista **solo durante la demo** y revocarla al final. |
| El PDF no sube en H-31 | Revisar tamaño (< 5 MB) y tipo (`application/pdf`); si S3 no está configurado en ese entorno, mostrar el 409/500 y pasar al acto 6: no improvisar. |
| Falta el permiso en el menú | Cerrar sesión y volver a entrar: `GET /api/v1/yo` se consulta al iniciar sesión. |
| La demo se repite | Los nombres no se pueden reutilizar en algunos casos (clubes cerrados, sedes, temporadas). Usar el sufijo ` (2)`, o repetir en la base local efímera. |

---

## 8. Limpieza después de la demo

1. Revertir el cambio compatible de permisos (acto 4, paso 5) con otro motivo y verificar la matriz.
2. Revocar la asignación temporal de Directiva si se usó una cuenta prestada.
3. En catálogos de disciplina (E5), **desactivar** el tipo y el artículo creados (no se borran).
4. No intentar borrar temporadas, clubes, canchas ni cobros: no se puede y no hace falta. Si la
   demo fue en la base compartida, anotar en el issue del sprint los nombres creados.

---

## Anexo · Verificación por API (para quien pida pruebas)

Guardar el token de la sesión y consultar (los listados respetan el ámbito del permiso):

```bash
# Identidad y permisos efectivos (lo que arma el menú)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/yo

# Temporadas y su lista de verificación de activación
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/temporadas
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/temporadas/$TEMPORADA_ID

# Canchas con su disponibilidad
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/canchas

# Cobros y adeudos del club
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/cobros?pagina=0&tamano=20"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/adeudos

# Auditoría filtrada por módulo y acción
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/auditoria?modulo=M-13&accion=REGISTRAR_COBRO&pagina=0&tamano=20"
```

Los errores son RFC 9457 con `codigo` (`VAL-028`, `RF-090`, `RN-058`…), y cada código remite a la
regla del ERS que se incumplió: es la mejor prueba de que las validaciones son de negocio y no
mensajes genéricos.

## Documentos de respaldo por historia

Cada acto tiene su prueba de aceptación con pasos, resultados y capturas:
[H-01](aceptacion/H-01-inicio-sesion.md) ·
[H-03](aceptacion/H-03-usuarios.md) · [H-04](aceptacion/H-04-temporadas.md) ·
[H-05](aceptacion/H-05-parametros-versionados.md) · [H-06](aceptacion/H-06-clubes-equipos.md) ·
[H-07](aceptacion/H-07-canchas.md) · [H-20](aceptacion/H-20-control-dual.md) ·
[H-21](aceptacion/H-21-auditoria.md) · [H-22](aceptacion/H-22-directiva.md) ·
[H-26](aceptacion/H-26-catalogos-disciplinarios.md) · [H-29](aceptacion/H-29-cobros-adeudos.md) ·
[H-30](aceptacion/H-30-pago-simulado.md) · [H-31](aceptacion/H-31-pago-externo.md) ·
[B-02](aceptacion/B-02-convocatoria.md) · [B-03](aceptacion/B-03-interpretaciones.md) ·
[B-04](aceptacion/B-04-cambio-nombre-club.md) · [B-11](aceptacion/B-11-sedes.md) ·
[B-12](aceptacion/B-12-mantenimiento-canchas.md) · [B-27](aceptacion/B-27-comunicados.md) ·
[B-31](aceptacion/B-31-vencimiento-cobros.md)
