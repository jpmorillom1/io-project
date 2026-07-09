import type { Transition, Variant, Variants } from 'motion/react'

/**
 * Vocabulario de animación compartido por todos los módulos.
 *
 * La UI imita un IDE: el movimiento debe ser breve y confirmar un cambio de estado,
 * nunca decorar. Todo entra desde abajo o desde el lado, jamás rebota.
 *
 * El respeto a `prefers-reduced-motion` se resuelve una sola vez en `main.tsx`
 * con `<MotionConfig reducedMotion="user">` — no lo repitas por componente.
 */

/** Salida exponencial: arranca rápido, aterriza suave. */
export const EASE_OUT = [0.16, 1, 0.3, 1] as const

export const T_SNAPPY: Transition = { duration: 0.18, ease: EASE_OUT }
export const T_BASE: Transition = { duration: 0.28, ease: EASE_OUT }
export const T_SLOW: Transition = { duration: 0.45, ease: EASE_OUT }

/** Para valores numéricos que cuentan hacia su destino (Z*, costo total, Q*). */
export const SPRING_NUMBER = { stiffness: 120, damping: 22, mass: 0.6 }

/**
 * Par oculto/visible. `InView` exige exactamente estas dos claves, así que no
 * basta con tipar como `Variants` (que admite cualquier nombre de variante).
 */
export type ParVariantes = { hidden: Variant; visible: Variant }

/** Contenedor que escalona a sus hijos. */
export function stagger(childrenDelay = 0.04, initialDelay = 0): Variants {
  return {
    hidden: {},
    visible: {
      transition: { staggerChildren: childrenDelay, delayChildren: initialDelay },
    },
  }
}

/** Hijo estándar: aparece subiendo unos pocos píxeles. */
export const revealUp: ParVariantes = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: T_BASE },
}

/** Filas de tabla: desplazamiento lateral mínimo — la altura no debe saltar. */
export const revealRow: ParVariantes = {
  hidden: { opacity: 0, x: -6 },
  visible: { opacity: 1, x: 0, transition: T_SNAPPY },
}

/** Fichas/chips (variables de decisión, holguras, precios sombra). */
export const revealChip: ParVariantes = {
  hidden: { opacity: 0, y: 6, scale: 0.96 },
  visible: { opacity: 1, y: 0, scale: 1, transition: T_SNAPPY },
}

/**
 * Entrada de las cards de un workspace. Se usa con `<AnimatedGroup variants={cardGroup}>`
 * y se re-dispara cuando la IA entrega algo (ver `revisionIA` en el store): las tarjetas
 * suben desenfocándose de menos a más nítidas, una tras otra.
 *
 * Es deliberadamente más amplio y lento que el resto del vocabulario: aquí el movimiento
 * anuncia "llegó contenido nuevo", no confirma un cambio de estado menor.
 */
export const cardGroup: { container: Variants; item: Variants } = {
  container: {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: { staggerChildren: 0.05 },
    },
  },
  item: {
    hidden: { opacity: 0, y: 40, filter: 'blur(4px)' },
    visible: {
      opacity: 1,
      y: 0,
      filter: 'blur(0px)',
      transition: { duration: 1.2, type: 'spring', bounce: 0.3 },
    },
  },
}

/**
 * Panel que se reemplaza con dirección: +1 avanza (entra por la derecha),
 * −1 retrocede. Se usa con `custom={direccion}` en `TransitionPanel`.
 */
export const panelSlide: { enter: Variant; center: Variant; exit: Variant } = {
  enter: (dir: number) => ({ opacity: 0, x: dir >= 0 ? 24 : -24 }),
  center: { opacity: 1, x: 0 },
  exit: (dir: number) => ({ opacity: 0, x: dir >= 0 ? -24 : 24 }),
}

/** Trazo de una arista/línea SVG que se dibuja sola. */
export const drawPath: ParVariantes = {
  hidden: { pathLength: 0, opacity: 0 },
  visible: {
    pathLength: 1,
    opacity: 1,
    transition: { pathLength: T_SLOW, opacity: { duration: 0.1 } },
  },
}

/** Nodo de un grafo: entra escalando desde su centro. */
export const popIn: ParVariantes = {
  hidden: { opacity: 0, scale: 0.5 },
  visible: {
    opacity: 1,
    scale: 1,
    transition: { type: 'spring', stiffness: 400, damping: 26 },
  },
}
