# Pantallas aprobadas (Google Stitch)

Prototipo de SIGLIM generado con los [prompts de Stitch](../prompts-stitch.md) y **aprobado por el
equipo el 20 de octubre de 2026** (PEN-014, del lado del equipo; falta validarlo con la Liga). Se
exportó con la opción *.zip* de Stitch. Cada carpeta tiene:

- `screen.png`: captura de la pantalla.
- `code.html`: HTML con Tailwind (desde CDN) y JavaScript de las interacciones dentro de la
  pantalla. Se abre directo en el navegador.

El sistema visual (colores, tipografía, espaciado, componentes) está en [`DESIGN.md`](../../../DESIGN.md).

> [!IMPORTANT]
> El prototipo es la **referencia visual y de interacción**, no la fuente de los requisitos ni del
> código. El HTML **no se copia** al frontend: las pantallas se construyen en React con los
> componentes y tokens del proyecto (frontend/AGENTS.md). Stitch inventó textos, reglas y
> elementos institucionales que **no se implementan**; están en la [sección 3](#3-diferencias-con-el-ers-que-no-se-implementan).
> Si algo choca, mandan el ERS y las convenciones de API (AGENTS.md §2).

## 1. Pantallas

| Frame | Carpeta | UI | E | Interacciones dentro de la pantalla |
| --- | --- | --- | --- | --- |
| F-01 | [`F-01-portal-programacion`](F-01-portal-programacion/) | UI-020 | N | — |
| F-02 | [`F-02-portal-programacion-movil`](F-02-portal-programacion-movil/) | UI-020 | N | Menú lateral; hoja inferior de filtros; quitar chips de filtro |
| F-03 | [`F-03-portal-resultados`](F-03-portal-resultados/) | UI-020 | N | — |
| F-04 | [`F-04-portal-tabla`](F-04-portal-tabla/) | UI-012, UI-020 | N | — |
| F-05 | [`F-05-inicio-sesion`](F-05-inicio-sesion/) | UI-001 | N | Mostrar u ocultar contraseña (frame modificado por el equipo, §2) |
| F-06 | [`F-06-aviso-privacidad-consentimiento`](F-06-aviso-privacidad-consentimiento/) | UI-033 | N | Casillas de finalidades y consentimiento |
| F-07 | [`F-07-panel-presidente`](F-07-panel-presidente/) | UI-002 | N | — |
| F-08 | [`F-08-panel-directiva-club`](F-08-panel-directiva-club/) | UI-002 | N | — |
| F-09 | [`F-09-notificaciones`](F-09-notificaciones/) | UI-030 | N | Pestañas No leídas/Todas; filtro por categoría; panel de la campana; marcar como leídas |
| F-10 | [`F-10-temporadas`](F-10-temporadas/) | UI-003 | N | Modal de cierre de temporada con motivo |
| F-11 | [`F-11-categorias-grupos-parametros`](F-11-categorias-grupos-parametros/) | UI-004 | N | Pestañas Categorías/Grupos/Parámetros; modal de historial; modal «Guardar como nueva versión» |
| F-12 | [`F-12-usuarios`](F-12-usuarios/) | UI-029 | N | Modal «Invitar usuario»; panel lateral del usuario |
| F-13 | [`F-13-roles-permisos`](F-13-roles-permisos/) | UI-029 | N | Pestañas Matriz/Solicitudes; modal de solicitud; modal de dictamen (aprobar o rechazar) |
| F-14 | [`F-14-auditoria`](F-14-auditoria/) | UI-022 | N | — |
| F-15 | [`F-15-clubes-equipos`](F-15-clubes-equipos/) | UI-005 | N | Pestañas del detalle del club |
| F-16 | [`F-16-afiliacion`](F-16-afiliacion/) | UI-023 | N | Vista Directiva/Presidente; modales de aprobación y rechazo |
| F-17 | [`F-17-plantilla-equipo`](F-17-plantilla-equipo/) | UI-006 | N | Filtro por estado; modal de baja con motivo |
| F-18 | [`F-18-alta-jugador`](F-18-alta-jugador/) | UI-006, UI-024 | N | Asistente de 4 pasos |
| F-19 | [`F-19-validacion-registros`](F-19-validacion-registros/) | UI-024 | N | Modal de rechazo con observaciones |
| F-20 | [`F-20-arbitros`](F-20-arbitros/) | UI-007 | N | Vista Comisión/Club; pestañas por estado; modales de registro y rechazo |
| F-21 | [`F-21-canchas`](F-21-canchas/) | UI-008 | N | Modal de bloqueo con motivo |
| F-22 | [`F-22-programacion-jornada`](F-22-programacion-jornada/) | UI-009 | N | Modal «Aceptar con motivo» de la advertencia de localía |
| F-23 | [`F-23-aprobacion-jornada`](F-23-aprobacion-jornada/) | UI-009 | N | Modales de aprobación y devolución |
| F-24 | [`F-24-designacion-arbitros`](F-24-designacion-arbitros/) | UI-025 | N | — |
| F-25 | [`F-25-detalle-partido`](F-25-detalle-partido/) | UI-010 | N | Pestañas; modal de reprogramación |
| F-26 | [`F-26-captura-cedula`](F-26-captura-cedula/) | UI-011 | N | Panel de penales; marcador; eventos |
| F-27 | [`F-27-validacion-cedula-arbitro`](F-27-validacion-cedula-arbitro/) | UI-014 | N | Modales de aprobar y devolver |
| F-28 | [`F-28-cedulas-pendientes-comision`](F-28-cedulas-pendientes-comision/) | UI-014 | N | — |
| F-29 | [`F-29-tabla-interna`](F-29-tabla-interna/) | UI-012 | N | Modal de sorteo |
| F-30 | [`F-30-expedientes`](F-30-expedientes/) | UI-015 | N | — |
| F-31 | [`F-31-resolucion-expediente`](F-31-resolucion-expediente/) | UI-015 | N | — |
| F-32 | [`F-32-catalogos-disciplinarios`](F-32-catalogos-disciplinarios/) | UI-026 | N | Pestañas Incidencias/Artículos |
| F-33 | [`F-33-tesoreria`](F-33-tesoreria/) | UI-027 | N | Modales «Nuevo cobro» y «Registrar pago externo» |
| F-34 | [`F-34-adeudos-pago-simulado`](F-34-adeudos-pago-simulado/) | UI-027 | N | Flujo de pago simulado en 3 pasos |
| F-35 | [`F-35-mi-informacion`](F-35-mi-informacion/) | UI-032 | N | Selector de menor; modal de revocación de difusión |
| F-36 | — | Transversal | N | No se generó: los estados (403, 404, error de conexión, avisos, modal de motivo, vacío, carga) se construyen con `DESIGN.md` (Q-UI-03) |
| B-01 | [`B-01-estadisticas`](B-01-estadisticas/) | UI-013 | B | Vista interna/pública; pestañas Equipos/Jugadores |
| B-02 | [`B-02-sanciones-castigados`](B-02-sanciones-castigados/) | UI-016 | B | Vista interna/pública; modal del expediente |
| B-03 | [`B-03-recursos`](B-03-recursos/) | UI-017 | B | Vista Club/Presidencia |
| B-04 | [`B-04-ascensos-descensos`](B-04-ascensos-descensos/) | UI-018 | B | Modales de validación y observaciones |
| B-05 | [`B-05-liguilla`](B-05-liguilla/) | UI-019 | B | Modales de parámetros y de programar partido de llave |
| B-06 | [`B-06-reportes`](B-06-reportes/) | UI-021 | B | Modal de descarga PDF/Excel |
| B-07 | [`B-07-tramites-cancha`](B-07-tramites-cancha/) | UI-028 | B | Pestañas Formato 1/2; vista previa de la carta |
| B-08 | [`B-08-comunicados`](B-08-comunicados/) | UI-031 | B | Pestañas Editor/Vista previa |
| B-09 | [`B-09-venta-equipo-plaza`](B-09-venta-equipo-plaza/) | UI-034 | B | Vista Club/Presidencia; modal de rechazo |
| B-10 | [`B-10-portal-ampliado`](B-10-portal-ampliado/) | UI-020 | B | Anclas a Calendario, Comunicados, Equipos y Sanciones |

**Navegación entre pantallas.** La exportación no incluye enlaces de una pantalla a otra: cada
`code.html` es independiente y sus interacciones son internas (pestañas, modales, pasos). La
navegación se implementa con las rutas del frontend (`/` portal, `/app` aplicación interna) según el
ERS §3.1.1: inicio de sesión → panel con los módulos permitidos y notificaciones; cada aviso y cada
tarjeta de pendientes lleva a la pantalla de su trámite.

**Responsivo.** Solo F-02, F-27, F-34 y F-35 tienen diseño móvil. Las demás pantallas se adaptan
con los puntos de quiebre de [`DESIGN.md`](../../../DESIGN.md) (escritorio ≥ 1024 px, tableta,
móvil < 768 px) y deben funcionar desde 360 px sin desplazamiento horizontal (RNF-007).

## 2. Cambio del equipo: inicio de sesión (F-05)

El equipo rediseñó F-05 a dos paneles: uno institucional a la izquierda con fotografía y otro con el
formulario a la derecha. Lo que se implementa:

- Disposición en dos paneles con la fotografía del panel izquierdo (es una imagen generada, no de un
  lugar real; Q-UI-04).
- **Solo correo** y contraseña con Supabase Auth (Q-UI-01): el campo se rotula «Correo
  electrónico». Mostrar u ocultar contraseña, «¿Olvidaste tu contraseña?», sin registro libre,
  enlaces al portal y al aviso de privacidad, y el aviso para tutores (UI-001).

Lo que **no** se implementa: acceso con «Folio federativo» (descartado, Q-UI-01); «2FA Federativo
Activo» y «Mantener sesión activa», pendientes por decisión del equipo (PEN-044, DEC-48, Q-UI-02);
los datos y sellos institucionales del panel izquierdo (§3).

## 3. Diferencias con el ERS que no se implementan

Stitch agregó contenido que no está en el ERS. La interfaz se construye **sin** estos elementos;
cuando sea texto de ejemplo, se reemplaza por datos reales del backend.

| ID | Qué agregó Stitch | Dónde | Por qué no | Qué se hace |
| --- | --- | --- | --- | --- |
| D-01 | «A.C.», afiliación o «Sello oficial» de la FMF, «Federado FMF», credenciales y estatus FMF | F-05, F-15, F-16, F-25, B-08, B-10 y otras | La naturaleza jurídica de la Liga está pendiente (PEN-039); el ERS no menciona a la FMF. Afirmarlo sin respaldo es un dato falso | Nombre: «Liga Municipal de Futbol Amateur Morelia», sin sellos ni afiliaciones |
| D-02 | «Colegio de Árbitros de Michoacán A.C.» como origen de los árbitros y requisito de constancia física | F-20, F-25, F-26, F-34, F-03 | Los árbitros los **presentan los clubes** y los valida la Comisión de Arbitraje (DEC-17, RF-112, RF-113) | Mostrar el club que presenta al árbitro |
| D-03 | Validación de CURP «en tiempo real con RENAPO» | F-06, F-18, F-35 | Integración no aprobada (ERS §2.6); validación de nivel 2 por documentos (DEC-10) | Validar formato de CURP y documentos revisados por el Secretario |
| D-04 | Avisos por SMS | F-25 | WhatsApp/SMS fuera de alcance (ERS §2.6) | Avisos en la aplicación (RF-142); correo en B (RF-143) |
| D-05 | «Firma electrónica», «PDF firmado», «Sesión monitoreada», «Acceso cifrado SHA-256», «2FA» | F-03, F-05, F-14, F-16 | No hay requisito de firma electrónica ni de MFA; la política de sesión está pendiente (PEN-030) | Sin esas leyendas |
| D-06 | Exportar o imprimir (padrón, plantilla, bitácora, rol de liguilla) en pantallas del núcleo | F-14, F-15, F-17, B-05 | La exportación es backlog (RF-092, M-12) | Botón oculto hasta que RF-092 entre a un Sprint |
| D-07 | Reglas inventadas: «Art. 88 … retención de cédula», suspensión por «5 amarillas», «mínimo 7 titulares», artículos 34, 37, 52 y 62 | F-26, F-34, B-08 | No inventar reglas (RN-006, RN-035) | Valores configurables rotulados «No oficial»; artículos del catálogo `[DEMO]` |
| D-08 | Aviso de privacidad con transferencias a aseguradoras, colegios de árbitros y la FMF | F-06 | El texto del aviso lo define la Liga (PEN-039, DEC-40) | El contenido se carga desde `avisos_privacidad`; el prototipo solo define la disposición |
| D-09 | «Tiempo real», «En vivo», «Cédula digital en vivo», «el árbitro desbloquea el llenado» | F-01, F-05, F-25, F-27 | Sin tiempo real (C-06); el partido pasa a En curso por hora y la cédula la captura el Capturista (RF-127, RF-025) | Estado al consultar; sin desbloqueo por el árbitro |
| D-10 | «Ver QR», «Pasarela federativa SPEI» | F-08, F-34 | Sin requisito de QR; el pago es solo simulado (DEC-37, ADR-0012) | Sin QR; rótulo «Pago simulado» |
| D-11 | Cifras y nombres inventados («142 clubes», «18,000+ deportistas», «Estadio Unidad Dep. Cuauhtémoc», «Club Félix Ireta», «Indios de Morelia») | F-05, F-26, B-05 | Datos de ejemplo (AGENTS.md §3.6) | Datos reales del backend; el panel de F-05 sin estadísticas |
| D-12 | `alert()` del navegador para confirmaciones | F-15, F-17, F-19, F-25, B-02, B-03, B-05 | Bloquea la página y no es accesible | Modal de confirmación del sistema de diseño |
| D-13 | Rol del funcionario, filtro «Entidad afectada», nombre legible de la entidad, IP, navegador, folio, hash, «No repudio», tabla de valor anterior y nuevo, lote de 200, rótulos «No oficial» y nombres de módulo desplazados | F-14 | La auditoría no guarda esos datos, el contrato no los ofrece (convenciones de API §6.1) y el ERS tiene su propia lista de módulos (§2.2). Validar integridad y exportar ya están en D-05 y D-06 | Solo los campos y filtros del contrato, módulos con el nombre del ERS, lotes de 50 o 100 y `detalle` como pares clave-valor |

## 4. Preguntas del prototipo

| ID | Pregunta | Respuesta del equipo (20 oct 2026) | Afecta |
| --- | --- | --- | --- |
| Q-UI-01 | ¿Inicio de sesión con «Folio federativo» además del correo? | **Solo correo.** | F-05, RF-094 |
| Q-UI-02 | ¿Segundo factor (2FA) y «Mantener sesión activa»? | **Pendiente** por decisión del equipo (PEN-044, DEC-48). No se implementa. | F-05, RNF-014 |
| Q-UI-03 | ¿F-36 en Stitch o directamente con `DESIGN.md`? | **Con `DESIGN.md`**, sin generar el frame. | F-36 |
| Q-UI-04 | ¿Se conserva la fotografía de F-05? | **Sí**: es una imagen generada, no de un lugar real. | F-05 |
