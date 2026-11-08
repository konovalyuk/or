import { animate, useInView, useReducedMotion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'

export function CountUp({ value, prefix = '', suffix = '', decimals, duration = 1.6 }: { value: number; prefix?: string; suffix?: string; decimals?: number; duration?: number }) {
  const ref = useRef<HTMLElement>(null)
  const inView = useInView(ref, { once: true, margin: '-40px' })
  const reduce = useReducedMotion()
  const digits = decimals ?? (String(value).split('.')[1] ?? '').length
  const [shown, setShown] = useState(reduce ? value : 0)
  useEffect(() => {
    if (reduce) { setShown(value); return }
    if (!inView) return
    const controls = animate(0, value, { duration, ease: [.2, .8, .2, 1], onUpdate: setShown })
    return () => controls.stop()
  }, [inView, reduce, value, duration])
  return <strong ref={ref}>{prefix}{shown.toFixed(digits)}{suffix}</strong>
}
