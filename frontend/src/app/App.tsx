import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { router } from '@/app/router'
import { reintentarConsulta } from '@/lib/api/errores'

const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: {
      retry: reintentarConsulta,
      refetchOnWindowFocus: false,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={clienteConsultas}>
      {/*
       * `useTransitions` expone el estado de carga mientras se descarga el
       * fragmento de una ruta, y así `InternalLayout` puede mostrar `EstadoCarga`
       * al navegar entre módulos (DT-01, RNF-007).
       */}
      <RouterProvider router={router} useTransitions />
    </QueryClientProvider>
  )
}
