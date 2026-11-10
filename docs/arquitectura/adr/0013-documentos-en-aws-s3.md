# ADR-0013: Guardar documentos e imágenes en AWS S3 privado y su referencia en Supabase

- **Estado:** Aceptada · reemplaza a [ADR-0008](0008-almacenamiento-privado-documentos.md)
- **Fecha:** 2026-10-20
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** RNF-012, RNF-018, RNF-019, RF-059, RF-108, RF-109, RF-139, DEC-10, DEC-45, PEN-023, PEN-040

## Contexto
ADR-0008 eligió Supabase Storage. El equipo decidió (DEC-45) usar **AWS S3** para las imágenes y
documentos, y guardar en Supabase (PostgreSQL) la URL de cada archivo. Los requisitos no cambian:
son datos personales de acceso restringido que se guardan en un repositorio privado y se leen solo
con URL firmadas de vigencia corta por usuarios con permiso y ámbito (RNF-018).

## Decisión
1. **Bucket privado de S3** (`SIGLIM_S3_BUCKET`) con *Block Public Access* activo, cifrado del lado
   del servidor (SSE-S3, predeterminado en S3) y **versionado** activado para recuperar archivos
   sobrescritos por error. Región configurable (`AWS_REGION`); se propone la región de México
   **[PROPUESTA]** para mantener los datos en el país.
2. **Acceso solo desde el backend** con un usuario IAM de mínimo privilegio: `s3:PutObject` y
   `s3:GetObject` sobre `arn:aws:s3:::<bucket>/liga/*`, sin `s3:DeleteObject`. Las credenciales
   (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) viven solo en `backend/.env`.
3. **Clave del objeto:** `liga/{ligaId}/{tipo}/{entidadId}/{documentoId}.{ext}`, donde `tipo` es
   `registros`, `tutores`, `afiliaciones`, `evidencias` o `comprobantes`. La clave no contiene
   nombres ni CURP.
4. **Carga a través del backend** (igual que ADR-0008): autorización por permiso y ámbito
   (ADR-0007), validación del tipo por firma binaria (JPEG, PNG, WebP o PDF) y del tamaño
   (5 MB **[PROPUESTA]**), subida con el AWS SDK para Java y registro en la tabla
   `siglim.documentos` en la misma operación auditada.
5. **Lo que se guarda en Supabase:** `s3_bucket`, `s3_clave` y `url` (la URL del objeto,
   `https://<bucket>.s3.<región>.amazonaws.com/<clave>`), además de tipo, tamaño y *hash* SHA-256.
   La URL guardada **no es pública**: sirve como referencia; abrirla directamente responde
   «Access Denied».
6. **Lectura con URL prefirmada:** el backend autoriza y genera con `S3Presigner` una URL de
   `GET` válida **60 segundos [PROPUESTA]**. La URL prefirmada no se guarda ni se registra en
   bitácoras (RNF-011).
7. **Sin borrado al reemplazar:** un documento nuevo sustituye al anterior lógicamente
   (`reemplazado_por_id`); el archivo anterior se conserva. La depuración sigue la política de
   retención de la Liga (PEN-040) con reglas de ciclo de vida de S3.
8. El cliente de S3 queda encapsulado en `compartido.almacenamiento.AlmacenamientoDocumentos`; los
   módulos no conocen al proveedor.

## Alternativas consideradas
- **Supabase Storage (ADR-0008):** un proveedor menos, pero su plan gratuito incluye 1 GB, que no
  alcanza para una temporada real; el equipo prefiere S3.
- **Guardar la URL como pública:** simple de mostrar, pero expondría INE, fotos y documentos de
  menores; contradice RNF-018 y la LFPDPPP.
- **Carga directa del navegador con URL prefirmada de `PUT`:** quita tráfico al backend, pero
  obliga a validar el archivo después y a manejar credenciales de AWS en un flujo del cliente.

## Consecuencias
- **Dependencia nueva:** `software.amazon.awssdk:s3` (AWS SDK para Java v2) en el backend, cuando
  la carga de documentos entre a un Sprint.
- Se necesita una cuenta de AWS de la Liga o del equipo; el costo depende del volumen almacenado y
  de las descargas (RNF-017).
- Los respaldos de la base no incluyen los archivos: el versionado de S3 y, si la Liga lo pide, la
  replicación a otro bucket cubren su recuperación.
- La tabla `siglim.documentos` y su RLS están en la migración inicial `20261020090000_esquema_inicial.sql`.

## Notas de implementación (2026-10-26, T-04)
- **Región `us-east-2`** en lugar de la región de México [PROPUESTA] del punto 1: la cuenta de AWS
  es del tipo «proyectos», que asigna `us-east-2` a México, y `mx-central-1` no está disponible sin
  activar funciones avanzadas de la cuenta. La región sigue siendo configurable (`AWS_REGION`).
- **Solo TLS:** además de lo del punto 1, la política del bucket deniega toda petición sin TLS
  (`aws:SecureTransport = false`).
- **Un usuario IAM por desarrollador** con la política de mínimo privilegio del punto 2 adjunta
  directamente, porque la cuenta por proyectos no permite grupos de IAM. Cada quien pone sus llaves
  en su propio `backend/.env`.
- **Sin `s3:ListBucket`:** una clave inexistente responde **403**, no 404, porque S3 no revela qué
  existe a quien no puede listar. El backend no debe interpretar un 403 de S3 como «no existe».
