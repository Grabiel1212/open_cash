package services.navegador;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Procesa eventos Runtime.consoleAPICalled y construye el "item" de venta.
 *
 * KeyFacil hace: console.log("🗃️ PROCESANDO VENTA ...", objetoVenta)
 * → llega un STRING + un OBJETO con objectId.
 * → resolvemos el objeto con Runtime.callFunctionOn(JSON.stringify).
 */
class KeyFacilConsoleProcessor {

    private static final Gson GSON = new GsonBuilder().create();
    private static final long DEDUPE_MS = 4_000;

    private final KeyFacilSession session;
    private final VentaListenerService ventaListener;
    private final Map<Integer, Long> recientes = new ConcurrentHashMap<>();

    KeyFacilConsoleProcessor(KeyFacilSession session, VentaListenerService ventaListener) {
        this.session = session;
        this.ventaListener = ventaListener;
    }

    // =====================================================
    // Manejo del evento console
    // =====================================================
    @SuppressWarnings("unchecked")
    void handleConsoleEvent(Map<String, Object> params) {

        Object argsObj = params.get("args");
        if (!(argsObj instanceof List<?> argsList))
            return;

        boolean menciona = false;
        String jsonStr = null;
        String objectId = null;

        for (Object argObj : argsList) {
            if (!(argObj instanceof Map<?, ?> argMap))
                continue;

            Object typeObj = argMap.get("type");
            Object valueObj = argMap.get("value");
            Object objIdObj = argMap.get("objectId");

            // --- String: puede contener "PROCESANDO" y/o un JSON embebido ---
            if ("string".equals(typeObj) && valueObj instanceof String s) {
                if (s.toUpperCase().contains("PROCESANDO")) {
                    menciona = true;
                }
                String cand = extraerJson(s);
                if (cand != null) {
                    try {
                        Map<String, Object> p = GSON.fromJson(cand, Map.class);
                        if (p != null && p.containsKey("lista_metodos_pago")) {
                            if (jsonStr == null || cand.length() > jsonStr.length()) {
                                jsonStr = cand;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            // --- Objeto JS: guardamos su objectId para resolverlo después ---
            else if ("object".equals(typeObj) && objIdObj instanceof String oid) {
                objectId = oid;
            }
        }

        if (!menciona)
            return;

        // Caso A: el JSON ya venía como string → procesar directo
        if (jsonStr != null) {
            procesarJsonStr(jsonStr);
            return;
        }

        // Caso B: hay un objeto JS → pedirle a Chrome que lo stringifique
        if (objectId != null) {
            final String oid = objectId;
            session.fetchObjectAsJson(oid, jsonFromObject -> {
                if (jsonFromObject != null && jsonFromObject.startsWith("{")) {
                    procesarJsonStr(jsonFromObject);
                } else {
                    System.out.println("⚠️ [" + session.shortId() + "] no se pudo stringificar la venta");
                }
            });
            return;
        }

        System.out.println("⚠️ [" + session.shortId() + "] PROCESANDO sin payload");
    }

    // =====================================================
    // Procesar el JSON ya extraído
    // =====================================================
    @SuppressWarnings("unchecked")
    private void procesarJsonStr(String jsonStr) {

        if (yaProcesado(jsonStr)) {
            System.out.println("⏭️ [" + session.shortId() + "] duplicado ignorado");
            return;
        }

        final String jsonFinal = jsonStr;
        KeyFacilSession.VENTA_EXECUTOR.submit(() -> {
            try {
                Map<String, Object> ventaJson = GSON.fromJson(jsonFinal, Map.class);
                Map<String, Object> item = construirVenta(ventaJson);
                if (item != null) {
                    ventaListener.procesarVenta(item);
                }
            } catch (Exception e) {
                System.err.println("⚠️ [" + session.shortId() + "] parse venta: " + e.getMessage());
            }
        });
    }

    private boolean yaProcesado(String json) {
        long now = System.currentTimeMillis();
        recientes.entrySet().removeIf(e -> now - e.getValue() > DEDUPE_MS);
        int hash = json.hashCode();
        Long prev = recientes.putIfAbsent(hash, now);
        return prev != null && (now - prev) < DEDUPE_MS;
    }

    // =====================================================
    // Extraer JSON embebido en un string
    // =====================================================
    private String extraerJson(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("{")) {
            return trimmed;
        }
        int idx = trimmed.indexOf('{');
        if (idx != -1) {
            String sub = trimmed.substring(idx).trim();
            if (sub.startsWith("{"))
                return sub;
        }
        return null;
    }

    // =====================================================
    // Construcción del item de venta
    // =====================================================
    @SuppressWarnings("unchecked")
    private Map<String, Object> construirVenta(Map<String, Object> v) {

        if (v == null)
            return null;

        String claveUnica;
        Object uuid = v.get("uuid");
        Object id = v.get("Id");

        if (uuid != null) {
            claveUnica = "UUID:" + uuid;
        } else if (id != null) {
            claveUnica = "ID:" + id;
        } else {
            Object idSerie = v.get("id_serie");
            Object serieDesc = v.get("serie_descripcion");
            Object total = v.get("total");
            Object clienteDoc = v.get("cliente_doc");
            Object fechaUtc = v.get("fecha_emision_utc");
            if (fechaUtc == null)
                fechaUtc = v.get("fecha_emision");
            Object listaProd = v.get("lista_productos");
            int numProd = (listaProd instanceof List<?> l) ? l.size() : 0;

            if (idSerie == null && serieDesc == null)
                return null;

            claveUnica = "FB:" + fechaUtc + "|" + idSerie + "|" + serieDesc
                    + "|" + total + "|" + clienteDoc + "|" + numProd;
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("claveUnica", claveUnica);
        item.put("uuid", uuid);
        item.put("id", id);
        item.put("serie", v.get("serie") != null ? v.get("serie") : v.get("serie_descripcion"));
        item.put("numero", v.get("id_numero"));
        item.put("total", v.get("total"));
        item.put("cliente", v.get("cliente_nombre"));
        item.put("responsable", v.get("responsable"));

        List<String> detallePagos = new ArrayList<>();
        List<Double> montosPago = new ArrayList<>();
        boolean esEfectivo = false;

        Object metodosObj = v.get("lista_metodos_pago");
        if (metodosObj instanceof List<?> metodos) {
            for (Object mObj : metodos) {
                if (!(mObj instanceof Map<?, ?> mMap))
                    continue;
                Map<String, Object> m = (Map<String, Object>) mMap;

                Object idCaja = m.get("id_caja");
                Object opcionesObj = m.get("select_metodo_pago_sucursal");
                if (!(opcionesObj instanceof List<?> opciones))
                    continue;

                Map<String, Object> elegido = null;
                if (idCaja != null) {
                    for (Object oObj : opciones) {
                        if (!(oObj instanceof Map<?, ?> oMap))
                            continue;
                        Map<String, Object> o = (Map<String, Object>) oMap;
                        if (idsIguales(o.get("Id"), idCaja)) {
                            elegido = o;
                            break;
                        }
                    }
                }

                if (elegido == null) {
                    detallePagos.add("?");
                    montosPago.add(parseMonto(m.get("monto")));
                    continue;
                }

                Object descObj = elegido.get("descripcion");
                String desc = descObj != null ? descObj.toString() : "?";
                double monto = parseMonto(m.get("monto"));

                boolean metodoMarcadoEfectivo = false;
                Object ef = elegido.get("efectivo");
                if (ef instanceof Number && ((Number) ef).intValue() == 1) {
                    metodoMarcadoEfectivo = true;
                }
                boolean descripcionEsEfectivo = desc.toLowerCase().contains("efectivo");

                boolean estePagoEsEfectivo = (metodoMarcadoEfectivo || descripcionEsEfectivo);

                System.out.println("💳 [" + session.shortId() + "] pago → desc='" + desc
                        + "' monto=" + monto
                        + " efectivo_field=" + metodoMarcadoEfectivo
                        + " descEfe=" + descripcionEsEfectivo
                        + " → cuentaEfectivo=" + estePagoEsEfectivo);

                if (estePagoEsEfectivo)
                    esEfectivo = true;

                detallePagos.add(desc);
                montosPago.add(monto);
            }
        }

        item.put("detallePagos", detallePagos);
        item.put("montosPagos", montosPago);
        item.put("esEfectivo", esEfectivo);
        item.put("pago", !detallePagos.isEmpty() ? String.join(" + ", detallePagos) : "DESCONOCIDO");

        return item;
    }

    // =====================================================
    // Utilidades
    // =====================================================
    private static boolean idsIguales(Object a, Object b) {
        if (a == null || b == null)
            return false;
        Double da = toDouble(a);
        Double db = toDouble(b);
        if (da != null && db != null)
            return da.doubleValue() == db.doubleValue();
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n)
            return n.doubleValue();
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static double parseMonto(Object montoObj) {
        if (montoObj instanceof Number n)
            return n.doubleValue();
        if (montoObj instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception ignored) {
            }
        }
        return 0d;
    }
}