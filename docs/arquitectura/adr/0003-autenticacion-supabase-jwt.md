# ADR-0003: Autenticar con Supabase Auth y validar el JWT en el backend

- **Estado:** Aceptada
- **Fecha:** 2026-10-13 (propuesta) · 2026-10-19 (aceptada; resuelve PEN-030)
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** RNF-003, RNF-013, RNF-014, RNF-019, UI-001, DD-009, DD-012, VAL-015, RF-094, RF-106, PEN-030

## Contexto
La arquitectura delega las credenciales a Supabase Auth (`auth.users`) para no almacenar
contraseñas propias (RNF-013, DD-012). El backend debe aplicar denegación por defecto por permiso y
ámbito (ADR-0007). El ERS Rev. 0.4 deja a esta ADR la política de sesión y contraseñas (PEN-030):
duración y renovación de la sesión, recuperación de acceso y límite de intentos.

Las cuentas no son de autoservicio: el Administrador general crea las cuentas internas (RF-094) y
el tutor de un menor recibe acceso a partir del registro del jugador (RF-106, RN-055).

## Decisión

### Autenticación y validación del token
1. El **frontend** inicia sesión con `@supabase/supabase-js` y obtiene el `access_token` (JWT).
   Es el **único** uso del cliente de Supabase en el frontend: los datos siempre se piden al
   backend.
2. El **backend** es un *OAuth2 Resource Server* (`spring-boot-starter-security-oauth2-resource-server`)
   que valida firma, expiración y emisor del JWT con el JWKS del proyecto:
   - `jwk-set-uri`: `https://<proyecto>.supabase.co/auth/v1/.well-known/jwks.json`
   - `issuer-uri` esperado: `https://<proyecto>.supabase.co/auth/v1`
   - El proyecto usa **claves de firma asimétricas** (*JWT Signing Keys*). Con la clave secreta
     heredada HS256 el JWKS viene vacío y Supabase desaconseja validarla localmente.
3. Los **roles y permisos no se leen del JWT**: el backend los obtiene de la base de datos a partir
   del `sub` del token (ADR-0007), para que un cambio de rol surta efecto sin esperar a que expire
   la sesión.
4. Rutas `/api/v1/publico/**` sin autenticación; todo lo demás autenticado y autorizado por
   permiso y ámbito.

### Alta de cuentas
5. El **registro público está deshabilitado** en Supabase Auth. Las cuentas se crean por
   **invitación** desde el backend con la API de administración de Supabase Auth, usando la clave
   secreta del proyecto, que solo existe en `backend/.env`:
   - cuentas internas y de directiva de club: las crea el Administrador general (RF-094);
   - tutores: se invitan con el correo capturado en el registro del menor; el menor no tiene
     credenciales propias (RF-106, RN-055);
   - árbitros: se invitan al validarse (RF-113).

   > Nota (5-nov-2026, DEF-03): el backend envía `redirect_to` a `/restablecer-contrasena` del
   > origen configurado en `SIGLIM_FRONTEND_ORIGEN`, para que el enlace del correo lleve al
   > invitado a fijar su contraseña en el frontend.
6. Una cuenta desactivada en SIGLIM (`perfil.activo = false`) se rechaza en el backend aunque su
   JWT siga vigente.

### Política de sesión y contraseñas (PEN-030)
Basada en NIST SP 800-63B-4 (2025) y en lo que Supabase Auth permite configurar:

| Control | Valor | Dónde se configura |
| --- | --- | --- |
| Longitud mínima de contraseña | 15 caracteres; sin reglas de composición; se aceptan frases | Supabase Auth → *Password strength* |
| Longitud máxima | 72 caracteres (límite de bcrypt en Supabase Auth) | Validación en el frontend |
| Lista de contraseñas filtradas | Activar *leaked password protection* si el plan lo incluye | Supabase Auth (plan Pro) |
| Cambio periódico obligatorio | No se exige; solo ante sospecha de compromiso | — |
| Vida del `access_token` | 1 hora | Supabase Auth → *JWT expiry* |
| Renovación | *Refresh token rotation* activa, con detección de reutilización | Supabase Auth |
| Cierre por inactividad | 60 minutos sin actividad en la aplicación interna **[PROPUESTA]** | Frontend (`signOut`) y, con plan Pro, *inactivity timeout* |
| Duración máxima de la sesión | 12 horas **[PROPUESTA]** | Supabase Auth → *time-box* (plan Pro) |
| Límite de intentos | Límites por IP de Supabase Auth (*token* y *sign-in*); el frontend muestra un mensaje genérico ante HTTP 429 | Supabase Auth → *Rate limits* |
| Recuperación de acceso | Restablecimiento por correo de Supabase Auth con SMTP propio (ADR-0010) | Supabase Auth → *SMTP* |
| Segundo factor | TOTP opcional para Administrador general y Presidente **[PROPUESTA]**, backlog | Supabase Auth → MFA |

El correo integrado de Supabase Auth solo envía unos cuantos mensajes por hora, por lo que las
invitaciones y la recuperación de acceso usan el mismo proveedor SMTP de las notificaciones
(ADR-0010).

## Alternativas consideradas
- **Autenticación propia en Spring (usuarios + BCrypt):** más control, pero duplica lo que
  Supabase ya ofrece y añade riesgos de seguridad.
- **Validar llamando a `/auth/v1/user` en cada petición:** funciona con HS256 pero agrega
  latencia y dependencia de red en cada solicitud.
- **Registro público con aprobación posterior:** permitiría cuentas falsas de club o tutor y
  obligaría a una cola de validación que el ERS no pide.
- **Contraseña de 8 caracteres con reglas de composición:** NIST 800-63B-4 la desaconseja para
  cuentas protegidas solo con contraseña.

## Consecuencias
- El frontend nunca envía contraseñas al backend; el backend nunca ve contraseñas.
- La clave secreta de Supabase queda solo en el backend; filtrarla daría control total del
  proyecto, por lo que se trata como secreto de máxima prioridad (AGENTS.md §9).
- En el plan gratuito no hay lista de contraseñas filtradas ni *time-box* de sesión: el cierre por
  inactividad del frontend es la única protección de ese tipo. Contratar el plan Pro los habilita
  sin cambios de código.
- Los valores marcados [PROPUESTA] deben confirmarse con la Liga; se cambian en configuración.
