# Guion de demostración del avance — SIGLIM

Guion para mostrar el sistema en vivo cuando la Liga, el docente o el equipo pidan ver el avance.
No inventa datos de negocio: cada valor de captura sale de las pruebas de aceptación de
[`aceptacion/`](aceptacion/README.md) y de las reglas del código, y cada «qué debe verse» está
respaldado por un escenario ya aprobado.

| Campo | Valor |
| --- | --- |
| Rama y versión | `v0.3.0` (`main` en `17c9c86`, cierre del Sprint 3, 21 nov 2026) |
| Alcance demostrable | 39 historias con prueba de aceptación aprobada (20 del Sprint 2 y 19 del Sprint 3) + la autorización y la auditoría transversales (H-02, H-21) |
| Duración | 20 min el núcleo del Sprint 2; 15 min más con los actos del Sprint 3 (§4.1); 35 min el extendido; 50 min si se parte de una base vacía |
| Pantallas | Portal, F-07/F-08 (panel), F-10/F-11 (temporadas), F-15/F-16 (clubes, tarjetón), F-17/F-18/F-19 (plantilla, alta de jugador, validación), F-20 (árbitros), F-21 (canchas), F-22/F-23 (programación y revisión de jornada), F-12/F-13 (usuarios, roles), F-14 (auditoría), F-33/F-34 (tesorería), F-35 (mi información), B-08 (comunicados), F-32 (catálogos) |
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

**Agregado en el Sprint 3** (actos 7 a 9, §4.1):

| Historia | Qué se ve | Ruta | Cuenta |
| --- | --- | --- | --- |
| H-08 | Tarjetón de afiliación por temporada: queda SOLICITADA con folio y la Presidencia lo aprueba o rechaza con observaciones | `/app/organizacion/afiliaciones` | Directiva + Presidente |
| H-09 | Alta de jugador por CURP con tutor y consentimiento; la misma CURP no se registra dos veces en la temporada (VAL-017) | `/app/organizacion/jugadores/alta` | Directiva |
| H-10 | Documentos del registro en S3 con visor de enlace temporal (60 s); el Secretario activa o rechaza el registro (VAL-001) | `/app/organizacion/jugadores/:id/documentos` · `/app/organizacion/registros` | Directiva + Secretario |
| H-23 | Baja de un registro ACTIVO con motivo, sin perder su historial | `/app/organizacion/jugadores` | Directiva |
| H-24 | «Mi información» del jugador o tutor, en móvil | `/app/mi-informacion` | Jugador o tutor (vínculo por SQL hasta DEF-10) |
| B-05 | Venta de equipo y conservación de plaza, aplicadas al cerrar la temporada | `/app/organizacion/operaciones-equipo` | Directiva + Presidente |
| B-06 / B-07 | Renovación y transferencia entre temporadas; historial de estados del registro | Asistente de alta y plantilla | Directiva |
| H-11 | Árbitros presentados por el club (PROPUESTO) y validados o rechazados por la Comisión | `/app/programacion/arbitros` | Directiva + Comisión de Arbitraje |
| B-08 / B-09 | Cuota de árbitros por club y disponibilidad arbitral | `/app/programacion/arbitros` | Comisión de Arbitraje |
| B-13 / B-14 | Horarios de cancha de club y comodato; trámites de préstamo, comodato y oferta con oficio | `/app/programacion/canchas` · `/app/programacion/tramites-cancha` | Administrador / Directiva |
| H-12 | Programación manual de la jornada con conflictos explicados (VAL-003 a VAL-006) y verificación sin guardar | `/app/programacion/jornadas` | Programador |
| B-15 | Advertencia de localía que se acepta con motivo | `/app/programacion/jornadas` | Programador |
| H-13 | Envío a aprobación, dictamen y publicación de la jornada | `/app/programacion/jornadas/:id/revision` | Programador + Presidente |
| B-29 / B-30 | Tarifas por temporada y categoría; cobros automáticos al publicar la jornada | `/app/tesoreria` → «Tarifas» | Tesorero |
| B-01 | Solicitudes ARCO con seguimiento (solo API; la evidencia visual es la auditoría) | `/api/v1/solicitudes-arco` · `/app/administracion/auditoria` | Administrador |

**Cifras del avance (decirlas, no enseñarlas todas):** 43 controladores REST, 72 tablas en
`siglim`, 14 migraciones, 73 permisos, 161 archivos de prueba de backend y 90 de frontend (839
pruebas). El Sprint 3 terminó **48 SP**: los 41 comprometidos y los 7 que venían del Sprint 2
(B-05, B-13 y B-29).

### Lo que todavía **no** existe (decirlo antes de que lo pregunten)

- **Designación de árbitros, estado del partido, cédula, resultados y tabla de posiciones** (H-14 a
  H-18): son del Sprint 4. La jornada ya se publica, pero el portal público (`/`, `/resultados`,
  `/tabla`) todavía no la consulta (H-19), así que muestra estados vacíos a propósito: *no se usan
  datos de ejemplo*.
- **Disciplina** (expedientes y sanciones, H-27): solo existen los catálogos de H-26.
- **Vínculo de una cuenta con su persona** (DEF-10): para enseñar «Mi información» (H-24) la cuenta
  se vincula por SQL en la base local; se corrige en el Sprint 4.
- **Competencias (liguilla, ascensos), reportes REP-xxx, avisos y correo** (M-10, M-12, M-14):
  pendientes de sprint.
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
oficial». Si el tiempo da, seguir con los actos del Sprint 3 (§4.1); si no, enseñar la tabla de §1.

### 4.1 Actos del Sprint 3 (15 min)

Hacen falta, además, las cuentas de **Secretario**, **Comisión de Arbitraje** y **Programador de
partidos**, y una temporada **ACTIVA** con categoría, grupo, dos equipos de clubes distintos y dos
canchas disponibles el mismo sábado. Los nombres llevan el sufijo «Demostración», y la CURP del
jugador es una de las ficticias de la prueba de aceptación de H-10 (§2): nunca una CURP real.

#### Acto 7 · Afiliación, jugador y documentos (6 min)

1. Como **Directiva** → Organización → Afiliaciones → capturar el tarjetón del club en la temporada
   activa. **Debe verse:** el tarjetón en **SOLICITADA** con su folio.
2. Como **Presidente** → la misma bandeja → abrir el tarjetón → «Aprobar». **Debe verse:**
   **APROBADA** con quién y cuándo. *(Rechazar sin observaciones no se permite: enseñarlo si sobra
   tiempo.)*
3. Como **Directiva** → Organización → Jugadores → «Registrar jugador»: buscar la CURP; como es nueva,
   capturar los datos de un **menor**, su tutor con el parentesco y el consentimiento. **Debe verse:**
   el registro en **PENDIENTE_VALIDACION** y el paso 4 (documentos).
4. En el paso 4, cargar un archivo cuyo contenido no es imagen ni PDF aunque se llame `.pdf`.
   **Debe verse:** «El tipo de archivo no está permitido…» (415, por la firma del archivo). Después
   cargar los documentos que pide la categoría: quedan **PENDIENTE**.
5. Como **Secretario** → Organización → Registros → abrir el registro. **Debe verse:** «Activar
   registro» deshabilitado mientras haya documentos pendientes (VAL-001). Abrir un documento en el
   visor (el enlace vence a los 60 s y no se guarda), aprobar cada uno y «Activar registro» →
   **ACTIVO**.
6. Volver a registrar la misma CURP en otro equipo de la temporada. **Debe verse:** el rechazo
   **VAL-017** con el club y el equipo donde ya está, sin otros datos.

*Frase:* «la identidad es única y global por CURP; un menor necesita tutor y consentimiento, y nadie
juega sin documentos revisados por la Liga».

#### Acto 8 · Árbitros (2 min)

1. Como **Directiva** → Programación → Árbitros → presentar un árbitro. **Debe verse:**
   **PROPUESTO**, ligado a su club.
2. Como **Comisión de Arbitraje** → la pestaña de propuestos → validar con una nota. **Debe verse:**
   **VALIDADO**; la nota queda en la auditoría (DEF-09). Enseñar el indicador de cuota del club
   (B-08) y la disponibilidad del árbitro (B-09).

#### Acto 9 · Jornada sin conflictos y publicación (6 min)

1. Como **Programador** → Programación → Jornadas → elegir grupo y jornada → registrar un partido
   en la Cancha 1 a las 10:00 del sábado.
2. Registrar otro partido en la **misma cancha y horario**. **Debe verse:** «La cancha «…» ya está
   ocupada por el partido «…» del … a …» (**409 VAL-004**) y el partido no se guarda. Repetir con
   un **equipo** que ya juega en ese bloque → **VAL-003**.
3. «Verificar conflictos». **Debe verse:** la lista de conflictos sin guardar nada. Si aparece la
   advertencia de localía (B-15), aceptarla con motivo.
4. Con la jornada sin conflictos → «Enviar a aprobación». **Debe verse:** **En revisión** y los
   partidos ya sin «Editar».
5. Como **Presidente** → la revisión de la jornada → «Devolver» sin observaciones (se rechaza) y
   después «Aprobar y publicar». **Debe verse:** «Jornada N aprobada y publicada…» y la insignia
   **Publicado**.
6. Como **Tesorero** → Tesorería: los **cobros automáticos** de la jornada publicada según las
   tarifas de la pestaña «Tarifas» (B-29, B-30).

*Frase:* «el sistema no programa solo (eso está fuera de alcance): valida y explica cada choque, y
nada se publica sin la aprobación de alguien distinto de quien programó».

**Cierre del Sprint 3 (30 s):** «Lo que sigue —designar árbitros, jugar el partido, capturar y
validar la cédula, la tabla y el portal con resultados— es el Sprint 4».

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
| «¿Ya programa partidos?» | Sí, de forma manual (H-12, H-13): el sistema rechaza y explica los conflictos, y la jornada se publica con la aprobación de la Presidencia. La programación automática está fuera de alcance. El cambio de estado del partido (H-15) y la cédula (H-16, H-17) son del Sprint 4. |
| «¿Y la tabla de posiciones?» | Se calcula con `calcular_tabla()` a partir de resultados oficiales; no se guarda en una tabla. Faltan la captura y la oficialización de resultados (H-16 y H-17) y la pantalla de la tabla (H-18), en el Sprint 4. |
| «¿Por qué el portal está vacío?» | Es una decisión: el portal solo muestra lo `PUBLICADO` y no usa datos de ejemplo (RF-056, VAL-014). La jornada ya se publica, pero el portal la consultará con H-19 (Sprint 4). |
| «¿Esto es una pasarela de pago?» | No, es una simulación rotulada (DEC-37): no pide tarjeta ni cobra. La pasarela real está fuera de alcance. |
| «¿Quién puede cambiar permisos?» | El Administrador los edita, pero un par incompatible se convierte en solicitud y la resuelve el Presidente; nadie edita su propio rol ni su propia cuenta (VAL-022). |
| «¿Se puede borrar un club, un cobro, un partido?» | No. Todo cambio es de estado con motivo y queda en auditoría (RN-005, RF-093). |
| «¿Dónde están los datos personales?» | En el esquema `siglim`, que no está expuesto por la API de Supabase: solo entra el backend (ADR-0014); el frontend usa Supabase únicamente para iniciar sesión. |
| «¿Qué falta para terminar?» | Designación de árbitros, estado del partido, cédula, resultados, tabla, disciplina, avisos y el portal con datos (Sprint 4); competencias y reportes quedan como extras. El detalle está en el [plan de sprints](../planning/sprint-plan.md). |
| «¿Es a prueba de errores?» | Cada operación valida en el servicio (mensaje que explica) y el trigger de la base es la última garantía; las transiciones de estado se rechazan por ambos lados. |
| «¿Cómo sé que funciona?» | 39 historias tienen prueba de aceptación de punta a punta con evidencia, hecha por un integrante distinto de quien programó, más 251 archivos de pruebas automáticas (161 de backend y 90 de frontend). |

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

Sprint 3:
[H-08](aceptacion/H-08-afiliacion.md) · [H-10](aceptacion/H-10-registro-documentos.md) (incluye
H-09) · [H-11](aceptacion/H-11-arbitros.md) · [H-12](aceptacion/H-12-conflictos.md) ·
[H-13](aceptacion/H-13-publicacion-jornada.md) · [H-23](aceptacion/H-23-baja-registro.md) ·
[H-24](aceptacion/H-24-mi-informacion.md) · [B-01](aceptacion/B-01-solicitudes-arco.md) ·
[B-05](aceptacion/B-05-venta-equipo.md) · [B-06](aceptacion/B-06-renovacion-transferencia.md) ·
[B-07](aceptacion/B-07-historial-registro.md) · [B-08](aceptacion/B-08-cuota-arbitros.md) ·
[B-09](aceptacion/B-09-disponibilidad-arbitral.md) · [B-13](aceptacion/B-13-horarios-club.md) ·
[B-14](aceptacion/B-14-tramites-cancha.md) · [B-15](aceptacion/B-15-advertencia-localia.md) ·
[B-29](aceptacion/B-29-tarifas.md) · [B-30](aceptacion/B-30-cobros-automaticos.md) ·
[DEF-07](aceptacion/DEF-07-migraciones-compartido.md)
