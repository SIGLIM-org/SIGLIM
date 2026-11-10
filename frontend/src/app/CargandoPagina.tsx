import { EstadoCarga } from '@/components/ui/EstadoCarga'

/**
 * Respaldo mientras se descarga el fragmento de una ruta (DT-01, RNF-007).
 * Reutiliza el esqueleto de carga de T-03.3; no usa `<main>` porque también se
 * muestra dentro del contenido de `InternalLayout`.
 */
export function CargandoPagina() {
  return (
    <div className="px-4 py-8 sm:px-6 lg:px-8">
      <EstadoCarga etiqueta="Cargando módulo…" filas={6} columnas={3} />
    </div>
  )
}
