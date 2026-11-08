import { Minus, Plus } from 'lucide-react'

type Props = {
  titulo: string
  codigos: readonly string[]
  descripciones: ReadonlyMap<string, string>
  sentido: 'agrega' | 'quita'
}

/** Permisos que una solicitud agrega o quita, con su descripción y su código. */
export function ListaPermisosSolicitud({ titulo, codigos, descripciones, sentido }: Props) {
  const Icono = sentido === 'agrega' ? Plus : Minus

  return (
    <section aria-label={titulo} className="flex min-w-0 flex-col gap-1.5">
      <h4 className="text-label-md text-text-main">{titulo}</h4>
      {codigos.length === 0 ? (
        <p className="text-body-sm text-text-muted">Ninguno</p>
      ) : (
        <ul className="flex flex-col gap-1">
          {codigos.map((codigo) => (
            <li key={codigo} className="flex items-start gap-2 text-body-md text-text-main">
              <Icono
                aria-hidden="true"
                className={[
                  'mt-0.5 size-4 shrink-0',
                  sentido === 'agrega' ? 'text-success-strong' : 'text-error-strong',
                ].join(' ')}
              />
              <span className="flex min-w-0 flex-col">
                <span>{descripciones.get(codigo) ?? 'Permiso del catálogo'}</span>
                <span className="break-all font-mono text-body-sm text-text-muted">{codigo}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
