# Pruebas de aceptación

Evidencia de que cada historia de usuario cumple sus **criterios de aceptación** antes de darla por
terminada (Definición de Terminado, [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) §4). Cada historia
del backlog tiene una subtarea de pruebas (H-01.4, H-03.3, H-04.3…) cuyo entregable es un archivo de
esta carpeta.

## Qué es y qué no es

- **Es** la verificación de la historia completa, de punta a punta, con el frontend, el backend y la
  base de datos reales, desde el punto de vista del usuario.
- **No sustituye** a las pruebas automáticas (unitarias, de controlador e integración): las
  complementa. Cada criterio cita las pruebas automáticas que ya lo cubren, y la prueba manual
  confirma que las piezas funcionan juntas.

## Convenciones

| Elemento | Regla |
| --- | --- |
| Archivo | `H-xx-<descripcion-corta>.md`, uno por historia |
| Evidencias | `evidencias/H-xx/CAn-<descripcion>.png`, una o más por criterio |
| Escenarios | Un escenario por criterio de aceptación (`CA-n`), con el mismo número que en Jira |
| Resultado | `Aprobado`, `Rechazado` o `Bloqueado`; si se rechaza, se anota el defecto y la historia no se cierra |
| Datos personales | Las cuentas se describen por su rol, nunca por su correo; las capturas no muestran tokens, contraseñas ni correos personales |
| Versión probada | Se registra el commit de `develop` sobre el que se ejecutó la prueba (las pruebas anteriores a DEC-51 registran `main`) |

## Plantilla

Copia esta estructura para cada historia nueva:

```markdown
# Prueba de aceptación · H-xx <título de la historia>

| Campo | Valor |
| --- | --- |
| Historia | H-xx (SCRUM-nnn) · subtarea H-xx.n (SCRUM-nnn) |
| Requisitos | RF-xxx, … |
| Versión probada | `develop` en `<commit>` |
| Fecha | AAAA-MM-DD |
| Ejecutó | <nombre> |
| Resultado global | Aprobado / Rechazado |

## Entorno y cuentas
## Escenarios
### CA-1 · <criterio>
**Pasos** · **Resultado esperado** · **Resultado obtenido** · **Evidencia** · **Pruebas automáticas relacionadas**
## Defectos encontrados
## Conclusión
```
