# Prueba de aceptación · B-11 Agrupar canchas por sede

| Campo | Valor |
| --- | --- |
| Historia | B-11 (SCRUM-220) · subtarea B-11.3 (SCRUM-381) |
| Requisitos | RF-028, RF-093, VAL-015 |
| Pantallas | F-21 (catálogo de canchas y modal «Sedes») |
| Versión probada | `develop` en `f8f86ac`, con las migraciones `20261020090000`, `20261022143000`, `20261027140000` y `20261103100000` |
| Fecha | 2026-11-05 |
| Ejecutó | Agente de IA (DeepSeek Harness) en el entorno local del equipo |
| Resultado global | **Aprobado** (3 de 3 criterios) |

## 1. Entorno

| Componente | Configuración |
| --- | --- |
| Base de datos | PostgreSQL 16.4 local con el entorno de Supabase simulado ([`mock_supabase.sql`](../../../supabase/pruebas/mock_supabase.sql)) y las cuatro migraciones del repositorio aplicadas |
| Autenticación | Servidor local que publica un JWKS RSA y responde los endpoints de Supabase Auth que usa el frontend (contraseña, usuario y cierre de sesión); el backend valida firma, emisor y audiencia del token |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080`, compilado con `release 21` y ejecutado con Java 25.0.5 y Spring Boot 4.1.1, contra la base local y el JWKS local |
| Frontend | `npm run dev` en `http://localhost:5173` (Vite 8.3.5, proxy hacia el backend) |
| Navegador | Chromium 156.0.8078.4 sin interfaz, Playwright 1.64.1, ventana de 1440 × 1100 px y zona `America/Mexico_City` |

La prueba se ejecutó contra una **base local efímera**, recreada desde las migraciones para esta
corrida: no se alteró el proyecto compartido de Supabase. A diferencia de las pruebas de H-07 y
B-27, esta corrida no usó Supabase local: la base se levantó con PostgreSQL 16.4 y el simulador del
repositorio, y la autenticación con un emisor JWKS local con el mismo contrato que Supabase Auth
(emisor `<url>/auth/v1`, audiencia `authenticated` y `sub` UUID).

## 2. Cuentas y datos de prueba

Las cuentas se identifican por su rol; sus correos y contraseñas no se publican en el repositorio.

| Cuenta | Rol y ámbito | Permisos relevantes |
| --- | --- | --- |
| **Administrador** | Administrador general · Liga | `canchas.cancha:CONSULTAR` y `canchas.cancha:EDITAR` (LIGA) |
| **Programador** | Programador de partidos · Liga | `canchas.cancha:CONSULTAR` (solo lectura) |

Las cuentas se crearon en `auth.users`; el Administrador se dio de alta con
`siglim.registrar_administrador_inicial` y el Programador se sembró con SQL para la verificación de
permisos, igual que en las pruebas de aceptación anteriores.

Datos creados durante la corrida:

- **Cancha «Cancha Norte de Aceptación B-11»** (`d781a315…`): ubicación Unidad deportiva,
  propietario Liga, activa y, al crearse, **sin sede**.
- **Sede «Unidad Deportiva Morelia»** (`cf45aab0…`): dirección «Av. Universidad 123, Morelia», que
  después se corrigió a «Av. Universidad 123, Morelia, Mich.» en la verificación V2.
- La sede se conserva aunque la cancha deje de usarla: las sedes no se borran (D3).

## 3. Escenarios

### CA-1 · Una sede nueva queda disponible y el nombre repetido se rechaza con 409

> Dada una sede nueva, cuando se registra, entonces queda disponible en el catálogo; cuando se
> intenta registrar otra con el mismo nombre, entonces responde 409 `DUPLICADO`.

**Pasos**

1. Iniciar sesión como **Administrador** y abrir Programación › Canchas (`/app/programacion/canchas`).
2. Pulsar **Sedes** y capturar el nombre «Unidad Deportiva Morelia» con su dirección.
3. Pulsar **Registrar sede** y revisar la lista y el aviso de éxito.
4. Repetir el mismo nombre y pulsar **Registrar sede** de nuevo.

**Resultado esperado**

- La sede aparece en la lista del modal y el aviso confirma el alta.
- El nombre repetido se marca en el campo Nombre con un mensaje que explica el conflicto, el modal
  sigue abierto y no se crea una segunda sede.

**Resultado obtenido:** **Aprobado.** El alta se guardó y la lista mostró «Unidad Deportiva
Morelia» con su dirección y el aviso «Sede «Unidad Deportiva Morelia» registrada.». El segundo
intento respondió `409 DUPLICADO` y el campo Nombre quedó marcado con «Ya existe una sede llamada
«Unidad Deportiva Morelia» en la liga.», sin cerrar el modal; el `GET /api/v1/sedes` devolvió una
sola sede.

**Evidencia**

- [`CA1-sede-registrada.png`](evidencias/B-11/CA1-sede-registrada.png)
- [`CA1-nombre-duplicado.png`](evidencias/B-11/CA1-nombre-duplicado.png)
- [`CA1-sedes.json`](evidencias/B-11/CA1-sedes.json)

**Pruebas automáticas relacionadas**

- `SedeServiceTest` · «RF-028: crear guarda el nombre sin espacios en los extremos y audita sin la
  dirección» y «RF-028: un nombre repetido en la liga responde 409 DUPLICADO sin escribir».
- `SedesIT` · «RF-028: una sede registrada queda disponible en el catálogo, ordenado por nombre» y
  «RF-028: un nombre repetido en la liga responde 409 DUPLICADO y no se guarda».
- `ModalSedes.test.tsx` · «RF-028 (CA-1): registrar una sede envía el nombre recortado y la nueva
  queda en la lista» y «RF-028 (CA-1): un 409 DUPLICADO se marca en el nombre y el modal sigue
  abierto».

### CA-2 · Una cancha con sede aparece al filtrar por esa sede

> Dada una cancha, cuando se le asigna una sede y se filtra el catálogo por esa sede, entonces
> aparece en la lista y el detalle muestra la sede.

**Pasos**

1. Abrir el detalle de una cancha sin sede y pulsar **Editar**.
2. Elegir «Unidad Deportiva Morelia» en el campo **Sede** y guardar.
3. Revisar el detalle y el renglón de datos de la cancha.
4. Elegir «Unidad Deportiva Morelia» en el filtro **Sede** del catálogo.

**Resultado esperado**

- El detalle muestra la sede en la fila «Sede».
- La lista muestra la sede al inicio de los datos de la cancha y el filtro devuelve esa cancha.

**Resultado obtenido:** **Aprobado.** El `PATCH /api/v1/canchas/{id}` guardó la sede; el detalle
mostró «Sede / Unidad Deportiva Morelia» y el renglón del catálogo
«Unidad Deportiva Morelia · Unidad deportiva · Liga». Al filtrar por la sede, la lista pidió
`GET /canchas?sedeId=cf45aab0…` y mostró «1 cancha» con esa cancha. La respuesta del detalle incluye
`sede: { id, nombre }`.

**Evidencia**

- [`CA2-detalle-sede.png`](evidencias/B-11/CA2-detalle-sede.png)
- [`CA2-filtro-sede.png`](evidencias/B-11/CA2-filtro-sede.png)
- [`CA2-cancha-con-sede.json`](evidencias/B-11/CA2-cancha-con-sede.json)

**Pruebas automáticas relacionadas**

- `CanchasIT` · «RF-028: una cancha con sede asignada al crear o al editar aparece al filtrar por
  esa sede».
- `CanchaServiceTest` · «RF-028: editar asigna la sede y audita el campo sede».
- `FormularioCancha.test.tsx` · «RF-028 (CA-2): editar cambiando de sede envía sedeId sin
  quitarSede».
- `PantallaCanchas.test.tsx` · «RF-028 (CA-2): filtrar por sede pide sedeId y «Quitar filtros» lo
  quita» y «RF-028 (D8): la sede encabeza los datos de la cancha en la lista y en el detalle».

### CA-3 · Una cancha puede quedar sin sede

> Dada una cancha con sede, cuando se elige «Sin sede» al editarla, entonces queda sin sede y la
> sede se conserva en el catálogo.

**Pasos**

1. En el detalle de la cancha con sede, pulsar **Editar**.
2. Elegir «Sin sede» en el campo **Sede** y guardar.
3. Revisar el detalle y el renglón de la cancha en el catálogo; volver el filtro a «Todas».
4. Abrir **Sedes** y comprobar que la sede sigue existiendo.

**Resultado esperado**

- La cancha queda sin sede; el detalle dice «Sin sede» y la lista deja de encabezar el renglón con
  la sede.
- La sede no se borra ni se desactiva: sigue disponible para otras canchas.

**Resultado obtenido:** **Aprobado.** El `PATCH` envió `quitarSede: true` (nunca junto con
`sedeId`), el detalle mostró «Sede / Sin sede» y el renglón del catálogo quedó
«Unidad deportiva · Liga». La respuesta del detalle ya no incluye la propiedad `sede` y, al volver
a abrir **Sedes**, «Unidad Deportiva Morelia» seguía en la lista; `GET /api/v1/sedes` la devolvió
con la dirección corregida.

**Evidencia**

- [`CA3-sin-sede.png`](evidencias/B-11/CA3-sin-sede.png)
- [`CA3-sede-conservada.png`](evidencias/B-11/CA3-sede-conservada.png)
- [`CA3-cancha-sin-sede.json`](evidencias/B-11/CA3-cancha-sin-sede.json)
- [`CA3-sedes-conservadas.json`](evidencias/B-11/CA3-sedes-conservadas.json)

**Pruebas automáticas relacionadas**

- `CanchasIT` · «RF-028: una cancha puede quedar sin sede con quitarSede y sin sedeId conserva la
  suya».
- `CanchaServiceTest` · «RF-028: con quitarSede la cancha queda sin sede» y «RF-028: sin sedeId ni
  quitarSede la sede se conserva y no se escribe nada».
- `FormularioCancha.test.tsx` · «RF-028 (CA-3): editar a «Sin sede» envía quitarSede sin sedeId».
- `PantallaCanchas.test.tsx` · «RF-028 (CA-3): el detalle de una cancha sin sede dice «Sin sede»».

## 4. Verificaciones adicionales

### V1 · Sin `EDITAR` se consulta y se filtra, pero no se gestionan sedes

**Pasos**

1. Iniciar sesión como **Programador** (solo `canchas.cancha:CONSULTAR`).
2. Abrir Programación › Canchas y revisar el encabezado y el filtro **Sede**.

**Resultado esperado**

- El filtro por sede está disponible para consultar el catálogo.
- Los botones **Sedes** y **Nueva cancha** no aparecen, y las acciones de escritura del detalle
  tampoco.

**Resultado obtenido:** **Aprobado.** El Programador vio el filtro **Sede** y la lista, sin los
botones **Sedes** ni **Nueva cancha** (los únicos botones de la pantalla fueron «Cerrar sesión»,
«Anterior» y «Siguiente»). Coincide con D1 y con VAL-015: el `GET /sedes` pide `CONSULTAR` en
cualquier ámbito, mientras que registrar y editar exigen `EDITAR`.

**Evidencia**

- [`V1-programador-sin-sedes.png`](evidencias/B-11/V1-programador-sin-sedes.png)

**Pruebas automáticas relacionadas**

- `SedesIT` · «VAL-015: el Programador lista las sedes pero no las registra (D1)» y «VAL-015: un rol
  sin canchas.* no lista las sedes».
- `PantallaCanchas.test.tsx` · «RF-028 (D1): solo con CONSULTAR no aparece «Sedes», pero sí el
  filtro por sede».

### V2 · El alta y la edición de la sede quedan auditadas sin la dirección

**Pasos**

1. Como **Administrador**, abrir **Sedes**, pulsar **Editar** en la sede y corregir la dirección.
2. Guardar y revisar el aviso de actualización.
3. Consultar `siglim.auditoria` para las acciones `REGISTRAR_SEDE` y `EDITAR_SEDE`.

**Resultado esperado**

- El `PATCH /api/v1/sedes/{id}` actualiza solo la dirección.
- La auditoría registra el alta y la edición con los nombres de los campos, sin el valor de la
  dirección (RF-093).

**Resultado obtenido:** **Aprobado.** La edición mostró «Sede «Unidad Deportiva Morelia»
actualizada.» y cambió la dirección a «Av. Universidad 123, Morelia, Mich.». La auditoría de M-06
registró `REGISTRAR_SEDE` con `{"campos":["nombre","direccion"]}` y `EDITAR_SEDE` con
`{"campos":["direccion"]}`; ningún evento guarda la dirección ni otro dato de contacto.

**Evidencia**

- [`V2-sede-editada.png`](evidencias/B-11/V2-sede-editada.png)
- [`V2-auditoria-sedes.txt`](evidencias/B-11/V2-auditoria-sedes.txt)

**Pruebas automáticas relacionadas**

- `SedesIT` · «RF-093: el alta y la edición se auditan sin la dirección».
- `SedeServiceTest` · «RF-028: crear guarda el nombre sin espacios en los extremos y audita sin la
  dirección» y «RF-028: una dirección en blanco se vacía y se audita sin su valor».

## 5. Verificación automática

| Comando | Dónde | Resultado |
| --- | --- | --- |
| `./mvnw verify` | `backend/` | **BUILD SUCCESS** · 130 pruebas, 0 fallos, 1 omitida (`AlmacenamientoDocumentosAwsIT`, sin credenciales de AWS) |
| `npm run lint` | `frontend/` | 0 advertencias y 0 errores |
| `npm test` | `frontend/` | 312 pruebas aprobadas |
| `npm run build` | `frontend/` | compilación de producción correcta |

## 6. Defectos encontrados

Ninguno. Las tres acciones del escenario (alta, asignación con filtro y retiro) funcionaron sin
errores y la sede conservó su identidad en todo el flujo.

Observaciones que no son defectos:

- El filtro no ofrece «sin sede» porque el contrato de `GET /canchas` solo admite `sedeId` (D8);
  para localizar esas canchas se consulta el catálogo completo.
- Las sedes no se borran ni se desactivan (D3): el modal solo ofrece alta y edición, y la cancha se
  desvincula con «Sin sede».
- Los permisos de gestión se evaluaron con el rol Programador. En la matriz inicial, la
  DIRECTIVA_CLUB solo tiene `canchas.solicitud:CREAR`, así que un usuario con ese rol tampoco puede
  abrir **Sedes** (D1).

## 7. Conclusión

Los tres criterios de aceptación de B-11 quedaron **aprobados** contra una base local efímera con
el backend y el frontend reales: una sede nueva se registra y el nombre repetido se rechaza con
409; una cancha se asigna a la sede y se filtra por ella; y la cancha puede quedar sin sede sin
borrar la sede. La verificación de permisos (V1) y la auditoría (V2) también pasaron.

La prueba confirma la integración de B-11.1 (catálogo de sedes y `sedeId`) y B-11.2 (modal,
formulario, filtro y detalle) en F-21. Las pruebas automáticas citadas cubren los casos de error que
no se repitieron aquí (422 `REFERENCIA_INVALIDA`, `sedeId` y `quitarSede` juntos, 404 al editar una
sede inexistente y la restricción `UNIQUE` como última garantía).
