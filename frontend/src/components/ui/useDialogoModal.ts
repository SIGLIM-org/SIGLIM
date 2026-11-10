import { useEffect, useRef } from 'react'

/**
 * Abre un `<dialog>` con `showModal()` al montarse (trampa de foco y Esc nativos) y, al
 * desmontarse, lo cierra y devuelve el foco a quien lo abrió (RNF-008).
 */
export function useDialogoModal() {
  const dialogoRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialogo = dialogoRef.current

    if (!dialogo) {
      return
    }

    const elementoPrevio =
      document.activeElement instanceof HTMLElement ? document.activeElement : null

    dialogo.showModal()

    return () => {
      dialogo.close()
      elementoPrevio?.focus()
    }
  }, [])

  return dialogoRef
}
