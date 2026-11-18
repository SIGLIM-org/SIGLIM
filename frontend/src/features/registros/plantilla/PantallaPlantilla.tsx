import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Building2, CircleCheck, Shield, UserRoundPlus, UsersRound } from 'lucide-react'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { clasesBoton } from '@/components/ui/estilosBoton'
import { useClubesActivosTodos, useEquipos } from '@/features/organizacion/api'
import { TAMANO_PLANTILLA, useRecargarRegistro, useRegistros } from '@/features/registros/api'
import { agruparEquipos } from '@/features/registros/asistenteAlta'
import type { FiltroEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import { PERMISOS_PLANTILLA, usePermisosRegistros } from '@/features/registros/permisos'
import { FILTROS_PLANTILLA, ListaPlantilla } from '@/features/registros/plantilla/ListaPlantilla'
import { ModalBajaRegistro } from '@/features/registros/plantilla/ModalBajaRegistro'
import { ModalHistorialRegistro } from '@/features/registros/plantilla/ModalHistorialRegistro'
import { SelectoresPlantilla } from '@/features/registros/plantilla/SelectoresPlantilla'
import { RUTA_ALTA_JUGADOR } from '@/features/registros/rutas'
import type { RegistroJugador, RegistroJugadorFila } from '@/features/registros/tipos'
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

/** Resultado de la baja que se muestra arriba de la lista (DEC-127, DEC-129). */
type Aviso = { tipo: 'exito' | 'error'; mensaje: string }

/**
 * La selección vive en `?club=&equipo=&estado=` para conservarla al volver (DEC-116). Un club o un
 * equipo de la URL que no está en las opciones cuenta como «sin elegir» y no se consultan registros.
 * La baja de un registro abre un modal desde la fila (DEC-125, DEC-126); su resultado se anuncia
 * arriba de la lista y recibe el foco, porque el botón que lo abrió desaparece (DEC-127, DEC-129).
 * «Ver historial» abre en un modal el historial de estados del registro (B-07.2, RF-018).
 *
 * Refs: B-07, RF-014, RF-017, RF-018, RF-019 (consulta), UI-006, DEC-108, DEC-109, DEC-111, DEC-116, DEC-117,
 * DEC-125 a DEC-129
 */
function PaginaPlantilla() {
  const [parametros, setParametros] = useSearchParams()
  const [pagina, setPagina] = useState(0)
  const [registroEnBaja, setRegistroEnBaja] = useState<RegistroJugadorFila | null>(null)
  const [registroEnHistorial, setRegistroEnHistorial] = useState<RegistroJugadorFila | null>(null)
  const [aviso, setAviso] = useState<Aviso | null>(null)
  const avisoRef = useRef<HTMLParagraphElement>(null)
  const recargar = useRecargarRegistro()
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

  // El aviso de la baja recibe el foco: el botón que abrió el modal ya no está (DEC-127).
  useEffect(() => {
    if (aviso) {
      avisoRef.current?.focus()
    }
  }, [aviso])

  // Si la baja vació la última página, se regresa a la anterior (DEC-127).
  const totalPaginas = lista.data && !lista.isPlaceholderData ? lista.data.totalPaginas : undefined
  useEffect(() => {
    if (totalPaginas !== undefined && pagina > 0 && pagina >= totalPaginas) {
      setPagina(pagina - 1)
    }
  }, [pagina, totalPaginas])

  if (esSinPermiso(lista.error) || esSinPermiso(clubes.error)) {
    return <PaginaSinPermiso rutaInicio="/app" />
  }

  const elegirClub = (nuevo: string) => {
    setPagina(0)
    setAviso(null)
    // Cambiar de club borra el equipo y la pestaña (DEC-116).
    setParametros(nuevo ? { club: nuevo } : {})
  }

  const elegirEquipo = (nuevo: string) => {
    setPagina(0)
    setAviso(null)
    setParametros(nuevo ? { club: clubId, equipo: nuevo } : { club: clubId })
  }

  const elegirFiltro = (nuevo: FiltroEstadoRegistro) => {
    setPagina(0)
    setAviso(null)
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

  const abrirBaja = (registro: RegistroJugadorFila) => {
    setAviso(null)
    setRegistroEnBaja(registro)
  }

  const alDarDeBaja = (registro: RegistroJugador) => {
    setRegistroEnBaja(null)
    setAviso({
      tipo: 'exito',
      mensaje: `${registro.persona.nombreCompleto} quedó dado de baja. Su historial se conserva.`,
    })
  }

  const alFallarBaja = (mensaje: string, recargarLista: boolean) => {
    if (recargarLista && registroEnBaja) {
      void recargar.todo(registroEnBaja.id)
    }
    setRegistroEnBaja(null)
    setAviso({ tipo: 'error', mensaje })
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
        permisos={{ cargar, validar: validarRegistro, darBaja: registrar }}
        accionRegistrar={botonRegistrar}
        onFiltro={elegirFiltro}
        onPagina={setPagina}
        onReintentar={() => void lista.refetch()}
        onDarDeBaja={abrirBaja}
        onVerHistorial={setRegistroEnHistorial}
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

      {aviso?.tipo === 'exito' && (
        <p
          ref={avisoRef}
          role="status"
          tabIndex={-1}
          className="flex items-center gap-2 text-label-md text-success-strong focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
          {aviso.mensaje}
        </p>
      )}
      {aviso?.tipo === 'error' && (
        <p
          ref={avisoRef}
          role="alert"
          tabIndex={-1}
          className="rounded-md bg-error/8 p-3 text-body-md text-error-strong focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          {aviso.mensaje}
        </p>
      )}

      {contenido()}

      {registroEnHistorial && (
        <ModalHistorialRegistro
          key={registroEnHistorial.id}
          registro={registroEnHistorial}
          onCerrar={() => setRegistroEnHistorial(null)}
        />
      )}
      {registroEnBaja && (
        <ModalBajaRegistro
          registro={registroEnBaja}
          onCerrar={() => setRegistroEnBaja(null)}
          onDadoDeBaja={alDarDeBaja}
          onErrorPagina={alFallarBaja}
        />
      )}
    </div>
  )
}
