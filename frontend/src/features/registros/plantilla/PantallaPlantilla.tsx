import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Building2, Shield, UserRoundPlus, UsersRound } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { useClubesActivosTodos, useEquipos } from '@/features/organizacion/api'
import { TAMANO_PLANTILLA, useRegistros } from '@/features/registros/api'
import { agruparEquipos } from '@/features/registros/asistenteAlta'
import type { FiltroEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import { PERMISOS_PLANTILLA, usePermisosRegistros } from '@/features/registros/permisos'
import { FILTROS_PLANTILLA, ListaPlantilla } from '@/features/registros/plantilla/ListaPlantilla'
import { SelectoresPlantilla } from '@/features/registros/plantilla/SelectoresPlantilla'
import { RUTA_ALTA_JUGADOR } from '@/features/registros/rutas'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { esSinPermiso } from '@/lib/api/errores'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

const formatoEntero = new Intl.NumberFormat('es-MX')

/**
 * Plantilla del equipo (F-17, H-09.4). Exige consultar registros, equipos y clubes a la vez (DEC-116):
 * sin los tres, la 403 sin pedir datos. El backend filtra el ámbito de cada consulta (ADR-0007).
 */
export function PantallaPlantilla() {
  return (
    <ConPermiso
      permiso={PERMISOS_PLANTILLA}
      todos
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <PaginaPlantilla />
    </ConPermiso>
  )
}

function filtroDe(valor: string | null): FiltroEstadoRegistro {
  return FILTROS_PLANTILLA.find((filtro) => filtro.valor === valor)?.valor ?? 'TODOS'
}

function textoJugadores(total: number): string {
  return total === 1 ? '1 jugador' : `${formatoEntero.format(total)} jugadores`
}

/**
 * La selección vive en `?club=&equipo=&estado=` para conservarla al volver (DEC-116). Un club o un
 * equipo de la URL que no está en las opciones cuenta como «sin elegir» y no se consultan registros.
 *
 * Refs: RF-017, RF-019 (consulta), UI-006, DEC-108, DEC-109, DEC-111, DEC-116, DEC-117
 */
function PaginaPlantilla() {
  const [parametros, setParametros] = useSearchParams()
  const [pagina, setPagina] = useState(0)
  const { registrar, cargar, validarRegistro } = usePermisosRegistros()

  const clubes = useClubesActivosTodos()
  const listaClubes = useMemo(() => clubes.data ?? [], [clubes.data])
  const clubParametro = parametros.get('club') ?? ''
  const clubId = listaClubes.some(({ id }) => id === clubParametro) ? clubParametro : ''

  const equipos = useEquipos({ clubId, estado: 'ACTIVO' }, { habilitada: Boolean(clubId) })
  // Mientras llegan los equipos de otro club, los anteriores no cuentan como opciones.
  const equiposVigentes = clubId && !equipos.isPlaceholderData ? equipos.data?.contenido : undefined
  const grupos = useMemo(() => agruparEquipos(equiposVigentes ?? []), [equiposVigentes])
  const opcionesEquipo = useMemo(() => grupos.flatMap((grupo) => grupo.equipos), [grupos])
  const equipoParametro = parametros.get('equipo') ?? ''
  const equipo = opcionesEquipo.find(({ id }) => id === equipoParametro)
  const filtro = filtroDe(parametros.get('estado'))

  const lista = useRegistros(
    {
      equipoId: equipo?.id,
      estado: filtro === 'TODOS' ? undefined : filtro,
      orden: 'nombre,asc',
      pagina,
      tamano: TAMANO_PLANTILLA,
    },
    { habilitada: Boolean(equipo) },
  )

  // Con un solo club o un solo equipo se elige solo, sin dejar una entrada extra en el historial.
  const unicoClub = listaClubes.length === 1 ? listaClubes[0].id : undefined
  const unicoEquipo = opcionesEquipo.length === 1 ? opcionesEquipo[0].id : undefined
  useEffect(() => {
    if (unicoClub && !clubId) {
      setParametros({ club: unicoClub }, { replace: true })
    } else if (clubId && unicoEquipo && !equipo) {
      setParametros({ club: clubId, equipo: unicoEquipo }, { replace: true })
    }
  }, [unicoClub, unicoEquipo, clubId, equipo, setParametros])

  if (esSinPermiso(lista.error) || esSinPermiso(clubes.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const elegirClub = (nuevo: string) => {
    setPagina(0)
    // Cambiar de club borra el equipo y la pestaña (DEC-116).
    setParametros(nuevo ? { club: nuevo } : {})
  }

  const elegirEquipo = (nuevo: string) => {
    setPagina(0)
    setParametros(nuevo ? { club: clubId, equipo: nuevo } : { club: clubId })
  }

  const elegirFiltro = (nuevo: FiltroEstadoRegistro) => {
    setPagina(0)
    setParametros((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (nuevo === 'TODOS') {
        siguientes.delete('estado')
      } else {
        siguientes.set('estado', nuevo)
      }
      return siguientes
    })
  }

  const botonRegistrar = registrar ? (
    <Link to={RUTA_ALTA_JUGADOR} className={clasesBoton('primario')}>
      <UserRoundPlus aria-hidden="true" className="size-4 shrink-0" />
      Registrar jugador
    </Link>
  ) : undefined

  const contenido = () => {
    if (clubes.isError) {
      return <ErrorConsulta error={clubes.error} onReintentar={() => void clubes.refetch()} />
    }
    if (clubes.isPending) {
      return <EstadoCarga etiqueta="Cargando clubes…" filas={3} columnas={3} />
    }
    if (listaClubes.length === 0) {
      return (
        <EstadoVacio
          icono={Building2}
          titulo="No hay clubes disponibles"
          descripcion="Cuando tengas acceso a un club activo, aquí podrás consultar la plantilla de sus equipos."
        />
      )
    }
    if (clubId && equipos.isError) {
      return <ErrorConsulta error={equipos.error} onReintentar={() => void equipos.refetch()} />
    }
    if (clubId && equiposVigentes === undefined) {
      return <EstadoCarga etiqueta="Cargando equipos…" filas={3} columnas={3} />
    }
    if (clubId && opcionesEquipo.length === 0) {
      return (
        <EstadoVacio
          icono={Shield}
          titulo="Este club no tiene equipos en una temporada abierta"
          descripcion="Elige otro club o revisa sus equipos en Clubes."
        />
      )
    }
    if (!equipo) {
      return (
        <EstadoVacio
          icono={UsersRound}
          titulo="Elige un club y un equipo para ver su plantilla"
          descripcion="La plantilla muestra a los jugadores del equipo en su temporada, con su estado y elegibilidad."
        />
      )
    }

    return (
      <ListaPlantilla
        equipo={equipo.nombre}
        filtro={filtro}
        pagina={pagina}
        datos={lista.data}
        cargando={lista.isFetching}
        actualizando={lista.isPlaceholderData}
        error={lista.error}
        permisos={{ cargar, validar: validarRegistro }}
        accionRegistrar={botonRegistrar}
        onFiltro={elegirFiltro}
        onPagina={setPagina}
        onReintentar={() => void lista.refetch()}
      />
    )
  }

  const total = lista.data && !lista.isPlaceholderData ? lista.data.totalElementos : undefined
  const pestana = FILTROS_PLANTILLA.find(({ valor }) => valor === filtro)?.texto

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-display-lg-mobile text-text-main sm:text-display-lg">Jugadores</h1>
          <p className="mt-1 text-body-md text-text-muted">Plantilla del equipo por temporada.</p>
        </div>
        {botonRegistrar && <div className="shrink-0">{botonRegistrar}</div>}
      </header>

      <section
        aria-label="Elegir equipo"
        className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-6"
      >
        <SelectoresPlantilla
          clubes={listaClubes}
          clubesCargando={clubes.isPending}
          clubId={clubId}
          grupos={grupos}
          equiposCargando={Boolean(clubId) && equiposVigentes === undefined}
          equipoId={equipo?.id ?? ''}
          onClub={elegirClub}
          onEquipo={elegirEquipo}
        />

        {equipo && (
          <div className="flex flex-col gap-1 border-t border-border pt-4 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
            <p className="text-headline-sm text-text-main">
              {equipo.nombre} · {equipo.categoria.nombre} · {equipo.temporada.nombre}
            </p>
            {total !== undefined && (
              <p className="text-body-sm text-text-muted" aria-live="polite">
                <span className="font-medium tabular-nums text-text-main">
                  {textoJugadores(total)}
                </span>
                {filtro !== 'TODOS' && ` en «${pestana}»`}
              </p>
            )}
          </div>
        )}
      </section>

      {contenido()}
    </div>
  )
}
