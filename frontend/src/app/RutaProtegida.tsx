import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Navigate, Outlet, useLocation } from 'react-router'
import { supabase } from '@/lib/supabase'

type EstadoSesion = 'comprobando' | 'autenticada' | 'anonima'

/**
 * Protege las rutas de `/app` (AGENTS.md, rutas) y mantiene sincronizada la
 * sesión entre pestañas (DEF-02). Supabase difunde `SIGNED_OUT` a las demás
 * pestañas por `BroadcastChannel`; al recibirlo se descarta la caché de
 * consultas y se vuelve a `/login` sin mostrar datos protegidos. Si el
 * navegador no entrega ese aviso, la sesión se vuelve a comprobar al recuperar
 * el foco, de modo que la redirección ocurre a más tardar en la siguiente
 * interacción. Al volver con «Atrás», esta misma comprobación impide reabrir
 * `/app` sin sesión.
 */
export function RutaProtegida() {
  const [estado, setEstado] = useState<EstadoSesion>('comprobando')
  const ubicacion = useLocation()
  const clienteConsultas = useQueryClient()
  const sesionTerminada = useRef(false)

  const cerrarPorSesionTerminada = useCallback(() => {
    sesionTerminada.current = true
    clienteConsultas.clear()
    setEstado('anonima')
  }, [clienteConsultas])

  useEffect(() => {
    let vigente = true

    supabase.auth
      .getSession()
      .then(({ data }) => {
        if (vigente && !sesionTerminada.current) {
          setEstado(data.session ? 'autenticada' : 'anonima')
        }
      })
      .catch(() => {
        if (vigente && !sesionTerminada.current) {
          setEstado('anonima')
        }
      })

    return () => {
      vigente = false
    }
  }, [])

  useEffect(() => {
    const { data } = supabase.auth.onAuthStateChange((evento) => {
      if (evento === 'SIGNED_OUT') {
        cerrarPorSesionTerminada()
      }
    })

    return () => data.subscription.unsubscribe()
  }, [cerrarPorSesionTerminada])

  useEffect(() => {
    const comprobarAlRecuperarElFoco = () => {
      if (document.visibilityState === 'hidden') {
        return
      }

      void supabase.auth
        .getSession()
        .then(({ data }) => {
          if (!data.session && !sesionTerminada.current) {
            cerrarPorSesionTerminada()
          }
        })
        .catch(() => {
          // Sin respuesta no se cambia el estado: el backend vuelve a validar
          // el token en cada petición y un 401 cierra la sesión.
        })
    }

    window.addEventListener('focus', comprobarAlRecuperarElFoco)
    document.addEventListener('visibilitychange', comprobarAlRecuperarElFoco)

    return () => {
      window.removeEventListener('focus', comprobarAlRecuperarElFoco)
      document.removeEventListener('visibilitychange', comprobarAlRecuperarElFoco)
    }
  }, [cerrarPorSesionTerminada])

  if (estado === 'comprobando') {
    return (
      <p role="status" className="p-4 text-body-md text-text-muted">
        Comprobando sesión…
      </p>
    )
  }

  if (estado === 'anonima') {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  }

  return <Outlet />
}
