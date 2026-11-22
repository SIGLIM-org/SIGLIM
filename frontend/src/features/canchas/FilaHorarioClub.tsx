import type { ReactNode } from 'react'
import { CircleAlert, Trash2 } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaDia } from '@/features/canchas/etiquetas'
import { DIAS_SEMANA, type CampoHorario, type ErroresFila, type FilaHorario } from '@/features/canchas/horariosClub'
import type { PropietarioCancha } from '@/features/canchas/tipos'

type Opcion = { id: string; nombre: string }

type Props = {
  fila: FilaHorario
  /** Posición en la lista (desde 0); la fila se nombra «Horario N». */
  posicion: number
  /** Prefijo único de la sección para los `id` de los controles. */
  idBase: string
  propietario: PropietarioCancha
  /** Nombre del club dueño de una cancha `CLUB`: la columna de club es fija (DEC-33). */
  clubPropietario: string
  editable: boolean
  /** Bloquea los controles mientras se guarda. */
  ocupado: boolean
  errores?: ErroresFila
  categorias: readonly Opcion[]
  /** Por qué no hay categorías que elegir (cargando, error o temporada sin categorías). */
  avisoCategorias?: string
  clubes: readonly Opcion[]
  avisoClubes?: string
  onCambiar: (campo: CampoHorario, valor: string) => void
  onQuitar: () => void
}

const CLASE_CELDA = 'block min-w-0 lg:table-cell lg:px-3 lg:py-3 lg:align-top'
const CLASE_ETIQUETA = 'text-label-sm text-text-muted lg:sr-only'
const CLASE_CONTROL =
  'min-h-11 w-full rounded-md border border-border bg-surface px-3 text-body-md text-text-main aria-invalid:border-error disabled:cursor-not-allowed disabled:opacity-60'
const CLASE_ERROR = 'mt-1 block text-body-sm text-error-strong'

/** Un nombre que ya no está en la lista se conserva como opción para no perder el valor. */
function opcionesConActual(opciones: readonly Opcion[], id: string, nombre: string): readonly Opcion[] {
  return id && !opciones.some((opcion) => opcion.id === id) ? [{ id, nombre: nombre || id }, ...opciones] : opciones
}

/**
 * Fila de «Horarios por categoría» (B-13.2, RF-119). En `lg` es una fila de tabla con etiquetas
 * solo para lectores de pantalla; debajo se apila como tarjeta con etiquetas visibles (RNF-007,
 * RNF-008). Un traslape se explica en una fila de error propia, enlazada a las horas.
 */
export function FilaHorarioClub({
  fila,
  posicion,
  idBase,
  propietario,
  clubPropietario,
  editable,
  ocupado,
  errores = {},
  categorias,
  avisoCategorias,
  clubes,
  avisoClubes,
  onCambiar,
  onQuitar,
}: Props) {
  const numero = posicion + 1
  const id = (campo: string) => `${idBase}-${fila.clave}-${campo}`
  const idTraslape = id('traslape')
  const esLiga = propietario === 'LIGA'
  const conErrorFila = Boolean(errores.fila)
  /** En edición el texto se centra con los controles de 44 px; en lectura no hace falta. */
  const alinear = editable ? 'lg:py-3' : ''

  /** `aria-describedby` del campo: su error, su aviso y, en las horas y el día, el traslape. */
  const descripcion = (campo: CampoHorario, aviso?: string) =>
    [
      errores[campo] ? id(`${campo}-error`) : '',
      aviso ? id(`${campo}-aviso`) : '',
      conErrorFila && (campo === 'horaInicio' || campo === 'horaFin' || campo === 'diaSemana') ? idTraslape : '',
    ]
      .filter(Boolean)
      .join(' ') || undefined

  const invalido = (campo: CampoHorario) =>
    Boolean(errores[campo]) || (conErrorFila && (campo === 'horaInicio' || campo === 'horaFin')) || undefined

  const etiqueta = (campo: CampoHorario, texto: string) => (
    <label htmlFor={id(campo)} className={CLASE_ETIQUETA}>
      {texto}
      <span className="sr-only"> del horario {numero}</span>
    </label>
  )

  const mensajes = (campo: CampoHorario, aviso?: string): ReactNode => (
    <>
      {errores[campo] && (
        <span id={id(`${campo}-error`)} className={CLASE_ERROR}>
          {errores[campo]}
        </span>
      )}
      {aviso && !errores[campo] && (
        <span id={id(`${campo}-aviso`)} className="mt-1 block text-body-sm text-text-muted">
          {aviso}
        </span>
      )}
    </>
  )

  const celdaClub = (
    <td role="cell" className={`${CLASE_CELDA} col-span-2`}>
      {esLiga && editable ? (
        <>
          {etiqueta('clubId', 'Club')}
          <select
            id={id('clubId')}
            className={CLASE_CONTROL}
            value={fila.clubId}
            disabled={ocupado}
            aria-invalid={invalido('clubId')}
            aria-describedby={descripcion('clubId', avisoClubes)}
            onChange={(evento) => onCambiar('clubId', evento.target.value)}
          >
            <option value="">Elige el club…</option>
            {opcionesConActual(clubes, fila.clubId, fila.clubNombre).map((club) => (
              <option key={club.id} value={club.id}>
                {club.nombre}
              </option>
            ))}
          </select>
          {mensajes('clubId', avisoClubes)}
        </>
      ) : (
        <>
          <span className={`block ${CLASE_ETIQUETA}`}>Club</span>
          <span className={`block break-words text-body-md text-text-main ${alinear}`}>
            {esLiga ? fila.clubNombre : clubPropietario}
          </span>
        </>
      )}
      {esLiga && (
        <span className="mt-1.5 block">
          <Insignia variante="informacion">Comodato</Insignia>
        </span>
      )}
    </td>
  )

  return (
    <>
      <tr
        role="row"
        className={[
          'relative grid grid-cols-2 gap-x-3 gap-y-3 p-4 lg:table-row lg:p-0',
          conErrorFila ? 'bg-error/4' : 'border-b border-border last:border-b-0',
        ].join(' ')}
      >
        <th
          role="rowheader"
          scope="row"
          className="col-span-2 flex min-h-11 items-center text-left lg:table-cell lg:min-h-0 lg:px-3 lg:py-3 lg:align-top"
        >
          <span className="text-headline-sm text-text-main lg:hidden">Horario {numero}</span>
          <span className={`hidden font-mono text-mono-data leading-5 text-text-muted lg:block ${alinear}`}>
            <span className="sr-only">Horario </span>
            {numero}
          </span>
        </th>

        <td role="cell" className={`${CLASE_CELDA} col-span-2`}>
          {editable ? (
            <>
              {etiqueta('categoriaId', 'Categoría')}
              <select
                id={id('categoriaId')}
                className={CLASE_CONTROL}
                value={fila.categoriaId}
                disabled={ocupado}
                aria-invalid={invalido('categoriaId')}
                aria-describedby={descripcion('categoriaId', avisoCategorias)}
                onChange={(evento) => onCambiar('categoriaId', evento.target.value)}
              >
                <option value="">Elige la categoría…</option>
                {opcionesConActual(categorias, fila.categoriaId, fila.categoriaNombre).map((categoria) => (
                  <option key={categoria.id} value={categoria.id}>
                    {categoria.nombre}
                  </option>
                ))}
              </select>
              {mensajes('categoriaId', avisoCategorias)}
            </>
          ) : (
            <>
              <span className={`block ${CLASE_ETIQUETA}`}>Categoría</span>
              <span className="block break-words text-body-md text-text-main">{fila.categoriaNombre}</span>
            </>
          )}
        </td>

        <td role="cell" className={`${CLASE_CELDA} col-span-2 sm:col-span-1 lg:w-40`}>
          {editable ? (
            <>
              {etiqueta('diaSemana', 'Día')}
              <select
                id={id('diaSemana')}
                className={CLASE_CONTROL}
                value={fila.diaSemana}
                disabled={ocupado}
                aria-invalid={invalido('diaSemana')}
                aria-describedby={descripcion('diaSemana')}
                onChange={(evento) => onCambiar('diaSemana', evento.target.value)}
              >
                <option value="">Elige el día…</option>
                {DIAS_SEMANA.map((dia) => (
                  <option key={dia} value={dia}>
                    {etiquetaDia[dia]}
                  </option>
                ))}
              </select>
              {mensajes('diaSemana')}
            </>
          ) : (
            <>
              <span className={`block ${CLASE_ETIQUETA}`}>Día</span>
              <span className="block text-body-md text-text-main">
                {fila.diaSemana === '' ? '' : etiquetaDia[fila.diaSemana]}
              </span>
            </>
          )}
        </td>

        {(['horaInicio', 'horaFin'] as const).map((campo) => (
          <td key={campo} role="cell" className={`${CLASE_CELDA} lg:w-36`}>
            {editable ? (
              <>
                {etiqueta(campo, campo === 'horaInicio' ? 'Inicio' : 'Fin')}
                <input
                  id={id(campo)}
                  type="time"
                  className={`${CLASE_CONTROL} font-mono`}
                  value={fila[campo]}
                  disabled={ocupado}
                  aria-invalid={invalido(campo)}
                  aria-describedby={descripcion(campo)}
                  onChange={(evento) => onCambiar(campo, evento.target.value)}
                />
                {mensajes(campo)}
              </>
            ) : (
              <>
                <span className={`block ${CLASE_ETIQUETA}`}>{campo === 'horaInicio' ? 'Inicio' : 'Fin'}</span>
                <span className="block font-mono text-mono-data leading-5 text-text-main">{fila[campo]}</span>
              </>
            )}
          </td>
        ))}

        {celdaClub}

        {editable && (
          // En móvil «Quitar» va en la cabecera de la tarjeta, junto a «Horario N».
          <td role="cell" className="absolute top-4 right-4 lg:static lg:table-cell lg:w-px lg:px-3 lg:py-3 lg:text-right lg:align-top">
            <Button
              variante="secundario"
              icono={Trash2}
              disabled={ocupado}
              aria-label={`Quitar horario ${numero}`}
              onClick={onQuitar}
            >
              Quitar
            </Button>
          </td>
        )}
      </tr>

      {conErrorFila && (
        <tr role="row" className="block border-b border-border bg-error/4 px-4 pb-4 last:border-b-0 lg:table-row lg:p-0">
          <td role="cell" colSpan={editable ? 7 : 6} className="block lg:table-cell lg:px-3 lg:pb-3">
            <p id={idTraslape} className="flex items-start gap-2 text-body-sm text-error-strong">
              <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              {errores.fila}
            </p>
          </td>
        </tr>
      )}
    </>
  )
}
