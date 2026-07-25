/**
 * Generadores de payloads para las pruebas de estres de los solvers.
 *
 * Cada generador devuelve un body VALIDO y FACTIBLE para su endpoint: el objetivo
 * es medir el costo real del algoritmo, no el del validador rechazando basura.
 *
 * El tamano del problema lo fija PERFIL (small | medium | large): asi el mismo
 * script sirve para medir latencia base y para saturar los solvers caros
 * (MODI, Branch & Bound, asignacion de recursos).
 *
 * Sin imports externos a proposito — el script corre sin acceso a jslib.k6.io.
 */

// ---------------------------------------------------------------------------
// RNG determinista (mulberry32). Sembrar por iteracion hace los runs repetibles.
// ---------------------------------------------------------------------------

let _estado = 1;

export function sembrar(n) {
  _estado = (n >>> 0) || 1;
}

function rand() {
  _estado |= 0;
  _estado = (_estado + 0x6d2b79f5) | 0;
  let t = Math.imul(_estado ^ (_estado >>> 15), 1 | _estado);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

/** Entero aleatorio en [min, max] inclusive. */
function ent(min, max) {
  return Math.floor(rand() * (max - min + 1)) + min;
}

/** Decimal aleatorio en [min, max] con 2 decimales. */
function dec(min, max) {
  return Math.round((rand() * (max - min) + min) * 100) / 100;
}

function elegir(arr) {
  return arr[ent(0, arr.length - 1)];
}

// ---------------------------------------------------------------------------
// Perfiles de tamano
// ---------------------------------------------------------------------------

export const PERFILES = {
  small: {
    lpVars: 3, lpRestr: 3,
    transOrigenes: 3, transDestinos: 3,
    redNodos: 8, redExtra: 6, asignacionN: 4,
    enteraVars: 3, enteraRestr: 3,
    pdRecurso: 10, pdActividades: 3,
    pdCapacidad: 12, pdArticulos: 5,
    pdEtapas: 4, pdAncho: 3,
    pdPeriodos: 4, pdHorizonte: 4, pdEdadMax: 4,
  },
  medium: {
    lpVars: 8, lpRestr: 8,
    transOrigenes: 6, transDestinos: 6,
    redNodos: 25, redExtra: 40, asignacionN: 8,
    enteraVars: 5, enteraRestr: 4,
    pdRecurso: 25, pdActividades: 5,
    pdCapacidad: 30, pdArticulos: 10,
    pdEtapas: 6, pdAncho: 5,
    pdPeriodos: 8, pdHorizonte: 6, pdEdadMax: 6,
  },
  large: {
    lpVars: 15, lpRestr: 15,
    transOrigenes: 10, transDestinos: 10,
    redNodos: 60, redExtra: 150, asignacionN: 14,
    enteraVars: 7, enteraRestr: 6,
    pdRecurso: 50, pdActividades: 8,
    pdCapacidad: 60, pdArticulos: 18,
    pdEtapas: 9, pdAncho: 8,
    pdPeriodos: 12, pdHorizonte: 10, pdEdadMax: 9,
  },
};

// ---------------------------------------------------------------------------
// LP — simplex / gran-m / dos-fases / grafico
// ---------------------------------------------------------------------------

function nombresVars(n) {
  const v = [];
  for (let i = 1; i <= n; i++) v.push('x' + i);
  return v;
}

/**
 * LP solo con restricciones LEQ y rhs >> 0. Siempre factible (x=0 lo cumple) y
 * acotada (todos los coeficientes positivos y se MAXIMIZA). Sirve para /simplex,
 * que rechaza GEQ y EQ.
 */
export function lpSoloLeq(p) {
  const n = p.lpVars;
  const restricciones = [];
  for (let i = 0; i < p.lpRestr; i++) {
    const coefs = [];
    for (let j = 0; j < n; j++) coefs.push(dec(1, 9));
    restricciones.push({ coeficientes: coefs, tipo: 'LEQ', rhs: dec(60, 220) });
  }
  const obj = [];
  for (let j = 0; j < n; j++) obj.push(dec(2, 20));

  return {
    variables: nombresVars(n),
    objetivo: { coeficientes: obj, tipo: 'MAXIMIZAR' },
    restricciones: restricciones,
  };
}

/**
 * LP mixta para gran-m / dos-fases: las mismas LEQ holgadas mas una GEQ y una EQ
 * de rhs pequeno. La construccion garantiza factibilidad (el poliedro LEQ es amplio
 * y las GEQ/EQ piden muy poco), asi el solver hace trabajo real en lugar de cortar
 * en la fase 1 por infactibilidad.
 */
export function lpMixta(p) {
  const modelo = lpSoloLeq(p);
  const n = modelo.variables.length;

  const unos = new Array(n).fill(1.0);
  modelo.restricciones.push({ coeficientes: unos, tipo: 'GEQ', rhs: dec(1, 4) });

  const eq = new Array(n).fill(0.0);
  eq[0] = 1.0;
  eq[n - 1] = 1.0;
  modelo.restricciones.push({ coeficientes: eq, tipo: 'EQ', rhs: dec(2, 6) });

  return modelo;
}

/** El metodo grafico exige exactamente 2 variables; solo variamos las restricciones. */
export function lpGrafico(p) {
  const restricciones = [];
  const cuantas = Math.min(p.lpRestr, 8);
  for (let i = 0; i < cuantas; i++) {
    restricciones.push({
      coeficientes: [dec(1, 9), dec(1, 9)],
      tipo: 'LEQ',
      rhs: dec(40, 200),
    });
  }
  return {
    variables: ['x1', 'x2'],
    objetivo: { coeficientes: [dec(2, 20), dec(2, 20)], tipo: 'MAXIMIZAR' },
    restricciones: restricciones,
  };
}

// ---------------------------------------------------------------------------
// Transporte
// ---------------------------------------------------------------------------

/**
 * Oferta y demanda se generan independientes: si no cuadran el backend balancea
 * solo con el origen/destino "Ficticio", y de paso ejercitamos el Balanceador.
 */
export function transporte(p) {
  const m = p.transOrigenes;
  const n = p.transDestinos;

  const origenes = [];
  const oferta = [];
  for (let i = 0; i < m; i++) {
    origenes.push('O' + (i + 1));
    oferta.push(ent(20, 120));
  }

  const destinos = [];
  const demanda = [];
  for (let j = 0; j < n; j++) {
    destinos.push('D' + (j + 1));
    demanda.push(ent(20, 120));
  }

  const costos = [];
  for (let i = 0; i < m; i++) {
    const fila = [];
    for (let j = 0; j < n; j++) fila.push(ent(2, 30));
    costos.push(fila);
  }

  return { origenes, destinos, oferta, demanda, costos };
}

// ---------------------------------------------------------------------------
// Redes
// ---------------------------------------------------------------------------

/**
 * Grafo conexo por construccion: primero una espina dorsal N0->N1->...->Nk (garantiza
 * que el sumidero es alcanzable y que Kruskal encuentra un MST), luego aristas extra
 * hacia adelante para dar alternativas que el algoritmo tenga que descartar.
 *
 * Cada arista lleva peso, capacidad y costo a la vez: el validador de cada metodo
 * mira solo el campo que le toca, asi un unico generador sirve para los cuatro.
 */
export function red(p, dirigido) {
  const n = p.redNodos;
  const nodos = [];
  for (let i = 0; i < n; i++) nodos.push('N' + i);

  const aristas = [];
  const push = (a, b) => {
    aristas.push({
      origen: 'N' + a,
      destino: 'N' + b,
      peso: ent(1, 25),
      capacidad: ent(3, 20),
      costo: ent(1, 12),
    });
  };

  for (let i = 0; i < n - 1; i++) push(i, i + 1);

  for (let k = 0; k < p.redExtra; k++) {
    const a = ent(0, n - 2);
    const b = ent(a + 1, n - 1);
    if (b !== a + 1 || rand() < 0.3) push(a, b);
  }

  return {
    nodos,
    aristas,
    dirigido: dirigido,
    fuente: 'N0',
    sumidero: 'N' + (n - 1),
  };
}

/** Asignacion: cuerpo distinto (matriz agentes x tareas). n != m fuerza el balanceo. */
export function asignacion(p) {
  const n = p.asignacionN;
  const m = rand() < 0.3 ? n + 1 : n;

  const agentes = [];
  for (let i = 0; i < n; i++) agentes.push('A' + (i + 1));

  const tareas = [];
  for (let j = 0; j < m; j++) tareas.push('T' + (j + 1));

  const matrizCostos = [];
  for (let i = 0; i < n; i++) {
    const fila = [];
    for (let j = 0; j < m; j++) fila.push(ent(1, 40));
    matrizCostos.push(fila);
  }

  // `dirigido` es un boolean PRIMITIVO en ModeloRed: omitirlo hace que Jackson
  // falle con 500 aunque la asignacion no use el grafo. Ver nota en el README.
  return { agentes, tareas, matrizCostos, dirigido: true };
}

// ---------------------------------------------------------------------------
// PL Entera — Branch & Bound
// ---------------------------------------------------------------------------

/**
 * Se mantiene deliberadamente pequeno: B&B es exponencial y con 15 variables una
 * sola peticion puede tocar el tope MAX_NODOS=5000 y dominar toda la prueba. Aqui
 * queremos carga sostenida, no un unico outlier.
 */
export function entera(p) {
  const n = p.enteraVars;
  const restricciones = [];
  for (let i = 0; i < p.enteraRestr; i++) {
    const coefs = [];
    for (let j = 0; j < n; j++) coefs.push(ent(1, 9));
    restricciones.push({ coeficientes: coefs, tipo: 'LEQ', rhs: ent(20, 60) });
  }

  const obj = [];
  const tipos = [];
  for (let j = 0; j < n; j++) {
    obj.push(ent(3, 25));
    tipos.push(rand() < 0.4 ? 'BINARIA' : 'ENTERA');
  }

  return {
    relajacion: {
      variables: nombresVars(n),
      objetivo: { coeficientes: obj, tipo: 'MAXIMIZAR' },
      restricciones: restricciones,
    },
    tiposVariable: tipos,
  };
}

// ---------------------------------------------------------------------------
// Inventarios — formulas cerradas, siempre OPTIMO
// ---------------------------------------------------------------------------

export function eoqBasico() {
  return {
    demanda: ent(1000, 50000),
    costoOrden: dec(20, 400),
    costoMantener: dec(1, 25),
  };
}

export function eoqFaltantes() {
  const base = eoqBasico();
  base.costoFaltante = dec(5, 60);
  return base;
}

/** POQ exige P > D; se genera la tasa como multiplo de la demanda para garantizarlo. */
export function produccionEconomica() {
  const base = eoqBasico();
  base.tasaProduccion = Math.round(base.demanda * dec(1.5, 5));
  return base;
}

export function puntoReorden() {
  const base = eoqBasico();
  base.leadTimeDias = ent(2, 45);
  base.diasHabiles = elegir([250, 300, 360]);
  return base;
}

/** Tramos ordenados por cantidadMinima ascendente y precio descendente (all-units). */
export function eoqDescuentos(p) {
  const cuantos = p.lpVars <= 3 ? 3 : 5;
  const tramos = [];
  let corte = 0;
  let precio = dec(40, 120);
  for (let i = 0; i < cuantos; i++) {
    tramos.push({ cantidadMinima: corte, precioUnitario: precio });
    corte += ent(300, 1200);
    precio = Math.round(precio * dec(0.85, 0.97) * 100) / 100;
  }
  return {
    demanda: ent(2000, 60000),
    costoOrden: dec(30, 400),
    tasaMantenerPorcentaje: dec(0.1, 0.35),
    tramos: tramos,
  };
}

// ---------------------------------------------------------------------------
// Programacion Dinamica
// ---------------------------------------------------------------------------

/** retornos debe cubrir x = 0..recursoTotal, es decir tamano recursoTotal + 1. */
export function pdAsignacionRecursos(p) {
  const R = p.pdRecurso;
  const actividades = [];
  for (let i = 0; i < p.pdActividades; i++) {
    const retornos = [];
    let acum = 0;
    for (let x = 0; x <= R; x++) {
      if (x > 0) acum += dec(0.5, 8);
      retornos.push(Math.round(acum * 100) / 100);
    }
    actividades.push({ nombre: 'Act' + (i + 1), retornos });
  }
  return { recursoTotal: R, actividades, sentido: 'MAXIMIZAR' };
}

export function pdMochila(p) {
  const C = p.pdCapacidad;
  const articulos = [];
  for (let i = 0; i < p.pdArticulos; i++) {
    const multiunidad = rand() < 0.4;
    articulos.push({
      nombre: 'Art' + (i + 1),
      peso: ent(1, Math.max(2, Math.floor(C / 3))),
      valor: dec(2, 40),
      unidadesMaximas: multiunidad ? ent(2, 4) : null,
    });
  }
  return { capacidad: C, articulos };
}

/**
 * Red por etapas totalmente conectada entre etapas consecutivas: siempre existe
 * camino origen -> destino, asi el resultado es OPTIMO y no INFACTIBLE.
 */
export function pdRutaEtapas(p) {
  const K = p.pdEtapas;
  const ancho = p.pdAncho;

  const etapasRuta = [];
  for (let k = 1; k <= K; k++) {
    const nodos = [];
    const cuantos = k === 1 || k === K ? 1 : ancho;
    for (let i = 0; i < cuantos; i++) nodos.push('E' + k + '_' + (i + 1));
    etapasRuta.push({ etapa: k, nodos });
  }

  const arcos = [];
  for (let k = 0; k < K - 1; k++) {
    for (const o of etapasRuta[k].nodos) {
      for (const d of etapasRuta[k + 1].nodos) {
        arcos.push({ origen: o, destino: d, costo: ent(1, 30) });
      }
    }
  }

  return { etapasRuta, arcos, sentido: 'MINIMIZAR' };
}

/**
 * capacidadProduccion se fija por encima de la demanda maxima y el almacen holgado:
 * asegura que el plan es factible en lugar de cortar temprano con INFACTIBLE.
 */
export function pdPlanificacionProduccion(p) {
  const T = p.pdPeriodos;
  const demandas = [];
  for (let t = 0; t < T; t++) demandas.push(ent(2, 12));
  const maxDemanda = Math.max.apply(null, demandas);

  return {
    demandas,
    costoPreparacion: dec(20, 150),
    costoUnitarioProduccion: dec(1, 15),
    costoMantener: dec(0.5, 6),
    capacidadProduccion: maxDemanda + ent(4, 12),
    capacidadAlmacen: maxDemanda + ent(3, 10),
    inventarioInicial: 0,
    inventarioFinal: 0,
  };
}

/** tablaEdades debe cubrir 0..edadMaxima sin huecos. */
export function pdReemplazoEquipos(p) {
  const edadMax = p.pdEdadMax;
  const tablaEdades = [];
  let ingreso = dec(40, 90);
  let costoOperacion = dec(5, 15);
  let valorRescate = dec(30, 70);

  for (let e = 0; e <= edadMax; e++) {
    tablaEdades.push({
      edad: e,
      ingreso: Math.round(ingreso * 100) / 100,
      costoOperacion: Math.round(costoOperacion * 100) / 100,
      valorRescate: Math.round(valorRescate * 100) / 100,
    });
    ingreso = Math.max(1, ingreso - dec(2, 8));
    costoOperacion += dec(2, 9);
    valorRescate = Math.max(1, valorRescate - dec(3, 10));
  }

  return {
    horizonteAnios: p.pdHorizonte,
    edadInicial: 0,
    edadMaxima: edadMax,
    costoCompra: dec(60, 200),
    tablaEdades,
  };
}
