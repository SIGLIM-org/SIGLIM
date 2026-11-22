# AGENTS.md — Frontend (React)

Complementa al [`AGENTS.md` raíz](../AGENTS.md). Léelo primero.

## Stack

React 19 · TypeScript 6 (estricto) · Vite 8 · Tailwind CSS 4 · React Router 8 · TanStack Query 5 ·
`@supabase/supabase-js` (solo autenticación) · Vitest + Testing Library · oxlint.

## Comandos

```bash
npm run dev        # http://localhost:5173 (proxy /api → http://localhost:8080)
npm run lint       # oxlint
npm test           # vitest run
npm run build      # tsc -b && vite build  ← obligatorio antes de terminar
```

## Estructura

```text
src/
├── app/            App.tsx (providers) y router.tsx (todas las rutas)
├── config/env.ts   variables de entorno tipadas
├── lib/
│   ├── api/        cliente HTTP único (cliente.ts, cuerpo JSON o FormData), ApiError (RFC 9457), tipos de paginación
│   └── supabase.ts cliente de Supabase SOLO para auth
├── components/ui/  componentes base del sistema de diseño (Button, …)
├── layouts/        PublicLayout (portal) e InternalLayout (aplicación interna)
├── features/       un directorio por módulo M-xx (ver features/README.md)
├── pages/          páginas genéricas (404, en construcción)
├── styles/         index.css con los tokens de diseño (@theme)
└── test/           configuración de Vitest
```

## Reglas

- **Datos:** siempre a través de `api` (`@/lib/api/cliente`) y hooks de TanStack Query
  (`useQuery`/`useMutation`) definidos en `features/<modulo>/api.ts`. Nunca `fetch` dentro de un
  componente ni `supabase.from(...)`.
- **Errores:** captura `ApiError` y muestra `error.problema.detail` al usuario (ya viene en español
  y explica la causa). Decide el tratamiento por `error.problema.codigo` (`VAL-004`, `VAL-015`…),
  nunca por el texto (DEC-63); marca los campos de `errores`. Ver convenciones de API §3. Los textos
  propios del frontend siguen la misma regla: dicen qué pasó y qué hacer, sin códigos internos ni
  datos de otros clubes o personas. Una **consulta**
  con error se muestra con `ErrorConsulta` (403/404) y no se reintenta en 4xx
  (`reintentarConsulta`); una **mutación** muestra `detail` en el formulario o el modal.
- **Rutas:** el portal público cuelga de `/` (`PublicLayout`); la aplicación interna de `/app`
  (`RutaProtegida` + `InternalLayout`). Registra rutas nuevas solo en `src/app/router.tsx`.
- **Navegación (DEC-69):** el menú actual es definitivo. Cada pantalla nueva se agrega bajo su
  módulo y solo se muestra con su permiso de consulta (`modulo.recurso:CONSULTAR`).
- **Permisos:** el backend decide (VAL-015). El frontend oculta lo que el usuario no puede hacer
  con los permisos de `GET /api/v1/yo` y las `accionesPermitidas` de cada recurso, pero nunca es la
  única barrera. Usa `ConPermiso` (`@/features/seguridad/ConPermiso`) para ocultar acciones y
  `RutaConPermiso` (`@/app/RutaConPermiso`) para bloquear la URL de un módulo; las páginas 403/404
  de `pages/` son la salida visible (H-02.4). Nunca condiciones por el nombre del rol.
- **Estilos:** solo utilidades de Tailwind con los tokens de `styles/index.css`
  (`bg-primary`, `text-text-muted`, `border-border`, `bg-surface-dark`…). **Prohibido** escribir
  colores HEX en componentes o crear archivos `.css` por componente.
- **Diseño:** sigue [`DESIGN.md`](../DESIGN.md) y las pantallas aprobadas de
  [`docs/diseno-ui/pantallas/`](../docs/diseno-ui/pantallas/README.md). El `code.html` de cada
  pantalla es referencia visual: **no lo copies**; reconstruye en React con los tokens y
  componentes del proyecto. No implementes las diferencias (D-xx) de [`pantallas/README.md` §3](../docs/diseno-ui/pantallas/README.md) ni los conflictos C-01 a
  C-08 (sin buscador global, estadísticas de jugador solo en backlog y con consentimiento, un solo
  «Iniciar sesión»).
- **Estados visibles (ERS 3.1.1):** muestra el estado del partido y las acciones permitidas,
  distingue Borrador/Revisión/Publicado y diferencia dato oficial de indicador calculado.
- **Accesibilidad (WCAG 2.1 AA):** `<label>` asociado a cada input, foco visible, `aria-hidden`
  en iconos decorativos, mensajes de error con `role="alert"`. Navegable con teclado.
- **Responsive:** móvil primero; breakpoints `sm` 640 px y `lg` 1024 px. Tablas anchas → tarjetas
  en móvil cuando sea necesario.
- **Tipos:** sin `any`. Los tipos de DTO van en `features/<modulo>/tipos.ts` con los mismos
  nombres camelCase del API. Importa con el alias `@/`.
- **Componentes:** funciones con props tipadas; un componente por archivo con su mismo nombre.
- **Iconos:** se usa **Lucide React** (`lucide-react`), la opción propuesta en P-02 de
  `open-design-decisions.md`. Los iconos decorativos llevan `aria-hidden="true"`; los interactivos,
  `aria-label`.

## Pruebas

Junto al archivo probado (`Button.test.tsx`). Prueba comportamiento visible para el usuario con
Testing Library (`getByRole`, `userEvent`), no detalles de implementación. Simula el API con
`vi.mock('@/lib/api/cliente')`.
