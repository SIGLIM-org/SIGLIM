"""Datos del backlog v4 de SIGLIM (fuente única del CSV y del plan de sprints).

Cada elemento cita sus requisitos del ERS Rev. 0.4.1, las pantallas aprobadas (F-xx / B-xx de
docs/diseno-ui/pantallas) y los endpoints de las convenciones de API v2.0. Las subtareas se
estiman en horas; las historias y tareas, en Story Points (1 SP ≈ 3 h de trabajo del equipo,
supuesto de planificación; se recalibra con la velocidad real).
"""

# ---------------------------------------------------------------------------
# Sprints (DEC-08 y aprobación del equipo del 20 oct 2026)
# ---------------------------------------------------------------------------
SPRINTS = {
    1: dict(inicio='2026-10-12', fin='2026-10-26', semanas=2, capacidad_h=27, capacidad_sp=9,
            nota='Sprint activo en Jira. Los primeros 8 días se dedicaron a la reformulación documental '
                 '(ERS 0.4.1, BPMN, arquitectura, BD, API y diseño), que no entra a este backlog; quedan '
                 'unos 6 días (≈ 27 h del equipo).',
            objetivo='Un usuario interno inicia sesión con Supabase Auth y llega a su panel, que muestra solo '
                     'los módulos de sus permisos; backend y frontend corren contra el esquema `siglim` ya '
                     'aplicado en Supabase.'),
    2: dict(inicio='2026-10-26', fin='2026-11-09', semanas=2, capacidad_h=54, capacidad_sp=18,
            nota='60 h nominales (3 personas × 10 h × 2 semanas) menos el 2 de noviembre.',
            objetivo='La Liga configura su operación: cuentas con rol y ámbito, temporada con categorías, '
                     'grupos y parámetros versionados, clubes, equipos y canchas con disponibilidad; cada '
                     'operación se autoriza por permiso y queda auditada.'),
    3: dict(inicio='2026-11-09', fin='2026-11-23', semanas=2, capacidad_h=54, capacidad_sp=18,
            nota='60 h nominales menos el 16 de noviembre.',
            objetivo='Los clubes afilian y registran jugadores por CURP con documentos validados, presentan '
                     'árbitros, y el Programador arma una jornada sin conflictos que el Presidente aprueba y '
                     'publica.'),
    4: dict(inicio='2026-11-23', fin='2026-12-07', semanas=2, capacidad_h=60, capacidad_sp=20,
            nota='60 h nominales; incluye la demostración final.',
            objetivo='Un partido publicado se juega de punta a punta: árbitros designados, cambio de estado, '
                     'cédula capturada y validada, resultado oficial, tabla recalculada y todo visible en el '
                     'portal público.'),
}
HORAS_POR_SP = 3

# ---------------------------------------------------------------------------
# Épicas
# ---------------------------------------------------------------------------
EPICAS = [
    ('E01', 'M-01', 'Acceso, permisos y auditoría',
     'Cuentas por invitación con Supabase Auth, roles con ámbito (liga, club, propio), autorización por '
     'permiso `modulo.recurso:ACCION` con denegación por defecto, control dual de permisos, consentimiento '
     'y auditoría inalterable. Incluye la base técnica de backend y frontend que comparten todos los '
     'módulos.', 'RF-093 a RF-098, RF-134; RNF-003, RNF-012 a RNF-015; ADR-0003, ADR-0005, ADR-0007, ADR-0014'),
    ('E02', 'M-02', 'Temporadas y normativa',
     'Temporada activa única, categorías con jerarquía, grupos y parámetros deportivos versionados '
     'rotulados «No oficial» mientras sean [DEMO].', 'RF-001 a RF-007, RF-099, RF-100'),
    ('E03', 'M-03', 'Organización deportiva',
     'Clubes, afiliación por temporada (tarjetón), directiva, equipos, personas por CURP y tutela de '
     'menores.', 'RF-008 a RF-012, RF-101 a RF-106'),
    ('E04', 'M-04', 'Registro de jugadores y elegibilidad',
     'Alta y baja de jugadores con unicidad por CURP, documentos privados en AWS S3, validación del '
     'Secretario, estados del registro, elegibilidad y consulta propia.',
     'RF-013 a RF-020, RF-107 a RF-111, RF-144; RNF-018; ADR-0013'),
    ('E05', 'M-05', 'Árbitros y designaciones',
     'Árbitros presentados por los clubes, validación de la Comisión de Arbitraje y designación a '
     'partidos sin árbitros del propio club.', 'RF-021 a RF-024, RF-112 a RF-115'),
    ('E06', 'M-06', 'Canchas y recursos',
     'Catálogo de canchas, disponibilidad y bloqueos; en backlog, sedes, comodatos y trámites de cancha.',
     'RF-027 a RF-032, RF-116 a RF-119'),
    ('E07', 'M-07', 'Programación manual',
     'Jornadas y partidos registrados a mano con detección de conflictos duros, ajustes con motivo y '
     'aprobación del Presidente antes de publicar.', 'RF-033, RF-036, RF-039, RF-040, RF-120, RF-121'),
    ('E08', 'M-08', 'Partidos, cédula y resultados',
     'Máquina de estados del partido, captura y validación de la cédula, resultado oficial versionado y '
     'tabla calculada con desempates configurables.', 'RF-025, RF-026, RF-043 a RF-056, RF-122 a RF-127'),
    ('E09', 'M-09', 'Disciplina',
     'Catálogos de incidencias y artículos, expedientes automáticos, resolución con sanción y efectos.',
     'RF-057 a RF-068, RF-128 a RF-133'),
    ('E10', 'M-10', 'Competencias y fases finales',
     'Ascensos y descensos con validación del Presidente y liguilla. Todo es backlog (B).', 'RF-069 a RF-081'),
    ('E11', 'M-11', 'Portal público y publicación',
     'Portal de solo lectura con lo PUBLICADO (programación, resultados, tablas) y, en backlog, calendario, '
     'comunicados, sanciones, equipos y estadísticas con consentimiento.', 'RF-082 a RF-090, RF-145; RNF-002'),
    ('E12', 'M-12', 'Reportes y exportación',
     'Reportes REP-001 a REP-014 y exportación a PDF y Excel. Todo es backlog (B).', 'RF-091, RF-092'),
    ('E13', 'M-13', 'Tesorería y cobros',
     'Cobros manuales, pago en línea simulado, pagos externos con comprobante y adeudos; en backlog, '
     'tarifas, cobros automáticos y vencimientos.', 'RF-135 a RF-141; ADR-0012'),
    ('E14', 'M-14', 'Notificaciones',
     'Avisos dentro de la aplicación; en backlog, correo (proveedor pendiente, PEN-034).',
     'RF-142, RF-143; ADR-0010'),
]


def S(titulo, hecho, horas, capa):
    """Subtarea: título, condición de terminado, horas, capa (backend, frontend, bd, pruebas, infra, docs)."""
    return dict(titulo=titulo, hecho=hecho, horas=horas, capa=capa)


# ---------------------------------------------------------------------------
# Historias y tareas del núcleo (N). moscow: Must = comprometido en sprint; Should = N sin sprint.
# ---------------------------------------------------------------------------
ITEMS = [
    # ======================= SPRINT 1 =======================
    dict(k='T-01', tipo='Tarea', epica='E01', sprint=1, moscow='Must', prio='Highest', sp=1,
         titulo='Aplicar el esquema inicial en Supabase y registrar al administrador',
         desc='Crear el proyecto de Supabase de desarrollo del equipo, ejecutar la migración '
              '`20261020090000_esquema_inicial.sql` y dar de alta al primer Administrador general. Es '
              'requisito de todo lo demás: hoy la migración no está aplicada.',
         resultado='Proyecto Supabase con el esquema `siglim`, semilla cargada y un administrador que puede '
                   'iniciar sesión.',
         rf=['DEC-42', 'ADR-0004', 'ADR-0014', 'RF-094'], ui=[], api=[], dep=[],
         sub=[S('Crear el proyecto Supabase y aplicar la migración',
                'La migración corre sin errores en el SQL Editor; `siglim` no aparece en Exposed schemas; '
                'las credenciales quedan solo en los `.env` locales de cada integrante.', 1.5, 'infra'),
              S('Registrar al Administrador general y verificar la semilla',
                'Usuario invitado en Authentication; `registrar_administrador_inicial` devuelve su perfil; '
                'consulta de verificación: 13 roles, 73 permisos, 1 liga.', 1.5, 'bd')]),

    dict(k='T-02', tipo='Tarea', epica='E01', sprint=1, moscow='Must', prio='Highest', sp=3,
         titulo='Crear el esqueleto del backend Spring Boot con seguridad y contexto de base de datos',
         desc='Proyecto Spring Boot 4.1 / Java 21 con Spring JDBC y los paquetes por módulo de ADR-0006. '
              'Incluye la validación del JWT de Supabase (ADR-0003), el contexto transaccional que fija el '
              'perfil y adopta `siglim_app` o `siglim_publico` (ADR-0014), el manejador global de errores '
              'RFC 9457 con la tabla HINT → HTTP de las convenciones de API §3.2 y el endpoint de salud.',
         resultado='`./mvnw verify` en verde; `GET /api/v1/salud` responde; una petición con token válido '
                   'ejecuta SQL como `siglim_app` con el perfil del usuario.',
         rf=['RNF-003', 'RNF-011', 'RNF-013', 'RNF-019', 'ADR-0002', 'ADR-0003', 'ADR-0006', 'ADR-0014'],
         ui=[], api=['GET /api/v1/salud'], dep=['T-01'],
         sub=[S('Generar el proyecto con paquetes por módulo y configuración por variables de entorno',
                'Estructura `mx.tecnm.morelia.siglim.<modulo>`; `backend/.env.example` documenta cada variable; '
                'sin secretos en el repositorio.', 2, 'backend'),
              S('Validar el JWT de Supabase con JWKS',
                'Peticiones sin token o con token vencido responden 401 `NO_AUTENTICADO`; prueba automatizada.',
                2, 'backend'),
              S('Implementar el contexto transaccional (`set_config` + `SET LOCAL ROLE`)',
                'Prueba de integración contra PostgreSQL local: el trigger de auditoría registra el perfil del '
                'token; sin contexto, RLS niega.', 3, 'backend'),
              S('Implementar el manejador de errores RFC 9457 y la tabla HINT → HTTP',
                'Pruebas: `P0001` con HINT `VAL-003` → 409; `23P01` → 409 `VAL-004`; `42501` en lectura → 404.',
                2, 'backend')]),

    dict(k='T-03', tipo='Tarea', epica='E01', sprint=1, moscow='Must', prio='Highest', sp=3,
         titulo='Crear el esqueleto del frontend React con el sistema de diseño aprobado',
         desc='Proyecto React 19 + TypeScript + Vite + Tailwind CSS 4 + TanStack Query. Tokens de '
              '`DESIGN.md`, layouts del portal (`/`) y de la aplicación interna (`/app`, barra '
              'lateral y superior), cliente HTTP que agrega el token y traduce los errores RFC 9457, y '
              'componentes base: botón, insignia de estado, insignia «No oficial», modal con motivo, alerta '
              'de bloqueo, estado vacío y de carga, páginas 403/404 (F-36 resuelto con DESIGN.md).',
         resultado='`npm run lint && npm test && npm run build` en verde; los layouts se ven como F-01 y F-07 '
                   'sin desplazamiento horizontal a 360 px.',
         rf=['RNF-006', 'RNF-007', 'RNF-008', 'PEN-014'], ui=['F-01', 'F-07', 'F-36'], api=[], dep=[],
         sub=[S('Generar el proyecto y configurar tokens, tipografía y rutas',
                'Colores, tipografías (Inter, JetBrains Mono) y radios de DESIGN.md como tokens de Tailwind; '
                'rutas `/` y `/app` con `RutaProtegida`.', 2, 'frontend'),
              S('Construir los layouts del portal y de la aplicación interna',
                'Encabezado del portal y barra lateral colapsable; responsivos desde 360 px; foco visible.',
                3, 'frontend'),
              S('Construir los componentes base y las páginas de estado',
                'Botones, insignias con ícono y texto, modal de motivo (mínimo 10 caracteres), alerta de '
                'bloqueo, vacío, carga, 403 y 404, con pruebas de componente.', 2.5, 'frontend'),
              S('Crear el cliente API con manejo de `ApiError`',
                'Agrega `Authorization`, expone `problema.codigo` y `errores`; prueba unitaria con un 409.',
                1.5, 'frontend')]),

    dict(k='H-01', tipo='Historia', epica='E01', sprint=1, moscow='Must', prio='Highest', sp=2,
         titulo='Iniciar sesión y ver el panel con mis módulos permitidos',
         como='usuario interno de la Liga', quiero='iniciar sesión con mi correo y contraseña y llegar a un panel '
              'que muestre solo los módulos que mis permisos me dejan usar',
         para='trabajar sin ver ni intentar acciones que no me corresponden',
         desc='Inicio de sesión solo con correo (Q-UI-01) en la pantalla F-05 aprobada, recuperación de '
              'contraseña de Supabase, cierre de sesión y `GET /api/v1/yo` con perfil, asignaciones y permisos '
              'efectivos. El panel (F-07/F-08) es por ahora el marco con la navegación filtrada; sus tarjetas '
              'de pendientes se completan en cada módulo. El 2FA y «Mantener sesión activa» no se implementan '
              '(PEN-044 y PEN-030).',
         ca=['Dado un usuario invitado y activo, cuando ingresa correo y contraseña correctos, entonces llega a '
             '`/app` y ve su nombre, rol y ámbito en la barra superior.',
             'Dado un correo o contraseña incorrectos, cuando intenta entrar, entonces ve «Correo o contraseña '
             'incorrectos» sin revelar cuál falló.',
             'Dado un usuario con rol Capturista, cuando abre el panel, entonces la barra lateral solo muestra '
             'los módulos de sus permisos según `GET /api/v1/yo`.',
             'Dada una sesión iniciada, cuando cierra sesión, entonces el token se elimina y `/app` redirige al '
             'inicio de sesión.',
             'La pantalla no ofrece registro libre, folio, proveedores externos ni 2FA.'],
         rf=['RF-094', 'RNF-013', 'RNF-014', 'UI-001', 'UI-002', 'VAL-015'], ui=['F-05', 'F-07', 'F-08'],
         api=['GET /api/v1/yo'], dep=['T-02', 'T-03'],
         sub=[S('Implementar `GET /api/v1/yo` con asignaciones y permisos efectivos',
                'Devuelve perfil, asignaciones (rol, ámbito, club) y lista de permisos; pruebas 200 y 401.',
                2, 'backend'),
              S('Construir la pantalla de inicio de sesión (F-05) con Supabase Auth',
                'Correo, contraseña, mostrar/ocultar, recuperar contraseña y mensajes de error; sin folio ni 2FA.',
                2, 'frontend'),
              S('Filtrar la navegación del panel por permisos',
                'La barra lateral se arma desde `/yo`; prueba con dos roles distintos.', 1, 'frontend'),
              S('Prueba de aceptación del inicio y cierre de sesión',
                'Escenarios de los criterios automatizados o documentados con evidencia.', 1, 'pruebas')]),

    # ======================= SPRINT 2 =======================
    dict(k='H-02', tipo='Historia', epica='E01', sprint=2, moscow='Must', prio='Highest', sp=3,
         titulo='Autorizar cada operación por permiso y ámbito y auditar las operaciones críticas',
         como='Presidente del Comité Ejecutivo', quiero='que cada acción se permita solo con el permiso y el '
              'ámbito correctos y que las operaciones críticas queden auditadas',
         para='confiar en que nadie actúa fuera de su función y poder rastrear cualquier cambio',
         desc='Servicio `autorizacion.exigir(permiso, objetivo)` de ADR-0007 con denegación por defecto, filtro '
              'por ámbito en listados, respuesta 404 fuera de ámbito, y servicio de auditoría que todos los '
              'módulos usan en la misma transacción. Es la base que reutilizan todas las historias siguientes.',
         ca=['Dado un usuario sin el permiso, cuando llama a un endpoint protegido, entonces recibe 403 con '
             '`codigo` VAL-015.',
             'Dado un usuario con ámbito CLUB, cuando consulta un recurso de otro club, entonces recibe 404 sin '
             'revelar que existe (VAL-024).',
             'Dado un listado, cuando lo consulta un usuario de club, entonces solo recibe filas de su club y el '
             'total cuenta solo esas.',
             'Dada una operación crítica, cuando se confirma, entonces existe un registro de auditoría con '
             'usuario, fecha, módulo, acción, entidad y motivo, y nadie puede modificarlo (RNF-015).'],
         rf=['RF-093', 'RNF-003', 'RNF-012', 'RNF-015', 'VAL-015', 'VAL-016', 'VAL-024'], ui=['F-36'],
         api=['Todos (§5 de las convenciones de API)'], dep=['H-01'],
         sub=[S('Implementar `autorizacion.exigir` y el objetivo por ámbito',
                'Pruebas parametrizadas LIGA, CLUB y PROPIO; sin coincidencia → 403.', 3, 'backend'),
              S('Implementar el servicio transversal de auditoría',
                'Registro en la misma transacción; prueba de que UPDATE/DELETE sobre `auditoria` fallan.',
                2, 'backend'),
              S('Crear el arnés de pruebas 401/403/404 reutilizable por endpoint',
                'Utilidad de pruebas que cada historia usa para sus endpoints (RNF-003).', 2, 'pruebas'),
              S('Ocultar en la interfaz las acciones sin permiso y manejar 403/404',
                'Componentes condicionados por permisos; páginas 403/404 en uso.', 2, 'frontend')]),

    dict(k='H-03', tipo='Historia', epica='E01', sprint=2, moscow='Must', prio='High', sp=2,
         titulo='Invitar usuarios y asignarles roles con ámbito',
         como='Administrador general', quiero='invitar cuentas y asignarles uno o más roles con su ámbito',
         para='que cada integrante de la Liga y de los clubes trabaje con sus permisos',
         desc='Pantalla F-12. La invitación usa Supabase Auth (correo integrado mientras se define PEN-034) y '
              'crea el perfil; las asignaciones llevan rol, ámbito y club cuando el ámbito es Club.',
         ca=['Dado el Administrador, cuando invita un correo con rol y ámbito, entonces se crea el perfil, la '
             'asignación y el usuario recibe la invitación.',
             'Dada una asignación con ámbito CLUB, cuando se guarda sin club, entonces se rechaza con 400.',
             'Dada una cuenta, cuando el Administrador la desactiva con motivo, entonces ya no puede operar y el '
             'cambio queda auditado.',
             'Dado el Administrador, cuando intenta cambiar las asignaciones de su propio rol, entonces se '
             'rechaza (VAL-022).'],
         rf=['RF-094', 'VAL-022', 'UI-029'], ui=['F-12'],
         api=['GET/POST /usuarios', 'PATCH /usuarios/{id}', 'POST /usuarios/{id}/asignaciones',
              'POST /asignaciones-rol/{id}/revocacion'], dep=['H-02'],
         sub=[S('Endpoints de usuarios, invitación y asignaciones',
                'Invitación vía API de administración de Supabase desde el backend; pruebas 201, 400, 403.',
                3, 'backend'),
              S('Pantalla de usuarios con panel lateral e invitación (F-12)',
                'Tabla con filtros, modal de invitación y revocación con motivo.', 2.5, 'frontend'),
              S('Pruebas de aceptación de invitación y desactivación',
                'Escenarios de los criterios con evidencia.', 0.5, 'pruebas')]),

    dict(k='H-04', tipo='Historia', epica='E02', sprint=2, moscow='Must', prio='High', sp=2,
         titulo='Gestionar temporadas con una sola activa y proteger las cerradas',
         como='Administrador general', quiero='crear, activar y cerrar temporadas',
         para='trabajar siempre sobre una temporada vigente sin alterar el histórico',
         desc='Pantalla F-10. Activar exige categorías, grupos y parámetros (lista de verificación); cerrar '
              'pide motivo y no se deshace. Las temporadas cerradas son solo lectura.',
         ca=['Dada una temporada ACTIVA, cuando se intenta activar otra, entonces se rechaza con el mensaje de '
             'la temporada activa.',
             'Dada una temporada sin parámetros, cuando se intenta activar, entonces se rechaza (RF-100) y la '
             'lista de verificación muestra lo que falta.',
             'Dada una temporada CERRADA, cuando se intenta editar, entonces se rechaza y sus datos se conservan.',
             'El cierre pide motivo y queda en auditoría.'],
         rf=['RF-001', 'RF-003', 'UI-003', 'RN-005'], ui=['F-10'],
         api=['GET/POST/PATCH /temporadas', 'POST /temporadas/{id}/activacion', 'POST /temporadas/{id}/cierre'],
         dep=['H-02'],
         sub=[S('Endpoints de temporadas con activación y cierre',
                'Transiciones según `estado_temporada`; pruebas de la regla de una activa y de solo lectura.',
                3, 'backend'),
              S('Pantalla de temporadas con lista de verificación (F-10)',
                'Tabla, botón de activar deshabilitado con explicación, modal de cierre con motivo.', 2.5, 'frontend'),
              S('Prueba de aceptación del ciclo de vida de la temporada', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    dict(k='H-05', tipo='Historia', epica='E02', sprint=2, moscow='Must', prio='High', sp=3,
         titulo='Configurar categorías, grupos y parámetros deportivos versionados',
         como='Administrador general', quiero='definir las categorías con su jerarquía, los grupos y los '
              'parámetros deportivos de la temporada',
         para='que la programación, la tabla y la cédula usen reglas configuradas y rastreables',
         desc='Pantalla F-11. Categorías con orden y categoría superior inmediata; categorías de la temporada; '
              'grupos; parámetros (puntos, desempates, duración y bloque de cancha, marcador por default, plazo '
              'de cédula, liguilla, ascensos) guardados como versión nueva y rotulados «No oficial» mientras '
              'sean [DEMO].',
         ca=['Dada una categoría, cuando se define su categoría superior inmediata, entonces la jerarquía se '
             'guarda sin cambios de código (RF-099).',
             'Dados parámetros vigentes, cuando se guardan cambios, entonces se crea una versión nueva y la '
             'anterior se conserva (RF-006).',
             'Los valores de demostración se muestran con la insignia «No oficial».',
             'Los criterios de desempate se ordenan y nunca incluyen el nombre del equipo (RF-050).'],
         rf=['RF-004', 'RF-005', 'RF-006', 'RF-099', 'RF-100', 'RNF-005', 'UI-004', 'DEC-25', 'DEC-26', 'DEC-27'],
         ui=['F-11'], api=['GET/POST/PATCH /categorias', 'GET/POST /temporadas/{id}/categorias',
                           'GET/POST/PATCH /grupos', 'GET/POST /temporadas/{id}/parametros'], dep=['H-04'],
         sub=[S('Endpoints de categorías, categorías de temporada y grupos',
                'CRUD con validación de jerarquía; pruebas de permisos.', 3, 'backend'),
              S('Endpoints de versiones de parámetros con `esDemo`',
                'POST crea versión; GET devuelve vigente e historial; pruebas.', 2, 'backend'),
              S('Pantalla de estructura y parámetros con pestañas (F-11)',
                'Lista arrastrable de categorías y desempates; historial de versiones; insignias «No oficial».',
                3, 'frontend'),
              S('Prueba de aceptación de versionado de parámetros', 'Evidencia de los criterios.', 1, 'pruebas')]),

    dict(k='H-06', tipo='Historia', epica='E03', sprint=2, moscow='Must', prio='High', sp=2,
         titulo='Registrar clubes y sus equipos en la categoría y grupo de la temporada',
         como='Secretario de la Liga', quiero='registrar los clubes y sus equipos en una categoría y grupo',
         para='tener la estructura sobre la que se registran jugadores y se programan partidos',
         desc='Pantalla F-15 (sin «A.C.», sellos FMF ni exportación: D-01, D-06). La Directiva de club consulta '
              'y edita solo los suyos. La baja de un equipo es cambio de estado con motivo.',
         ca=['Dado el Secretario, cuando registra un club y un equipo en un grupo de la temporada activa, entonces '
             'ambos aparecen en el detalle del club.',
             'Dada la Directiva de un club, cuando lista equipos, entonces solo ve los de su club.',
             'Dado un equipo con historial, cuando se da de baja, entonces queda en estado BAJA con motivo y no se '
             'borra (RN-005).'],
         rf=['RF-008', 'RF-009', 'UI-005', 'RN-005'], ui=['F-15'],
         api=['GET/POST/PATCH /clubes', 'GET/POST/PATCH /equipos', 'POST /equipos/{id}/baja'], dep=['H-05'],
         sub=[S('Endpoints de clubes y equipos con ámbito de club',
                'Pruebas de ámbito y de baja sin borrado.', 3, 'backend'),
              S('Pantalla de clubes con detalle y pestaña de equipos (F-15)',
                'Lista, detalle, alta de equipo y baja con motivo; sin exportar.', 2.5, 'frontend'),
              S('Prueba de aceptación de clubes y equipos', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    dict(k='H-07', tipo='Historia', epica='E06', sprint=2, moscow='Must', prio='High', sp=3,
         titulo='Registrar canchas con su disponibilidad y bloqueos',
         como='Administrador general', quiero='registrar canchas, su disponibilidad y sus bloqueos con motivo',
         para='que la programación solo use canchas disponibles',
         desc='Pantalla F-21 con calendario semanal. Bloquear un horario con partidos programados muestra los '
              'partidos afectados para reprogramarlos a mano (propuesta de PEN-007; no se reprograma solo).',
         ca=['Dada una cancha, cuando se registra con ubicación y propietario, entonces aparece en el catálogo.',
             'Dado un intervalo, cuando se registra disponibilidad o bloqueo con motivo, entonces se ve en el '
             'calendario semanal.',
             'Dado un bloqueo sobre partidos programados, cuando se intenta guardar, entonces se listan los '
             'partidos afectados y no se reprograma ninguno automáticamente.',
             'Una cancha inactiva o bloqueada no puede usarse en la programación (VAL-005).'],
         rf=['RF-027', 'RF-029', 'RF-030', 'VAL-005', 'UI-008', 'PEN-007'], ui=['F-21'],
         api=['GET/POST/PATCH /canchas', 'GET/POST /canchas/{id}/disponibilidad', 'GET /canchas/{id}/ocupacion'],
         dep=['H-02'],
         sub=[S('Endpoints de canchas, disponibilidad y ocupación',
                'Intervalos `tstzrange` como `{desde, hasta}`; pruebas de traslape y de bloqueo con partidos.',
                4, 'backend'),
              S('Pantalla de canchas con calendario semanal y modal de bloqueo (F-21)',
                'Bloques Disponible/Bloqueo/Mantenimiento/Partido; aviso de partidos afectados.', 4, 'frontend'),
              S('Prueba de aceptación de disponibilidad y bloqueo', 'Evidencia de los criterios.', 1, 'pruebas')]),

    dict(k='T-04', tipo='Tarea', epica='E04', sprint=2, moscow='Must', prio='High', sp=2,
         titulo='Configurar AWS S3 y el servicio de almacenamiento de documentos',
         desc='Crear la cuenta de AWS del equipo, el bucket privado con Block Public Access, cifrado SSE-S3 y '
              'versionado, el usuario IAM con Put/Get sobre `liga/*`, y el componente '
              '`compartido.almacenamiento.AlmacenamientoDocumentos` con carga, validación por firma binaria y '
              'URL prefirmadas de 60 s (ADR-0013). Agrega la dependencia `software.amazon.awssdk:s3`.',
         resultado='Prueba de integración que sube un PDF, rechaza un ejecutable renombrado y genera una URL '
                   'prefirmada que expira; credenciales solo en `backend/.env`.',
         rf=['RNF-018', 'ADR-0013', 'DEC-45', 'RF-108'], ui=[], api=['GET /documentos/{id}/enlace'], dep=['T-02'],
         sub=[S('Crear la cuenta de AWS, el bucket privado y el usuario IAM de mínimo privilegio',
                'Block Public Access activo; política solo Put/Get en `liga/*`; región documentada en '
                '`.env.example`.', 2, 'infra'),
              S('Implementar `AlmacenamientoDocumentos` con firma binaria y URL prefirmada',
                'JPEG, PNG, WebP y PDF de hasta 5 MB [PROPUESTA]; 413 y 415 según convenciones.', 3, 'backend'),
              S('Prueba de integración con S3 y nota de costos',
                'Prueba ejecutable con perfil local; nota de costo esperado en el README del backend.', 1, 'pruebas')]),

    # ======================= SPRINT 3 =======================
    dict(k='H-08', tipo='Historia', epica='E03', sprint=3, moscow='Must', prio='High', sp=2,
         titulo='Solicitar y aprobar la afiliación del club por temporada (tarjetón)',
         como='Directiva de club', quiero='capturar el tarjetón de mi club y enviarlo a aprobación',
         para='que mi club quede afiliado a la temporada',
         desc='Pantalla F-16: vista de captura (Directiva) y de revisión (Presidente). Folio asignado por el '
              'sistema; aprobación o rechazo con observaciones.',
         ca=['Dada la Directiva, cuando envía el tarjetón completo, entonces queda SOLICITADA con folio.',
             'Dado el Presidente, cuando aprueba o rechaza, entonces el estado cambia, se registran quién y '
             'cuándo, y el rechazo exige observaciones.',
             'Dado un usuario de otro club, cuando consulta la afiliación, entonces recibe 404.'],
         rf=['RF-101', 'UI-023', 'DEC-09'], ui=['F-16'],
         api=['GET/POST/PATCH /afiliaciones', 'POST /afiliaciones/{id}/resolucion'], dep=['H-06'],
         sub=[S('Endpoints de afiliación con folio y resolución', 'Pruebas de estados y de ámbito.', 2.5, 'backend'),
              S('Pantalla de tarjetón con vistas de captura y revisión (F-16)',
                'Formulario por secciones y modales de aprobar/rechazar.', 3, 'frontend'),
              S('Prueba de aceptación de la afiliación', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    dict(k='H-09', tipo='Historia', epica='E04', sprint=3, moscow='Must', prio='Highest', sp=3,
         titulo='Registrar un jugador por CURP con tutor y consentimiento',
         como='Directiva de club', quiero='registrar jugadores en mis equipos usando su CURP',
         para='que cada persona tenga un solo registro activo por temporada y los menores tengan tutor',
         desc='Pasos 1, 2 y 4 del asistente F-18 y la pantalla F-06. Reutiliza la persona si la CURP existe; '
              'rechaza CURP con registro activo e indica club y equipo; para menores exige tutor vigente y su '
              'consentimiento con versión del aviso. Sin validación con RENAPO (D-03). El texto del aviso viene '
              'de `avisos_privacidad` (PEN-039). Conserva el historial de pertenencia.',
         ca=['Dada una CURP nueva, cuando se registra al jugador, entonces se crea la persona y el registro queda '
             'PENDIENTE_VALIDACION.',
             'Dada una CURP con registro activo en la temporada, cuando se intenta registrar, entonces se rechaza '
             'con el club y el equipo donde está (VAL-017).',
             'Dado un menor sin tutor o sin consentimiento, cuando se envía, entonces no se completa el registro '
             '(VAL-025).',
             'Dada una persona ya existente, cuando se registra en otra temporada, entonces se reutiliza su '
             'identidad sin duplicarla (RF-010).'],
         rf=['RF-010', 'RF-011', 'RF-012', 'RF-013', 'RF-097', 'RF-106', 'RF-107', 'VAL-017', 'VAL-025',
             'UI-006', 'UI-033'], ui=['F-17', 'F-18', 'F-06'],
         api=['GET/POST /personas', 'POST /personas/{id}/tutelas', 'POST /personas/{id}/consentimientos',
              'GET /avisos-privacidad/vigente', 'POST /registros-jugador', 'GET /registros-jugador'],
         dep=['H-06', 'H-08'],
         sub=[S('Endpoints de personas, tutela y consentimiento',
                'POST con CURP existente responde 409 con el id; pruebas de tutela y consentimiento.', 3, 'backend'),
              S('Endpoint de alta de registro con unicidad por CURP',
                'Traduce VAL-017 y VAL-025 con mensaje explicativo; pruebas.', 2, 'backend'),
              S('Asistente de alta (pasos 1, 2 y 4) y pantalla de consentimiento (F-18, F-06)',
                'Búsqueda por CURP, datos y tutor, consentimiento; mensajes de bloqueo.', 3, 'frontend'),
              S('Plantilla del equipo (F-17)', 'Lista con estado, CURP enmascarada y elegibilidad.', 1, 'frontend')]),

    dict(k='H-10', tipo='Historia', epica='E04', sprint=3, moscow='Must', prio='Highest', sp=3,
         titulo='Cargar documentos del jugador y validar el registro',
         como='Secretario de la Liga', quiero='revisar los documentos de cada registro y validarlo o rechazarlo',
         para='que solo jueguen personas con identidad comprobada',
         desc='Paso 3 de F-18 (carga) y pantalla F-19 (validación). Los requisitos dependen del tipo de '
              'categoría; los archivos van a S3 y se ven con URL prefirmada de 60 s. Validar exige todos los '
              'documentos aprobados.',
         ca=['Dado un registro, cuando la Directiva carga los documentos requeridos, entonces quedan PENDIENTE '
             'con tipo, tamaño y hash.',
             'Dado un archivo que no es JPEG, PNG, WebP ni PDF por su firma, cuando se carga, entonces se '
             'rechaza con 415.',
             'Dado el Secretario, cuando aprueba todos los documentos y valida, entonces el registro pasa a ACTIVO; '
             'si rechaza, exige observaciones y pasa a RECHAZADO.',
             'La URL de un documento expira a los 60 s y no se guarda ni se registra (RNF-018).',
             'Faltando un documento obligatorio, el registro no puede validarse (VAL-001).'],
         rf=['RF-108', 'RF-109', 'RF-017', 'VAL-001', 'RNF-018', 'UI-024'], ui=['F-18', 'F-19'],
         api=['POST /registros-jugador/{id}/documentos', 'GET /registros-jugador/{id}/requisitos',
              'GET /documentos/{id}/enlace', 'POST /documentos/{id}/validacion',
              'POST /registros-jugador/{id}/validacion'], dep=['H-09', 'T-04'],
         sub=[S('Endpoints de carga, requisitos, enlace y validación de documentos',
                'Usa `AlmacenamientoDocumentos`; pruebas 413/415, ámbito y expiración.', 3, 'backend'),
              S('Endpoint de validación del registro', 'Transición con observaciones; prueba de VAL-001.', 1.5, 'backend'),
              S('Paso 3 del asistente y bandeja de validación (F-18, F-19)',
                'Carga con vista previa; visor seguro; aprobar/rechazar con observaciones.', 3.5, 'frontend'),
              S('Prueba de aceptación del registro completo', 'Alta → documentos → validación.', 1, 'pruebas')]),

    dict(k='H-11', tipo='Historia', epica='E05', sprint=3, moscow='Must', prio='High', sp=2,
         titulo='Presentar árbitros del club y validarlos',
         como='Comisión de Arbitraje', quiero='validar o rechazar a los árbitros que presentan los clubes',
         para='contar con un catálogo de árbitros validados para designar',
         desc='Pantalla F-20 con vista de la Comisión y del club. Los árbitros los presenta el club (DEC-17), '
              'no un colegio (D-02). La cuota de árbitros por club es backlog (RF-114).',
         ca=['Dada la Directiva, cuando presenta un árbitro, entonces queda PROPUESTO ligado a su club.',
             'Dada la Comisión, cuando valida o rechaza con observaciones, entonces cambia su estado y queda '
             'auditado.',
             'Solo los árbitros VALIDADO aparecen en el catálogo para designar.'],
         rf=['RF-021', 'RF-112', 'RF-113', 'UI-007'], ui=['F-20'],
         api=['GET/POST /arbitros', 'POST /arbitros/{id}/validacion'], dep=['H-09'],
         sub=[S('Endpoints de árbitros con validación', 'Pruebas de estados y ámbito.', 2.5, 'backend'),
              S('Pantalla de árbitros con vistas Comisión y club (F-20)',
                'Pestañas por estado; modales de registro y rechazo; sin «Colegio».', 3, 'frontend'),
              S('Prueba de aceptación de árbitros', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    dict(k='H-12', tipo='Historia', epica='E07', sprint=3, moscow='Must', prio='Highest', sp=5,
         titulo='Programar una jornada con detección de conflictos explicada',
         como='Programador de partidos', quiero='registrar a mano los partidos de una jornada y que el sistema '
              'rechace los conflictos explicándome la causa',
         para='publicar una programación sin choques de equipos ni de canchas',
         desc='Pantalla F-22. Registrar y ajustar partidos solo con la jornada en BORRADOR; cada ajuste lleva '
              'motivo. El servicio valida antes de escribir VAL-003 (equipo en el mismo bloque), VAL-004 (cancha '
              'ocupada), VAL-005 (cancha no disponible) y VAL-006 (equipo con baja o sanción), usando el bloque de '
              'cancha de la categoría; el trigger y la restricción GiST son la última garantía. La verificación '
              'devuelve todos los conflictos sin guardar. La advertencia de localía es backlog (RF-120).',
         ca=['Dado un partido cuya cancha ya está ocupada en el bloque, cuando se guarda, entonces se rechaza con '
             '409 VAL-004 y el mensaje nombra el otro partido, la cancha y el horario.',
             'Dado un equipo con otro partido en el bloque, cuando se guarda, entonces se rechaza con VAL-003.',
             'Dada una cancha bloqueada o inactiva, cuando se asigna, entonces se rechaza con VAL-005.',
             'Dada una jornada, cuando se pide «Verificar conflictos», entonces se listan todos sin guardar.',
             'Dado un ajuste a un partido existente, cuando se guarda sin motivo, entonces se rechaza; con motivo, '
             'queda en el historial y la auditoría (RF-040).'],
         rf=['RF-033', 'RF-036', 'RF-039', 'RF-040', 'VAL-003', 'VAL-004', 'VAL-005', 'VAL-006', 'UI-009',
             'RF-032'], ui=['F-22'],
         api=['GET/POST /jornadas', 'GET /jornadas/{id}', 'POST /jornadas/{id}/partidos', 'PATCH /partidos/{id}',
              'POST /jornadas/{id}/verificacion'], dep=['H-07', 'H-05', 'H-06'],
         sub=[S('Endpoints de jornadas y partidos en borrador',
                'Registro y ajuste con motivo; pruebas de estado BORRADOR.', 3, 'backend'),
              S('Validador de conflictos VAL-003 a VAL-006 en el servicio',
                'Usa el bloque de cancha de los parámetros; mensajes explicativos; pruebas por cada regla.',
                4, 'backend'),
              S('Endpoint de verificación y traducción de errores del trigger',
                'Devuelve conflictos y advertencias; prueba de que el trigger respalda al servicio.', 2, 'backend'),
              S('Pantalla de programación con validación en línea (F-22)',
                'Tabla editable, filas con conflicto marcadas con su explicación, botón de envío deshabilitado '
                'con conflictos.', 4, 'frontend'),
              S('Prueba de aceptación de conflictos', 'Un escenario por regla, con evidencia.', 2, 'pruebas')]),

    dict(k='H-13', tipo='Historia', epica='E07', sprint=3, moscow='Must', prio='Highest', sp=2,
         titulo='Enviar la jornada a aprobación y publicarla',
         como='Presidente del Comité Ejecutivo', quiero='revisar la jornada que envía el Programador y aprobarla '
              'o devolverla',
         para='que solo se publique la programación autorizada',
         desc='Pantallas F-22 (envío) y F-23 (revisión). Flujo BORRADOR → REVISION → PUBLICADO; quien programa '
              'no aprueba (par incompatible). Al publicar, la jornada aparece en el portal.',
         ca=['Dada una jornada sin conflictos, cuando el Programador la envía, entonces pasa a REVISION.',
             'Dada una jornada en REVISION, cuando el Presidente la aprueba, entonces pasa a PUBLICADO y queda '
             'visible en `v_publica_programacion`.',
             'Dada una devolución, cuando no lleva observaciones, entonces se rechaza; con observaciones vuelve a '
             'BORRADOR.',
             'Un usuario con permiso de editar la jornada no puede aprobarla.'],
         rf=['RF-121', 'RF-090', 'UI-009', 'DD-008'], ui=['F-22', 'F-23'],
         api=['POST /jornadas/{id}/envio', 'POST /jornadas/{id}/resolucion', 'GET /publicaciones/pendientes'],
         dep=['H-12'],
         sub=[S('Endpoints de envío, resolución y pendientes de publicación',
                'Transiciones con historial; pruebas de separación de funciones.', 2.5, 'backend'),
              S('Pantalla de revisión de la jornada (F-23) y envío desde F-22',
                'Resumen, tabla de solo lectura, modales de aprobar y devolver.', 3, 'frontend'),
              S('Prueba de aceptación del flujo de publicación', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    # ======================= SPRINT 4 =======================
    dict(k='H-14', tipo='Historia', epica='E05', sprint=4, moscow='Must', prio='High', sp=2,
         titulo='Designar árbitros a los partidos de una jornada',
         como='Comisión de Arbitraje', quiero='designar árbitro central y asistentes a cada partido',
         para='que cada partido tenga quién valide su cédula',
         desc='Pantalla F-24. Rechaza árbitros del club de cualquiera de los equipos (VAL-019) y advierte '
              'choques de horario del árbitro (RF-039).',
         ca=['Dado un árbitro del mismo club que un equipo del partido, cuando se designa, entonces se rechaza '
             'con VAL-019.',
             'Dado un árbitro ya designado en otro partido a la misma hora, cuando se designa, entonces se rechaza '
             'con el conflicto explicado.',
             'Dada una designación válida, cuando se guarda, entonces el árbitro ve el partido en «Mis '
             'designaciones».'],
         rf=['RF-023', 'RF-115', 'RF-039', 'VAL-019', 'UI-025'], ui=['F-24'],
         api=['GET /designaciones', 'POST /partidos/{id}/designaciones', 'GET /mis-designaciones'],
         dep=['H-11', 'H-13'],
         sub=[S('Endpoints de designación y mis designaciones', 'Pruebas VAL-019 y choque de horario.', 2.5, 'backend'),
              S('Pantalla de designación con selectores filtrados (F-24)',
                'Árbitros no elegibles deshabilitados con su causa; indicador de avance.', 3, 'frontend'),
              S('Prueba de aceptación de designación', 'Evidencia de los criterios.', 0.5, 'pruebas')]),

    dict(k='H-15', tipo='Historia', epica='E08', sprint=4, moscow='Must', prio='Highest', sp=3,
         titulo='Gestionar el estado del partido con motivo e historial',
         como='Programador de partidos', quiero='suspender, reprogramar o cancelar un partido con motivo y ver su '
              'historial',
         para='reflejar lo que pasa en la cancha sin perder la trazabilidad',
         desc='Pantalla F-25. Máquina de estados DEC-23 (ERS §4.3.1); `accionesPermitidas` según estado y '
              'permisos. Tarea programada idempotente (ADR-0009) que pasa los partidos a EN_CURSO a su hora. Los '
              'partidos no se borran. Sin avisos por SMS (D-04).',
         ca=['Dado un partido PROGRAMADO, cuando se reprograma con nueva fecha y motivo, entonces pasa a '
             'REPROGRAMADO y el historial guarda usuario, fecha y motivo.',
             'Dada una transición no permitida (por ejemplo, suspender un PROGRAMADO), cuando se intenta, '
             'entonces se rechaza con 409 VAL-008/RF-046.',
             'Dada la hora de inicio, cuando corre la tarea programada, entonces el partido pasa a EN_CURSO una '
             'sola vez aunque la tarea se repita.',
             'El detalle muestra solo las acciones permitidas para el estado y el usuario.'],
         rf=['RF-043', 'RF-044', 'RF-045', 'RF-046', 'RF-127', 'VAL-008', 'RN-021', 'UI-010'], ui=['F-25'],
         api=['GET /partidos', 'GET /partidos/{id}', 'GET /partidos/{id}/historial', 'POST /partidos/{id}/transiciones'],
         dep=['H-13'],
         sub=[S('Endpoints de partido con `accionesPermitidas` y transiciones',
                'Pruebas de cada transición permitida y prohibida.', 3, 'backend'),
              S('Tarea programada que inicia los partidos a su hora',
                'Idempotente con usuario Sistema; prueba de doble ejecución.', 2, 'backend'),
              S('Pantalla de detalle del partido con pestañas e historial (F-25)',
                'Barra de acciones según estado; modal de reprogramación con motivo.', 3, 'frontend'),
              S('Prueba de aceptación de estados del partido', 'Evidencia de los criterios.', 1, 'pruebas')]),

    dict(k='H-16', tipo='Historia', epica='E08', sprint=4, moscow='Must', prio='Highest', sp=5,
         titulo='Capturar la cédula del partido y enviarla a validación',
         como='Capturista', quiero='capturar alineaciones, marcador, goles, tarjetas, sustituciones, incidencias '
              'y observaciones del partido',
         para='que el árbitro valide un registro completo y el resultado pueda oficializarse',
         desc='Pantalla F-26. La cédula se abre con el partido EN_CURSO. Alineación con validación de elegibilidad '
              '(registro activo, validado, sin sanción; categoría inmediata superior del mismo club), no '
              'presentación con marcador por default de los parámetros («No oficial»), incidencias con '
              'involucrados y envío con campos obligatorios completos. Sin reglas inventadas como «5 amarillas» o '
              '«mínimo 7 titulares» (D-07); el mínimo de jugadores, si se exige, es parámetro [PROPUESTA].',
         ca=['Dado un jugador sin registro activo o con sanción vigente, cuando se agrega a la alineación, entonces '
             'se rechaza con la causa (VAL-002, RF-020).',
             'Dado un jugador de otro equipo del club que no está en la categoría inmediata inferior, cuando se '
             'alinea, entonces se rechaza (VAL-018).',
             'Dada la no presentación de un equipo, cuando se marca, entonces se aplica el marcador por default '
             'configurado.',
             'Dada una cédula incompleta, cuando se envía, entonces se rechaza listando lo que falta (VAL-020); '
             'completa, pasa a PENDIENTE_VALIDACION.',
             'Una cédula en validación o aprobada no se edita.'],
         rf=['RF-025', 'RF-026', 'RF-047', 'RF-122', 'RF-126', 'RF-019', 'RF-020', 'RF-110', 'VAL-002',
             'VAL-018', 'VAL-020', 'UI-011'], ui=['F-26'],
         api=['GET/POST /partidos/{id}/cedula', 'PUT /cedulas/{id}/alineaciones', 'PUT /cedulas/{id}/marcador',
              'POST /cedulas/{id}/eventos', 'POST /cedulas/{id}/incidencias', 'POST /cedulas/{id}/envio',
              'GET /registros-jugador/{id}/elegibilidad'], dep=['H-15', 'H-10'],
         sub=[S('Endpoints de cédula, alineación con elegibilidad y marcador',
                'Errores por jugador en `errores`; pruebas VAL-002, VAL-018 y default.', 4, 'backend'),
              S('Endpoints de eventos, incidencias y envío a validación',
                'Pruebas VAL-020 y bloqueo de edición fuera de EN_CAPTURA.', 3, 'backend'),
              S('Pantalla de captura de cédula (F-26)',
                'Tres columnas, línea de eventos, incidencias, guardado y validación previa al envío.', 6, 'frontend'),
              S('Prueba de aceptación de captura', 'Escenarios de elegibilidad, default y envío.', 2, 'pruebas')]),

    dict(k='H-17', tipo='Historia', epica='E08', sprint=4, moscow='Must', prio='Highest', sp=3,
         titulo='Validar la cédula y oficializar el resultado',
         como='Árbitro designado', quiero='aprobar la cédula de mi partido o devolverla con observaciones',
         para='que el resultado quede oficial solo con mi validación',
         desc='Pantallas F-27 (árbitro, móvil) y F-28 (Comisión). Aprobar finaliza el partido, registra el '
              'resultado oficial versionado y lo publica; quien capturó no aprueba (VAL-021). Al vencer el plazo '
              'configurado (48 h [DEMO]) la Comisión puede aprobar (tarea programada). Abrir expedientes al '
              'aprobar es parte de la historia de disciplina (Should).',
         ca=['Dada una cédula PENDIENTE_VALIDACION, cuando el árbitro central designado la aprueba, entonces el '
             'partido pasa a FINALIZADO y existe un resultado oficial vigente visible en el portal.',
             'Dada una devolución, cuando no lleva observaciones, entonces se rechaza; con observaciones, la cédula '
             'pasa a DEVUELTA y el capturista puede reabrirla.',
             'Dado quien capturó la cédula, cuando intenta aprobarla, entonces se rechaza con VAL-021.',
             'Dado el plazo vencido, cuando la Comisión aprueba, entonces se registra como aprobada por comisión; '
             'antes del plazo se rechaza (RF-124).',
             'Ninguna otra vía crea un resultado oficial (RF-048, VAL-009).'],
         rf=['RF-048', 'RF-123', 'RF-124', 'RF-125', 'RF-056', 'VAL-009', 'VAL-021', 'UI-014'],
         ui=['F-27', 'F-28'],
         api=['GET /cedulas', 'POST /cedulas/{id}/aprobacion', 'POST /cedulas/{id}/devolucion',
              'POST /cedulas/{id}/reapertura'], dep=['H-16', 'H-14'],
         sub=[S('Endpoints de aprobación, devolución y reapertura',
                'Transacción atómica cédula + partido + resultado + auditoría (RNF-004); pruebas VAL-021.',
                3.5, 'backend'),
              S('Tarea programada del plazo vencido', 'Marca `plazo_vencido` de forma idempotente; prueba.', 1.5, 'backend'),
              S('Pantallas de validación del árbitro (F-27) y de la Comisión (F-28)',
                'Móvil con hoja de devolución; bandeja con plazo y aprobación por vencimiento.', 3, 'frontend'),
              S('Prueba de aceptación de la oficialización', 'Evidencia de los criterios.', 1, 'pruebas')]),

    dict(k='H-18', tipo='Historia', epica='E08', sprint=4, moscow='Must', prio='High', sp=2,
         titulo='Consultar la tabla de posiciones con desempates y sorteo',
         como='Presidente del Comité Ejecutivo', quiero='ver la tabla calculada de cada grupo con el criterio que '
              'definió cada empate',
         para='conocer la clasificación oficial y resolver por sorteo los empates que persistan',
         desc='Pantalla F-29. La tabla sale de `calcular_tabla` con los parámetros vigentes; distingue dato '
              'oficial de indicador calculado y rotula «No oficial» los parámetros [DEMO]. El sorteo se registra '
              'con motivo.',
         ca=['Dado un grupo con resultados oficiales, cuando se consulta, entonces muestra PJ, PG, PE, PP, GF, GC, '
             'DG y puntos según los parámetros (RF-049).',
             'Dados equipos empatados, cuando se aplican los criterios, entonces el orden sigue la configuración y '
             'nunca el nombre del equipo (RF-050).',
             'Dado un empate persistente, cuando se registra el sorteo, entonces la tabla lo aplica.',
             'Los indicadores calculados se distinguen del dato oficial (RF-055).'],
         rf=['RF-049', 'RF-050', 'RF-053', 'RF-055', 'VAL-011', 'UI-012', 'RN-022'], ui=['F-29'],
         api=['GET /grupos/{id}/tabla', 'POST /grupos/{id}/sorteos'], dep=['H-17'],
         sub=[S('Endpoints de tabla y sorteo', 'Pruebas con el conjunto de desempates de `prueba_tabla.sql`.', 2.5, 'backend'),
              S('Pantalla de tabla interna con modal de sorteo (F-29)', 'Tooltip de criterio; insignias.', 3, 'frontend'),
              S('Prueba de aceptación de la tabla', 'Evidencia con empates.', 0.5, 'pruebas')]),

    dict(k='H-19', tipo='Historia', epica='E11', sprint=4, moscow='Must', prio='Highest', sp=3,
         titulo='Consultar en el portal la programación, los resultados y las tablas publicadas',
         como='usuario público', quiero='ver la programación de la semana, los resultados oficiales y las tablas '
              'con filtros', para='seguir la liga sin pedir información a nadie',
         desc='Pantallas F-01 a F-04. Endpoints `/api/v1/publico/**` con el rol `siglim_publico` sobre las vistas '
              '`v_publica_*` y `calcular_tabla`; filtros por semana, categoría, grupo y equipo; sin buscador '
              'global, sin tiempo real (D-09) y sin datos personales.',
         ca=['Dada una jornada en REVISION, cuando se consulta el portal, entonces no aparece (VAL-014).',
             'Dado un resultado de cédula aprobada, cuando se consulta, entonces aparece con su marcador y «Por '
             'default» si aplica.',
             'Dados los filtros de categoría, grupo y equipo, cuando se aplican, entonces la lista se ajusta '
             '(RF-089).',
             'El portal funciona sin sesión, a 360 px sin desplazamiento horizontal y no muestra datos personales.'],
         rf=['RF-056', 'RF-082', 'RF-083', 'RF-084', 'RF-089', 'VAL-014', 'RNF-002', 'RNF-012', 'UI-020'],
         ui=['F-01', 'F-02', 'F-03', 'F-04'],
         api=['GET /publico/temporada', 'GET /publico/programacion', 'GET /publico/resultados',
              'GET /publico/grupos/{id}/tabla'], dep=['H-13', 'H-17', 'H-18'],
         sub=[S('Endpoints públicos sobre las vistas con caché', 'Rol `siglim_publico`; `Cache-Control` 60 s; pruebas.',
                2.5, 'backend'),
              S('Páginas del portal: programación, resultados y tabla (F-01 a F-04)',
                'Escritorio y móvil; hoja de filtros en móvil.', 5, 'frontend'),
              S('Prueba de aceptación del portal', 'Solo lo publicado; filtros; 360 px.', 1.5, 'pruebas')]),

    dict(k='T-05', tipo='Tarea', epica='E11', sprint=4, moscow='Must', prio='High', sp=2,
         titulo='Probar de extremo a extremo el flujo central y preparar la demostración',
         desc='Recorrido completo con datos de prueba (`seed.sql` del equipo, no en la migración): temporada → '
              'club → registro validado → jornada aprobada → designación → partido → cédula validada → tabla → '
              'portal. Incluye la medición básica del portal (RNF-002) y el guion de la demostración en local con '
              'el proyecto Supabase de desarrollo (PEN-036 y PEN-037 siguen abiertos).',
         resultado='Escenario de punta a punta reproducible, reporte de aceptación por historia y guion de la '
                   'demo.',
         rf=['RNF-002', 'RNF-006', 'RNF-016', 'PEN-036', 'PEN-037', 'ERS §3.4.3'], ui=[], api=[],
         dep=['H-19', 'H-17'],
         sub=[S('Crear `supabase/seed.sql` con datos ficticios del recorrido',
                'Datos sin personas reales; se carga después de la migración.', 1.5, 'bd'),
              S('Ejecutar el recorrido completo y registrar evidencias',
                'Reporte de aceptación por historia con capturas.', 2.5, 'pruebas'),
              S('Medir tiempos del portal y operaciones internas',
                'p95 medido y comparado con RNF-002/RNF-016; la herramienta se justifica en el reporte.', 1, 'pruebas'),
              S('Guion de la demostración del incremento final', 'Pasos, usuarios de prueba y datos.', 1, 'docs')]),

    # ======================= NÚCLEO SIN SPRINT (Should) =======================
    dict(k='H-20', tipo='Historia', epica='E01', sprint=None, moscow='Should', prio='Medium', sp=3,
         titulo='Editar los permisos de un rol con control dual',
         como='Administrador general', quiero='modificar los permisos de los roles, con aprobación del Presidente '
              'cuando combinen permisos incompatibles', para='adaptar la matriz sin concentrar funciones',
         desc='Pantalla F-13. No se editan los permisos del propio rol (VAL-022); un par incompatible queda '
              'SOLICITADO hasta que el Presidente lo resuelva (VAL-023), y quien solicita no aprueba.',
         ca=['Dado un cambio sin pares incompatibles, cuando se guarda con motivo, entonces se aplica y se audita.',
             'Dado un cambio con un par incompatible, cuando se guarda, entonces responde 202 y queda pendiente.',
             'Dado el Presidente, cuando aprueba o rechaza con motivo, entonces se aplica o descarta.',
             'Dado el Administrador, cuando edita su propio rol, entonces se rechaza (VAL-022).'],
         rf=['RF-095', 'RF-096', 'VAL-022', 'VAL-023', 'UI-029', 'RN-053'], ui=['F-13'],
         api=['GET /roles', 'PATCH /roles/{id}/permisos', 'GET /solicitudes-cambio-permisos',
              'POST /solicitudes-cambio-permisos/{id}/resolucion'], dep=['H-03'],
         sub=[S('Endpoints de roles, permisos y solicitudes', 'Pruebas 200, 202, VAL-022 y separación.', 4, 'backend'),
              S('Pantalla de matriz de permisos y solicitudes (F-13)', 'Pestañas y modales de dictamen.', 4, 'frontend'),
              S('Prueba de aceptación del control dual', 'Evidencia de los criterios.', 1, 'pruebas')]),

    dict(k='H-21', tipo='Historia', epica='E01', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Consultar la auditoría con filtros',
         como='Presidente del Comité Ejecutivo', quiero='consultar la auditoría por usuario, módulo, acción y fecha',
         para='rastrear quién hizo cada cambio crítico',
         desc='Pantalla F-14 de solo lectura (sin «PDF firmado»: D-05, D-06).',
         ca=['Dados filtros, cuando se consulta, entonces se listan los registros paginados.',
             'Al expandir un registro se ve el detalle y el motivo completo.',
             'Ningún usuario puede modificar ni borrar registros.'],
         rf=['RF-134', 'UI-022', 'RNF-015'], ui=['F-14'], api=['GET /auditoria'], dep=['H-02'],
         sub=[S('Endpoint de consulta de auditoría', 'Filtros y paginación de 50; pruebas de permisos.', 2, 'backend'),
              S('Pantalla de auditoría (F-14)', 'Filtros, tabla densa y fila expandible.', 3, 'frontend'),
              S('Prueba de aceptación de auditoría', 'Evidencia.', 1, 'pruebas')]),

    dict(k='H-22', tipo='Historia', epica='E03', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Registrar la directiva del club y sus cambios',
         como='Directiva de club', quiero='registrar los cargos de la directiva y sus cambios',
         para='que la Liga sepa quién representa al club', desc='Pestaña «Directiva» de F-15; los cambios se '
         'autorizan con fecha.',
         ca=['Dado un cambio de cargo, cuando se registra, entonces queda con vigencia desde y pendiente de '
             'autorización.', 'Dada la autorización, cuando se aprueba, entonces el cargo anterior termina su '
             'vigencia.'],
         rf=['RF-102', 'UI-005'], ui=['F-15'],
         api=['GET/POST /clubes/{id}/directiva', 'POST /cargos-directiva/{id}/autorizacion'], dep=['H-06'],
         sub=[S('Endpoints de directiva con vigencia y autorización', 'Pruebas.', 2.5, 'backend'),
              S('Pestaña de directiva en F-15', 'Lista de cargos y registro de cambio.', 3, 'frontend'),
              S('Prueba de aceptación de directiva', 'Evidencia.', 0.5, 'pruebas')]),

    dict(k='H-23', tipo='Historia', epica='E04', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Dar de baja a un jugador conservando su historial',
         como='Directiva de club', quiero='dar de baja a un jugador con fecha y motivo',
         para='mantener al día la plantilla sin perder el historial',
         desc='Modal de baja de F-17. La baja es cambio de estado; el historial de estados lo conserva el trigger.',
         ca=['Dado un registro ACTIVO, cuando se da de baja con motivo, entonces pasa a BAJA y conserva su '
             'historial.', 'Sin motivo, se rechaza (RN-021).'],
         rf=['RF-014', 'RF-017', 'UI-006'], ui=['F-17'], api=['POST /registros-jugador/{id}/baja'], dep=['H-10'],
         sub=[S('Endpoint de baja de registro', 'Pruebas de transición y motivo.', 2, 'backend'),
              S('Modal de baja en la plantilla (F-17)', 'Motivo obligatorio y advertencia.', 3, 'frontend'),
              S('Prueba de aceptación de baja', 'Evidencia.', 1, 'pruebas')]),

    dict(k='H-24', tipo='Historia', epica='E04', sprint=None, moscow='Should', prio='Medium', sp=3,
         titulo='Consultar mi información como jugador o tutor',
         como='jugador o tutor de un menor', quiero='consultar registro, documentos, elegibilidad, partidos y '
              'sanciones', para='saber si puedo jugar y por qué',
         desc='Pantalla F-35 (móvil). Ámbito PROPIO; el tutor elige entre sus menores. Sin datos de RENAPO (D-03).',
         ca=['Dado un tutor con dos menores, cuando abre la pantalla, entonces puede elegir a cada uno.',
             'Dado un jugador suspendido, cuando consulta, entonces ve la causa y la vigencia.',
             'Ningún otro usuario de ámbito PROPIO ve esta información (404).'],
         rf=['RF-144', 'UI-032', 'RNF-012'], ui=['F-35'], api=['GET /mi-informacion'], dep=['H-10'],
         sub=[S('Endpoint `GET /mi-informacion`', 'Ámbito PROPIO con tutela; pruebas.', 3, 'backend'),
              S('Pantalla móvil de mi información (F-35)', 'Selector de menor y secciones.', 4.5, 'frontend'),
              S('Prueba de aceptación de consulta propia', 'Evidencia.', 1.5, 'pruebas')]),

    dict(k='H-25', tipo='Historia', epica='E14', sprint=None, moscow='Should', prio='Medium', sp=3,
         titulo='Recibir avisos dentro de la aplicación',
         como='usuario interno', quiero='recibir avisos de los eventos que me corresponden y marcarlos como leídos',
         para='atender a tiempo cédulas, trámites y cobros',
         desc='Pantalla F-09 y la campana. Eventos de RF-142 escritos en la misma transacción (ADR-0010); '
              'consulta al cargar y cada 60 s [PROPUESTA]; sin correo (RF-143 es B y PEN-034 está abierto).',
         ca=['Dada una cédula enviada, cuando se confirma, entonces el árbitro designado recibe un aviso.',
             'Dado un aviso, cuando se abre, entonces lleva a la pantalla del trámite y queda leído.',
             'El contador de no leídas se actualiza al cargar y cada 60 s mientras la pestaña está visible.'],
         rf=['RF-142', 'RF-122', 'UI-030'], ui=['F-09'],
         api=['GET /notificaciones', 'POST /notificaciones/{id}/lectura', 'POST /notificaciones/lectura'],
         dep=['H-17'],
         sub=[S('Módulo de notificaciones con escucha de eventos de dominio', 'Pruebas por evento.', 4, 'backend'),
              S('Pantalla de notificaciones y panel de la campana (F-09)', 'Pestañas y marcar leídas.', 4, 'frontend'),
              S('Prueba de aceptación de avisos', 'Evidencia.', 1, 'pruebas')]),

    dict(k='H-26', tipo='Historia', epica='E09', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Configurar los catálogos de incidencias y artículos',
         como='Comisión Disciplinaria', quiero='configurar tipos de incidencia y artículos del reglamento',
         para='resolver con el catálogo vigente',
         desc='Pantalla F-32. El catálogo es [DEMO] hasta cargar el Reglamento (PEN-021) y se rotula «No oficial».',
         ca=['Dado un tipo de incidencia, cuando se marca «abre expediente», entonces la aprobación de una cédula '
             'con esa incidencia abrirá uno.', 'Los artículos guardan sanción, sujeto, efecto y aplicación '
             'automática.', 'Los elementos de demostración llevan la insignia «No oficial».'],
         rf=['RF-128', 'RF-129', 'RF-060', 'UI-026', 'PEN-021'], ui=['F-32'],
         api=['GET/POST/PATCH /tipos-incidencia', 'GET/POST/PATCH /articulos'], dep=['H-02'],
         sub=[S('Endpoints de catálogos disciplinarios', 'Pruebas de permisos.', 2.5, 'backend'),
              S('Pantalla de catálogos con pestañas (F-32)', 'Formulario lateral de edición.', 3, 'frontend'),
              S('Prueba de aceptación de catálogos', 'Evidencia.', 0.5, 'pruebas')]),

    dict(k='H-27', tipo='Historia', epica='E09', sprint=None, moscow='Should', prio='Medium', sp=5,
         titulo='Abrir expedientes desde la cédula y resolverlos con sanción',
         como='Comisión Disciplinaria', quiero='revisar los expedientes abiertos por las incidencias y resolverlos '
              'con un artículo y su sanción', para='aplicar el reglamento con vigencia y efecto en la elegibilidad',
         desc='Pantallas F-30 y F-31. Apertura automática al aprobarse la cédula; resolución inmutable con '
              'vigencia; la sanción vigente bloquea la alineación (RN-057). Sin artículos inventados (D-07).',
         ca=['Dada una cédula aprobada con incidencia que abre expediente, entonces se crea un expediente ABIERTO '
             'con sus involucrados.', 'Dada una resolución con artículo y fundamentos, cuando se emite, entonces '
             'el expediente pasa a RESUELTO y la sanción calcula su vigencia.', 'Dado un jugador suspendido, '
             'cuando se alinea, entonces se rechaza (RF-064).', 'Las resoluciones no se modifican (RF-065).',
             'Sin artículo identificado, el expediente queda en revisión (VAL-012).'],
         rf=['RF-057', 'RF-058', 'RF-062', 'RF-063', 'RF-064', 'RF-065', 'VAL-012', 'VAL-013', 'UI-015'],
         ui=['F-30', 'F-31'],
         api=['GET/POST /expedientes', 'POST /expedientes/{id}/revision', 'POST /expedientes/{id}/resoluciones',
              'GET /sanciones'], dep=['H-17', 'H-26'],
         sub=[S('Apertura automática de expedientes por evento de cédula aprobada', 'Pruebas.', 3, 'backend'),
              S('Endpoints de revisión y resolución con sanciones', 'Inmutabilidad y vigencia probadas.', 4, 'backend'),
              S('Pantallas de expedientes y resolución (F-30, F-31)', 'Vista previa de efectos.', 6, 'frontend'),
              S('Prueba de aceptación de disciplina', 'Escenario incidencia → sanción → bloqueo.', 2, 'pruebas')]),

    dict(k='H-28', tipo='Historia', epica='E08', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Corregir un resultado oficial por resolución conservando la versión anterior',
         como='Comisión Disciplinaria', quiero='que una resolución pueda cambiar un resultado oficial',
         para='aplicar el reglamento sin perder la versión original',
         desc='Solo por resolución disciplinaria (VAL-010); la versión anterior deja de ser vigente y la tabla se '
              'recalcula. El portal muestra «Resultado modificado por resolución» (F-03).',
         ca=['Dada una resolución, cuando corrige el resultado, entonces existe una versión nueva vigente y la '
             'anterior se conserva.', 'Sin resolución, la corrección se rechaza con 403 VAL-010.',
             'La tabla refleja el resultado vigente.'],
         rf=['RF-052', 'RN-024', 'VAL-010'], ui=['F-25', 'F-03'], api=['GET /partidos/{id}/resultados'], dep=['H-27'],
         sub=[S('Servicio de corrección por resolución', 'Pruebas de versión y VAL-010.', 2.5, 'backend'),
              S('Pestaña de resultados en F-25 y rótulo en F-03', 'Versiones visibles.', 2.5, 'frontend'),
              S('Prueba de aceptación de corrección', 'Evidencia con la tabla.', 1, 'pruebas')]),

    dict(k='H-29', tipo='Historia', epica='E13', sprint=None, moscow='Should', prio='Medium', sp=3,
         titulo='Crear cobros manuales y consultar adeudos',
         como='Tesorero de la Liga', quiero='crear cobros a clubes o equipos y ver los adeudos',
         para='dar seguimiento a lo que se debe', desc='Pantalla F-33 (sin «retención de cédula» ni «Art. 88»: D-07).',
         ca=['Dado un cobro con concepto, monto y periodo, cuando se crea, entonces queda PENDIENTE.',
             'Los adeudos se suman por club y equipo según el ámbito del usuario.',
             'La Directiva solo ve los cobros de su club.'],
         rf=['RF-137', 'RF-140', 'UI-027'], ui=['F-33', 'F-08'], api=['GET/POST /cobros', 'GET /adeudos'],
         dep=['H-06'],
         sub=[S('Endpoints de cobros y adeudos', 'Montos `BigDecimal`; pruebas de ámbito.', 3, 'backend'),
              S('Pantalla de tesorería (F-33) y tarjeta de adeudos en F-08', 'Modal de nuevo cobro.', 5, 'frontend'),
              S('Prueba de aceptación de cobros', 'Evidencia.', 1, 'pruebas')]),

    dict(k='H-30', tipo='Historia', epica='E13', sprint=None, moscow='Should', prio='Medium', sp=3,
         titulo='Pagar un cobro en línea de forma simulada',
         como='Directiva de club', quiero='pagar un cobro pendiente con el pago simulado',
         para='liquidar adeudos sin acudir a Tesorería',
         desc='Pantalla F-34 (móvil). Pasarela simulada de ADR-0012 con referencia `SIM-…`; sin datos de tarjeta, '
              'sin «SPEI» ni QR (D-10).',
         ca=['Dado un cobro PENDIENTE, cuando se aprueba la simulación, entonces queda PAGADO con un pago '
             '`EN_LINEA_SIMULADO`.', 'Si se rechaza, queda RECHAZADO y puede reintentarse.',
             'Un cobro pagado o vencido no se paga en línea (VAL-028).',
             'La pantalla muestra «Pago simulado: no se realiza ningún cargo».'],
         rf=['RF-138', 'VAL-028', 'RNF-004', 'DEC-37'], ui=['F-34'],
         api=['POST /cobros/{id}/intentos-pago', 'POST /intentos-pago/{id}/confirmacion'], dep=['H-29'],
         sub=[S('`PasarelaSimulada` con intentos y confirmación atómica', 'Pruebas VAL-028 y concurrencia.', 4, 'backend'),
              S('Flujo móvil de pago simulado (F-34)', 'Tres pasos y comprobante.', 4, 'frontend'),
              S('Prueba de aceptación del pago simulado', 'Evidencia.', 1, 'pruebas')]),

    dict(k='H-31', tipo='Historia', epica='E13', sprint=None, moscow='Should', prio='Medium', sp=2,
         titulo='Registrar un pago recibido fuera del sistema con su comprobante',
         como='Tesorero de la Liga', quiero='registrar pagos en efectivo o transferencia con comprobante',
         para='reflejar lo cobrado en ventanilla',
         desc='Modal de F-33. Aplica a cobros PENDIENTE o VENCIDO; el comprobante va a S3.',
         ca=['Dado un cobro pendiente o vencido, cuando se registra el pago con comprobante, entonces queda PAGADO.',
             'El pago debe cubrir el monto total (RF-139).', 'Un cobro pagado no admite otro pago (VAL-028).'],
         rf=['RF-139', 'VAL-028', 'RNF-018'], ui=['F-33'], api=['POST /cobros/{id}/pagos', 'GET /cobros/{id}/pagos'],
         dep=['H-29', 'T-04'],
         sub=[S('Endpoint de pago externo con comprobante', 'Pruebas.', 2.5, 'backend'),
              S('Modal de registro de pago en F-33', 'Carga del comprobante.', 3, 'frontend'),
              S('Prueba de aceptación del pago externo', 'Evidencia.', 0.5, 'pruebas')]),
]


# ---------------------------------------------------------------------------
# Backlog B (sin sprint ni subtareas; se desglosan al entrar a un sprint).
# (k, épica, título, rf, ui, sp, nota)
# ---------------------------------------------------------------------------
BACKLOG_B = [
    ('B-01', 'E01', 'Atender solicitudes ARCO', ['RF-098'], [], 2, ''),
    ('B-02', 'E02', 'Asociar la convocatoria a la temporada', ['RF-002'], [], 1, ''),
    ('B-03', 'E02', 'Registrar reglas interpretadas pendientes de autorización', ['RF-007'], [], 2, ''),
    ('B-04', 'E03', 'Registrar el cambio de nombre de un club como club nuevo', ['RF-105'], [], 1, ''),
    ('B-05', 'E03', 'Registrar la venta de un equipo y la conservación de plaza', ['RF-103', 'RF-104', 'UI-034'], ['B-09'], 3, ''),
    ('B-06', 'E04', 'Renovar registros y transferir jugadores entre temporadas', ['RF-015', 'RF-016', 'RF-111', 'VAL-026'], [], 3, ''),
    ('B-07', 'E04', 'Consultar el historial de cambios de un registro', ['RF-018'], [], 1, ''),
    ('B-08', 'E05', 'Verificar la cuota de árbitros por club', ['RF-114'], ['F-20'], 1, ''),
    ('B-09', 'E05', 'Registrar la disponibilidad arbitral', ['RF-022'], [], 2, ''),
    ('B-10', 'E05', 'Consultar el historial de designaciones', ['RF-024'], [], 1, ''),
    ('B-11', 'E06', 'Agrupar canchas por sede', ['RF-028'], [], 1, ''),
    ('B-12', 'E06', 'Registrar mantenimiento tipificado de canchas', ['RF-031'], ['F-21'], 1, ''),
    ('B-13', 'E06', 'Registrar horarios de cancha de club y comodato', ['RF-119'], [], 2, ''),
    ('B-14', 'E06', 'Gestionar solicitudes de préstamo, comodato y oferta de cancha con oficio', ['RF-116', 'RF-117', 'RF-118', 'UI-028'], ['B-07'], 5, ''),
    ('B-15', 'E07', 'Advertir las reglas de localía y aceptar con motivo', ['RF-120', 'VAL-027'], ['F-22'], 2, ''),
    ('B-16', 'E08', 'Conservar el histórico de la tabla por jornada', ['RF-051'], [], 2, ''),
    ('B-17', 'E11', 'Calcular y publicar estadísticas de jugador con consentimiento', ['RF-054', 'RF-145', 'UI-013'], ['B-01'], 3, ''),
    ('B-18', 'E09', 'Adjuntar evidencias al expediente', ['RF-059'], ['F-30'], 2, ''),
    ('B-19', 'E09', 'Proponer la sanción según el artículo', ['RF-061'], ['F-31'], 1, ''),
    ('B-20', 'E09', 'Gestionar la lista de castigados y publicar sanciones', ['RF-066', 'RF-068', 'UI-016'], ['B-02'], 2, ''),
    ('B-21', 'E09', 'Interponer y resolver recursos de inconformidad', ['RF-067', 'UI-017'], ['B-03'], 3, 'Plazo pendiente (PEN-041).'),
    ('B-22', 'E09', 'Aplicar efectos automáticos: baja, sanción retroactiva y partidos restantes', ['RF-130', 'RF-131', 'RF-132'], [], 5, ''),
    ('B-23', 'E09', 'Generar el cobro de una multa', ['RF-133'], [], 1, ''),
    ('B-24', 'E10', 'Proponer y validar ascensos y descensos', ['RF-069', 'RF-070', 'RF-071', 'RF-072', 'RF-073', 'RF-074', 'RF-075', 'UI-018'], ['B-04'], 5, ''),
    ('B-25', 'E10', 'Configurar y jugar la liguilla', ['RF-076', 'RF-077', 'RF-078', 'RF-079', 'RF-080', 'RF-081', 'UI-019'], ['B-05'], 8, ''),
    ('B-26', 'E11', 'Ampliar el portal: calendario, comunicados, sanciones y equipos', ['RF-085', 'RF-086', 'RF-087', 'RF-088'], ['B-10'], 3, ''),
    ('B-27', 'E11', 'Redactar, aprobar y publicar comunicados', ['RF-090', 'UI-031'], ['B-08'], 2, ''),
    ('B-28', 'E12', 'Generar y exportar reportes REP-001 a REP-014', ['RF-091', 'RF-092', 'UI-021'], ['B-06'], 5, ''),
    ('B-29', 'E13', 'Configurar tarifas de arbitraje y cancha', ['RF-135'], [], 2, 'Tarifas reales pendientes (PEN-042).'),
    ('B-30', 'E13', 'Generar cobros automáticos al publicar la jornada', ['RF-136'], [], 3, ''),
    ('B-31', 'E13', 'Vencer cobros no pagados', ['RF-141'], [], 2, ''),
    ('B-32', 'E14', 'Enviar los avisos por correo', ['RF-143'], [], 3, 'Bloqueada hasta elegir proveedor (PEN-034, DEC-46).'),
]

# ---------------------------------------------------------------------------
# Replanificación del 25 oct 2026 (DEC-50). El núcleo Should y lo B se asignan a los Sprints 2 a 4.
# La línea base de arriba (Should y B sin sprint) se conserva porque es la que generó el CSV ya importado;
# esta tabla refleja las etiquetas `sprint-N` que esos issues tienen hoy en Jira.
# ---------------------------------------------------------------------------
REPLAN_FECHA = '25 de octubre de 2026'
REPLAN = {
    2: ['H-20', 'H-21', 'H-22', 'H-26', 'H-29', 'H-30', 'H-31',
        'B-02', 'B-03', 'B-04', 'B-05', 'B-11', 'B-12', 'B-13', 'B-27', 'B-29', 'B-31'],
    3: ['H-23', 'H-24', 'B-01', 'B-06', 'B-07', 'B-08', 'B-09', 'B-14', 'B-15', 'B-30'],
    4: ['H-25', 'H-27', 'H-28', 'B-10', 'B-16', 'B-17', 'B-18', 'B-19', 'B-20', 'B-21', 'B-22', 'B-23',
        'B-24', 'B-25', 'B-26', 'B-28', 'B-32'],
}
# Horas de las subtareas creadas en Jira (SCRUM-366 a SCRUM-396) para las historias B del Sprint 2.
HORAS_B = {'B-02': 3, 'B-03': 6, 'B-04': 3, 'B-05': 9, 'B-11': 3, 'B-12': 3, 'B-13': 6, 'B-27': 6,
           'B-29': 6, 'B-31': 6}
# Responsable de cada historia y tarea del Sprint 2 (Jira, 25 oct 2026). Las pruebas de aceptación las hace
# otro integrante: Leonardo prueba lo de Israel, Israel lo de Geovani y Geovani lo de Leonardo.
RESPONSABLE_S2 = {
    'H-02': 'Israel', 'H-03': 'Geovani', 'H-04': 'Geovani', 'H-05': 'Israel', 'H-06': 'Leonardo',
    'H-07': 'Leonardo', 'T-04': 'Leonardo', 'H-20': 'Israel', 'H-21': 'Leonardo', 'H-22': 'Israel',
    'H-26': 'Israel', 'H-29': 'Geovani', 'H-30': 'Geovani', 'H-31': 'Leonardo', 'B-02': 'Geovani',
    'B-03': 'Israel', 'B-04': 'Leonardo', 'B-05': 'Geovani', 'B-11': 'Leonardo', 'B-12': 'Leonardo',
    'B-13': 'Leonardo', 'B-27': 'Geovani', 'B-29': 'Israel', 'B-31': 'Leonardo',
}

# Requisitos fuera de la planificación por estar pendientes o fuera de alcance
EXCLUIDOS = [
    ('2FA y «Mantener sesión activa»', 'PEN-044 (nuevo) y PEN-030', 'Pendiente por decisión del equipo (20 oct 2026).'),
    ('Programación automática', 'RF-034, RF-035, RF-037, RF-038, RF-041, RF-042', 'Fuera de alcance (F, DEC-06).'),
    ('Despliegue en un ambiente público', 'PEN-036, PEN-037', 'Pendiente; la demo es local.'),
    ('Respaldo diario y disponibilidad de fin de semana', 'RNF-010, RNF-001 (B)', 'Dependen del alojamiento (PEN-037).'),
]
