import { useId, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import type {
  Ambito,
  ClubResumen,
  FiltrosUsuarios,
  RolCatalogo,
} from '@/features/usuarios/tipos'

type Props = {
  aplicados: FiltrosUsuarios
  roles: readonly RolCatalogo[]
  clubes: readonly ClubResumen[]
  onAplicar: (filtros: FiltrosUsuarios) => void
}

type Borrador = {
  q: string
  rolId: string
  ambito: string
  clubId: string
  activo: string
}

function borradorDe(filtros: FiltrosUsuarios): Borrador {
  return {
    q: filtros.q ?? '',
    rolId: filtros.rolId ?? '',
    ambito: filtros.ambito ?? '',
    clubId: filtros.clubId ?? '',
    activo: filtros.activo === undefined ? '' : String(filtros.activo),
  }
}

const VACIO: Borrador = { q: '', rolId: '', ambito: '', clubId: '', activo: '' }

/**
 * Filtros de F-12 (RF-094): rol, ámbito, club, estado y búsqueda por nombre o correo. Se edita un
 * borrador y solo se pide a la API al pulsar «Aplicar filtros», igual que la auditoría.
 */
export function FormularioFiltrosUsuarios({
  aplicados,
  roles,
  clubes,
  onAplicar,
}: Props) {
  const id = useId()
  const [borrador, setBorrador] = useState(() => borradorDe(aplicados))
  const [previos, setPrevios] = useState(aplicados)

  if (JSON.stringify(aplicados) !== JSON.stringify(previos)) {
    setPrevios(aplicados)
    setBorrador(borradorDe(aplicados))
  }

  const cambiar = (cambios: Partial<Borrador>) => {
    setBorrador((actual) => ({ ...actual, ...cambios }))
  }

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault()

    onAplicar({
      q: borrador.q.trim() || undefined,
      rolId: borrador.rolId || undefined,
      ambito: borrador.ambito ? (borrador.ambito as Ambito) : undefined,
      clubId: borrador.clubId || undefined,
      activo: borrador.activo === '' ? undefined : borrador.activo === 'true',
      pagina: 0,
      tamano: aplicados.tamano,
    })
  }

  const limpiar = () => {
    setBorrador(VACIO)
    onAplicar({ pagina: 0, tamano: aplicados.tamano })
  }

  const clubDeshabilitado = borrador.ambito !== '' && borrador.ambito !== 'CLUB'

  return (
    <form
      noValidate
      aria-label="Filtros de usuarios"
      onSubmit={aplicar}
      className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="grid items-start gap-3 sm:grid-cols-2 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,0.8fr)]">
        <CampoTexto
          id={`${id}-q`}
          etiqueta="Buscar"
          type="search"
          placeholder="Nombre o correo"
          value={borrador.q}
          onChange={(evento) => cambiar({ q: evento.target.value })}
        />
        <CampoSeleccion
          id={`${id}-rol`}
          etiqueta="Rol"
          value={borrador.rolId}
          onChange={(evento) => cambiar({ rolId: evento.target.value })}
        >
          <option value="">Todos los roles</option>
          {roles.map((rol) => (
            <option key={rol.id} value={rol.id}>
              {rol.nombre}
            </option>
          ))}
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-ambito`}
          etiqueta="Ámbito"
          value={borrador.ambito}
          onChange={(evento) => {
            const ambito = evento.target.value
            cambiar({ ambito, clubId: ambito === 'CLUB' ? borrador.clubId : '' })
          }}
        >
          <option value="">Todos los ámbitos</option>
          <option value="LIGA">Liga</option>
          <option value="CLUB">Club</option>
          <option value="PROPIO">Propio</option>
        </CampoSeleccion>
        <CampoSeleccion
          id={`${id}-club`}
          etiqueta="Club"
          value={borrador.clubId}
          disabled={clubDeshabilitado}
          ayuda={
            clubDeshabilitado
              ? 'Solo aplica cuando el ámbito es Club.'
              : undefined
          }
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
          id={`${id}-activo`}
          etiqueta="Estado"
          value={borrador.activo}
          onChange={(evento) => cambiar({ activo: evento.target.value })}
        >
          <option value="">Todos</option>
          <option value="true">Activo</option>
          <option value="false">Inactivo</option>
        </CampoSeleccion>
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
