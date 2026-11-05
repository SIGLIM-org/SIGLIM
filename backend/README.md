# SIGLIM · Backend

API REST de SIGLIM: Spring Boot 4.1, Java 21, Spring JDBC y PostgreSQL en Supabase.
Reglas para agentes de IA: [`AGENTS.md`](AGENTS.md) · Contrato REST:
[`convenciones-api.md`](../docs/arquitectura/convenciones-api.md).

## Requisitos

- JDK 21 (`java -version`). Maven **no** es necesario: el proyecto trae Maven Wrapper (`mvnw`).
- Un proyecto de Supabase con el esquema de `supabase/migrations/` aplicado y **JWT Signing Keys**
  asimétricas activadas (Authentication → JWT Keys).

## Configuración

```bash
cp .env.example .env    # Windows: copy .env.example .env
```

| Variable | Descripción |
| --- | --- |
| `DB_URL` | Cadena JDBC de Supabase (Connect → JDBC → *Transaction pooler*, puerto 6543) terminada en `?currentSchema=siglim&prepareThreshold=0` (ADR-0014) |
| `DB_USERNAME` / `DB_PASSWORD` | Usuario del pooler (`postgres.<ref>`) y contraseña de la base de datos |
| `SUPABASE_URL` | `https://<ref>.supabase.co`, sin diagonal final; de aquí se derivan el JWKS y el emisor del JWT |
| `SUPABASE_SECRET_KEY` | Clave secreta del proyecto para invitar cuentas desde el backend (H-03.1, ADR-0003). Es `sb_secret_…` o el JWT de `service_role`; **nunca** va al frontend |
| `SIGLIM_CORS_ORIGEN` | Orígenes del frontend permitidos, separados por coma (convenciones de API §5) |
| `SIGLIM_LIGA_ID` | UUID de la liga activa (ADR-0011 §5); en `.env.example` está el de la semilla |

El detalle y los valores de ejemplo están en [`.env.example`](.env.example). Si falta una variable o
tiene un formato incorrecto, la aplicación no arranca y el error dice cuál es. Una prueba
(`VariablesEntornoDocumentadasTest`) falla si `application.yml` usa una variable que no esté en
`.env.example`: al agregar una, documéntala ahí en el mismo PR.

## Autenticación

La API valida el `access_token` de Supabase Auth (`Authorization: Bearer <token>`) con las llaves
públicas del proyecto (`<SUPABASE_URL>/auth/v1/.well-known/jwks.json`, ES256), sin llamar a
Supabase en cada petición (ADR-0003). Solo son públicas `GET /api/v1/salud`,
`GET /api/v1/publico/**` y `GET /actuator/health`; todo lo demás responde
`401` con `codigo: "NO_AUTENTICADO"` si no hay token o si está vencido o no es válido.

Para probar a mano con un token real, inicia sesión con la API de Supabase Auth y copia el
`access_token` (vive 1 hora). **No lo pegues en chats ni lo guardes en el repositorio.**

## Almacenamiento de documentos (AWS S3)

Los documentos e imágenes (INE, fotos, comprobantes, evidencias) se guardan en un bucket privado de
S3 y se leen solo con URL prefirmadas de 60 s ([ADR-0013](../docs/arquitectura/adr/0013-documentos-en-aws-s3.md)).
El componente es `compartido.almacenamiento.AlmacenamientoDocumentos`: valida el tipo por firma
binaria (JPEG, PNG, WebP, PDF) y el tamaño (5 MB), sube con SHA-256 y genera la URL prefirmada. No
autoriza, no audita ni escribe en la base de datos: eso lo hace el servicio que lo usa.

| Variable | Descripción |
| --- | --- |
| `SIGLIM_S3_BUCKET` | Bucket privado, por ejemplo `siglim-documentos-dev-<sufijo>` |
| `AWS_REGION` | `us-east-2` (la región que la cuenta por proyectos asigna a México) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Llaves de **tu** usuario IAM (secretas y personales); pueden quedar vacías |

**Credenciales.** Si las dos llaves están en `backend/.env`, el backend las usa. Si las dejas vacías,
el SDK usa su cadena predeterminada: el perfil de `~/.aws/credentials` o las variables reales del
sistema. Las llaves de `.env` se leen desde la configuración de Spring y no las ve el SDK por su
cuenta, porque `.env` no se carga como variables del sistema operativo. Poner solo una de las dos
impide que la aplicación arranque.

**Pruebas.** `AlmacenamientoDocumentosS3IT` corre en cada `verify` contra S3Mock en un contenedor
(requiere Docker). La prueba contra el bucket real, que comprueba que la URL prefirmada vence y que
el bucket no es público, está desactivada por defecto. Para correrla con las llaves de tu `.env`:

```bash
./mvnw verify -Dit.test=AlmacenamientoDocumentosAwsIT -Dsiglim.s3.pruebaReal=true
```
En PowerShell, encierra cada `-D` entre comillas:
`./mvnw verify "-Dit.test=AlmacenamientoDocumentosAwsIT" "-Dsiglim.s3.pruebaReal=true"`

Sube un PDF pequeño a `liga/00000000-0000-0000-0000-000000000000/evidencias/…`; el objeto queda
en el bucket porque el usuario IAM no tiene permiso de borrado.

### Nota de costos (T-04.3)

Precios de lista de S3 Standard en `us-east-2` (verifícalos en la
[calculadora de AWS](https://calculator.aws/) antes de presentarlos):

| Concepto | Precio aproximado |
| --- | --- |
| Almacenamiento | US$0.023 por GB-mes |
| Cargas (`PUT`) | US$0.005 por cada 1,000 |
| Lecturas (`GET`) | US$0.0004 por cada 1,000 |

- **Temporada real:** con la estimación de ADR-0008 (15,000 personas × 3 documentos de ~300 KB ≈
  13 GB) el almacenamiento cuesta unos **US$0.30 al mes**; cargar los ~45,000 documentos, unos
  US$0.23 una sola vez. Las lecturas con URL prefirmada son centavos al mes. El versionado suma lo
  que ocupen las versiones anteriores, que solo existen si un archivo se sobrescribe.
- **Demo:** unos cuantos MB y decenas de peticiones: el costo es prácticamente cero.
- **Límite de gasto:** La cuenta de AWS del equipo usa el **plan gratuito** (consultado el 26-oct-2026): no genera cargos, incluye US$120 en créditos y vence el **26 de abril de 2027**. Al vencer o agotarse los créditos, AWS suspende la cuenta (y después la cierra con sus recursos, incluido el bucket) si no se actualiza al plan de pago. Para operar después de esa fecha, la Liga debe actualizar el plan o usar una cuenta propia (ver «Consecuencias» en ADR-0013).

## Ejecutar

```bash
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

- Salud: <http://localhost:8080/actuator/health>
- API en ejecución: <http://localhost:8080/api/v1/salud> → `{"estado":"OK"}`

## Probar

```bash
./mvnw verify
```

`verify` corre las pruebas unitarias y de controlador (surefire) y después las de integración `*IT`
(failsafe), que levantan PostgreSQL 17 en un contenedor con el esquema de `supabase/migrations/`.
Estas requieren **Docker Desktop encendido**; sin Docker se omiten y el resumen de failsafe muestra
`Skipped`.

## Abrir en el IDE

IntelliJ IDEA: *File → Open* → selecciona `backend/pom.xml` → *Open as Project*.
VS Code: extensión *Extension Pack for Java* y abre la carpeta `backend/`.

## Nota para Windows y Git

Si `./mvnw` no es ejecutable después de clonar en Linux/macOS, ejecuta una vez desde la raíz del
repositorio: `git update-index --chmod=+x backend/mvnw` y haz commit.
