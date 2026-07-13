import { useEffect, useRef } from 'react'
import { useReducedMotion } from 'motion/react'

/**
 * Glow de bienvenida: ondas concentricas que laten desde el centro del canvas.
 * No es un fondo a pantalla completa: se coloca desbordado en una esquina y se
 * difumina con blur + mascara radial desde el contenedor (ver HomeWorkspace).
 *
 * El shader pinta con ALFA (transparente donde no hay onda), asi que flota sobre
 * el fondo del editor en vez de imponer un rectangulo negro. Los tres canales
 * del efecto original se re-tintan a la paleta de la app: teal, violeta, naranja.
 *
 * WebGL2 a pelo, como ShaderGlow y PivotAvatar: three.js aqui solo serviria
 * para dibujar un quad a pantalla completa.
 */

const VERT = `#version 300 es
in vec2 a_pos;
void main() {
  gl_Position = vec4(a_pos, 0.0, 1.0);
}`

const FRAG = `#version 300 es
precision highp float;

uniform vec2  u_res;
uniform float u_time;

out vec4 out_color;

void main(void) {
  vec2 uv = (gl_FragCoord.xy * 2.0 - u_res.xy) / min(u_res.x, u_res.y);
  float t = u_time * 0.05;
  float lineWidth = 0.002;

  /* Tres familias de anillos, una por canal, ligeramente desfasadas. */
  vec3 color = vec3(0.0);
  for (int j = 0; j < 3; j++) {
    for (int i = 0; i < 5; i++) {
      color[j] += lineWidth * float(i * i) /
        abs(fract(t - 0.01 * float(j) + float(i) * 0.01) * 5.0
            - length(uv) + mod(uv.x + uv.y, 0.2));
    }
  }

  /* Re-tintado a la paleta de la app: canal R -> teal, G -> violeta, B -> naranja. */
  vec3 tinta = color.r * vec3(0.078, 0.769, 0.714)
             + color.g * vec3(0.549, 0.200, 0.922)
             + color.b * vec3(0.949, 0.502, 0.102);
  tinta = clamp(tinta, 0.0, 1.0);

  /* Alfa = brillo: donde no hay onda el pixel es transparente. Como cada canal
     queda por debajo del maximo, el color ya es valido premultiplicado. */
  float a = max(tinta.r, max(tinta.g, tinta.b));
  out_color = vec4(tinta, a);
}`

function buildProgram(gl: WebGL2RenderingContext): WebGLProgram {
  function compile(type: number, src: string) {
    const s = gl.createShader(type)!
    gl.shaderSource(s, src)
    gl.compileShader(s)
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS))
      console.error('[ShaderBackdrop]', gl.getShaderInfoLog(s))
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

/**
 * Fotograma desde el que arranca la onda. No es 0 a proposito: el home se remonta
 * cada vez que vuelves a el, y en t=0 las cinco familias de anillos estan casi
 * superpuestas — el glow salia plano y tardaba en "abrirse". Aqui ya esta formado
 * desde el primer fotograma.
 */
const FASE_INICIAL = 20

interface Props {
  /**
   * Unidades de tiempo del shader por segundo. El ejemplo original avanzaba
   * 0.05/fotograma (3/s a 60 Hz) y ademas dependia del refresco; aqui es
   * tiempo real y va MUY calmado: es un elemento ambiental permanente, no un
   * efecto que reclame la vista. Bajarlo alarga el ciclo de la onda.
   */
  rate?: number
  className?: string
}

export function ShaderBackdrop({ rate = 0.8, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const reducirMovimiento = useReducedMotion()

  const rateRef = useRef(rate)
  rateRef.current = reducirMovimiento ? 0 : rate

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('webgl2', { antialias: true, premultipliedAlpha: true })
    if (!ctx) {
      console.warn('[ShaderBackdrop] WebGL2 no disponible')
      return
    }
    // Alias no-nulo: dentro del closure del bucle TS pierde el estrechamiento.
    const gl: WebGL2RenderingContext = ctx

    const prog = buildProgram(gl)
    const vao = gl.createVertexArray()!
    const buf = gl.createBuffer()!

    gl.bindVertexArray(vao)
    gl.bindBuffer(gl.ARRAY_BUFFER, buf)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW)
    const posLoc = gl.getAttribLocation(prog, 'a_pos')
    gl.enableVertexAttribArray(posLoc)
    gl.vertexAttribPointer(posLoc, 2, gl.FLOAT, false, 0, 0)
    gl.bindVertexArray(null)
    gl.useProgram(prog)

    const uRes = gl.getUniformLocation(prog, 'u_res')
    const uTime = gl.getUniformLocation(prog, 'u_time')

    // Pixel ratio limitado: es un glow difuminado, no gana nada a 2x.
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5)

    function ajustarTamano() {
      const w = Math.max(1, Math.round(canvas!.clientWidth * dpr))
      const h = Math.max(1, Math.round(canvas!.clientHeight * dpr))
      if (canvas!.width !== w || canvas!.height !== h) {
        canvas!.width = w
        canvas!.height = h
        gl.viewport(0, 0, w, h)
      }
    }

    let rafId: number
    const t0 = performance.now()

    function pintar(t: number) {
      ajustarTamano()
      gl.uniform1f(uTime, t)
      gl.uniform2f(uRes, canvas!.width, canvas!.height)
      gl.bindVertexArray(vao)
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      gl.bindVertexArray(null)
    }

    function frame() {
      // Con reduced-motion no basta con congelar el reloj: hay que SALIR del bucle.
      // Un rAF que repinta el mismo fotograma para siempre gasta GPU y batería sin
      // dibujar nada nuevo. El ResizeObserver se encarga de repintar si hace falta.
      if (rateRef.current === 0) {
        pintar(FASE_INICIAL)
        return
      }
      pintar(FASE_INICIAL + ((performance.now() - t0) / 1000) * rateRef.current)
      rafId = requestAnimationFrame(frame)
    }

    // Redimensionar reasigna `canvas.width`, lo que BORRA el lienzo. Mientras el
    // bucle corre eso da igual (el siguiente fotograma repinta), pero congelados
    // hay que volver a pintar a mano o el glow desaparecería al cambiar de tamaño.
    const ro = new ResizeObserver(() => {
      ajustarTamano()
      if (rateRef.current === 0) pintar(FASE_INICIAL)
    })
    ro.observe(canvas)

    rafId = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(rafId)
      ro.disconnect()
      gl.deleteBuffer(buf)
      gl.deleteVertexArray(vao)
      gl.deleteProgram(prog)
    }
    // `reducirMovimiento` entra en las dependencias para que al desactivar el ajuste
    // del sistema el bucle vuelva a arrancar: `frame` sale con `return`, no se repone solo.
  }, [reducirMovimiento])

  return (
    <canvas
      ref={canvasRef}
      className={className}
      aria-hidden
      style={{ display: 'block', width: '100%', height: '100%' }}
    />
  )
}
