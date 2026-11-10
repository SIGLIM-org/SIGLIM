# AGENTS.md — Instrucciones para agentes de IA

Este archivo lo leen los agentes de programación (Claude Code, Codex, GitHub Copilot, Cursor,
Gemini, etc.) antes de trabajar en el repositorio. Las carpetas `frontend/` y `backend/` tienen su
propio `AGENTS.md` con reglas del stack; **el más cercano al archivo que editas tiene prioridad**
en lo específico, y este aplica a todo.

## 1. El proyecto en tres líneas

SIGLIM es la plataforma web de la **Liga Municipal de Futbol Amateur Morelia**: afilia clubes,
registra y valida jugadores por CURP, programa partidos manualmente, oficializa resultados con una
cédula validada por el árbitro, aplica la disciplina, cobra cuotas y publica en un portal de solo
lectura, con 12 tipos de usuario. Proyecto académico real (ITM, equipo de 3, 8 semanas) con
requisitos en IEEE 830 **todavía no aprobados por la Liga**.

## 2. Antes de escribir código, lee

| Orden | Documento | Por qué |
| --- | --- | --- |
| 1 | [`docs/requisitos/ERS.md`](docs/requisitos/ERS.md) | Qué hace el sistema, qué **no** hace (§2.6) y qué está pendiente |
| 2 | [`docs/arquitectura/arquitectura-y-diseno-jerarquico.md`](docs/arquitectura/arquitectura-y-diseno-jerarquico.md) | Módulos y submódulos M-xx con sus RF, dependencias entre módulos, capas |
| 3 | [`docs/arquitectura/adr/`](docs/arquitectura/adr/README.md) | Decisiones técnicas vigentes |
| 4 | [`docs/arquitectura/convenciones-api.md`](docs/arquitectura/convenciones-api.md) | Contrato REST, errores, paginación |
| 5 | [`docs/base-de-datos/diccionario-de-datos.md`](docs/base-de-datos/diccionario-de-datos.md) + [`supabase/migrations/`](supabase/migrations/) | Tablas, columnas, triggers, RLS |
| 6 | [`docs/diseno-ui/`](docs/diseno-ui/README.md) | Tokens, componentes, accesibilidad (solo frontend) |

No leas todo en cada tarea: localiza el módulo (M-xx) y los RF de la tarea y lee esas secciones.

**Si dos documentos se contradicen, gana el de menor número en la tabla.** El diseño UI es el de
menor autoridad: ver los conflictos C-01 a C-08 en
[`docs/diseno-ui/open-design-decisions.md`](docs/diseno-ui/open-design-decisions.md).

> `docs/requisitos/ERS.md` es la **Rev. 0.4.1** (integra el registro de decisiones DEC-01 a DEC-46).
> Si un requisito es ambiguo, pregunta en lugar de suponer.
> Las carpetas `historico/` (en `docs/requisitos/`, `docs/requisitos/bpmn/`, `docs/arquitectura/` y `docs/base-de-datos/`) contienen versiones **reemplazadas**,
> y `docs/requisitos/fuentes/` el levantamiento original: nunca los uses para implementar.

## 3. Reglas de alcance (las más importantes)

> ⚠️ **Antes de programar:** la arquitectura, los ADR, el diccionario de datos, la migración y las
> convenciones de API ya reflejan la Rev. 0.4.1. El prototipo de pantallas está aprobado por el
> equipo ([`docs/diseno-ui/pantallas/`](docs/diseno-ui/pantallas/README.md) y
> [`DESIGN.md`](DESIGN.md)); sus diferencias (D-xx) con el ERS, en [`pantallas/README.md` §3](docs/diseno-ui/pantallas/README.md), no se
> implementan. El trabajo se toma del **backlog v4**
> ([`docs/planning/sprint-plan.md`](docs/planning/sprint-plan.md)): implementa solo historias de un
> sprint y cita su clave (H-xx, T-xx) en la rama, el commit y el PR.

> 🌿 **Ramas (DEC-51):** todo el trabajo sale de `develop` y vuelve a `develop` por Pull Request;
> nunca crees una rama desde `main` ni abras un PR hacia `main`. `main` solo recibe `develop` al
> cerrar cada sprint. Detalle en [`CONTRIBUTING.md`](CONTRIBUTING.md) §1.

1. **Entrega `N`** (núcleo comprometido): se implementa en los Sprints. **Entrega `B`** (backlog
   priorizado): solo cuando su historia entre a un Sprint. **Entrega `F`** (ERS §2.6): nunca, aunque
   parezca fácil: programación automática, pasarela de pago real, reconocimiento facial,
   WhatsApp/SMS, mapas, API pública, login con proveedores externos, importación masiva de
   históricos, varias ligas en operación, CI/CD y contenedores.
2. **Marca `[PENDIENTE]` → no se implementa.** Detente e informa qué decisión falta (cita el
   `PEN-xxx` o `Q-xx` del ERS, Apéndices A y C).
3. **Valores `[DEMO]`** (puntos, desempates, bloque de cancha, plazo de cédula, marcador por default,
   liguilla, ascensos, catálogo de artículos): se implementan como **configuración**, nunca fijos en
   el código, y se rotulan «no oficial» en la interfaz.
4. **No inventes reglas de negocio** (RN-006, RN-035). Si una regla no está escrita, usa un valor
   configurable, márcalo `[PROPUESTA]` en un comentario y menciónalo en tu respuesta.
5. **Permisos:** nunca decidas por el nombre del rol; usa el permiso `modulo.recurso:ACCION` más el
   **ámbito** (liga, club o propio) con `autorizacion.exigir(...)` (ADR-0007). Para **listados**, usa
   `autorizacion.alcance(permiso)` en el `WHERE` y en el `COUNT`. La matriz inicial está en el
   Apéndice E del ERS y es configurable.
6. Los datos de las capturas de diseño ("Liga Apex", "Temporada 2024/25", "Primera División") son
   de ejemplo, no requisitos.

## 4. Mapa del repositorio

```text
docs/                 Documentación (fuente de verdad). No la borres ni la reescribas sin pedirlo.
supabase/migrations/  Esquema SQL versionado. Única fuente del esquema (ADR-0004).
backend/              Spring Boot 4.1 · Java 21 · Spring JDBC · paquetes por módulo M-xx
frontend/             React 19 · TypeScript · Vite · Tailwind CSS 4 · TanStack Query
.github/              Plantillas de PR e issues
```

## 5. Comandos

| Qué | Dónde | Comando |
| --- | --- | --- |
| Compilar y probar backend | `backend/` | `./mvnw verify` (Windows: `mvnw.cmd verify`) |
| Ejecutar backend | `backend/` | `./mvnw spring-boot:run` (requiere `backend/.env`) |
| Instalar frontend | `frontend/` | `npm install` |
| Ejecutar frontend | `frontend/` | `npm run dev` |
| Verificar frontend | `frontend/` | `npm run lint && npm test && npm run build` |
| Probar el esquema (migraciones) | `supabase/pruebas/` | `PGHOST=localhost PGPORT=5432 PGUSER=postgres ./ejecutar.sh` |

Antes de dar una tarea por terminada, ejecuta la verificación del subproyecto que tocaste y
reporta el resultado real. Si no pudiste ejecutarla, dilo.

## 6. Modelo de dominio (resumen)

Esquema `siglim` de la migración `20261020090000_esquema_inicial.sql` (72 tablas; detalle en el
[diccionario de datos](docs/base-de-datos/diccionario-de-datos.md)). No está expuesto por la API de
Supabase: solo lo usa el backend con los roles `siglim_app` y `siglim_publico` (ADR-0014).

```text
Liga ─< Temporada ─< CategoríaTemporada ─< Grupo ─< Equipo >─ Club ─< Afiliación, CargoDirectiva
            │                                 │       └─< RegistroJugador >─ Persona (CURP) ─< Tutela
            └─< ParámetrosTemporada (versión)  └─< Jornada ─< Partido >─ Cancha ─< DisponibilidadCancha
Partido ─ Cédula ─< Alineación, EventoCédula, Incidencia ─ Expediente ─< Resolución ─< Sanción
Partido ─< Resultado (versionado, uno vigente) · Designación >─ Árbitro · Cobro ─< IntentoPago, Pago
Perfil (auth.users) ─< AsignaciónRol (ámbito) >─ Rol ─< RolPermiso >─ Permiso · Auditoría · Notificación
Documento (bucket, clave y URL en AWS S3) → registro, persona, afiliación, expediente o pago
```

| Enum (BD) | Valores |
| --- | --- |
| `estado_temporada` | `CONFIGURACION`, `ACTIVA`, `CERRADA` |
| `estado_partido` | `PROGRAMADO`, `EN_CURSO`, `FINALIZADO`, `SUSPENDIDO`, `REPROGRAMADO`, `CANCELADO` (DEC-23) |
| `estado_cedula` | `EN_CAPTURA`, `PENDIENTE_VALIDACION`, `DEVUELTA`, `APROBADA` |
| `estado_registro` | `PENDIENTE_VALIDACION`, `ACTIVO`, `SUSPENDIDO`, `BAJA`, `RECHAZADO` |
| `estado_expediente` | `ABIERTO`, `EN_REVISION`, `RESUELTO`, `EN_RECURSO`, `CERRADO` |
| `estado_cobro` | `PENDIENTE`, `PAGADO`, `RECHAZADO`, `VENCIDO` |
| `estado_publicacion` | `BORRADOR`, `REVISION`, `PUBLICADO` |
| `ambito` | `LIGA`, `CLUB`, `PROPIO` |
| Roles semilla | 12 tipos de usuario del ERS §2.3 (11 con cuenta) + `SISTEMA` |

Los triggers validan las transiciones y lanzan errores con el código del ERS en `HINT`
(`VAL-003`, `RN-021`…); el servicio valida antes y explica (§7).

## 7. Invariantes que el código nunca debe romper

- **Nada con historial se borra** (temporadas, equipos, partidos, resultados, registros): RF-003,
  RF-044, RN-005, RN-020. Las bajas y cancelaciones son cambios de estado.
- **Toda transición de estado del partido lleva motivo** y queda en historial (RF-045, RN-021).
- **Toda operación crítica se audita**: usuario, fecha, módulo, acción y motivo (RF-093, VAL-016).
- **La tabla de posiciones se calcula** con `calcular_tabla()` a partir de resultados oficiales;
  no se guarda en una tabla (RN-022).
- **Corregir un resultado oficial** conserva la versión anterior y recalcula la tabla (RF-052, RN-024).
- **Programación:** un equipo no puede tener dos partidos que se traslapen; una cancha no puede
  estar ocupada dos veces ni usarse si está bloqueada (RF-039, VAL-003 a VAL-005). El servicio lo
  valida **antes** y responde con un mensaje que explique el conflicto; el trigger es la última
  garantía.
- **El portal solo muestra lo `PUBLICADO`** y nunca datos personales (RF-056, VAL-014, RNF-012).
- **Denegación por defecto:** sin permiso explícito, la acción se rechaza (VAL-015, RNF-003).

## 8. Convenciones de código

- **Idioma:** dominio en **español** y alineado con la BD (`Temporada`, `equipoLocalId`,
  `registrarTransicion`); sufijos técnicos en inglés (`Controller`, `Service`, `Repository`, `Dto`).
  Comentarios, mensajes al usuario y documentación en español. Sin abreviaturas crípticas.
- **Trazabilidad:** cita los IDs del ERS en Javadoc/JSDoc de servicios y en el nombre o
  `@DisplayName` de las pruebas (`RF-039: rechaza cancha ocupada`).
- **Base de datos:** nunca modifiques una migración existente; crea una nueva en
  `supabase/migrations/AAAAMMDDHHMMSS_descripcion.sql` y actualiza el diccionario en el mismo cambio.
  Usa los nombres exactos de tablas y columnas del diccionario. La migración se aplica **sola** al
  proyecto compartido de Supabase cuando su PR se fusiona en `develop` (DEC-52): antes, haz que
  pasen `supabase/pruebas/ejecutar.sh` y `./mvnw verify`, y nunca propongas aplicarla con el SQL
  Editor ni con `supabase db push` contra el proyecto compartido.
- **API:** sigue [`convenciones-api.md`](docs/arquitectura/convenciones-api.md): `/api/v1`, JSON
  camelCase, errores RFC 9457 con `codigo`, acciones de estado como `POST` a sub-recursos.
- **Commits y ramas:** Conventional Commits con `Refs: RF-xxx`; ramas desde `develop` y PR hacia
  `develop` (ver [`CONTRIBUTING.md`](CONTRIBUTING.md)).
- Cambios pequeños y enfocados en la tarea. No reformatees ni renombres archivos ajenos a ella.
- **Diseño de interfaz (solo `frontend/`):** al crear o modificar UI (componentes, pantallas,
  estilos, layout, textos de interfaz) usa la skill `impeccable` y, antes de cerrar una pantalla,
  pásale `/impeccable polish`. Su fuente de verdad visual es [`DESIGN.md`](DESIGN.md) (en la
  raíz, donde la skill lo detecta) y el resto de `docs/diseno-ui/`: no lo muevas ni crees copias
  en otras carpetas. Si una sugerencia de la skill contradice esos documentos o
  el ERS, ganan ellos (sección 2). No la uses en tareas de backend, base de datos o documentación.

## 9. Seguridad

- **Nunca** escribas secretos en el código ni en archivos versionados. Las credenciales van en
  `backend/.env` y `frontend/.env.local` (ignorados por git); documenta cada variable nueva en el
  `.env.example` correspondiente.
- La clave `service_role`/`secret` de Supabase y las credenciales de AWS **jamás** van en el frontend.
- El frontend usa Supabase **solo para iniciar sesión**; todos los datos pasan por el backend.
- No registres en logs contraseñas, tokens ni datos personales.
- SQL siempre parametrizado (`:parametro` con `JdbcClient`); nunca concatenes entradas del usuario.

## 10. Cómo trabajar una tarea

1. Identifica el módulo (M-xx), los RF/CU/VAL y su estado de entrega en el ERS.
2. Si algo está `[PENDIENTE]`, fuera de alcance o es ambiguo, **pregunta antes de implementar**.
3. Revisa tablas y funciones existentes en el diccionario; reutiliza las del esquema.
4. Implementa de abajo hacia arriba: migración (si hace falta) → repositorio → servicio (reglas +
   transacción + auditoría) → controlador → cliente del frontend → pantalla.
5. Escribe pruebas de la regla principal y de su caso de error.
6. Ejecuta la verificación (sección 5) y actualiza la documentación afectada.
7. Resume: qué hiciste, qué RF cubre, qué quedó pendiente y qué supusiste.

## 11. No hagas

- No agregues dependencias nuevas sin justificarlas en tu respuesta.
- No uses JPA/Hibernate ni `ddl-auto`; no ejecutes DDL desde el backend (ADR-0002, ADR-0004).
- No consultes tablas de Supabase directamente desde React.
- No crees pantallas, endpoints o tablas "por si acaso" ni carpetas vacías.
- No marques una tarea como terminada si no compila o si las pruebas fallan.
- No edites `docs/requisitos/ERS.md` salvo que te lo pidan: es un documento del equipo con control de revisiones.
