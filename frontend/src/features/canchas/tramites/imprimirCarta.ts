/**
 * Abre la carta en una ventana aparte con los estilos de la aplicación y lanza el diálogo de
 * impresión del navegador, donde se guarda como PDF (RF-118). Así no se agrega una biblioteca de
 * PDF al proyecto. Devuelve `false` si el navegador bloqueó la ventana.
 */
export function imprimirCarta(elemento: HTMLElement, titulo: string): boolean {
  const ventana = window.open('', '_blank', 'width=900,height=1000')

  if (!ventana) {
    return false
  }

  const estilos = Array.from(document.querySelectorAll('link[rel="stylesheet"], style'))
    .map((nodo) => nodo.outerHTML)
    .join('\n')
  const documento = ventana.document

  documento.open()
  documento.write(
    `<!doctype html><html lang="es-MX"><head><meta charset="utf-8"><title></title>${estilos}` +
      '<style>@page{margin:2cm}body{margin:0}</style></head><body></body></html>',
  )
  documento.close()
  documento.title = titulo
  documento.body.innerHTML = elemento.outerHTML
  ventana.focus()
  ventana.setTimeout(() => {
    ventana.print()
  }, 300)

  return true
}
