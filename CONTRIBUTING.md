# Cómo contribuir a SIGLIM

Guía de trabajo del equipo. Aplica igual a personas y a agentes de IA.

## 1. Flujo de ramas (`develop` durante el sprint, `main` al cierre)

Decisión del equipo del 26 de octubre de 2026 ([DEC-51](docs/requisitos/decisiones/registro-de-decisiones.md)).

| Rama | Para qué sirve | Cómo recibe cambios |
| --- | --- | --- |
| `develop` | Integración del sprint: aquí se junta todo el código y la documentación del sprint en curso. | Solo por Pull Request desde una rama de trabajo. |
| `main` | Versión estable que se demuestra en la revisión del sprint. | Solo por **un** Pull Request `develop → main` al terminar cada sprint. |

- **Nadie hace push directo a `develop` ni a `main`.**
- Cada historia o tarea se trabaja en una rama corta que **sale de `develop`** y **vuelve a `develop`**
  por Pull Request. Al abrir el PR en GitHub, la rama **base** debe ser `develop` (GitHub propone
  `main` por defecto).
- Antes de crear la rama, actualiza `develop`:

  ```bash
  git switch develop
  git pull origin develop
  git switch -c feat/m01-h02-auditoria
  ```

- Al fusionar el PR, borra la rama y vuelve a actualizar `develop` local.
- **Cierre del sprint:** después de la revisión del sprint se abre un PR `develop → main` con el
  resumen del incremento. Se fusiona con **«Create a merge commit»**, no con *squash*: si se
  aplasta, `main` y `develop` quedan con historiales distintos y los siguientes PR muestran
  conflictos que no existen.
- Una corrección urgente sobre lo ya demostrado también sale de `develop` y llega a `main` en el
  siguiente cierre de sprint.

> 🗄️ **Migraciones de base de datos (DEC-52).** La integración de Supabase usa `develop` como
> rama de producción: una migración se aplica al proyecto compartido **en cuanto su PR se fusiona
> en `develop`**. Por eso, antes de fusionarlo deben pasar `supabase/pruebas/ejecutar.sh` y
> `./mvnw verify`, y después se confirma que quedó aplicada. Nunca se aplica a mano con el SQL
> Editor. Detalle en [`supabase/README.md`](supabase/README.md).

Nombres de las ramas de trabajo (todas salen de `develop`):

| Tipo | Formato | Ejemplo |
| --- | --- | --- |
| Funcionalidad | `feat/<modulo>-<descripcion>` | `feat/m07-validar-conflictos` |
| Corrección | `fix/<modulo>-<descripcion>` | `fix/m08-recalculo-tabla` |
| Documentación | `docs/<descripcion>` | `docs/adr-0006-despliegue` |
| Base de datos | `db/<descripcion>` | `db/indices-partidos` |
| Mantenimiento | `chore/<descripcion>` | `chore/actualizar-dependencias` |
| Pruebas | `test/<modulo>-<descripcion>` | `test/m01-h01-aceptacion-sesion` |

## 2. Commits (Conventional Commits)

```text
<tipo>(<ámbito>): <resumen en imperativo y minúsculas>

<cuerpo opcional: qué y por qué>

Refs: RF-039, VAL-004
```

- **Tipos:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `db`, `style`.
- **Ámbitos:** `front`, `back`, `db`, `docs` o el módulo (`m07`).
- Ejemplo: `feat(back): rechazar partido con cancha ocupada` + `Refs: RF-039, VAL-004`.

## 3. Pull Requests

- Un PR = una historia o tarea. Pequeño (idealmente < 400 líneas cambiadas sin contar generados).
- La rama **base** es `develop`. Solo el PR de cierre de sprint usa `main` como base (§1).
- Llena la plantilla, incluida la **trazabilidad** (RF/CU/VAL que cubre).
- Al menos **1 aprobación** de otro integrante antes de fusionar. Los PR hacia `develop` se fusionan
  con *squash*; el PR de cierre `develop → main`, con *merge commit*.
- Si el PR toca la BD: incluye la migración nueva **y** la actualización del diccionario.

## 4. Definición de Terminado (DoD)

Una historia está terminada cuando:

- [ ] Cumple los criterios de aceptación de la historia y del ERS (§3.4.3).
- [ ] Compila sin errores: `./mvnw verify` (backend) y `npm run build && npm run lint` (frontend).
- [ ] Tiene pruebas de la regla de negocio principal y del caso de error.
- [ ] Respeta las convenciones de API y el sistema de diseño.
- [ ] Las operaciones críticas registran auditoría (RF-093).
- [ ] La documentación afectada está actualizada.
- [ ] Fue revisada y aprobada en PR.

## 5. Historias de usuario

Viven en **Jira** (proyecto SIGLIM) y se generan desde el backlog v4
([`docs/planning/sprint-plan.md`](docs/planning/sprint-plan.md)); para agregar o cambiar historias se edita
`docs/planning/fuente/datos.py` y se regenera el plan y el CSV. Usa la clave interna (H-xx, T-xx) en la
rama y el commit (`feat/m07-h12-conflictos`, `Refs: H-12, RF-039`). Formato:

> **Como** <perfil>, **quiero** <acción>, **para** <beneficio>.
> Criterios de aceptación en Gherkin (Dado / Cuando / Entonces) y trazabilidad a RF/CU.

## 6. Uso de agentes de IA

- El agente debe leer [`AGENTS.md`](AGENTS.md) antes de trabajar.
- Todo código generado por IA se revisa como si lo hubiera escrito otro integrante: se entiende,
  se prueba y se defiende en la revisión.
- No pegues secretos (`.env`, claves de Supabase) en conversaciones con una IA.
