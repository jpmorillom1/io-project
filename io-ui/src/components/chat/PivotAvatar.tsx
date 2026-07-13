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
 *
 * ── Reposo y "pensando" ────────────────────────────────────────────────────
 * El avatar orbita SIEMPRE: es lo que lo hace parecer vivo. Lo que cambia cuando
 * Pivot está pensando (`activo`) es la ENERGÍA — un solo escalar en [0,1] que
 * interpola a la vez la velocidad de órbita y la apertura de las órbitas. Con
 * energía alta los blobs giran más rápido y se separan más del centro, así que la
 * silueta se estira en lóbulos: se nota de un vistazo, sin leer nada.
 *
 * La energía se aproxima exponencialmente a su objetivo, no salta: el avatar se
 * "acelera" y se "calma". Por eso la fase se ACUMULA en JS (`fase += dt·v`) en vez
 * de calcularse como `tiempo · velocidad` dentro del shader — multiplicar el tiempo
 * transcurrido por una velocidad que cambia teletransporta los blobs (era un bug
 * real: con `prefers-reduced-motion` la velocidad caía a 0 y la fase con ella).
 *
 * `prefers-reduced-motion` es el único caso en que el bucle se detiene del todo:
 * queda una silueta compuesta y estática, y se abandona el rAF en lugar de
 * repintar el mismo fotograma para siempre.
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

/* Fase acumulada por el bucle de JS, no tiempo transcurrido: ver la nota del
   componente sobre por que la velocidad no puede multiplicarse aqui dentro. */
uniform float u_fase;

/* Solo ASCII dentro del shader: algunos drivers rechazan el fuente si aparecen
   caracteres no-ASCII, incluso dentro de comentarios.

   u_radio = tamano de cada blob. u_orbit = cuanto se alejan del centro.
   Con u_orbit por debajo de u_radio los blobs nunca se separan: se funden en una
   silueta unica. Al subir u_orbit por encima, la silueta se estira en lobulos
   (es lo que pasa cuando Pivot piensa). u_radio sube un poco a la vez para que
   los lobulos sigan unidos por un istmo y la figura no se rompa en discos. */
uniform float u_radio;
uniform float u_orbit;

in  vec2 v_uv;
out vec4 out_color;

/* Campo de metaball: (R*R)/(d*d). La isosuperficie f = 1 cae a distancia R. */
float campo(vec2 p, vec2 c) {
  vec2 d = p - c;
  return (u_radio * u_radio) / max(dot(d, d), 1e-5);
}

void main() {
  float t = u_fase;
  vec2  p = v_uv;
  vec2  o = vec2(0.5);

  /* Mismas frecuencias y desfases que el ShaderGlow original, pero con un radio
     de orbita mucho menor: antes se alejaban hasta 0.42 del centro. */
  vec2 c1 = o + u_orbit * vec2(sin(t * 0.80),         cos(t * 0.60));
  vec2 c2 = o + u_orbit * vec2(sin(t * 0.50 + 2.094), cos(t * 0.70 + 2.094));
  vec2 c3 = o + u_orbit * vec2(sin(t * 0.65 + 4.189), cos(t * 0.42 + 4.189));
  vec2 c4 = o + u_orbit * vec2(sin(t * 0.45 + 1.047), cos(t * 0.55 + 3.142));

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

/**
 * Los dos extremos entre los que interpola la energía. En reposo el avatar orbita
 * a un ritmo tranquilo pero perfectamente visible; pensando gira al triple y abre
 * las órbitas por encima del radio de los blobs, que es cuando la silueta empieza
 * a estirarse en lóbulos. El radio sube apenas — lo justo para que los lóbulos
 * sigan unidos y la figura no se desarme en cuatro discos.
 */
const REPOSO   = { velocidad: 0.7, orbit: 0.125, radio: 0.145 }
const PENSANDO = { velocidad: 2.1, orbit: 0.185, radio: 0.155 }

/**
 * Constante de tiempo de la aceleración y la calma, en segundos: tras ~1.5 s el
 * avatar ha alcanzado en la práctica su estado. Corta = cambia de golpe.
 */
const TAU = 0.5

/**
 * Fase de partida. No es 0 a propósito: en t=0 los cuatro blobs arrancan de
 * posiciones muy alineadas y la silueta sale sosa. Aquí ya están repartidos.
 */
const FASE_INICIAL = 12

const mezcla = (a: number, b: number, e: number) => a + (b - a) * e

interface Props {
  /** Lado del avatar en px. */
  size?: number
  /** `true` mientras Pivot piensa: sube la energía (gira más rápido y más abierto). */
  activo?: boolean
  className?: string
}

export function PivotAvatar({ size = 34, activo = false, className }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const reducirMovimiento = useReducedMotion()

  // El bucle lee estos valores por ref: cambiar de estado no reinicia el contexto WebGL.
  const energiaObjetivoRef = useRef(0)
  energiaObjetivoRef.current = activo ? 1 : 0

  const reducirRef = useRef(false)
  reducirRef.current = !!reducirMovimiento

  // Con movimiento reducido el bucle se abandona; quien lo repone si el usuario
  // desactiva el ajuste es este puente, no el propio bucle.
  const despertarRef = useRef<() => void>(() => {})

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

    const uFase = gl.getUniformLocation(prog, 'u_fase')
    const uRadio = gl.getUniformLocation(prog, 'u_radio')
    const uOrbit = gl.getUniformLocation(prog, 'u_orbit')

    // Acotado a 2: es un blob difuminado de 34 px, a 3x no gana nada y cuadruplica píxeles.
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const lado = Math.round(size * dpr)
    canvas.width = lado
    canvas.height = lado
    gl.viewport(0, 0, lado, lado)

    let fase = FASE_INICIAL
    let energia = 0
    let rafId = 0
    let anterior = performance.now()

    function pintar() {
      gl.uniform1f(uFase, fase)
      gl.uniform1f(uRadio, mezcla(REPOSO.radio, PENSANDO.radio, energia))
      gl.uniform1f(uOrbit, mezcla(REPOSO.orbit, PENSANDO.orbit, energia))
      gl.clearColor(0, 0, 0, 0)
      gl.clear(gl.COLOR_BUFFER_BIT)
      gl.bindVertexArray(vao)
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
      gl.bindVertexArray(null)
    }

    function frame(ahora: number) {
      // Si el ajuste se activa a mitad de sesión, se sale del bucle dejando la
      // silueta donde esté.
      if (reducirRef.current) {
        rafId = 0
        return
      }

      // Acotado: si la pestaña estuvo oculta, `ahora - anterior` puede ser enorme
      // y la fase daría un salto al volver.
      const dt = Math.min((ahora - anterior) / 1000, 0.05)
      anterior = ahora

      // Aproximación exponencial, independiente de la tasa de refresco: la energía
      // persigue a su objetivo en vez de saltar a él.
      energia += (energiaObjetivoRef.current - energia) * (1 - Math.exp(-dt / TAU))
      fase += dt * mezcla(REPOSO.velocidad, PENSANDO.velocidad, energia)
      pintar()

      rafId = requestAnimationFrame(frame)
    }

    function despertar() {
      if (rafId !== 0) return
      anterior = performance.now()
      rafId = requestAnimationFrame(frame)
    }
    despertarRef.current = despertar

    // Con movimiento reducido se pinta UN fotograma y no se entra al bucle: una
    // silueta estática. Repintar eternamente el mismo cuadro solo gasta batería.
    if (reducirRef.current) pintar()
    else despertar()

    return () => {
      despertarRef.current = () => {}
      if (rafId !== 0) cancelAnimationFrame(rafId)
      gl.deleteBuffer(buf)
      gl.deleteVertexArray(vao)
      gl.deleteProgram(prog)
    }
  }, [size])

  // Si el usuario desactiva el ajuste del sistema, hay que reponer el bucle:
  // nunca llegó a arrancar.
  useEffect(() => {
    if (!reducirMovimiento) despertarRef.current()
  }, [reducirMovimiento])

  return (
    <canvas
      ref={canvasRef}
      className={className}
      aria-hidden
      style={{ width: size, height: size, display: 'block' }}
    />
  )
}
