import { useEffect, useRef } from 'react'
import { useReducedMotion } from 'motion/react'

/**
 * Avatar del Asistente Pivot.
 *
 * Un solo shader hace las dos cosas:
 *  - la SILUETA es un campo de metaballs (isosuperficie), así que los blobs se
 *    funden entre sí en vez de verse como discos sueltos;
 *  - el RELLENO es el mismo glow aditivo de `ShaderGlow`: cada blob aporta color
 *    con caída exponencial y donde se solapan el color se suma y se enciende.
 *
 * Ese halo por suma es lo que hace el efecto; no se reproduce solo con la paleta.
 */

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
uniform float u_speed;

in  vec2 v_uv;
out vec4 out_color;

/* Solo ASCII dentro del shader: algunos drivers rechazan el fuente si aparecen
   caracteres no-ASCII, incluso dentro de comentarios.

   RADIO = tamano de cada blob. ORBIT = cuanto se alejan del centro.
   Con ORBIT por debajo de RADIO los blobs nunca se separan: se funden en una
   silueta unica con lobulos. Subir ORBIT los separa; subir RADIO los engorda. */
const float RADIO = 0.145;
const float ORBIT = 0.125;

/* Campo de metaball: (R*R)/(d*d). La isosuperficie f = 1 cae a distancia R. */
float campo(vec2 p, vec2 c) {
  vec2 d = p - c;
  return (RADIO * RADIO) / max(dot(d, d), 1e-5);
}

void main() {
  float t = u_time * u_speed;
  vec2  p = v_uv;
  vec2  o = vec2(0.5);

  /* Mismas frecuencias y desfases que el ShaderGlow original, pero con un radio
     de orbita mucho menor: antes se alejaban hasta 0.42 del centro. */
  vec2 c1 = o + ORBIT * vec2(sin(t * 0.80),         cos(t * 0.60));
  vec2 c2 = o + ORBIT * vec2(sin(t * 0.50 + 2.094), cos(t * 0.70 + 2.094));
  vec2 c3 = o + ORBIT * vec2(sin(t * 0.65 + 4.189), cos(t * 0.42 + 4.189));
  vec2 c4 = o + ORBIT * vec2(sin(t * 0.45 + 1.047), cos(t * 0.55 + 3.142));

  /* Silueta: suma de los cuatro campos, recortada en la isosuperficie f = 1. */
  float f = campo(p, c1) + campo(p, c2) + campo(p, c3) + campo(p, c4);
  float a = smoothstep(0.90, 1.35, f);

  /* Relleno. Los mismos cuatro colores y la misma caida exponencial del
     ShaderGlow, pero PONDERADOS en vez de sumados: se divide entre la suma de
     pesos. Sumarlos saturaba los tres canales a 1.0 (o sea, blanco) ahora que
     los blobs se solapan siempre.

     SUAVIDAD manda: baja = cada blob impone su color y se ven nitidos;
     alta = los colores se mezclan y tienden al gris. */
  const float SUAVIDAD = 0.16;

  float w1 = exp(-length(p - c1) / SUAVIDAD);
  float w2 = exp(-length(p - c2) / SUAVIDAD);
  float w3 = exp(-length(p - c3) / SUAVIDAD);
  float w4 = exp(-length(p - c4) / SUAVIDAD);

  vec3 col = vec3(0.55, 0.20, 0.92) * w1
           + vec3(0.14, 0.76, 0.84) * w2
           + vec3(0.95, 0.50, 0.10) * w3
           + vec3(0.38, 0.08, 0.72) * w4;
  col /= max(w1 + w2 + w3 + w4, 1e-4);

  /* Realce leve donde dos blobs se encuentran, sin llegar a quemar el color. */
  col = clamp(col * 1.15, 0.0, 1.0);

  /* El canvas compone con alfa premultiplicado: hay que escalar el color por a. */
  out_color = vec4(col * a, a);
}`

// ─── WebGL ───────────────────────────────────────────────────────────────────

function buildProgram(gl: WebGL2RenderingContext): WebGLProgram {
  function compile(type: number, src: string) {
    const s = gl.createShader(type)!
    gl.shaderSource(s, src)
    gl.compileShader(s)
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS))
      console.error('[PivotAvatar]', gl.getShaderInfoLog(s))
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

// ─── Componente ──────────────────────────────────────────────────────────────

interface Props {
  /** Lado del avatar en px. */
  size?: number
  className?: string
}

export function PivotAvatar({ size = 56, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const reducirMovimiento = useReducedMotion()

  // El bucle lee la velocidad por ref: cambiarla no reinicia el contexto WebGL.
  const speedRef = useRef(0.7)
  speedRef.current = reducirMovimiento ? 0 : 0.7

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('webgl2', { premultipliedAlpha: true })
    if (!ctx) {
      console.warn('[PivotAvatar] WebGL2 no disponible')
      return
    }
    // Alias no-nulo: dentro del closure del bucle TS pierde el estrechamiento de `ctx`.
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

    const uTime = gl.getUniformLocation(prog, 'u_time')
    const uSpeed = gl.getUniformLocation(prog, 'u_speed')

    const dpr = window.devicePixelRatio || 1
    const lado = Math.round(size * dpr)
    canvas.width = lado
    canvas.height = lado
    gl.viewport(0, 0, lado, lado)

    let rafId: number
    const t0 = performance.now()

    function frame() {
      const t = (performance.now() - t0) / 1000
      gl.uniform1f(uTime, t)
      gl.uniform1f(uSpeed, speedRef.current)
      gl.clearColor(0, 0, 0, 0)
      gl.clear(gl.COLOR_BUFFER_BIT)
      gl.bindVertexArray(vao)
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      gl.bindVertexArray(null)
      rafId = requestAnimationFrame(frame)
    }

    rafId = requestAnimationFrame(frame)

    return () => {
      cancelAnimationFrame(rafId)
      gl.deleteBuffer(buf)
      gl.deleteVertexArray(vao)
      gl.deleteProgram(prog)
    }
  }, [size])

  return (
    <canvas
      ref={canvasRef}
      className={className}
      style={{ width: size, height: size, display: 'block' }}
    />
  )
}
