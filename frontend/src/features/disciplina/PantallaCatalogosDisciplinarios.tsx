import { CatalogosDisciplinarios } from '@/features/disciplina/CatalogosDisciplinarios'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import { PaginaSinPermiso } from '@/pages/PaginaSinPermiso'

/**
 * Página de la ruta de catálogos disciplinarios (F-32, H-26.2). `RutaConPermiso`
 * solo mira el módulo M-09, así que aquí se exige el permiso de configurar los
 * catálogos antes de montar la pantalla y pedir sus datos (VAL-015).
 */
export function PantallaCatalogosDisciplinarios() {
  return (
    <ConPermiso
      permiso="disciplina.catalogo:EDITAR"
      alternativa={<PaginaSinPermiso rutaInicio="/app" />}
    >
      <CatalogosDisciplinarios />
    </ConPermiso>
  )
}
