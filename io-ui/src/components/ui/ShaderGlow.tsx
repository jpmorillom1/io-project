import { useEffect, useRef, type ReactNode } from 'react'

// ─── GLSL ────────────────────────────────────────────────────────────────────

const VERT = `#version 300 es
in vec2 a_pos;
out vec2 v_uv;
void main() {
  v_uv = a_pos * 0.5 + 0.5;
  gl_Position = vec4(a_pos, 0.0, 1.0);
}`

const FRAG = `#version 300 es
precision highp float;

uniform float u_time;
uniform vec2  u_res;
uniform float u_speed;
uniform float u_vig;   /* 1 = full vignette (icon/pill), 0 = none (banner) */

in  vec2 v_uv;
out vec4 out_color;

void main() {
  float ar = u_res.x / u_res.y;
  float t  = u_time * u_speed;

  /* Blob centers orbit in [0,1]x[0,1] UV space */
  vec2 bc1 = vec2(0.5 + sin(t * 0.80)         * 0.42, 0.5 + cos(t * 0.60)         * 0.38);
  vec2 bc2 = vec2(0.5 + sin(t * 0.50 + 2.094) * 0.38, 0.5 + cos(t * 0.70 + 2.094) * 0.34);
  vec2 bc3 = vec2(0.5 + sin(t * 0.65 + 4.189) * 0.40, 0.5 + cos(t * 0.42 + 4.189) * 0.36);
  vec2 bc4 = vec2(0.5 + sin(t * 0.45 + 1.047) * 0.43, 0.5 + cos(t * 0.55 + 3.142) * 0.37);

  /* Aspect-corrected positions */
  vec2 p  = vec2(v_uv.x * ar, v_uv.y);
  vec2 p1 = vec2(bc1.x  * ar, bc1.y);
  vec2 p2 = vec2(bc2.x  * ar, bc2.y);
  vec2 p3 = vec2(bc3.x  * ar, bc3.y);
  vec2 p4 = vec2(bc4.x  * ar, bc4.y);

  /* Spread scales with shape so banners stay filled */
  float s = max(0.45, ar * 0.20);

  vec3 col  = vec3(0.55, 0.20, 0.92) * exp(-length(p - p1) / s);
       col += vec3(0.14, 0.76, 0.84) * exp(-length(p - p2) / s);
       col += vec3(0.95, 0.50, 0.10) * exp(-length(p - p3) / s);
       col += vec3(0.38, 0.08, 0.72) * exp(-length(p - p4) / s);

  /* Edge vignette — suppressed for banner (u_vig = 0) */
  vec2 edgeDist = min(v_uv, 1.0 - v_uv);
  float vx = mix(1.0, smoothstep(0.0, 0.18, edgeDist.x), u_vig);
  float vy = mix(1.0, smoothstep(0.0, 0.18, edgeDist.y), u_vig);
  col *= vx * vy * (1.6 + u_vig * 0.6);

  out_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}`

// ─── WebGL helpers ────────────────────────────────────────────────────────────

function buildProgram(gl: WebGL2RenderingContext): WebGLProgram {
  function compile(type: number, src: string) {
    const s = gl.createShader(type)!
    gl.shaderSource(s, src)
    gl.compileShader(s)
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS))
      console.error('[ShaderGlow]', gl.getShaderInfoLog(s))
    return s
  }
  const vs = compile(gl.VERTEX_SHADER, VERT)
  const fs = compile(gl.FRAGMENT_SHADER, FRAG)
  const prog = gl.createProgram()!
  gl.attachShader(prog, vs)
  gl.attachShader(prog, fs)
  gl.linkProgram(prog)
  gl.deleteShader(vs)
  gl.deleteShader(fs)
  return prog
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  target: 'icon' | 'pill' | 'banner' | 'circle'
  state?: 'processing' | 'done'
  children?: ReactNode
  className?: string
}

export function ShaderGlow({ target, state = 'processing', children, className }: Props) {
  const canvasRef    = useRef<HTMLCanvasElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const speedRef     = useRef(1.0)
  const vigRef       = useRef(1.0)

  /* Update refs each render — render loop reads them without restarting */
  speedRef.current = target === 'banner' ? (state === 'done' ? 0.3 : 1.0) : 0.7
  vigRef.current   = target === 'banner' ? 0.0 : target === 'icon' ? 1.0 : 0.8

  const EXT    = target === 'icon' ? 12 : 6
  const DOUBLE = EXT * 2

  useEffect(() => {
    const canvas    = canvasRef.current
    const container = containerRef.current
    if (!canvas || !container) return

    const gl = canvas.getContext('webgl2')
    if (!gl) { console.warn('[ShaderGlow] WebGL2 not available'); return }

    const prog = buildProgram(gl)
    const vao  = gl.createVertexArray()!
    const buf  = gl.createBuffer()!

    gl.bindVertexArray(vao)
    gl.bindBuffer(gl.ARRAY_BUFFER, buf)
    gl.bufferData(gl.ARRAY_BUFFER,
      new Float32Array([-1, -1,  1, -1,  -1, 1,  1, 1]),
      gl.STATIC_DRAW)
    const posLoc = gl.getAttribLocation(prog, 'a_pos')
    gl.enableVertexAttribArray(posLoc)
    gl.vertexAttribPointer(posLoc, 2, gl.FLOAT, false, 0, 0)
    gl.bindVertexArray(null)
    gl.useProgram(prog)

    const uTime  = gl.getUniformLocation(prog, 'u_time')
    const uRes   = gl.getUniformLocation(prog, 'u_res')
    const uSpeed = gl.getUniformLocation(prog, 'u_speed')
    const uVig   = gl.getUniformLocation(prog, 'u_vig')

    function syncSize() {
      const dpr  = window.devicePixelRatio || 1
      const rect = container.getBoundingClientRect()
      const w = Math.round((rect.width  + (target === 'banner' ? 0 : DOUBLE)) * dpr)
      const h = Math.round((rect.height + (target === 'banner' ? 0 : DOUBLE)) * dpr)
      if (canvas.width !== w || canvas.height !== h) {
        canvas.width  = w
        canvas.height = h
        gl.viewport(0, 0, w, h)
      }
    }

    const ro = new ResizeObserver(syncSize)
    ro.observe(container)
    syncSize()

    let rafId: number
    const t0 = performance.now()

    function frame() {
      syncSize()
      const t = (performance.now() - t0) / 1000
      gl.uniform1f(uTime,  t)
      gl.uniform2f(uRes,   canvas.width, canvas.height)
      gl.uniform1f(uSpeed, speedRef.current)
      gl.uniform1f(uVig,   vigRef.current)
      gl.bindVertexArray(vao)
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      gl.bindVertexArray(null)
      rafId = requestAnimationFrame(frame)
    }

    rafId = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(rafId)
      ro.disconnect()
      gl.deleteBuffer(buf)
      gl.deleteVertexArray(vao)
      gl.deleteProgram(prog)
    }
  // target doesn't change after mount; speed/vig are read via refs
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /* ── icon ── */
  if (target === 'icon') {
    return (
      <div ref={containerRef} className={className}
        style={{ position: 'relative', display: 'inline-flex' }}
      >
        <canvas ref={canvasRef} style={{
          position: 'absolute',
          top: -EXT, left: -EXT,
          width:  `calc(100% + ${DOUBLE}px)`,
          height: `calc(100% + ${DOUBLE}px)`,
          pointerEvents: 'none',
          zIndex: 0,
          borderRadius: '50%',
        }} />
        <div style={{ position: 'relative', zIndex: 1 }}>{children}</div>
      </div>
    )
  }

  /* ── pill ── */
  if (target === 'pill') {
    return (
      <div ref={containerRef} className={className}
        style={{ position: 'relative', display: 'inline-flex' }}
      >
        <canvas ref={canvasRef} style={{
          position: 'absolute',
          top: -EXT, left: -EXT,
          width:  `calc(100% + ${DOUBLE}px)`,
          height: `calc(100% + ${DOUBLE}px)`,
          pointerEvents: 'none',
          zIndex: 0,
          borderRadius: '999px',
        }} />
        <div style={{
          position: 'relative', zIndex: 1,
          display: 'flex', alignItems: 'center', gap: '6px',
          padding: '6px 12px',
          color: 'var(--ij-bg-editor)',
          fontSize: '12px',
          fontFamily: "'JetBrains Mono', monospace",
        }}>
          {children}
        </div>
      </div>
    )
  }

  /* ── circle ── */
  if (target === 'circle') {
    return (
      <div ref={containerRef} className={className}
        style={{ position: 'relative', display: 'inline-flex' }}
      >
        <canvas ref={canvasRef} style={{
          position: 'absolute',
          top: -EXT, left: -EXT,
          width:  `calc(100% + ${DOUBLE}px)`,
          height: `calc(100% + ${DOUBLE}px)`,
          pointerEvents: 'none',
          zIndex: 0,
          borderRadius: '50%',
        }} />
        {children && <div style={{ position: 'relative', zIndex: 1 }}>{children}</div>}
      </div>
    )
  }

  /* ── banner ── */
  return (
    <div ref={containerRef} className={className}
      style={{ position: 'relative', overflow: 'hidden', borderRadius: '4px' }}
    >
      <canvas ref={canvasRef} style={{
        position: 'absolute', inset: 0,
        width: '100%', height: '100%',
        pointerEvents: 'none',
      }} />
      {/* overlay keeps text legible over the shader */}
      <div style={{
        position: 'absolute', inset: 0,
        background: 'rgba(0,0,0,0.38)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'relative', zIndex: 1,
        padding: '6px 12px',
        display: 'flex', alignItems: 'center', gap: '6px',
        color: '#fff',
        fontSize: '12px',
        fontFamily: "'JetBrains Mono', monospace",
      }}>
        {children}
      </div>
    </div>
  )
}
