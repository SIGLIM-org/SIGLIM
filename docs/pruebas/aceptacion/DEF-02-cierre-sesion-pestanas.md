# Prueba de aceptación · DEF-02 Cerrar sesión en una pestaña no cierra la sesión en las demás

| Campo | Valor |
| --- | --- |
| Defecto | DEF-02 (Sprint 2) |
| Origen | Observación de la revisión de H-01.3 (Sprint 1) |
| Requisitos | RNF-014, UI-001, VAL-015 |
| Historia relacionada | H-01 Iniciar sesión y ver el panel con mis módulos permitidos |
| Rama | `fix/m01-def-02-cierre-sesion-pestanas` sobre `develop` en `a569fa1` |
| Fecha | 2026-11-06 |
| Ejecutó | Pendiente de ejecución en un navegador real (ver §5) |
| Resultado global | **Pendiente** de la prueba manual; las pruebas automáticas de la corrección están aprobadas |

## 1. Alcance

Comprobar que el cierre de sesión se propaga entre pestañas del mismo navegador y que, después de
la redirección, el botón «Atrás» no vuelve a mostrar contenido de `/app`.

`RutaProtegida` comprobaba la sesión solo al montarse. Al cerrar sesión en otra pestaña, Supabase
Auth borra el token del almacenamiento local y emite `SIGNED_OUT`, pero la pestaña abierta seguía
mostrando el área `/app` con los datos ya cargados. La corrección:

1. Escucha `SIGNED_OUT` con `supabase.auth.onAuthStateChange`; Supabase lo difunde a las demás
   pestañas por `BroadcastChannel`.
2. Descarta la caché de TanStack Query y cambia a estado anónimo, lo que renderiza
   `<Navigate to="/login" replace>`.
3. Como respaldo para navegadores sin `BroadcastChannel`, vuelve a comprobar la sesión al recuperar
   el foco o la visibilidad de la pestaña; así la redirección ocurre a más tardar en la siguiente
   interacción.
4. Al volver con «Atrás», `RutaProtegida` se monta de nuevo, `getSession()` no encuentra sesión y
   vuelve a redirigir antes de renderizar las rutas hijas.

## 2. Entorno previsto

| Componente | Configuración |
| --- | --- |
| Base de datos y autenticación | Proyecto de Supabase **SIGLIM** con las migraciones aplicadas |
| Backend | `./mvnw spring-boot:run` en `http://localhost:8080` con `backend/.env` |
| Frontend | `npm run dev` en `http://localhost:5173` |
| Cuenta | Una cuenta interna activa (por ejemplo, **Capturista**) |
| Navegador | Chrome, Firefox o Edge actuales, con dos pestañas del mismo origen |

No se publican correos, contraseñas ni tokens en las capturas.

## 3. Escenarios

### CA-1 · La otra pestaña redirige al inicio de sesión

> Dadas dos pestañas con sesión iniciada, cuando se cierra sesión en una, entonces la otra redirige
> al inicio de sesión a más tardar en su siguiente interacción.

**Pasos**

1. Abrir `http://localhost:5173/login` e iniciar sesión con la cuenta de prueba.
2. En `/app`, abrir una segunda pestaña con la misma URL y entrar a una pantalla con datos
   protegidos, por ejemplo `/app/tesoreria` o `/app/organizacion/clubes`.
3. En la primera pestaña, pulsar «Cerrar sesión» en la barra superior.
4. Observar la segunda pestaña sin interactuar con ella.
5. Si la segunda pestaña no cambió, hacer clic dentro de ella o cambiar a ella desde otra ventana.

**Resultado esperado**

- La segunda pestaña muestra `/login` (o la pantalla de acceso) sin ningún dato de `/app`.
- No se muestra el panel, la barra lateral ni datos de la cuenta anterior.
- La redirección no requiere recargar la página.

**Resultado obtenido:** pendiente.

**Evidencia:** pendiente.

**Pruebas automáticas relacionadas**

- `RutaProtegida.test.tsx` · «DEF-02: redirige al inicio de sesión cuando otra pestaña cierra la
  sesión»: simula el evento `SIGNED_OUT` que Supabase difunde y comprueba la redirección y la
  limpieza de la caché de consultas.
- `RutaProtegida.test.tsx` · «DEF-02: al recuperar el foco comprueba la sesión si no hubo aviso de
  otra pestaña»: cubre el respaldo cuando no llega el evento.

### CA-2 · «Atrás» no vuelve a mostrar `/app`

> Después de la redirección, volver con el botón «Atrás» no muestra contenido de `/app`.

**Pasos**

1. Partir del estado de CA-1, con la segunda pestaña ya redirigida a `/login`.
2. Pulsar «Atrás» en la segunda pestaña.
3. Repetir con el historial que haya quedado de `/app` (por ejemplo, entrar a `/app/tesoreria`,
   cerrar sesión en la otra pestaña y pulsar «Atrás» dos veces).
4. Revisar la URL y el contenido.

**Resultado esperado**

- Se permanece en `/login` o se vuelve a redirigir a `/login`.
- No aparece contenido de `/app` en ningún momento.
- En el almacenamiento local ya no existe la clave `sb-…-auth-token`.

**Resultado obtenido:** pendiente.

**Evidencia:** pendiente.

**Pruebas automáticas relacionadas**

- `RutaProtegida.test.tsx` · «redirige al inicio de sesión cuando no hay sesión»: al montar la ruta
  sin sesión, no se renderiza la ruta hija.
- `router.test.tsx` · «envía /app al inicio de sesión cuando no hay sesión».

### CA-3 · Prueba manual documentada

> La prueba manual queda documentada en `docs/pruebas/aceptacion/`.

**Resultado esperado:** este archivo, con los pasos, el entorno, los criterios y el resultado de la
ejecución.

**Resultado obtenido:** documento creado. La ejecución manual queda pendiente de una persona con
un navegador y la cuenta de prueba reales (ver §5).

## 4. Verificación automática ejecutada

Fecha: 2026-11-06. Rama `fix/m01-def-02-cierre-sesion-pestanas`.

| Comando | Resultado |
| --- | --- |
| `npm test` (frontend) | **458/458** pruebas aprobadas, 60 archivos |
| `npm run lint` (frontend) | 0 advertencias y 0 errores |
| `npm run build` (frontend) | Compilación correcta |

También se ejecutaron las pruebas del router (`router.test.tsx`) y de la ruta protegida
(`RutaProtegida.test.tsx`) de forma aislada: 34/34 aprobadas.

## 5. Estado de la prueba manual

La prueba manual **no se ejecutó en el entorno del agente** porque requiere un navegador real con
una sesión de Supabase Auth y dos pestañas, además de la cuenta de prueba de la Liga. Para cerrarla:

1. Ejecutar los escenarios CA-1 y CA-2 en el entorno de §2.
2. Guardar las capturas en `docs/pruebas/aceptacion/evidencias/DEF-02/` con los nombres
   `CA1-pestana-redirigida.png` y `CA2-atras-sin-app.png` (sin tokens, contraseñas ni correos).
3. Anotar el resultado obtenido y cambiar el resultado global a **Aprobado** o **Rechazado**.

## 6. Observaciones

- En Chrome, Firefox y Edge actuales `BroadcastChannel` está disponible, así que la redirección es
  inmediata. El respaldo por foco/visibilidad cubre navegadores que no lo implementen.
- El `access_token` sigue siendo válido hasta su expiración (hasta 1 hora), pero la pestaña ya no
  puede usarlo: `tokenActual()` lo lee de Supabase Auth y el almacenamiento local se borró. El
  backend, además, vuelve a validar el token en cada petición (VAL-015).
- El cierre de sesión local (misma pestaña) sigue funcionando como antes; el nuevo escucha de
  `SIGNED_OUT` no cambia el destino `/login`.
