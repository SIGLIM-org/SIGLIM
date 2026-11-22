# ADR-0010: Notificar en la aplicación y por correo SMTP con bandeja de salida

- **Estado:** Aceptada · el proveedor del punto 4 queda **pendiente** (PEN-034, DEC-46): Brevo es solo candidato; el mecanismo (SMTP y bandeja de salida) sigue vigente
- **Fecha:** 2026-10-19
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** RF-142, RF-143, RNF-004, RNF-011, RNF-012, DEC-38, PEN-033, PEN-034, ADR-0003, ADR-0006, ADR-0009

## Contexto
El ERS pide avisos dentro de la aplicación (RF-142, núcleo) y por correo (RF-143, backlog) ante los
eventos del flujo: cédula pendiente, devuelta o con plazo vencido; expediente abierto; trámite por
aprobar; cobro generado o vencido; programación publicada. El proveedor de correo debe justificarse
por costo, seguridad y necesidad (PEN-034). Además, Supabase Auth necesita un SMTP propio para
invitar usuarios y recuperar el acceso, porque su correo integrado solo envía unos cuantos mensajes
por hora (ADR-0003).

Un correo que falle no debe revertir la aprobación de una cédula, y un aviso no debe perderse si el
backend se reinicia justo después de confirmar la operación.

## Decisión
1. El módulo `notificaciones` escucha los eventos de dominio de los demás módulos (ADR-0006) **en
   la misma transacción** y escribe:
   - la fila `notificacion` (destinatario, evento, título, enlace a la pantalla, `leida_en`), que la
     aplicación muestra (RF-142);
   - si el destinatario tiene correo y el evento lo amerita, una fila en la **bandeja de salida**
     `correo_saliente` (estado `PENDIENTE`, intentos, último error).
2. Una tarea programada (ADR-0009) toma los correos `PENDIENTE` con `FOR UPDATE SKIP LOCKED`, los
   envía y los marca `ENVIADO`; ante un error reintenta con espera creciente hasta 5 veces y luego
   los marca `FALLIDO` para revisión. Así el envío nunca bloquea ni revierte la operación de negocio
   y ningún aviso confirmado se pierde (patrón *transactional outbox*).
3. El envío usa **SMTP** con `spring-boot-starter-mail` (`JavaMailSender`); el proveedor es
   configuración (`SPRING_MAIL_HOST`, usuario y contraseña en `backend/.env`).
4. **Proveedor:** **Brevo** para desarrollo y demostración (plan gratuito de 300 correos al día con
   SMTP). El mismo SMTP se configura en Supabase Auth para invitaciones y recuperación de acceso.
   Para la operación real de la Liga se revisa el volumen: la publicación de una jornada avisa a
   cientos de clubes y equipos en un día, lo que excede los planes gratuitos y requiere un plan de
   pago de Brevo o Amazon SES; cambiarlo es solo configuración.
5. **Contenido mínimo:** el correo dice qué pasó y enlaza a la aplicación; no incluye CURP,
   documentos, montos de terceros ni datos de menores (RNF-012). Se envía desde un dominio con SPF,
   DKIM y DMARC configurados.
6. Los correos y avisos se escriben en plantillas por evento dentro de `notificaciones`; ningún otro
   módulo arma correos.

## Alternativas consideradas
- **Enviar el correo dentro de la transacción:** si el SMTP tarda o falla, la operación crítica se
  alarga o se revierte.
- **Enviar después del *commit* sin bandeja de salida:** si el proceso se cae entre el *commit* y el
  envío, el aviso se pierde sin rastro.
- **Resend:** API moderna y SMTP, pero su plan gratuito permite 100 correos al día.
- **SendGrid:** ya no ofrece plan gratuito permanente.
- **Amazon SES:** el más barato a gran volumen, pero exige cuenta de AWS y salir del modo
  *sandbox*; se reserva para la operación real.
- **Correo integrado de Supabase:** solo para pruebas; su límite por hora no alcanza.

## Consecuencias
- `spring-boot-starter-mail` es una dependencia nueva; se agrega cuando RF-143 entre a un Sprint.
  Mientras tanto, la bandeja de salida puede quedar sin despachador.
- Hace falta un dominio propio verificado en el proveedor (PEN-037).
- Las preferencias de aviso por usuario no forman parte del ERS; si la Liga las pide, se agregan
  como requisito nuevo.

## Actualización (11-nov-2026, DEC-68)

Proveedor elegido (resuelve PEN-034): **Brevo SMTP en plan gratuito, sin dominio propio**, para
Supabase Auth (invitaciones y restablecimiento de contraseña) y para el despachador de la bandeja de
salida de esta ADR. Las credenciales van solo en `backend/.env` y en el panel de Supabase, nunca en
archivos versionados. Riesgo aceptado: sin dominio propio no se configuran SPF, DKIM ni DMARC (punto
5 de la decisión; PEN-037), así que algunos correos pueden llegar a spam.
