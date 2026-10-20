import { useEffect, useState } from 'react'
import { navigate, openPalette } from '../navigation'

const isModelPath = (path: string) => path === '/study' || path === '/transportation' || path === '/linear-programming' || path.startsWith('/network') || path === '/education'

export function TopBar({ path }: { path: string }) {
  const [scrolled, setScrolled] = useState(false)
  const [overHero, setOverHero] = useState(true)
  useEffect(() => {
    const onScroll = () => { setScrolled(window.scrollY > 12); setOverHero(window.scrollY < window.innerHeight * .85) }
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])
  const dark = path === '/' && overHero
  const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)
  const link = (label: string, target: string, active: boolean) => <button className={`topnav-link ${active ? 'active' : ''}`} onClick={() => navigate(target)} aria-current={active ? 'page' : undefined}>{label}</button>
  return <header className={`topbar ${scrolled ? 'scrolled' : ''} ${dark ? 'on-dark' : ''}`}>
    <div className="page-width topbar-inner">
      <button className="brand-button" onClick={() => navigate('/')}><span className="brand-mark">∑</span><span><b>Operations Research Lab</b><small>Decisions backed by mathematics</small></span></button>
      <nav className="topnav">
        {link('Overview', '/', path === '/')}
        {link('Production', '/production', path === '/production')}
        {link('Models', '/study', isModelPath(path))}
      </nav>
      <button className="search-hint" onClick={openPalette} aria-label="Search applications"><span className="search-icon" aria-hidden>⌕</span><span className="search-label">Search uses</span><kbd>{isMac ? '⌘' : 'Ctrl'} K</kbd></button>
    </div>
  </header>
}
