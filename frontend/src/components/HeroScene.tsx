import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { useMemo, useRef } from 'react'
import * as THREE from 'three'

type Graph = { nodes: THREE.Vector3[]; edges: [number, number][]; adjacency: { to: number; edge: number; w: number }[][] }

function random(seed: number) { return () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646 } }

function buildGraph(count: number): Graph {
  const rand = random(7)
  const nodes = Array.from({ length: count }, () => new THREE.Vector3((rand() - .5) * 22, (rand() - .5) * 11, (rand() - .5) * 7))
  const key = new Set<string>()
  const edges: [number, number][] = []
  nodes.forEach((node, i) => {
    nodes.map((other, j) => ({ j, d: node.distanceTo(other) })).filter(item => item.j !== i).sort((a, b) => a.d - b.d).slice(0, 3).forEach(({ j }) => {
      const id = i < j ? `${i}-${j}` : `${j}-${i}`
      if (!key.has(id)) { key.add(id); edges.push([Math.min(i, j), Math.max(i, j)]) }
    })
  })
  const adjacency = nodes.map(() => [] as { to: number; edge: number; w: number }[])
  edges.forEach(([a, b], edge) => { const w = nodes[a].distanceTo(nodes[b]); adjacency[a].push({ to: b, edge, w }); adjacency[b].push({ to: a, edge, w }) })
  return { nodes, edges, adjacency }
}

function shortestPath(graph: Graph, source: number, target: number) {
  const dist = graph.nodes.map(() => Infinity), prev = graph.nodes.map(() => -1), done = graph.nodes.map(() => false)
  dist[source] = 0
  for (let k = 0; k < graph.nodes.length; k++) {
    let u = -1
    for (let i = 0; i < dist.length; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i
    if (u < 0 || dist[u] === Infinity) break
    done[u] = true
    if (u === target) break
    for (const { to, w } of graph.adjacency[u]) if (dist[u] + w < dist[to]) { dist[to] = dist[u] + w; prev[to] = u }
  }
  if (dist[target] === Infinity) return []
  const path = [target]
  while (path[0] !== source) path.unshift(prev[path[0]])
  return path
}

function dotTexture() {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 64
  const ctx = canvas.getContext('2d')!
  const g = ctx.createRadialGradient(32, 32, 0, 32, 32, 32)
  g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.25, 'rgba(255,255,255,.85)'); g.addColorStop(1, 'rgba(255,255,255,0)')
  ctx.fillStyle = g; ctx.fillRect(0, 0, 64, 64)
  return new THREE.CanvasTexture(canvas)
}

const PARTICLES = 160
const SAMPLES = 24

function Network() {
  const graph = useMemo(() => buildGraph(64), [])
  const sprite = useMemo(dotTexture, [])
  const group = useRef<THREE.Group>(null)
  const { pointer } = useThree()

  const lines = useMemo(() => {
    const geometry = new THREE.BufferGeometry().setFromPoints(graph.edges.flatMap(([a, b]) => [graph.nodes[a], graph.nodes[b]]))
    return new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ color: '#4f6dff', transparent: true, opacity: .22, blending: THREE.AdditiveBlending, depthWrite: false }))
  }, [graph])

  const nodes = useMemo(() => {
    const geometry = new THREE.BufferGeometry().setFromPoints(graph.nodes)
    return new THREE.Points(geometry, new THREE.PointsMaterial({ size: .32, map: sprite, color: '#c9d6ff', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }))
  }, [graph, sprite])

  const flow = useMemo(() => {
    const rand = random(11)
    const state = Array.from({ length: PARTICLES }, () => ({ edge: Math.floor(rand() * graph.edges.length), t: rand(), speed: .12 + rand() * .25, forward: rand() > .5 }))
    const positions = new Float32Array(PARTICLES * 3)
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    const points = new THREE.Points(geometry, new THREE.PointsMaterial({ size: .18, map: sprite, color: '#5ee7ff', transparent: true, opacity: .9, depthWrite: false, blending: THREE.AdditiveBlending }))
    return { state, positions, points }
  }, [graph, sprite])

  const route = useMemo(() => {
    const capacity = graph.nodes.length * SAMPLES
    const positions = new Float32Array(capacity * 3), colors = new Float32Array(capacity * 3)
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    geometry.setDrawRange(0, 0)
    const material = new THREE.LineBasicMaterial({ vertexColors: true, transparent: true, opacity: 1, blending: THREE.AdditiveBlending, depthWrite: false })
    const line = new THREE.Line(geometry, material)
    const endsGeometry = new THREE.BufferGeometry()
    endsGeometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(6), 3))
    const ends = new THREE.Points(endsGeometry, new THREE.PointsMaterial({ size: .9, map: sprite, color: '#ffffff', transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }))
    return { line, ends, material, positions, colors, count: 0, started: -10 }
  }, [graph, sprite])

  const pick = useRef(random(23))
  function newRoute(time: number) {
    const rand = pick.current
    let path: number[] = []
    for (let tries = 0; tries < 12 && path.length < 5; tries++) {
      const a = Math.floor(rand() * graph.nodes.length), b = Math.floor(rand() * graph.nodes.length)
      if (a !== b && graph.nodes[a].distanceTo(graph.nodes[b]) > 11) path = shortestPath(graph, a, b)
    }
    if (path.length < 2) return
    const from = new THREE.Color('#5ee7ff'), to = new THREE.Color('#a77bff')
    const total = (path.length - 1) * SAMPLES
    let k = 0
    for (let s = 0; s < path.length - 1; s++) for (let j = 0; j < SAMPLES; j++) {
      const p = graph.nodes[path[s]].clone().lerp(graph.nodes[path[s + 1]], j / (SAMPLES - 1))
      const c = from.clone().lerp(to, k / total)
      route.positions.set([p.x, p.y, p.z], k * 3); route.colors.set([c.r, c.g, c.b], k * 3); k++
    }
    route.count = k
    route.line.geometry.attributes.position.needsUpdate = true
    route.line.geometry.attributes.color.needsUpdate = true
    route.line.geometry.computeBoundingSphere()
    const endAttr = route.ends.geometry.attributes.position as THREE.BufferAttribute
    const a = graph.nodes[path[0]], b = graph.nodes[path[path.length - 1]]
    endAttr.set([a.x, a.y, a.z, b.x, b.y, b.z]); endAttr.needsUpdate = true
    route.started = time
  }

  useFrame((state, delta) => {
    const time = state.clock.elapsedTime
    if (group.current) {
      group.current.rotation.y = Math.sin(time * .06) * .35 + pointer.x * .12
      group.current.rotation.x = Math.sin(time * .045) * .08 - pointer.y * .08
    }
    flow.state.forEach((p, i) => {
      p.t += delta * p.speed
      if (p.t > 1) { p.t -= 1; p.edge = (p.edge * 31 + i) % graph.edges.length }
      const [a, b] = graph.edges[p.edge]
      const from = graph.nodes[p.forward ? a : b], to = graph.nodes[p.forward ? b : a]
      flow.positions[i * 3] = from.x + (to.x - from.x) * p.t
      flow.positions[i * 3 + 1] = from.y + (to.y - from.y) * p.t
      flow.positions[i * 3 + 2] = from.z + (to.z - from.z) * p.t
    })
    flow.points.geometry.attributes.position.needsUpdate = true
    const age = time - route.started
    if (age > 4.2) newRoute(time)
    const draw = Math.min(1, Math.max(0, age / 1.4))
    route.line.geometry.setDrawRange(0, Math.floor(route.count * draw))
    const fade = age < 3.2 ? 1 : Math.max(0, 1 - (age - 3.2))
    route.material.opacity = fade
    ;(route.ends.material as THREE.PointsMaterial).opacity = fade * Math.min(1, age * 2)
  })

  return <group ref={group}>
    <primitive object={lines} />
    <primitive object={nodes} />
    <primitive object={flow.points} />
    <primitive object={route.line} />
    <primitive object={route.ends} />
  </group>
}

export default function HeroScene() {
  return <Canvas camera={{ position: [0, 0, 15], fov: 50 }} dpr={[1, 2]} gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}>
    <fog attach="fog" args={['#05070d', 12, 26]} />
    <Network />
  </Canvas>
}
