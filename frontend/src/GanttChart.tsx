import { useEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import type { CpmActivity } from './api'
import { num, short } from './networkData'

export function activityKey(activity: CpmActivity) {
  return `${activity.from}-${activity.to}`
}

export function activityName(activity: CpmActivity) {
  return activity.symbol || `${activity.from}-${activity.to}`
}

export function scheduleActivities(activities: CpmActivity[], chosen: Record<string, number>) {
  const start = new Map<string, number>()
  const forced = new Map<string, number>()
  const notes: string[] = []
  const ordered = [...activities].sort((left, right) => left.from - right.from || left.to - right.to)
  for (const activity of ordered) {
    const key = activityKey(activity)
    let required = activity.earliestStart
    for (const predecessor of activities) {
      if (predecessor.to !== activity.from) continue
      const predecessorStart = start.get(activityKey(predecessor)) ?? predecessor.earliestStart
      const delay = predecessorStart - predecessor.earliestStart
      const push = Math.max(0, delay - predecessor.freeFloat)
      required = Math.max(required, activity.earliestStart + push)
    }
    forced.set(key, required)
    const cap = activity.earliestStart + activity.totalFloat
    const request = chosen[key]
    let value = activity.critical ? activity.earliestStart : request == null || !Number.isFinite(request) ? required : Math.max(required, Math.min(Math.max(request, activity.earliestStart), cap))
    if (required > cap) value = required
    start.set(key, value)
    if (!activity.critical && value > activity.earliestStart + 1e-9 && request == null) {
      notes.push(`${activityName(activity)} moves to ${num(value)} because an earlier activity used more than its free float.`)
    }
  }
  return { start, forced, notes }
}

export function GanttChart({ activities, project }: { activities: CpmActivity[]; project: number }) {
  const flexible = activities.filter(activity => !activity.critical)
  const [picked, setPicked] = useState(flexible[0] ? activityKey(flexible[0]) : '')
  const [chosen, setChosen] = useState<Record<string, number>>({})
  const plan = useMemo(() => scheduleActivities(activities, chosen), [activities, chosen])
  const previous = useRef<Map<string, number> | null>(null)
  const [flash, setFlash] = useState<{ keys: string[]; tick: number }>({ keys: [], tick: 0 })

  useEffect(() => {
    const before = previous.current
    previous.current = plan.start
    if (!before) return
    const keys = [...plan.start.entries()]
      .filter(([key, value]) => key !== picked && Math.abs((before.get(key) ?? value) - value) > 1e-9)
      .map(([key]) => key)
    if (keys.length) setFlash(current => ({ keys, tick: current.tick + 1 }))
  }, [plan, picked])

  const selected = flexible.find(activity => activityKey(activity) === picked)
  const ends = activities.map(activity => (plan.start.get(activityKey(activity)) ?? activity.earliestStart) + activity.duration)
  const horizon = Math.max(1, Math.ceil(Math.max(project, ...ends)))
  const pct = (time: number) => `${(time / horizon) * 100}%`
  const ticks = Array.from({ length: horizon + 1 }, (_, day) => day)
  const everyDay = horizon <= 30
  const finish = Math.max(project, ...ends)

  return <div className="gantt">
    <div className="gantt-toolbar">
      <div className="gantt-legend">
        <span className="chip chip-critical">Critical</span>
        <span className="chip chip-flex">Noncritical</span>
        <span className="chip chip-window">Float window</span>
        <span className="chip chip-ff">Free-float limit</span>
      </div>
      {selected && <div className="gantt-controls">
        <label>Activity<select value={picked} onChange={event => setPicked(event.target.value)}>{flexible.map(activity => <option key={activityKey(activity)} value={activityKey(activity)}>{activityName(activity)} ({activity.from}–{activity.to})</option>)}</select></label>
        <label>Start time<input className="matrix-input" type="number" step="any"
          min={plan.forced.get(activityKey(selected)) ?? selected.earliestStart}
          max={Math.max(selected.earliestStart + selected.totalFloat, plan.forced.get(activityKey(selected)) ?? 0)}
          value={plan.start.get(activityKey(selected)) ?? selected.earliestStart}
          onChange={event => setChosen({ ...chosen, [activityKey(selected)]: Number(event.target.value) })} /></label>
        <button type="button" className="button secondary" onClick={() => setChosen({})} disabled={Object.keys(chosen).length === 0}>Reset</button>
      </div>}
    </div>
    <div className="gantt-scroll">
      <div className="gantt-grid" style={{ '--days': horizon, '--end': finish / horizon } as CSSProperties}>
        <div className="gantt-row gantt-axis">
          <div className="gantt-label">Day</div>
          <div className="gantt-track">
            {ticks.map(day => <span key={day} className={`gantt-tick ${day % 5 === 0 ? 'major' : ''}`} style={{ left: pct(day) }}>{everyDay || day % 5 === 0 ? day : ''}</span>)}
          </div>
        </div>
        {activities.map(activity => {
          const key = activityKey(activity)
          const start = plan.start.get(key) ?? activity.earliestStart
          const freeLimit = activity.earliestStart + activity.duration + activity.freeFloat
          const moved = start > activity.earliestStart + 1e-9
          return <div className={`gantt-row ${key === picked ? 'picked' : ''}`} key={key}>
            <div className="gantt-label">
              <b>{activityName(activity)}</b><small>{activity.from}–{activity.to}</small>
              {activity.redFlag && <span className="chip chip-flag" title="Free float is smaller than total float">red flag</span>}
            </div>
            <div className="gantt-track lines">
              {!activity.critical && <span className="gantt-window" style={{ left: pct(activity.earliestStart), width: pct(activity.latestCompletion - activity.earliestStart) }} />}
              {!activity.critical && activity.freeFloat < activity.totalFloat - 1e-9 && <span className="gantt-ff" style={{ left: pct(freeLimit) }} />}
              <button type="button" className={`gantt-bar ${activity.critical ? 'critical' : ''} ${moved ? 'moved' : ''}`}
                style={{ left: pct(start), width: pct(activity.duration) }}
                onClick={() => { if (!activity.critical) setPicked(key) }}>
                <span className="gantt-bar-text">{activityName(activity)} · {short(activity.duration)}</span>
                {flash.keys.includes(key) && <span key={flash.tick} className="gantt-flash" />}
                <span className="gantt-tip">
                  <b>{activityName(activity)}</b> start {short(start)} · ES {short(activity.earliestStart)} · EF {short(activity.earliestStart + activity.duration)} · LC {short(activity.latestCompletion)} · TF {short(activity.totalFloat)} · FF {short(activity.freeFloat)}
                </span>
              </button>
            </div>
          </div>
        })}
        <span className="gantt-end" aria-hidden="true"><span>{short(finish)}</span></span>
      </div>
    </div>
    {plan.notes.length > 0 && <ul className="gantt-notes">{plan.notes.map(note => <li key={note}>{note}</li>)}</ul>}
  </div>
}
