# Documentación de SIGLIM

Índice de la documentación técnica del proyecto. Si eres una persona nueva en el equipo o un
agente de IA, léela en el orden de la tabla.

## Mapa de documentos

| # | Documento | Para qué sirve | Cuándo consultarlo |
| --- | --- | --- | --- |
| 1 | [`requisitos/ERS.md`](requisitos/ERS.md) | Especificación de requisitos (IEEE 830): RF, RNF, reglas de negocio (RN), casos de uso (CU), validaciones (VAL), alcance y pendientes. | Antes de implementar cualquier funcionalidad. Es la **fuente de verdad** de *qué* hace el sistema. |
| 2 | [`arquitectura/arquitectura-y-diseno-jerarquico.md`](arquitectura/arquitectura-y-diseno-jerarquico.md) | Versión 2.1: HIPO de los 14 módulos M-xx y sus submódulos con RF, diagramas IPO, contenedores, dependencias entre módulos, secuencias, seguridad, calidad y respaldo (MD, DOCX, PDF). | Para saber en qué módulo y capa vive un cambio. |
| 3 | [`arquitectura/convenciones-api.md`](arquitectura/convenciones-api.md) | Versión 2.2 (Rev. 0.4.1): contrato REST entre frontend y backend, con rutas por módulo, permisos con ámbito, errores RFC 9457, paginación, documentos en S3 y portal. | Al crear o consumir un endpoint. |
| 4 | [`arquitectura/adr/`](arquitectura/adr/) | Registros de decisiones de arquitectura (ADR): qué se decidió, por qué y qué sigue abierto. | Antes de cambiar una decisión técnica. |
| 5 | [`base-de-datos/diccionario-de-datos.md`](base-de-datos/diccionario-de-datos.md) | Versión 2.0 (Rev. 0.4.1): esquema `siglim` con 72 tablas por módulo, diagramas ER, enums, triggers, funciones, vistas públicas, RLS, semilla y trazabilidad ERS → BD (MD, DOCX, PDF). | Al escribir SQL, repositorios o DTO. |
| 6 | [`../supabase/migrations/`](../supabase/migrations/) | Migración inicial ejecutable `20261020090000_esquema_inicial.sql` (se pega en el SQL Editor de Supabase) y pruebas en [`../supabase/pruebas/`](../supabase/pruebas/). | Para crear o modificar la base de datos. |
| 7 | [`diseno-ui/README.md`](diseno-ui/README.md) | Sistema de diseño: tipografía, colores y tokens, componentes, layouts, responsive, accesibilidad, decisiones abiertas y prompts de Google Stitch por pantalla. | Al construir pantallas en React. |
| 8 | [`requisitos/bpmn/`](requisitos/bpmn/bpmn-procesos-del-sistema.md) | BPMN v2.0 de los procesos P-01 a P-13: documento (MD, DOCX, PDF), archivos `.bpmn` editables y figuras. | Para entender el flujo de un proceso antes de implementarlo. |
| — | [`requisitos/historico/`](requisitos/historico/), [`requisitos/bpmn/historico/`](requisitos/bpmn/historico/), [`arquitectura/historico/`](arquitectura/historico/) y [`base-de-datos/historico/`](base-de-datos/historico/) | Versiones anteriores **reemplazadas** (ERS Rev. 0.1 y 0.3, BPMN 1.0, arquitectura 1.0 con su DOCX original y figuras, convenciones de API 1.0, diccionario 1.0 y migración de la Rev. 0.3). Solo como referencia histórica. | Nunca para implementar. |
| 10 | [`requisitos/decisiones/registro-de-decisiones.md`](requisitos/decisiones/registro-de-decisiones.md) | Decisiones DEC-01 a DEC-48 con su origen; integradas en el ERS Rev. 0.4 y 0.4.1 salvo DEC-47 y DEC-48. | Para rastrear por qué existe un requisito. |
| 11 | [`planning/sprint-plan.md`](planning/sprint-plan.md) | Backlog v4: épicas, historias, tareas y subtareas por sprint con su CSV para Jira ([`planning/backlog-siglim.csv`](planning/backlog-siglim.csv)). | Para saber qué se hace en cada sprint y en qué orden. |
| 12 | [`pruebas/aceptacion/`](pruebas/aceptacion/README.md) | Pruebas de aceptación por historia: escenarios por criterio, evidencias y resultado. | Para cerrar una historia o comprobar qué se verificó. |
| — | [`requisitos/fuentes/`](requisitos/fuentes/) | Documentación técnica base, transcripción del levantamiento (R2) y formatos de carta del cliente. | Para rastrear de dónde salió un requisito o una figura. |

## Jerarquía de fuentes de verdad

Cuando dos documentos se contradicen, manda el de mayor nivel:

```text
1. ERS (requisitos/ERS.md)                     ← qué debe hacer el sistema y qué NO
2. ADR aceptados + arquitectura                ← cómo se organiza
3. Migraciones SQL + diccionario de datos      ← cómo se guardan los datos
4. Convenciones de API                         ← cómo se comunican front y back
5. Diseño UI                                   ← cómo se ve
```

Ejemplo: el diseño UI menciona un *buscador global*, pero el ERS (RF-089) dice explícitamente
"sin buscador general". **Gana el ERS**: no se implementa el buscador. Los conflictos conocidos
están listados en [`diseno-ui/open-design-decisions.md`](diseno-ui/open-design-decisions.md).

## Identificadores que verás en todos los documentos

| Prefijo | Significado | Definido en |
| --- | --- | --- |
| `RF-xxx` / `RNF-xxx` | Requisito funcional / no funcional | ERS §3.2 / §3.3 |
| `RN-xxx` | Regla de negocio | ERS §3.4.1 |
| `CU-xxx` | Caso de uso | ERS §4.1 |
| `VAL-xxx` | Validación y comportamiento ante fallo | ERS §3.2.10 |
| `UI-xxx` | Pantalla propuesta | ERS §3.1.1 |
| `P-xx` | Proceso BPMN | ERS §4.2 |
| `M-xx` / `M-xx.n` | Módulo / submódulo | ERS §2.2 y arquitectura §2 |
| `DD-xxx` / `DP-xxx` | Decisión / propuesta de diseño | ERS §2.4 |
| `PEN-xxx` / `Q-xx` | Pendiente / pregunta abierta | ERS Apéndices A y C |
| `ADR-xxxx` | Decisión de arquitectura del equipo | `arquitectura/adr/` |

Úsalos en ramas, commits, PR, comentarios de código y pruebas para mantener la trazabilidad.

## Cómo mantener la documentación

- Un cambio de requisito se hace **primero en el ERS** y después en código.
- Un cambio de esquema se hace con **una nueva migración** en `supabase/migrations/` y se refleja
  en el diccionario de datos en el mismo PR.
- Una decisión técnica nueva o revertida se registra como ADR.
- No dupliques contenido: enlaza al documento que ya lo define.
