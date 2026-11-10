import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { SeleccionMultiple } from '@/components/ui/SeleccionMultiple'
import { FILTROS_VACIOS, type ValoresFiltros } from '@/features/auditoria/filtros'
import type { OpcionesFiltroAuditoria } from '@/features/auditoria/tipos'
import { etiquetaModulo } from '@/lib/modulos'

type Props = {
  /** Filtros aplicados, los de la URL. */
  aplicados: ValoresFiltros
  opciones?: OpcionesFiltroAuditoria
  onAplicar: (filtros: ValoresFiltros) => void
}

/** Conserva lo aplicado aunque ya no aparezca entre las opciones del servidor. */
function conSeleccionados(opciones: readonly string[] = [], seleccion: readonly string[]): string[] {
  return [...new Set([...opciones, ...seleccion])]
}

/**
 * Filtros de la auditoría (RF-134). Lo que se edita es un borrador: solo llega a la URL, y con
 * ello a la API, al pulsar «Aplicar filtros». Si la URL cambia (atrás, «Limpiar filtros» del
 * estado vacío), el borrador se vuelve a tomar de ella.
 */
export function FormularioFiltrosAuditoria({ aplicados, opciones, onAplicar }: Props) {
  const id = useId()
  const [borrador, setBorrador] = useState(aplicados)
  const [previos, setPrevios] = useState(aplicados)
  const [errorRango, setErrorRango] = useState<string>()

  if (JSON.stringify(aplicados) !== JSON.stringify(previos)) {
    setPrevios(aplicados)
    setBorrador(aplicados)
    setErrorRango(undefined)
  }

  const cambiar = (cambios: Partial<ValoresFiltros>) => {
    setBorrador((actual) => ({ ...actual, ...cambios }))
  }

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault()

    if (borrador.desde && borrador.hasta && borrador.desde > borrador.hasta) {
      setErrorRango('«Hasta» no puede ser anterior a «Desde». Corrige una de las dos fechas.')
      return
    }

    setErrorRango(undefined)
    onAplicar(borrador)
  }

  const limpiar = () => {
    setBorrador(FILTROS_VACIOS)
    setErrorRango(undefined)
    onAplicar(FILTROS_VACIOS)
  }

  const perfilFueraDeOpciones =
    borrador.perfilId && !opciones?.perfiles.some(({ id: perfil }) => perfil === borrador.perfilId)
  const textoVacio = opciones ? 'Aún no hay opciones' : 'Cargando opciones…'

  return (
    <form
      noValidate
      aria-label="Filtros de auditoría"
      onSubmit={aplicar}
      className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1fr)]">
        <CampoSeleccion
          id={`${id}-usuario`}
          etiqueta="Usuario"
          value={borrador.perfilId}
          onChange={(evento) => cambiar({ perfilId: evento.target.value })}
        >
          <option value="">Todos</option>
          {opciones?.perfiles.map((perfil) => (
            <option key={perfil.id} value={perfil.id}>
              {perfil.nombreMostrado}
            </option>
          ))}
          {perfilFueraDeOpciones && <option value={borrador.perfilId}>Otro usuario</option>}
        </CampoSeleccion>
        <SeleccionMultiple
          id={`${id}-modulo`}
          etiqueta="Módulo"
          opciones={conSeleccionados(opciones?.modulos, borrador.modulos).map((modulo) => ({
            valor: modulo,
            etiqueta: etiquetaModulo(modulo),
          }))}
          seleccion={borrador.modulos}
          onChange={(modulos) => cambiar({ modulos })}
          textoVacio={textoVacio}
        />
        <SeleccionMultiple
          id={`${id}-accion`}
          etiqueta="Acción"
          opciones={conSeleccionados(opciones?.acciones, borrador.acciones).map((accion) => ({
            valor: accion,
            etiqueta: accion,
          }))}
          seleccion={borrador.acciones}
          onChange={(acciones) => cambiar({ acciones })}
          claseOpcion="font-mono text-mono-data break-all"
          textoVacio={textoVacio}
        />
        <CampoTexto
          id={`${id}-desde`}
          etiqueta="Desde"
          type="date"
          value={borrador.desde}
          onChange={(evento) => {
            setErrorRango(undefined)
            cambiar({ desde: evento.target.value })
          }}
        />
        <CampoTexto
          id={`${id}-hasta`}
          etiqueta="Hasta"
          type="date"
          value={borrador.hasta}
          error={errorRango}
          onChange={(evento) => {
            setErrorRango(undefined)
            cambiar({ hasta: evento.target.value })
          }}
        />
      </div>
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variante="secundario" onClick={limpiar}>
          Limpiar
        </Button>
        <Button type="submit">Aplicar filtros</Button>
      </div>
    </form>
  )
}
