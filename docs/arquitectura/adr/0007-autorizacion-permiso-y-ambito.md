# ADR-0007: Autorizar cada operación por permiso y ámbito, con RLS como defensa en profundidad

- **Estado:** Aceptada · el punto 4 (RLS) lo precisa [ADR-0014](0014-esquema-propio-y-roles-de-base-de-datos.md)
- **Fecha:** 2026-10-19
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** DD-009, DP-008, RNF-003, RNF-012, RN-053, RN-054, VAL-015, VAL-021 a VAL-024, RF-094 a RF-096, RF-144, Apéndice E del ERS

## Contexto
El ERS Rev. 0.4 tiene 12 tipos de usuario con permisos por módulo y acción y un **ámbito** (liga,
club o propio). La matriz inicial (Apéndice E) es configurable por el Administrador general, con
dos límites: no puede modificar su propio rol y un par de permisos incompatibles requiere la
aprobación del Presidente (RN-053). Un usuario de club solo ve y modifica información de su club
(RN-054); un jugador o tutor solo la propia o la del menor (RF-144).

La migración de la Rev. 0.3 decidía por **nombre de rol** (`usuario_tiene_rol('ADMINISTRADOR')`),
lo que impide que los permisos sean configurables (DP-008).

## Decisión

### 1. Modelo
- **Permiso** = código `<modulo>.<recurso>:<ACCION>`, por ejemplo `partidos.cedula:CAPTURAR`,
  `partidos.cedula:APROBAR`, `partidos.cedula:APROBAR_VENCIDA`, `programacion.jornada:APROBAR`,
  `seguridad.permisos:APROBAR_INCOMPATIBLE`. El catálogo es **dato de semilla** de la migración;
  cada letra del Apéndice E (C, L, E, A, P, X) se traduce a uno o más permisos concretos.
- **Rol** = conjunto de permisos (`rol_permiso`), editable.
- **Asignación de rol** = perfil + rol + **ámbito** (`LIGA`, `CLUB` o `PROPIO`) + `club_id`
  cuando el ámbito es `CLUB` + `liga_id` (ADR-0011). Una persona puede tener varias asignaciones.
- **Par incompatible** = dos permisos que no deben concentrarse en un rol; catálogo con los cinco
  pares iniciales del ERS.

### 2. Evaluación en el backend (barrera principal)
- Cada método público de servicio llama primero a
  `autorizacion.exigir(permiso, objetivo)`, donde `objetivo` describe a quién pertenece el
  recurso: los clubes involucrados y, para el ámbito propio, los perfiles titulares (el jugador, su
  tutor, el árbitro designado). Cada módulo construye el objetivo de sus recursos.
- Regla: con ámbito `LIGA` se permite en toda la liga; con `CLUB`, si el club de la asignación
  está entre los clubes del objetivo; con `PROPIO`, si el usuario es titular. **Sin una asignación
  que cumpla, se niega** (VAL-015).
- Los **listados** se filtran por ámbito en el SQL (nunca devuelven datos de otro club). El acceso
  directo a un recurso fuera de ámbito responde **404** para no revelar su existencia (VAL-024); la
  falta del permiso responde **403**. Las convenciones de API (paso 5) fijan el formato.
- Las asignaciones del usuario se leen **una vez por petición**, sin caché entre peticiones, para
  que un cambio de permisos surta efecto de inmediato (ADR-0003).
- Actualización 2026-10-27 (H-02.1): las asignaciones se leen en cada llamada a `exigir` o
  `alcance`, sin caché (cumple la intención de «sin caché entre peticiones»; un caché por petición
  fallaría fuera de HTTP y usaría permisos viejos si la petición los cambia); los listados usan
  `autorizacion.alcance(permiso)` en su `WHERE`; el objetivo de un partido incluye a jugadores y
  árbitros designados.
- El código **nunca** compara nombres de rol. `GET /api/v1/yo` devuelve los permisos
  efectivos con su ámbito para que el frontend oculte lo que no aplica; el frontend no es barrera.
- Actualización 2026-10-23 (H-01.1): un solo endpoint, decisión del equipo. `GET /api/v1/yo`
  reúne el perfil, las asignaciones y los permisos efectivos; no existe `GET /api/v1/yo/permisos`.

### 3. Control dual (RF-095, RF-096, RN-053)
- Al editar los permisos de un rol, el servicio rechaza el cambio si el rol pertenece al propio
  Administrador.
- Si el cambio deja en el rol un par incompatible, se guarda como **solicitud de cambio**
  (`Solicitado`) y no se aplica hasta que un usuario con `seguridad.permisos:APROBAR_INCOMPATIBLE`
  (el Presidente en la matriz inicial) la apruebe; quien solicita no puede aprobar.
- Todo cambio, aprobación o rechazo se audita con motivo.
- La separación de funciones que no depende del rol sino de la persona se valida en el servicio del
  módulo: por ejemplo, quien capturó una cédula no puede aprobarla aunque tenga el permiso (VAL-021).

### 4. RLS en PostgreSQL (defensa en profundidad)
- Con el contexto de usuario de ADR-0005, la base evalúa la misma regla con funciones
  `tiene_permiso(permiso, club_id)` y `es_titular(persona_id)` (`SECURITY DEFINER`, `STABLE`).
- Se habilita RLS en las tablas con **datos personales o de ámbito de club**: persona, tutela,
  consentimiento, documento, registro de jugador, cobro, pago, expediente, sanción y notificación.
  Las demás tablas conceden lectura al rol `authenticated` y dejan la regla fina al backend.
- El portal usa el rol `anon` con acceso **solo** a vistas o funciones de datos publicados (DP-006).
- La auditoría solo admite `INSERT` y lectura; `UPDATE` y `DELETE` se niegan a todos los roles
  (RNF-015).

## Alternativas consideradas
- **Permisos por nombre de rol en el código:** simple, pero contradice DD-009 y DP-008.
- **Solo letras C/L/E/A del Apéndice E:** demasiado gruesas; por ejemplo, «M-08 A» no distingue
  aprobar una cédula propia de aprobar una con plazo vencido.
- **Permisos dentro del JWT (claims personalizados):** evita una consulta, pero un cambio de
  permisos no surtiría efecto hasta renovar el token.
- **Solo RLS, sin evaluación en servicios:** los errores llegarían como fallas de SQL sin mensaje
  explicativo y la lógica de ámbito propio (árbitro designado, tutor) sería difícil de expresar.

## Consecuencias
- Cada endpoint necesita pruebas de 401, 403 y acceso fuera de ámbito (RNF-003).
- La regla vive en dos lugares (servicio y RLS); la migración y el servicio deben usar el mismo
  catálogo de permisos. Una prueba de integración compara ambos para los casos principales.
- El catálogo de permisos y su traducción del Apéndice E se definen en el diccionario de datos
  (paso 4) y deben validarse con la Liga (PEN-002, PEN-010).
