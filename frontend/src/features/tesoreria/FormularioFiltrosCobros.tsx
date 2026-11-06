import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { FILTROS_COBROS_INICIALES, TAMANO_COBROS } from '@/features/tesoreria/filtros'
import { etiquetaTipoCobro } from '@/features/tesoreria/etiquetas'
import type {
  ClubResumen,
  EstadoCobro,
  FiltrosCobros,
  TipoCobro,
} from '@/features/tesoreria/tipos'

type Props = {
  aplicados: FiltrosCobros
  clubes: readonly ClubResumen[]
  onAplicar: (filtros: FiltrosCobros) => void
}

type Borrador = {
  clubId: string
  tipo: TipoCobro | ''
  estado: EstadoCobro | ''
  desde: string
  hasta: string
}

function borradorDe(filtros: FiltrosCobros): Borrador {
  return {
    clubId: filtros.clubId ?? '',
    tipo: filtros.tipo ?? '',
    estado: filtros.estado ?? '',
    desde: filtros.desde ?? '',
    hasta: filtros.hasta ?? '',
  }
}

const VACIO: Borrador = {
  clubId: '',
  tipo: '',
  estado: '',
  desde: '',
  hasta: '',
}

/**
 * Filtros de F-33 (H-29.2, RF-137, RF-140): club, tipo, estado y rango de fecha límite. Se edita un
 * borrador y solo se pide a la API al pulsar «Aplicar filtros», como en usuarios y auditoría.
 */
export function FormularioFiltrosCobros({ aplicados, clubes, onAplicar }: Props) {
  const id = useId()
  const [borrador, setBorrador] = useState(() => borradorDe(aplicados))
  const [previos, setPrevios] = useState(aplicados)
  const [errorRango, setErrorRango] = useState<string>()

  if (JSON.stringify(aplicados) !== JSON.stringify(previos)) {
    setPrevios(aplicados)
    setBorrador(borradorDe(aplicados))
    setErrorRango(undefined)
  }

  const cambiar = (cambios: Partial<Borrador>) => {
    setBorrador((actual) => ({ ...actual, ...cambios }))
  }

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault()

    if (borrador.desde && borrador.hasta && borrador.desde > borrador.hasta) {
      setErrorRango('«Hasta» no puede ser anterior a «Desde». Corrige una de las dos fechas.')
      return
    }

    setErrorRango(undefined)
    onAplicar({
      clubId: borrador.clubId || undefined,
      tipo: borrador.tipo || undefined,
      estado: borrador.estado || undefined,
      desde: borrador.desde || undefined,
      hasta: borrador.hasta || undefined,
      pagina: 0,
      tamano: aplicados.tamano,
    })
  }

  const limpiar = () => {
    setBorrador(VACIO)
    setErrorRango(undefined)
    onAplicar({ ...FILTROS_COBROS_INICIALES, tamano: aplicados.tamano || TAMANO_COBROS })
  }

  return (
    <form
      noValidate
      aria-label="Filtros de cobros"
      onSubmit={aplicar}
      className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <CampoSeleccion
          id={`${id}-club`}
          etiqueta="Club"
          value={borrador.clubId}
          onChange={(evento) => cambiar({ clubId: evento.target.value })}
        >
          <option value="">Todos los clubes</option>
          {clubes.map((club) => (
            <option key={club.id} value={club.id}>
              {club.nombre}
            </option>
          ))}
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-tipo`}
          etiqueta="Tipo"
          value={borrador.tipo}
          onChange={(evento) => cambiar({ tipo: evento.target.value as TipoCobro | '' })}
        >
          <option value="">Todos los tipos</option>
          {Object.entries(etiquetaTipoCobro).map(([valor, etiqueta]) => (
            <option key={valor} value={valor}>
              {etiqueta}
            </option>
          ))}
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-estado`}
          etiqueta="Estado"
          value={borrador.estado}
          onChange={(evento) => cambiar({ estado: evento.target.value as EstadoCobro | '' })}
        >
          <option value="">Todos los estados</option>
          <option value="PENDIENTE">Pendiente</option>
          <option value="PAGADO">Pagado</option>
          <option value="RECHAZADO">Rechazado</option>
          <option value="VENCIDO">Vencido</option>
        </CampoSeleccion>
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
      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-body-sm text-text-muted">
          Las fechas filtran por fecha límite; el último día se incluye.
        </p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row">
          <Button variante="secundario" onClick={limpiar}>
            Limpiar
          </Button>
          <Button type="submit">Aplicar filtros</Button>
        </div>
      </div>
    </form>
  )
}
