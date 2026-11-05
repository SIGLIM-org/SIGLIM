/**
 * Nombre de la columna dentro de una fila apilada como tarjeta. Solo se ve bajo
 * `xl`, donde el encabezado de la tabla no está visible (RNF-008).
 */
export function EtiquetaTarjeta({ children }: { children: string }) {
  return <span className="block text-label-sm text-text-muted xl:hidden">{children}</span>
}
