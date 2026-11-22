import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CalendarClock, CheckCircle2, Lock, Plus, Save, TriangleAlert } from 'lucide-react'
import { AlertaBloqueo } from '@/components/ui/AlertaBloqueo'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { ErrorConsulta } from '@/components/ui/ErrorConsulta'
import { EstadoCarga } from '@/components/ui/EstadoCarga'
import { EstadoVacio } from '@/components/ui/EstadoVacio'
import { Insignia } from '@/components/ui/Insignia'
import { useHorariosClub, useReemplazarHorariosClub } from '@/features/canchas/api'
import { FilaHorarioClub } from '@/features/canchas/FilaHorarioClub'
import {
  erroresDeServidor,
  esDiaSemana,
  filaDeHorario,
  filaVacia,
  mismasFilas,
  peticionDeFilas,
  validarFilas,
  type CampoHorario,
  type ErroresFilas,
  type FilaHorario,
} from '@/features/canchas/horariosClub'
import { usePermisosHorarios } from '@/features/canchas/permisosHorarios'
import type { Cancha } from '@/features/canchas/tipos'
import { useClubesActivosTodos } from '@/features/organizacion/api'
import { detalleDeFallo } from '@/features/organizacion/erroresFormulario'
import { clavesTemporadas, useCategoriasTemporada, useTemporadas } from '@/features/temporadas/api'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { ApiError } from '@/lib/api/ApiError'

type Props = { cancha: Cancha }

/** Fallo del `PUT` que no se lleva a una fila: texto según el `codigo`, nunca el código (PEN-016). */
type ErrorGeneral =
  | { tipo: 'bloqueo'; detalle: string }
  | { tipo: 'mensaje'; mensaje: string; detalle?: string }

/** Las temporadas vienen por `fechaInicio` descendente; 100 cubren de sobra a la liga. */
const TAMANO_TEMPORADAS = 100

const CLASE_ENCABEZADO = 'px-3 py-3 text-left text-label-sm uppercase text-text-muted'

function errorDeGuardado(error: unknown): ErrorGeneral {
  if (!(error instanceof ApiError)) {
    return {
      tipo: 'mensaje',
      mensaje: detalleDeFallo(error, 'No se pudieron guardar los horarios. Revisa tu conexión e inténtalo de nuevo.'),
    }
  }

  const { status, codigo, detail } = error.problema

  if (status === 422 && codigo === 'RF-119') {
    return { tipo: 'bloqueo', detalle: detail }
  }
  if (status === 422 && codigo === 'REFERENCIA_INVALIDA') {
    return {
      tipo: 'mensaje',
      mensaje: 'Algunos datos ya no son válidos (temporada, categoría o club). Actualiza la página y vuelve a intentarlo.',
      detalle: detail,
    }
  }
  if (status === 409 && codigo === 'RF-003') {
    return { tipo: 'mensaje', mensaje: 'La temporada se cerró: sus horarios ya no se pueden modificar.' }
  }
  if (status === 403) {
    return { tipo: 'mensaje', mensaje: 'No tienes permiso para modificar los horarios de esta cancha.' }
  }
  if (status === 404) {
    return { tipo: 'mensaje', mensaje: 'La cancha ya no existe. Vuelve al catálogo.' }
  }

  return {
    tipo: 'mensaje',
    mensaje: detalleDeFallo(error, 'No se pudieron guardar los horarios. Revisa tu conexión e inténtalo de nuevo.'),
  }
}

/**
 * «Horarios por categoría» del detalle de la cancha (F-21): días y horas en que cada categoría usa
 * una cancha de club o, en una de la liga, el comodato a cada club, por temporada. Se edita la
 * lista completa y se guarda con un solo `PUT` que reemplaza la temporada (convenciones §6.6.1).
 * La validación previa aplica la regla del servidor: mismo día, cualquier categoría, intervalos
 * semiabiertos. Una temporada cerrada es de solo lectura (RF-003).
 *
 * Quien la monta decide si se ve con `usePermisosHorarios(...).ver` (nunca en una cancha EXTERNO).
 *
 * Refs: B-13.2, RF-119, DEC-33, RF-003.
 */
export function SeccionHorariosClub({ cancha }: Props) {
  const idBase = useId()
  const idTitulo = `${idBase}-titulo`
  const permisos = usePermisosHorarios(cancha.propietario)
  const clienteConsultas = useQueryClient()

  const temporadas = useTemporadas(0, TAMANO_TEMPORADAS)
  const [temporadaElegida, setTemporadaElegida] = useState<string | null>(null)
  const listaTemporadas = useMemo(() => temporadas.data?.contenido ?? [], [temporadas.data])
  const temporada =
    listaTemporadas.find(({ id }) => id === temporadaElegida) ??
    listaTemporadas.find(({ estado }) => estado === 'ACTIVA') ??
    listaTemporadas[0]
  const temporadaId = temporada?.id

  const horarios = useHorariosClub(cancha.id, temporadaId)
  const guardar = useReemplazarHorariosClub(cancha.id)

  const cerrada = temporada?.estado === 'CERRADA'
  const editable = permisos.editar && Boolean(temporada) && !cerrada
  const esLiga = cancha.propietario === 'LIGA'

  const [borrador, setBorrador] = useState<FilaHorario[] | null>(null)
  const [errores, setErrores] = useState<ErroresFilas>({})
  const [errorGeneral, setErrorGeneral] = useState<ErrorGeneral | null>(null)
  const [guardado, setGuardado] = useState(false)
  const contador = useRef(0)

  const base = useMemo(() => (horarios.data ?? []).map(filaDeHorario), [horarios.data])
  const filas = editable && borrador ? borrador : base
  const hayCambios = editable && borrador !== null && !mismasFilas(borrador, base)

  const categorias = useCategoriasTemporada(temporadaId ?? '', { habilitada: editable })
  // Clubes activos del comodato, de todas las páginas; uno ya asignado que no esté se conserva.
  const clubes = useClubesActivosTodos({ habilitada: editable && esLiga })

  const opcionesCategorias = useMemo(
    () => (categorias.data ?? []).map(({ categoria }) => ({ id: categoria.id, nombre: categoria.nombre })),
    [categorias.data],
  )
  const opcionesClubes = useMemo(
    () => (clubes.data ?? []).map(({ id, nombre }) => ({ id, nombre })),
    [clubes.data],
  )
  const avisoCategorias = categorias.isPending
    ? 'Cargando las categorías de la temporada…'
    : categorias.isError
      ? 'No se pudieron cargar las categorías de la temporada. Actualiza la página.'
      : opcionesCategorias.length === 0
        ? 'La temporada no tiene categorías asignadas: asígnalas en Temporadas.'
        : undefined
  const avisoClubes = clubes.isPending
    ? 'Cargando los clubes…'
    : clubes.isError
      ? 'No se pudieron cargar los clubes. Actualiza la página.'
      : undefined

  useEffect(() => {
    if (!hayCambios) {
      return
    }

    const avisar = (evento: BeforeUnloadEvent) => {
      evento.preventDefault()
    }

    window.addEventListener('beforeunload', avisar)
    return () => window.removeEventListener('beforeunload', avisar)
  }, [hayCambios])

  const limpiarAvisos = () => {
    setErrorGeneral(null)
    setGuardado(false)
  }

  const editar = (cambiar: (actuales: FilaHorario[]) => FilaHorario[]) => {
    setBorrador((actuales) => cambiar(actuales ?? base))
    limpiarAvisos()
  }

  const cambiarCampo = (clave: string, campo: CampoHorario, valor: string) => {
    editar((actuales) =>
      actuales.map((fila) => {
        if (fila.clave !== clave) {
          return fila
        }
        if (campo === 'diaSemana') {
          const dia = Number(valor)
          return { ...fila, diaSemana: esDiaSemana(dia) ? dia : '' }
        }
        if (campo === 'categoriaId') {
          const nombre = opcionesCategorias.find(({ id }) => id === valor)?.nombre ?? ''
          return { ...fila, categoriaId: valor, categoriaNombre: nombre }
        }
        if (campo === 'clubId') {
          const nombre = opcionesClubes.find(({ id }) => id === valor)?.nombre ?? ''
          return { ...fila, clubId: valor, clubNombre: nombre }
        }
        return { ...fila, [campo]: valor }
      }),
    )
    // Un cambio de horario puede resolver el traslape de las dos filas: se vuelven a revisar al guardar.
    setErrores((actuales) =>
      Object.fromEntries(
        Object.entries(actuales).map(([claveFila, propios]) => [
          claveFila,
          { ...propios, fila: undefined, ...(claveFila === clave ? { [campo]: undefined } : {}) },
        ]),
      ),
    )
  }

  const agregar = () => {
    contador.current += 1
    const clave = `nuevo-${contador.current}`
    editar((actuales) => [...actuales, filaVacia(clave)])
    requestAnimationFrame(() => document.getElementById(`${idBase}-${clave}-categoriaId`)?.focus())
  }

  const quitar = (clave: string) => {
    editar((actuales) => actuales.filter((fila) => fila.clave !== clave))
    setErrores((actuales) =>
      Object.fromEntries(
        Object.entries(actuales)
          .filter(([claveFila]) => claveFila !== clave)
          .map(([claveFila, propios]) => [claveFila, { ...propios, fila: undefined }]),
      ),
    )
  }

  const descartar = () => {
    setBorrador(null)
    setErrores({})
    limpiarAvisos()
  }

  const enviar = () => {
    if (!temporadaId) {
      return
    }

    limpiarAvisos()
    const encontrados = validarFilas(filas, cancha.propietario)
    setErrores(encontrados)

    const primera = filas.find(({ clave }) => encontrados[clave])
    if (primera) {
      const campo = (['categoriaId', 'diaSemana', 'horaInicio', 'horaFin', 'clubId'] as const).find(
        (nombre) => encontrados[primera.clave]?.[nombre],
      )
      document.getElementById(`${idBase}-${primera.clave}-${campo ?? 'horaInicio'}`)?.focus()
      return
    }

    guardar.mutate(
      { temporadaId, horarios: peticionDeFilas(filas, cancha.propietario) },
      {
        onSuccess: () => {
          setBorrador(null)
          setErrores({})
          setGuardado(true)
        },
        onError: (error) => {
          if (error instanceof ApiError && error.problema.status === 400 && error.errores.length > 0) {
            const { porFila, sinMapear } = erroresDeServidor(error.errores, filas)
            setErrores(porFila)
            if (sinMapear.length > 0) {
              setErrorGeneral({ tipo: 'mensaje', mensaje: sinMapear.join(' ') })
            }
            return
          }
          if (error instanceof ApiError && error.problema.status === 409 && error.problema.codigo === 'RF-003') {
            // Se fija la temporada antes de refrescar: si era la ACTIVA por omisión, al cerrarse la
            // elección por omisión saltaría a otra temporada con el aviso de «se cerró» encima.
            setTemporadaElegida(temporadaId)
            setBorrador(null)
            setErrores({})
            void clienteConsultas.invalidateQueries({ queryKey: clavesTemporadas.todas })
          }
          setErrorGeneral(errorDeGuardado(error))
        },
      },
    )
  }

  const cambiarTemporada = (id: string) => {
    setTemporadaElegida(id)
    setBorrador(null)
    setErrores({})
    limpiarAvisos()
  }

  const conErrores = Object.values(errores).some((propios) => Object.values(propios).some(Boolean))
  const descripcion = esLiga
    ? 'Días y horas en que la liga presta esta cancha en comodato a cada club, por categoría.'
    : `Días y horas en que las categorías de ${cancha.club?.nombre ?? 'el club'} usan esta cancha.`

  return (
    <section aria-labelledby={idTitulo} className="flex min-w-0 flex-col gap-4 lg:col-span-2">
      <div className="flex flex-col gap-1">
        <h3 id={idTitulo} className="text-headline-md text-text-main">
          Horarios por categoría
        </h3>
        <p className="max-w-prose text-body-md text-text-muted">{descripcion}</p>
      </div>

      {temporadas.isPending && <EstadoCarga etiqueta="Cargando las temporadas…" filas={2} columnas={3} />}
      {temporadas.isError && (
        <ErrorConsulta error={temporadas.error} onReintentar={() => void temporadas.refetch()} />
      )}
      {temporadas.isSuccess && !temporada && (
        <EstadoVacio
          icono={CalendarClock}
          titulo="Aún no hay temporadas"
          descripcion="Los horarios se asignan por temporada. Registra una temporada y asígnale categorías en Temporadas; después vuelve aquí."
        />
      )}

      {temporada && (
        <>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
            <div className="sm:w-72">
              <CampoSeleccion
                id={`${idBase}-temporada`}
                etiqueta="Temporada"
                value={temporada.id}
                disabled={hayCambios || guardar.isPending}
                ayuda={hayCambios ? 'Guarda o descarta los cambios para cambiar de temporada.' : undefined}
                onChange={(evento) => cambiarTemporada(evento.target.value)}
              >
                {listaTemporadas.map(({ id, nombre }) => (
                  <option key={id} value={id}>
                    {nombre}
                  </option>
                ))}
              </CampoSeleccion>
            </div>
            <div className="sm:mt-9">
              <Insignia
                variante={etiquetaEstadoTemporada[temporada.estado].variante}
                icono={etiquetaEstadoTemporada[temporada.estado].icono}
              >
                {etiquetaEstadoTemporada[temporada.estado].texto}
              </Insignia>
            </div>
          </div>

          {cerrada && (
            <p className="flex items-start gap-2 text-body-md text-text-muted">
              <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              Temporada cerrada: sus horarios son de solo lectura.
            </p>
          )}

          {horarios.isPending && <EstadoCarga etiqueta="Cargando los horarios…" filas={3} columnas={5} />}
          {horarios.isError && (
            <ErrorConsulta error={horarios.error} onReintentar={() => void horarios.refetch()} />
          )}

          {horarios.isSuccess && (
            <>
              {filas.length === 0 ? (
                <EstadoVacio
                  icono={CalendarClock}
                  titulo={hayCambios ? 'Quitaste todos los horarios' : 'Sin horarios en esta temporada'}
                  descripcion={
                    hayCambios
                      ? 'Guarda para dejar la temporada sin horarios o descarta los cambios.'
                      : editable
                        ? 'Agrega el día y las horas en que cada categoría usa la cancha.'
                        : undefined
                  }
                />
              ) : (
                <div className="overflow-x-auto rounded-lg border border-border bg-surface">
                  <table role="table" aria-labelledby={idTitulo} className="block w-full lg:table">
                    <thead role="rowgroup" className="hidden border-b-2 border-border bg-background lg:table-header-group">
                      <tr role="row">
                        <th role="columnheader" scope="col" className={`${CLASE_ENCABEZADO} w-12`}>
                          N.º
                        </th>
                        <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                          Categoría
                        </th>
                        <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                          Día
                        </th>
                        <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                          Inicio
                        </th>
                        <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                          Fin
                        </th>
                        <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                          Club
                        </th>
                        {editable && (
                          <th role="columnheader" scope="col" className={CLASE_ENCABEZADO}>
                            <span className="sr-only">Acciones</span>
                          </th>
                        )}
                      </tr>
                    </thead>
                    <tbody role="rowgroup" className="block lg:table-row-group">
                      {filas.map((fila, posicion) => (
                        <FilaHorarioClub
                          key={fila.clave}
                          fila={fila}
                          posicion={posicion}
                          idBase={idBase}
                          propietario={cancha.propietario}
                          clubPropietario={cancha.club?.nombre ?? 'Club propietario'}
                          editable={editable}
                          ocupado={guardar.isPending}
                          errores={errores[fila.clave]}
                          categorias={opcionesCategorias}
                          avisoCategorias={avisoCategorias}
                          clubes={opcionesClubes}
                          avisoClubes={avisoClubes}
                          onCambiar={(campo, valor) => cambiarCampo(fila.clave, campo, valor)}
                          onQuitar={() => quitar(fila.clave)}
                        />
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/*
                [PROPUESTA] Desviación consciente de frontend/AGENTS.md (línea 65, errores con
                role="alert"): cada error de campo va enlazado a su control con aria-describedby y
                aria-invalid, sin role="alert", para que el lector de pantalla no anuncie varias
                alertas a la vez; este resumen es el único que se anuncia.
              */}
              {conErrores && (
                <p role="alert" className="flex items-start gap-2 text-body-md text-error-strong">
                  <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  Corrige los horarios marcados; no se guardó ningún cambio.
                </p>
              )}

              {errorGeneral?.tipo === 'bloqueo' && (
                <AlertaBloqueo
                  causa="No se pudieron guardar los horarios"
                  conflicto={errorGeneral.detalle}
                  accion="Ajusta el día o las horas para que no choquen y vuelve a guardar."
                />
              )}
              {errorGeneral?.tipo === 'mensaje' && (
                <div role="alert" className="flex flex-col gap-1 rounded-md bg-error/8 p-3 text-body-md text-error-strong">
                  <p>{errorGeneral.mensaje}</p>
                  {errorGeneral.detalle && <p className="text-text-main">{errorGeneral.detalle}</p>}
                </div>
              )}

              {guardado && (
                <p role="status" className="flex items-center gap-2 text-label-md text-success-strong">
                  <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
                  Horarios guardados.
                </p>
              )}

              {editable && (
                <div className="flex flex-col gap-3 border-t border-border pt-4">
                  {hayCambios && (
                    <p role="status" className="text-body-sm text-warning-strong">
                      Hay cambios sin guardar.
                    </p>
                  )}
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <Button variante="secundario" icono={Plus} onClick={agregar} disabled={guardar.isPending}>
                      Agregar horario
                    </Button>
                    <div className="flex flex-col-reverse gap-2 sm:flex-row">
                      {hayCambios && (
                        <Button variante="secundario" onClick={descartar} disabled={guardar.isPending}>
                          Descartar cambios
                        </Button>
                      )}
                      <Button icono={Save} onClick={enviar} cargando={guardar.isPending} disabled={!hayCambios}>
                        Guardar horarios
                      </Button>
                    </div>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}
    </section>
  )
}
