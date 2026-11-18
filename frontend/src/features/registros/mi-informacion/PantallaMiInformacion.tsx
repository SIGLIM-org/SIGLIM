import { useSearchParams } from 'react-router'
import { SearchX, UserRoundX } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import {
  useMiInformacion,
  useTitularesMiInformacion,
} from '@/features/registros/mi-informacion/api'
import { BloqueRegistro } from '@/features/registros/mi-informacion/BloqueRegistro'
import { SeccionPrivacidad } from '@/features/registros/mi-informacion/SeccionPrivacidad'
import { SeccionSanciones } from '@/features/registros/mi-informacion/SeccionSanciones'
import { SelectorTitular } from '@/features/registros/mi-informacion/SelectorTitular'
import type { MiInformacion } from '@/features/registros/mi-informacion/tipos'
import { esNoEncontrado, esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/** Parámetro de la URL con el titular elegido (DEC-147). */
const PARAMETRO_PERSONA = 'persona'

function Carga() {
  return <EstadoCarga etiqueta="Cargando tu información…" filas={4} columnas={2} />
}

function Detalle({ informacion }: { informacion: MiInformacion }) {
  const { registros, sanciones } = informacion
  const plegable = registros.length > 1

  return (
    <>
      {registros.length === 0 ? (
        <EstadoVacio titulo="Sin registros en la temporada vigente" />
      ) : (
        <div className="flex flex-col gap-4">
          {registros.map((registro, indice) => (
            <BloqueRegistro
              key={registro.id}
              registro={registro}
              plegable={plegable}
              abierto={indice === 0}
            />
          ))}
        </div>
      )}
      <SeccionSanciones sanciones={sanciones} />
      <SeccionPrivacidad />
    </>
  )
}

/**
 * «Mi información» (F-35, UI-032): el jugador, o el tutor de un menor, consulta en solo lectura sus
 * registros, documentos, elegibilidad, partidos y sanciones. Con varios titulares se elige en
 * `?persona=`; un titular que no está en la lista nunca se pide al API ni se cambia por otro sin
 * avisar (DEC-147). Diseñada primero para 390 px, en una columna (DEC-146).
 *
 * Refs: RF-144, RF-019, RF-020, UI-032, DEC-146 a DEC-151
 */
export function PantallaMiInformacion() {
  const [parametros, setParametros] = useSearchParams()
  const titulares = useTitularesMiInformacion()
  const lista = titulares.data?.titulares
  const solicitado = parametros.get(PARAMETRO_PERSONA)
  const seleccionado = solicitado
    ? lista?.find(({ personaId }) => personaId === solicitado)
    : lista?.[0]
  const detalle = useMiInformacion(seleccionado?.personaId)

  if (esSinPermiso(titulares.error) || esSinPermiso(detalle.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const elegir = (personaId: string) =>
    setParametros({ [PARAMETRO_PERSONA]: personaId }, { replace: true })
  const verPropia = () => setParametros({}, { replace: true })

  const contenido = () => {
    if (titulares.isPending) {
      return <Carga />
    }
    if (titulares.isError) {
      return <ErrorConsulta error={titulares.error} onReintentar={() => void titulares.refetch()} />
    }
    if (lista?.length === 0) {
      return (
        <EstadoVacio
          icono={UserRoundX}
          titulo="Tu cuenta aún no está vinculada a un jugador"
          descripcion="Pide a la Secretaría de la Liga que vincule tu cuenta con tu registro."
        />
      )
    }
    if (!seleccionado || esNoEncontrado(detalle.error)) {
      return (
        <EstadoVacio
          icono={SearchX}
          titulo="No encontramos esa información"
          descripcion="Puede que ya no tengas acceso a este registro."
          accion={
            <Button variante="secundario" onClick={verPropia}>
              Ver mi información
            </Button>
          }
        />
      )
    }

    return (
      <>
        {lista && lista.length > 1 && (
          <SelectorTitular
            titulares={lista}
            seleccionado={seleccionado.personaId}
            onCambiar={elegir}
          />
        )}
        {detalle.isPending && <Carga />}
        {detalle.isError && (
          <ErrorConsulta error={detalle.error} onReintentar={() => void detalle.refetch()} />
        )}
        {detalle.data && <Detalle informacion={detalle.data} />}
      </>
    )
  }

  const persona = detalle.data?.persona

  return (
    <div className="mx-auto flex w-full max-w-2xl min-w-0 flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Mi información</h1>
        {persona && (
          <p className="flex flex-wrap items-baseline gap-x-2 text-body-md text-text-muted">
            <span className="break-words text-label-md text-text-main">
              {persona.nombreCompleto}
            </span>
            <span className="font-mono text-mono-data">
              <span className="sr-only">CURP </span>
              {persona.curpEnmascarada}
            </span>
          </p>
        )}
      </header>
      {contenido()}
    </div>
  )
}
