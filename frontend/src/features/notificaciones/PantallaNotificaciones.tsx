import { useEffect, useState, type KeyboardEvent } from 'react'
import { CheckCheck, CircleCheck, Inbox } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { useAvisos, useAvisosNoLeidos, useMarcarTodos } from '@/features/notificaciones/api'
import { TarjetaAviso } from '@/features/notificaciones/TarjetaAviso'
import type { PestanaAvisos } from '@/features/notificaciones/tipos'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { ApiError } from '@/lib/api/ApiError'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const CONSULTAR = 'notificaciones.aviso:CONSULTAR'
const formatoEntero = new Intl.NumberFormat('es-MX')

const pestanas: { valor: PestanaAvisos; texto: string }[] = [
  { valor: 'NO_LEIDAS', texto: 'Sin leer' },
  { valor: 'TODAS', texto: 'Todos' },
]

/**
 * Pantalla de avisos F-09 (UI-030, RF-142). M-14 no tiene sección en el menú: se entra desde la
 * campana. Sin el permiso de consulta se muestra la 403 sin pedir datos.
 */
export function PantallaNotificaciones() {
  return (
    <ConPermiso permiso={CONSULTAR} alternativa={<PaginaSinPermiso rutaInicio="/app" />}>
      <BandejaAvisos />
    </ConPermiso>
  )
}

/**
 * Pestañas «Sin leer» y «Todos», avisos más recientes primero y lectura de uno o de todos (H-25.2).
 * El filtro por categoría del prototipo queda fuera: el contrato no filtra por evento y filtrar una
 * página ya recortada daría totales falsos.
 */
function BandejaAvisos() {
  const [pestana, setPestana] = useState<PestanaAvisos>('NO_LEIDAS')
  const [pagina, setPagina] = useState(0)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const lista = useAvisos(pestana, pagina)
  const noLeidos = useAvisosNoLeidos()
  const marcarTodos = useMarcarTodos()
  const totalNoLeidos = noLeidos.data?.totalElementos ?? 0
  const datos = lista.data
  const avisos = datos?.contenido ?? []

  // Al leer los avisos de la última página de «Sin leer», esa página queda vacía: se vuelve a la última
  // que todavía tiene avisos.
  useEffect(() => {
    if (datos && datos.contenido.length === 0 && pagina > 0) {
      setPagina(Math.max(0, datos.totalPaginas - 1))
    }
  }, [datos, pagina])

  const cambiarPestana = (siguiente: PestanaAvisos) => {
    setPestana(siguiente)
    setPagina(0)
    setMensaje(null)
  }

  const navegarPestanas = (evento: KeyboardEvent<HTMLDivElement>) => {
    if (evento.key !== 'ArrowLeft' && evento.key !== 'ArrowRight') {
      return
    }

    evento.preventDefault()
    const actual = pestanas.findIndex((opcion) => opcion.valor === pestana)
    const paso = evento.key === 'ArrowRight' ? 1 : -1
    const siguiente = pestanas[(actual + paso + pestanas.length) % pestanas.length].valor

    cambiarPestana(siguiente)
    document.getElementById(`pestana-avisos-${siguiente}`)?.focus()
  }

  const marcarTodosLosAvisos = () => {
    setMensaje(null)
    marcarTodos.mutate(undefined, {
      onSuccess: ({ marcadas }) => {
        setPagina(0)
        setMensaje(
          marcadas === 1
            ? 'Se marcó 1 aviso como leído.'
            : `Se marcaron ${formatoEntero.format(marcadas)} avisos como leídos.`,
        )
      },
    })
  }

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-6">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Avisos</h1>
          <p className="mt-1 max-w-2xl text-body-md text-text-muted">
            Cédulas, cobros y programación que te corresponden. Abre un aviso para ir al trámite.
          </p>
        </div>
        <Button
          variante="secundario"
          icono={CheckCheck}
          onClick={marcarTodosLosAvisos}
          cargando={marcarTodos.isPending}
          disabled={totalNoLeidos === 0}
          className="shrink-0"
        >
          Marcar todos como leídos
        </Button>
      </header>

      {mensaje && (
        <p role="status" className="text-body-md text-success-strong">
          {mensaje}
        </p>
      )}
      {marcarTodos.isError && (
        <p role="alert" className="text-body-md text-error-strong">
          {marcarTodos.error instanceof ApiError
            ? marcarTodos.error.problema.detail
            : 'No se pudieron marcar los avisos. Intenta de nuevo.'}
        </p>
      )}

      <section aria-label="Bandeja de avisos" className="flex flex-col gap-4">
        <div
          role="tablist"
          aria-label="Filtrar avisos"
          onKeyDown={navegarPestanas}
          className="flex w-fit gap-1 rounded-md bg-border/50 p-1"
        >
          {pestanas.map((opcion) => {
            const activa = pestana === opcion.valor

            return (
              <button
                key={opcion.valor}
                type="button"
                role="tab"
                id={`pestana-avisos-${opcion.valor}`}
                aria-selected={activa}
                aria-controls="panel-avisos"
                tabIndex={activa ? 0 : -1}
                onClick={() => cambiarPestana(opcion.valor)}
                className={[
                  'inline-flex min-h-11 items-center gap-2 rounded-md px-4 text-label-md transition-colors',
                  activa ? 'bg-surface text-text-main shadow-sm' : 'text-text-muted hover:text-text-main',
                ].join(' ')}
              >
                {opcion.texto}
                {opcion.valor === 'NO_LEIDAS' && totalNoLeidos > 0 && (
                  <span className="rounded-[6px] bg-primary px-1.5 font-mono text-body-sm text-on-primary">
                    {formatoEntero.format(totalNoLeidos)}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        <div
          role="tabpanel"
          id="panel-avisos"
          aria-labelledby={`pestana-avisos-${pestana}`}
          className="flex flex-col gap-3"
        >
          {lista.isPending ? (
            <EstadoCarga etiqueta="Cargando avisos…" filas={4} columnas={2} />
          ) : lista.isError ? (
            <ErrorConsulta error={lista.error} onReintentar={() => void lista.refetch()} />
          ) : avisos.length === 0 ? (
            pestana === 'NO_LEIDAS' ? (
              <EstadoVacio
                icono={CircleCheck}
                titulo="Estás al día"
                descripcion="No tienes avisos sin leer. Los nuevos aparecerán aquí y en la campana."
              />
            ) : (
              <EstadoVacio
                icono={Inbox}
                titulo="Aún no tienes avisos"
                descripcion="Aquí verás las cédulas por validar, los cobros vencidos y la programación publicada que te corresponden."
              />
            )
          ) : (
            <>
              <p aria-live="polite" className="text-label-md text-text-muted">
                {datos?.totalElementos === 1
                  ? '1 aviso'
                  : `${formatoEntero.format(datos?.totalElementos ?? 0)} avisos`}
              </p>
              <ul className="flex flex-col gap-2">
                {avisos.map((aviso) => (
                  <li key={aviso.id}>
                    <TarjetaAviso aviso={aviso} />
                  </li>
                ))}
              </ul>
              {(datos?.totalPaginas ?? 0) > 1 && (
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="order-first w-full text-center text-body-sm text-text-muted sm:order-none sm:w-auto">
                    Página {pagina + 1} de {datos?.totalPaginas}
                  </span>
                  <Button
                    variante="secundario"
                    disabled={pagina === 0}
                    onClick={() => setPagina((actual) => actual - 1)}
                  >
                    Anterior
                  </Button>
                  <Button
                    variante="secundario"
                    disabled={pagina + 1 >= (datos?.totalPaginas ?? 1)}
                    onClick={() => setPagina((actual) => actual + 1)}
                  >
                    Siguiente
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </section>
    </div>
  )
}
