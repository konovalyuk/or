type ViewTransitionDocument = Document & { startViewTransition?: (update: () => void) => unknown }

export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function navigate(path: string) {
  if (path === window.location.pathname) return
  const update = () => {
    window.history.pushState({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior })
  }
  const doc = document as ViewTransitionDocument
  if (doc.startViewTransition && !prefersReducedMotion()) doc.startViewTransition(update)
  else update()
}

export const OPEN_PALETTE_EVENT = 'or:open-palette'
export const openPalette = () => window.dispatchEvent(new Event(OPEN_PALETTE_EVENT))
