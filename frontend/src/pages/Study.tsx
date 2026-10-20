import { byMethod, type Method, STUDY_METHODS } from '../applications/catalog'
import { ApplicationsTrigger } from '../components/ApplicationsSheet'
import { Reveal, spotlight } from '../components/Reveal'
import { navigate } from '../navigation'

const MODELS: { title: string; chapter: string; glyph: string; text: string; methods: Method[]; route: string; tabs?: { label: string; route: string }[] }[] = [
  { title: 'Transportation', chapter: 'Chapter 5 · transportation and assignment', glyph: '⇄', text: 'Ship from sources to destinations at minimum cost. Replay Vogel, least-cost and northwest-corner starts, then every MODI pivot.', methods: ['transportation', 'assignment'], route: '/transportation' },
  { title: 'Linear programming', chapter: 'Chapters 2–4 · graphical, simplex, duality', glyph: '∠', text: 'Allocate scarce resources for the best profit. Draw the feasible region, or follow the simplex tableau to the optimum.', methods: ['lp'], route: '/linear-programming' },
  { title: 'Network models', chapter: 'Chapter 6 · trees, routes, flows, projects', glyph: '⬡', text: 'Design, route, push flow and schedule on a graph you can edit. Each algorithm shows how it builds its answer.', methods: ['mst', 'shortest-route', 'max-flow', 'cpm'], route: '/network/spanning-tree', tabs: [
    { label: 'Spanning tree', route: '/network/spanning-tree' }, { label: 'Shortest route', route: '/network/shortest-route' }, { label: 'Max flow', route: '/network/max-flow' }, { label: 'CPM / PERT', route: '/network/critical-path' }] },
]

export function Study() {
  return <>
    <section className="hero compact-hero">
      <div><span className="eyebrow">Models</span><h2>Pick a model. Watch it reach the optimum.</h2><p>Three classroom solvers from Taha’s book, each with the book’s own example loaded and every step shown.</p></div>
      <div className="hero-actions"><ApplicationsTrigger methods={STUDY_METHODS} title="Where these models pay off" /></div>
    </section>
    <section className="study-grid" onPointerMove={spotlight}>
      {MODELS.map((model, i) => {
        const uses = byMethod(...model.methods).filter(app => !app.documented).slice(0, 4)
        return <Reveal key={model.title} delay={i * .08} className="bento-cell"><article className="study-card spotlight">
          <span className="glyph gradient-text" aria-hidden>{model.glyph}</span>
          <span className="chapter">{model.chapter}</span>
          <h3>{model.title}</h3>
          <p>{model.text}</p>
          {model.tabs && <div className="tab-links">{model.tabs.map(tab => <button key={tab.route} className="filter-chip" onClick={() => navigate(tab.route)}>{tab.label}</button>)}</div>}
          <div className="study-uses">
            <div className="study-uses-title"><span>Where it pays</span><span aria-hidden>↘</span></div>
            <ul>{uses.map(app => <li key={app.id}>{app.title} <span>({app.companies.slice(0, 2).join(', ')})</span></li>)}</ul>
          </div>
          <div className="actions"><button className="button primary" onClick={() => navigate(model.route)}>Open {model.title.toLowerCase()}</button><ApplicationsTrigger methods={model.methods} title={`Where ${model.title.toLowerCase()} pays off`} /></div>
        </article></Reveal>
      })}
    </section>
  </>
}
