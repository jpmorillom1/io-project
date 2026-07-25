/**
 * Prueba de estres de los solvers de IO — SOLO endpoints deterministas.
 *
 * Cubre los 24 endpoints de resolucion (LP, Transporte, Redes, PL Entera,
 * Inventarios, Programacion Dinamica). Los endpoints de /ai/* quedan FUERA a
 * proposito: dependen de Groq y de ChromaDB, asi que su latencia mide la red de
 * un tercero y no el backend — ademas de quemar cuota de tokens en cada iteracion.
 *
 * Uso:
 *   k6 run stress-solvers.js
 *   k6 run -e SCENARIO=stress -e PERFIL=large stress-solvers.js
 *   k6 run -e MODULO=transporte -e BASE_URL=http://localhost:8080 stress-solvers.js
 *
 * Variables de entorno:
 *   BASE_URL  destino            (default http://localhost:8080)
 *   SCENARIO  smoke|load|stress|spike|soak   (default stress)
 *   PERFIL    small|medium|large  tamano de las instancias (default medium)
 *   MODULO    lp|transporte|redes|entera|inventario|dinamica|todos (default todos)
 *   SEED      semilla del generador de payloads (default 20260719)
 */

import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import * as gen from './payloads.js';

// ---------------------------------------------------------------------------
// Configuracion
// ---------------------------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = BASE_URL + '/api/v1';
const SCENARIO = __ENV.SCENARIO || 'stress';
const MODULO = __ENV.MODULO || 'todos';
const SEED = parseInt(__ENV.SEED || '20260719', 10);

const P = gen.PERFILES[__ENV.PERFIL || 'medium'];
if (!P) {
  throw new Error('PERFIL invalido: ' + __ENV.PERFIL + ' (usa small | medium | large)');
}

// ---------------------------------------------------------------------------
// Catalogo de endpoints — un solver por entrada
// ---------------------------------------------------------------------------

const ENDPOINTS = [
  // --- LP ---
  { modulo: 'lp', nombre: 'lp/simplex', path: '/lp/simplex', body: () => gen.lpSoloLeq(P) },
  { modulo: 'lp', nombre: 'lp/gran-m', path: '/lp/gran-m', body: () => gen.lpMixta(P) },
  { modulo: 'lp', nombre: 'lp/dos-fases', path: '/lp/dos-fases', body: () => gen.lpMixta(P) },
  { modulo: 'lp', nombre: 'lp/grafico', path: '/lp/grafico', body: () => gen.lpGrafico(P) },

  // --- Transporte ---
  { modulo: 'transporte', nombre: 'transporte/esquina-noroeste', path: '/transporte/esquina-noroeste', body: () => gen.transporte(P) },
  { modulo: 'transporte', nombre: 'transporte/costo-minimo', path: '/transporte/costo-minimo', body: () => gen.transporte(P) },
  { modulo: 'transporte', nombre: 'transporte/vogel', path: '/transporte/vogel', body: () => gen.transporte(P) },
  { modulo: 'transporte', nombre: 'transporte/modi', path: '/transporte/modi', body: () => gen.transporte(P) },

  // --- Redes ---
  { modulo: 'redes', nombre: 'redes/dijkstra', path: '/redes/dijkstra', body: () => gen.red(P, true) },
  { modulo: 'redes', nombre: 'redes/kruskal', path: '/redes/kruskal', body: () => gen.red(P, false) },
  { modulo: 'redes', nombre: 'redes/edmonds-karp', path: '/redes/edmonds-karp', body: () => gen.red(P, true) },
  { modulo: 'redes', nombre: 'redes/flujo-costo-minimo', path: '/redes/flujo-costo-minimo', body: () => gen.red(P, true) },
  { modulo: 'redes', nombre: 'redes/asignacion', path: '/redes/asignacion', body: () => gen.asignacion(P) },

  // --- PL Entera ---
  { modulo: 'entera', nombre: 'entera/branch-and-bound', path: '/entera/branch-and-bound', body: () => gen.entera(P) },

  // --- Inventarios ---
  { modulo: 'inventario', nombre: 'inventario/eoq-basico', path: '/inventario/eoq-basico', body: () => gen.eoqBasico() },
  { modulo: 'inventario', nombre: 'inventario/eoq-descuentos', path: '/inventario/eoq-descuentos', body: () => gen.eoqDescuentos(P) },
  { modulo: 'inventario', nombre: 'inventario/eoq-faltantes', path: '/inventario/eoq-faltantes', body: () => gen.eoqFaltantes() },
  { modulo: 'inventario', nombre: 'inventario/produccion-economica', path: '/inventario/produccion-economica', body: () => gen.produccionEconomica() },
  { modulo: 'inventario', nombre: 'inventario/punto-reorden', path: '/inventario/punto-reorden', body: () => gen.puntoReorden() },

  // --- Programacion Dinamica ---
  { modulo: 'dinamica', nombre: 'dinamica/asignacion-recursos', path: '/dinamica/asignacion-recursos', body: () => gen.pdAsignacionRecursos(P) },
  { modulo: 'dinamica', nombre: 'dinamica/mochila', path: '/dinamica/mochila', body: () => gen.pdMochila(P) },
  { modulo: 'dinamica', nombre: 'dinamica/ruta-etapas', path: '/dinamica/ruta-etapas', body: () => gen.pdRutaEtapas(P) },
  { modulo: 'dinamica', nombre: 'dinamica/planificacion-produccion', path: '/dinamica/planificacion-produccion', body: () => gen.pdPlanificacionProduccion(P) },
  { modulo: 'dinamica', nombre: 'dinamica/reemplazo-equipos', path: '/dinamica/reemplazo-equipos', body: () => gen.pdReemplazoEquipos(P) },
];

const ACTIVOS = MODULO === 'todos' ? ENDPOINTS : ENDPOINTS.filter((e) => e.modulo === MODULO);
if (ACTIVOS.length === 0) {
  throw new Error('MODULO invalido: ' + MODULO);
}

// ---------------------------------------------------------------------------
// Metricas propias
// ---------------------------------------------------------------------------

// Latencia del solver aislada del transporte HTTP, para comparar modulos entre si.
const duracionSolver = new Trend('solver_duracion', true);
// Un 5xx, un 400 por payload malformado o un status ERROR cuentan como fallo logico.
const fallosSolver = new Rate('solver_fallos');
// INFACTIBLE / NO_ACOTADO son resultados VALIDOS: se cuentan aparte, no como error.
const noOptimos = new Counter('solver_no_optimos');
const tamanoRespuesta = new Trend('solver_bytes_respuesta');

// ---------------------------------------------------------------------------
// Escenarios de carga
// ---------------------------------------------------------------------------

const ESCENARIOS = {
  // Valida que los 24 payloads son correctos antes de gastar tiempo en una corrida larga.
  smoke: {
    executor: 'shared-iterations',
    vus: 1,
    iterations: ACTIVOS.length,
    maxDuration: '2m',
  },

  // Carga esperada en clase: sube, se mantiene, baja.
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 },
      { duration: '2m', target: 10 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '30s',
  },

  // Estres: escalones crecientes hasta encontrar el punto de quiebre.
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 25 },
      { duration: '2m', target: 25 },
      { duration: '1m', target: 60 },
      { duration: '2m', target: 60 },
      { duration: '1m', target: 120 },
      { duration: '2m', target: 120 },
      { duration: '1m', target: 0 },
    ],
    gracefulRampDown: '1m',
  },

  // Pico subito: mide si el pool de hilos y la JVM absorben la rafaga o se caen.
  spike: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '20s', target: 10 },
      { duration: '10s', target: 250 },
      { duration: '1m', target: 250 },
      { duration: '20s', target: 10 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '1m',
  },

  // Resistencia: carga plana prolongada. Delata fugas de memoria y degradacion del GC.
  soak: {
    executor: 'constant-vus',
    vus: 20,
    duration: '30m',
  },
};

const escenario = ESCENARIOS[SCENARIO];
if (!escenario) {
  throw new Error('SCENARIO invalido: ' + SCENARIO + ' (usa ' + Object.keys(ESCENARIOS).join(' | ') + ')');
}

export const options = {
  scenarios: { solvers: Object.assign({ exec: 'resolver' }, escenario) },
  thresholds: {
    // Presupuestos por modulo: el costo algoritmico es muy distinto entre ellos.
    // Inventarios son formulas cerradas; MODI y B&B son iterativos y caros.
    'http_req_duration{modulo:inventario}': ['p(95)<300'],
    'http_req_duration{modulo:lp}': ['p(95)<800'],
    'http_req_duration{modulo:redes}': ['p(95)<1000'],
    'http_req_duration{modulo:dinamica}': ['p(95)<1500'],
    'http_req_duration{modulo:transporte}': ['p(95)<2000'],
    'http_req_duration{modulo:entera}': ['p(95)<3000'],

    http_req_failed: ['rate<0.01'],
    solver_fallos: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

// ---------------------------------------------------------------------------
// Setup — fallar rapido si la API no esta arriba
// ---------------------------------------------------------------------------

export function setup() {
  gen.sembrar(SEED);
  const sonda = http.post(API + '/inventario/eoq-basico', JSON.stringify(gen.eoqBasico()), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'setup' },
  });

  if (sonda.status !== 200) {
    throw new Error(
      'La API no responde en ' + API + ' (status ' + sonda.status + '). ' +
      'Arranca el backend con: cd io-api && ./gradlew bootRun'
    );
  }

  console.log(
    'Estres de solvers | escenario=' + SCENARIO + ' perfil=' + (__ENV.PERFIL || 'medium') +
    ' modulo=' + MODULO + ' endpoints=' + ACTIVOS.length + ' destino=' + API
  );

  return { arranque: Date.now() };
}

// ---------------------------------------------------------------------------
// Iteracion
// ---------------------------------------------------------------------------

const STATUS_VALIDOS = ['OPTIMO', 'MULTIPLE_OPTIMO', 'INFACTIBLE', 'NO_ACOTADO'];

export function resolver() {
  // Round-robin global sobre el indice de iteracion: reparte la carga por igual
  // entre los endpoints en vez de dejarlo al azar, que sesga las muestras.
  const i = exec.scenario.iterationInTest;
  const ep = ACTIVOS[i % ACTIVOS.length];

  // Semilla distinta por iteracion: cada peticion lleva una instancia diferente,
  // asi ningun cache de la JVM ni del solver abarata artificialmente el resultado.
  gen.sembrar(SEED + i);

  const payload = JSON.stringify(ep.body());
  const params = {
    headers: { 'Content-Type': 'application/json' },
    // 'name' agrupa las metricas de k6; modulo/endpoint alimentan los thresholds.
    tags: { name: ep.nombre, modulo: ep.modulo, endpoint: ep.nombre },
    timeout: '60s',
  };

  const res = http.post(API + ep.path, payload, params);

  duracionSolver.add(res.timings.duration, { modulo: ep.modulo, endpoint: ep.nombre });
  tamanoRespuesta.add(res.body ? res.body.length : 0, { endpoint: ep.nombre });

  const ok = check(
    res,
    {
      'HTTP 200': (r) => r.status === 200,
      'status de solver valido': (r) => {
        const s = leerStatus(r);
        return s !== null && STATUS_VALIDOS.indexOf(s) !== -1;
      },
      'trae pasos del procedimiento': (r) => {
        const b = leerJson(r);
        return b !== null && Array.isArray(b.steps) && b.steps.length > 0;
      },
      'solucion coherente con el status': (r) => {
        const b = leerJson(r);
        if (b === null) return false;
        // Contrato: OPTIMO/MULTIPLE_OPTIMO traen solucion; INFACTIBLE/NO_ACOTADO la traen null.
        const conSolucion = b.status === 'OPTIMO' || b.status === 'MULTIPLE_OPTIMO';
        return conSolucion ? b.solution !== null && b.solution !== undefined : true;
      },
    },
    { modulo: ep.modulo, endpoint: ep.nombre }
  );

  const status = leerStatus(res);
  if (status !== null && status !== 'OPTIMO' && status !== 'MULTIPLE_OPTIMO') {
    noOptimos.add(1, { modulo: ep.modulo, endpoint: ep.nombre, resultado: status });
  }

  fallosSolver.add(!ok, { modulo: ep.modulo, endpoint: ep.nombre });

  if (!ok) {
    // Un solo log por fallo, recortado: en un pico de 250 VUs el ruido tapa la senal.
    console.error(
      '[' + ep.nombre + '] HTTP ' + res.status + ' -> ' +
      String(res.body).substring(0, 200)
    );
  }

  // Pausa corta: sin ella un VU es un bucle cerrado y la prueba mide el cliente,
  // no un patron de uso realista.
  sleep(Math.random() * 0.5 + 0.1);
}

// ---------------------------------------------------------------------------
// Helpers de parseo — un body no-JSON (502 del proxy, stacktrace) no debe reventar el VU
// ---------------------------------------------------------------------------

function leerJson(res) {
  try {
    return res.json();
  } catch (e) {
    return null;
  }
}

function leerStatus(res) {
  const b = leerJson(res);
  return b && typeof b.status === 'string' ? b.status : null;
}
