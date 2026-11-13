import { createBrowserRouter, type RouteObject } from 'react-router'
import { CargandoPagina } from '@/app/CargandoPagina'
import { PortalAvisoPrivacidad } from '@/features/portal/PortalAvisoPrivacidad'
import { PortalProgramacion } from '@/features/portal/PortalProgramacion'
import { PortalResultados } from '@/features/portal/PortalResultados'
import { PortalTabla } from '@/features/portal/PortalTabla'
import { PublicLayout } from '@/layouts/PublicLayout'
import { seccionesApp } from '@/layouts/navegacion'
import { PaginaNoEncontrada } from '@/pages/PaginaNoEncontrada'

/*
 * El portal público se importa de forma estática: es el fragmento inicial que
 * descarga un visitante. La autenticación, la aplicación interna y cada módulo
 * de `/app` se cargan bajo demanda (DT-01, RNF-007).
 */

/**
 * Marcador de cada módulo de `/app` en un fragmento aparte. Las secciones con pantalla propia
 * (Inicio y Tesorería) se registran más abajo; el resto sigue usando `EnConstruccion` hasta que su
 * historia lo sustituya.
 */
const RUTAS_CON_PANTALLA_PROPIA = new Set([
  '/app',
  '/app/tesoreria',
  '/app/programacion/arbitros',
])

const paginasApp: RouteObject[] = seccionesApp
  .filter(({ ruta }) => !RUTAS_CON_PANTALLA_PROPIA.has(ruta))
  .map(({ nombre, ruta, subsecciones }) => ({
    path: ruta,
    lazy: async () => {
      const { EnConstruccion } = await import('@/pages/EnConstruccion')

      return {
        element: (
          <EnConstruccion
            titulo={nombre}
            descripcion="Módulo de la aplicación interna en construcción."
            subsecciones={subsecciones}
          />
        ),
      }
    },
    HydrateFallback: CargandoPagina,
  }))

export const rutas: RouteObject[] = [
  {
    element: <PublicLayout />,
    children: [
      { path: '/', element: <PortalProgramacion /> },
      { path: '/resultados', element: <PortalResultados /> },
      { path: '/tabla', element: <PortalTabla /> },
      { path: '/aviso-de-privacidad', element: <PortalAvisoPrivacidad /> },
      { path: '*', element: <PaginaNoEncontrada /> },
    ],
  },
  {
    path: '/login',
    lazy: async () => {
      const { InicioSesion } = await import('@/features/seguridad/InicioSesion')

      return { Component: InicioSesion }
    },
    HydrateFallback: CargandoPagina,
  },
  {
    path: '/restablecer-contrasena',
    lazy: async () => {
      const { RestablecerContrasena } = await import(
        '@/features/seguridad/RestablecerContrasena'
      )

      return { Component: RestablecerContrasena }
    },
    HydrateFallback: CargandoPagina,
  },
  {
    lazy: async () => {
      const { RutaProtegida } = await import('@/app/RutaProtegida')

      return { Component: RutaProtegida }
    },
    HydrateFallback: CargandoPagina,
    children: [
      {
        lazy: async () => {
          const { AplicacionInterna } = await import('@/layouts/AplicacionInterna')

          return { Component: AplicacionInterna }
        },
        children: [
          /*
           * RutaConPermiso es la barrera por módulo de todas las rutas de
           * /app: las rutas nuevas deben colgar de aquí (H-02.4).
           */
          {
            lazy: async () => {
              const { RutaConPermiso } = await import('@/app/RutaConPermiso')

              return { Component: RutaConPermiso }
            },
            HydrateFallback: CargandoPagina,
            children: [
              {
                path: '/app',
                lazy: async () => {
                  const { PanelInicio } = await import('@/pages/PanelInicio')

                  return { Component: PanelInicio }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/tesoreria',
                lazy: async () => {
                  const { PantallaTesoreria } = await import(
                    '@/features/tesoreria/PantallaTesoreria'
                  )

                  return { Component: PantallaTesoreria }
                },
                HydrateFallback: CargandoPagina,
              },
              ...paginasApp,
              {
                path: '/app/organizacion/clubes/:clubId?',
                lazy: async () => {
                  const { PantallaClubes } = await import(
                    '@/features/organizacion/PantallaClubes'
                  )

                  return { Component: PantallaClubes }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/organizacion/afiliaciones/:afiliacionId?',
                lazy: async () => {
                  const { PantallaAfiliaciones } = await import(
                    '@/features/organizacion/afiliaciones/PantallaAfiliaciones'
                  )

                  return { Component: PantallaAfiliaciones }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/organizacion/jugadores/alta',
                lazy: async () => {
                  const { PantallaAltaJugador } = await import(
                    '@/features/registros/PantallaAltaJugador'
                  )

                  return { Component: PantallaAltaJugador }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/organizacion/jugadores/:registroId/documentos',
                lazy: async () => {
                  const { PantallaDocumentosRegistro } = await import(
                    '@/features/registros/documentos/PantallaDocumentosRegistro'
                  )

                  return { Component: PantallaDocumentosRegistro }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/organizacion/registros/:registroId?',
                lazy: async () => {
                  const { PantallaValidacionRegistros } = await import(
                    '@/features/registros/validacion/PantallaValidacionRegistros'
                  )

                  return { Component: PantallaValidacionRegistros }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/programacion/canchas/:canchaId?',
                lazy: async () => {
                  const { PantallaCanchas } = await import(
                    '@/features/canchas/PantallaCanchas'
                  )

                  return { Component: PantallaCanchas }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/programacion/arbitros',
                lazy: async () => {
                  const { PantallaArbitros } = await import(
                    '@/features/arbitraje/PantallaArbitros'
                  )

                  return { Component: PantallaArbitros }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/disciplina/catalogos',
                lazy: async () => {
                  const { PantallaCatalogosDisciplinarios } = await import(
                    '@/features/disciplina/PantallaCatalogosDisciplinarios'
                  )

                  return { Component: PantallaCatalogosDisciplinarios }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/administracion/temporadas',
                lazy: async () => {
                  const { PantallaTemporadas } = await import(
                    '@/features/temporadas/PantallaTemporadas'
                  )

                  return { Component: PantallaTemporadas }
                },
                HydrateFallback: CargandoPagina,
              },
                            {
                path: '/app/administracion/temporadas/:temporadaId/estructura',
                lazy: async () => {
                  const { PantallaEstructuraTemporada } = await import(
                    '@/features/temporadas/PantallaEstructuraTemporada'
                  )

                  return { Component: PantallaEstructuraTemporada }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/administracion/usuarios',
                lazy: async () => {
                  const { PantallaUsuarios } = await import(
                    '@/features/usuarios/PantallaUsuarios'
                  )

                  return { Component: PantallaUsuarios }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/administracion/roles',
                lazy: async () => {
                  const { PantallaRolesPermisos } = await import(
                    '@/features/roles/PantallaRolesPermisos'
                  )

                  return { Component: PantallaRolesPermisos }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/administracion/comunicados',
                lazy: async () => {
                  const { PantallaComunicados } = await import(
                    '@/features/publicacion/PantallaComunicados'
                  )

                  return { Component: PantallaComunicados }
                },
                HydrateFallback: CargandoPagina,
              },
              {
                path: '/app/administracion/auditoria',
                lazy: async () => {
                  const { PantallaAuditoria } = await import(
                    '@/features/auditoria/PantallaAuditoria'
                  )

                  return { Component: PantallaAuditoria }
                },
                HydrateFallback: CargandoPagina,
              },
              { path: '/app/*', element: <PaginaNoEncontrada rutaInicio="/app" /> },
            ],
          },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(rutas)
