import { motion, useReducedMotion } from 'motion/react'
import type { PointerEvent, ReactNode } from 'react'

export function Reveal({ children, delay = 0, className, as = 'div' }: { children: ReactNode; delay?: number; className?: string; as?: 'div' | 'section' | 'li' }) {
  const reduce = useReducedMotion()
  const Tag = motion[as]
  if (reduce) return <Tag className={className}>{children}</Tag>
  return <Tag className={className} initial={{ opacity: 0, y: 28 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: '-60px' }} transition={{ duration: .8, delay, ease: [.2, .8, .2, 1] }}>{children}</Tag>
}

export function spotlight(event: PointerEvent<HTMLElement>) {
  const target = (event.target as HTMLElement).closest<HTMLElement>('.spotlight')
  if (!target) return
  const rect = target.getBoundingClientRect()
  target.style.setProperty('--mx', `${event.clientX - rect.left}px`)
  target.style.setProperty('--my', `${event.clientY - rect.top}px`)
}
