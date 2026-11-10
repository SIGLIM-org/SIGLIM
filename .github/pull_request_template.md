## Qué cambia
<!-- Resumen en 1-3 líneas. -->

## Trazabilidad
- Historia / Issue: #
- Requisitos: <!-- RF-xxx, CU-xxx, VAL-xxx, RN-xxx -->
- Módulo: <!-- M-xx -->

## Tipo
- [ ] Funcionalidad  - [ ] Corrección  - [ ] Base de datos  - [ ] Documentación  - [ ] Mantenimiento

## Cómo probarlo
1.

## Lista de verificación
- [ ] La rama base es `develop` (solo el PR de cierre de sprint va a `main`)
- [ ] `./mvnw verify` y/o `npm run build && npm run lint` pasan
- [ ] Incluye pruebas de la regla principal y del caso de error
- [ ] Si cambia la BD: migración nueva + diccionario de datos actualizado + `supabase/pruebas/ejecutar.sh` pasa (se aplica al proyecto compartido al fusionar en `develop`, DEC-52)
- [ ] Si cambia el contrato REST: `docs/arquitectura/convenciones-api.md` o DTO actualizados
- [ ] No implementa nada marcado `[PENDIENTE]` ni fuera del alcance (ERS 2.6)
- [ ] Sin secretos ni `.env` en el diff

## Capturas (si cambia la interfaz)
