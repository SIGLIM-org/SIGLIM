# Registros de decisiones de arquitectura (ADR)

Cada ADR documenta **una** decisión técnica: el contexto, lo que se decidió, las alternativas y
sus consecuencias. Formato basado en Michael Nygard (*Documenting Architecture Decisions*).

| ADR | Decisión | Estado |
| --- | --- | --- |
| [0001](0001-monorepo.md) | Monorepo con `frontend/`, `backend/`, `supabase/` y `docs/` | Aceptada |
| [0002](0002-monolito-modular-spring-jdbc.md) | Backend como monolito modular por paquetes con Spring JDBC | Aceptada (tabla de paquetes reemplazada por 0006) |
| [0003](0003-autenticacion-supabase-jwt.md) | Supabase Auth, validación del JWT con JWKS, alta por invitación y política de sesión (PEN-030) | Aceptada |
| [0004](0004-migraciones-supabase.md) | El esquema solo cambia mediante migraciones en `supabase/migrations/` | Aceptada |
| [0005](0005-contexto-usuario-rls-jdbc.md) | Propagar el usuario autenticado a PostgreSQL en cada transacción | Aceptada (punto 1 reemplazado por 0014) |
| [0006](0006-paquetes-14-modulos-y-dependencias.md) | Paquetes de los 14 módulos, niveles de dependencia, eventos y prueba ArchUnit | Aceptada |
| [0007](0007-autorizacion-permiso-y-ambito.md) | Autorización por permiso y ámbito, control dual y RLS como defensa en profundidad | Aceptada (RLS precisada por 0014) |
| [0008](0008-almacenamiento-privado-documentos.md) | Documentos en Supabase Storage privado, carga por el backend y URL firmadas | Reemplazada por 0013 |
| [0009](0009-tareas-programadas-idempotentes.md) | Tareas programadas idempotentes para los procesos por tiempo | Aceptada |
| [0010](0010-notificaciones-y-correo.md) | Avisos en la aplicación y correo SMTP con bandeja de salida | Aceptada (proveedor pendiente, PEN-034) |
| [0011](0011-una-liga-preparada-para-varias.md) | Una sola liga con el modelo preparado para varias | Aceptada |
| [0012](0012-pago-en-linea-simulado.md) | Pago en línea simulado detrás de la interfaz `PasarelaDePago` | Aceptada |
| [0013](0013-documentos-en-aws-s3.md) | Documentos e imágenes en AWS S3 privado; bucket, clave y URL en Supabase; lectura con URL prefirmada | Aceptada |
| [0014](0014-esquema-propio-y-roles-de-base-de-datos.md) | Esquema `siglim` no expuesto y roles `siglim_app` y `siglim_publico` | Aceptada |
| [0015](0015-algoritmo-programacion-automatica.md) | Programación automática: método del círculo, heurística voraz validada con `ValidadorConflictos` y árbitro central equitativo | Aceptada |

Las decisiones abiertas que todavía no tienen ADR (proveedor de correo, PEN-034; ambientes,
alojamiento y dominio, PEN-036 y PEN-037) se listan en la [arquitectura](../arquitectura-y-diseno-jerarquico.md#9-pendientes-y-riesgos).

## Estados

`Propuesta` → `Aceptada` → (`Reemplazada por ADR-xxxx` | `Obsoleta`). Un ADR aceptado **no se
edita**: si la decisión cambia, se crea uno nuevo que lo reemplaza.

## Plantilla

```markdown
# ADR-XXXX: Título en imperativo

- **Estado:** Propuesta | Aceptada | Reemplazada por ADR-YYYY
- **Fecha:** AAAA-MM-DD
- **Responsables:** nombres
- **Trazabilidad:** RNF-xxx, DD-xxx, PEN-xxx

## Contexto
¿Qué problema o fuerza obliga a decidir?

## Decisión
Lo que se hará, en una o dos frases claras.

## Alternativas consideradas
- Opción A: pros / contras.

## Consecuencias
Positivas, negativas y lo que queda pendiente.
```
