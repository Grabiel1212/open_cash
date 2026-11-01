package services.navegador.impresion;

import java.io.File;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import helpers.MensajeHelper;
import services.cash.ConfigManagerDB;
import services.tunnel.TunnelConfig;

public class ChromePrintService {

    private static final long REINJECT_MS = 20_000;

    private final ConfigManagerDB configManager;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger idGen = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<Map<String, Object>>> futures = new ConcurrentHashMap<>();

    private Thread worker;
    private volatile WebSocket ws;
    private volatile boolean wsAlive = false;
    private volatile String tabId;
    private volatile String sessionId = null;
    private volatile long lastInject = 0;
    private volatile long lastPrintTs = 0;

    public ChromePrintService(ConfigManagerDB configManager) {
        this.configManager = configManager;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (!running.compareAndSet(false, true))
            return;
        worker = new Thread(this::bucle, "ChromePrintService");
        worker.setDaemon(true);
        worker.start();
        MensajeHelper.info("🖨️ ChromePrintService iniciado (túnel CDP " + TunnelConfig.CDP_BASE + ")");
    }

    public void stop() {
        if (!running.compareAndSet(true, false))
            return;
        if (worker != null)
            worker.interrupt();
        cerrarWs();
        MensajeHelper.info("🛑 ChromePrintService detenido");
    }

    public void abrirNavegador() {
        new Thread(() -> {
            try {
                if (!TunnelConfig.disponible()) {
                    MensajeHelper.error("El puente CDP no responde en " + TunnelConfig.CDP_BASE, null);
                    return;
                }
                MensajeHelper.info("Abre esta URL en tu Chrome:\n" + TunnelConfig.URL_NUEVA_VENTA);
            } catch (Exception e) {
                MensajeHelper.error("No se pudo abrir la pestaña: " + e.getMessage(), e);
            }
        }, "AbrirPestanaPrint").start();
    }

    private void bucle() {
        try {
            if (!TunnelConfig.disponible()) {
                MensajeHelper.error("El puente CDP no responde en " + TunnelConfig.CDP_BASE, null);
                running.set(false);
                return;
            }
            System.out.println("🖨️ ChromePrintService conectado al túnel → " + TunnelConfig.CDP_BASE);
            System.out.println("👉 Esperando pestaña KeyFácil con botón IMPRIMIR...");

            aplicarImpresoraComoDefault();

            while (running.get()) {
                try {
                    asegurarPestanaVentaNueva();

                    if (tabId != null && !wsAlive) {
                        conectar();
                    }

                    long now = System.currentTimeMillis();
                    if (wsAlive && now - lastInject > REINJECT_MS) {
                        inyectarDetector();
                        lastInject = now;
                    }

                    // Mantenemos el fallback por si el usuario hace Ctrl+P manual
                    detectarYForzarImpresionTicket();

                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    System.err.println("⚠️ ChromePrintService: " + e.getMessage());
                    cerrarWs();
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            MensajeHelper.error("Error en ChromePrintService: " + e.getMessage(), e);
        } finally {
            running.set(false);
            cerrarWs();
        }
    }

    private void aplicarImpresoraComoDefault() {
        if (configManager == null)
            return;
        String impresora = null;
        try {
            impresora = configManager.getImpresoraSeleccionada();
        } catch (Exception ignored) {
        }

        if (impresora == null || impresora.isBlank()) {
            System.out.println("⚠️ ChromePrintService: no hay impresora seleccionada en Home.");
            return;
        }
        try {
            String nombreEscapado = impresora.replace("'", "''");
            String ps = "(New-Object -ComObject WScript.Network).SetDefaultPrinter('" + nombreEscapado + "')";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", ps);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);
            System.out.println("🖨️ Impresora default → " + impresora);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo setear la impresora default: " + e.getMessage());
        }
    }

    private void asegurarPestanaVentaNueva() {
        List<Map<String, Object>> pestanas = TunnelConfig.pestanasKeyFacil();
        String first = pestanas.isEmpty() ? null : pestanas.get(0).get("id").toString();

        boolean tabIdVive = false;
        if (tabId != null) {
            for (Map<String, Object> t : pestanas) {
                if (tabId.equals(t.get("id").toString())) {
                    tabIdVive = true;
                    break;
                }
            }
        }

        if (!tabIdVive) {
            if (first != null) {
                tabId = first;
                cerrarWs();
            } else {
                tabId = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void conectar() throws Exception {
        if (tabId == null)
            return;

        String wsUrl = TunnelConfig.buscarWsUrlDePestana(tabId);
        if (wsUrl == null) {
            System.err.println("⚠️ No hay WebSocket para la pestaña " + tabId);
            return;
        }

        idGen.set(0);
        futures.clear();
        lastInject = 0;
        sessionId = null;

        WebSocket socket = TunnelConfig.HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WsListener())
                .get(5000, TimeUnit.MILLISECONDS);

        this.ws = socket;
        this.wsAlive = true;

        Map<String, Object> attach = new LinkedHashMap<>();
        attach.put("targetId", tabId);
        attach.put("flatten", true);
        Map<String, Object> attachResp = enviar("Target.attachToTarget", attach, 3000);

        Map<String, Object> attachResult = (Map<String, Object>) attachResp.get("result");
        if (attachResult == null || !(attachResult.get("sessionId") instanceof String sid)) {
            throw new RuntimeException("Target.attachToTarget no devolvió sessionId");
        }
        this.sessionId = sid;

        enviar("Runtime.enable", new LinkedHashMap<>(), 3000);
        try {
            enviar("Page.enable", new LinkedHashMap<>(), 3000);
        } catch (Exception ignored) {
        }

        System.out.println("✅ ChromePrintService conectado a " + tabId + " (session "
                + sid.substring(0, Math.min(8, sid.length())) + ")");
    }

    private void cerrarWs() {
        wsAlive = false;
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            try {
                w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        futures.clear();
    }

    private class WsListener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket w) {
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                try {
                    handleMessage(msg);
                } catch (Exception ignored) {
                }
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            wsAlive = false;
            System.out.println("🔴 ChromePrintService WS cerrado: " + code);
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            wsAlive = false;
            System.err.println("🔴 ChromePrintService WS error: " + error);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String msg) {
        Map<String, Object> parsed;
        try {
            parsed = TunnelConfig.GSON.fromJson(msg, Map.class);
        } catch (Exception e) {
            return;
        }
        if (parsed == null)
            return;

        Object idObj = parsed.get("id");
        if (idObj instanceof Number) {
            int id = ((Number) idObj).intValue();
            CompletableFuture<Map<String, Object>> f = futures.remove(id);
            if (f != null)
                f.complete(parsed);
            return;
        }

        Object methodObj = parsed.get("method");
        if (!(methodObj instanceof String method))
            return;

        if ("Runtime.consoleAPICalled".equals(method)) {
            Object p = parsed.get("params");
            if (p instanceof Map<?, ?> params) {
                Object args = params.get("args");
                if (args instanceof List<?> list) {
                    for (Object a : list) {
                        if (a instanceof Map<?, ?> m) {
                            Object v = m.get("value");
                            if (v instanceof String s && s.startsWith("KF_PRINT_")) {
                                System.out.println("🖨️ " + s);

                                // 🔑 NUEVO: Cuando el JS intercepta window.print, imprimimos directo sin abrir
                                // ventana
                                if ("KF_PRINT_INTERCEPTED".equals(s)) {
                                    System.out.println("🚀 Disparando impresión directa por CDP...");
                                    new Thread(this::imprimirTicket80mmPorCDP).start();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Map<String, Object> enviar(String metodo, Map<String, Object> params, int timeoutMs) throws Exception {
        if (!wsAlive || ws == null)
            throw new RuntimeException("WS no conectado");

        int id = idGen.incrementAndGet();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        futures.put(id, future);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", id);
        cmd.put("method", metodo);
        cmd.put("params", params);

        if (sessionId != null && !metodo.startsWith("Target.")) {
            cmd.put("sessionId", sessionId);
        }

        ws.sendText(TunnelConfig.GSON.toJson(cmd), true).whenComplete((w, err) -> {
            if (err != null) {
                wsAlive = false;
                CompletableFuture<Map<String, Object>> f = futures.remove(id);
                if (f != null)
                    f.completeExceptionally(err);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            futures.remove(id);
            throw e;
        }
    }

    private void inyectarDetector() {
        String script = """
                (function() {
                    var HOST_OK = 'keyfacil-erp.vitekey.com';
                    var HASH_OK = '/ventas/nuevo';
                    var MARK    = 'data-kf-print-clicked';

                    if (window.__kfPrintObserver) {
                        try { window.__kfPrintObserver.disconnect(); } catch (e) {}
                        window.__kfPrintObserver = null;
                    }
                    if (window.__kfPrintHashHandler) {
                        try { window.removeEventListener('hashchange', window.__kfPrintHashHandler); } catch (e) {}
                        window.__kfPrintHashHandler = null;
                    }

                    // 🔑 INTERCEPTAR window.print PARA QUE NO ABRA EL PANEL DE CHROME
                    if (!window.__kfPrintOverridden) {
                        window.__kfPrintOverridden = true;
                        window.print = function() {
                            console.log('KF_PRINT_INTERCEPTED');
                        };
                    }

                    function enUrlCorrecta() {
                        return location.hostname === HOST_OK && location.hash.indexOf(HASH_OK) !== -1;
                    }

                    function intentarImprimir() {
                        if (!enUrlCorrecta()) return;
                        var botones = document.querySelectorAll('button.button-option');
                        for (var i = 0; i < botones.length; i++) {
                            var btn = botones[i];
                            var texto = (btn.textContent || '').trim().toUpperCase();
                            if (texto !== 'IMPRIMIR') continue;
                            if (btn.getAttribute(MARK) === '1') return;

                            btn.setAttribute(MARK, '1');
                            try {
                                console.log('KF_PRINT_AUTO_CLICK');
                                btn.click();
                                console.log('KF_PRINT_CLICK_OK');
                            } catch (e) {
                                console.log('KF_PRINT_ERROR:' + (e && e.message));
                            }
                            return;
                        }
                    }

                    var obs = new MutationObserver(function() { intentarImprimir(); });
                    window.__kfPrintObserver = obs;
                    obs.observe(document.documentElement, { childList: true, subtree: true });

                    var hh = function() { setTimeout(intentarImprimir, 120); };
                    window.__kfPrintHashHandler = hh;
                    window.addEventListener('hashchange', hh);

                    setTimeout(intentarImprimir, 200);
                    console.log('KF_PRINT_DETECTOR_ACTIVO');
                })();
                """;

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("expression", script);
            params.put("awaitPromise", false);
            enviar("Runtime.evaluate", params, 3000);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo inyectar el detector: " + e.getMessage());
            wsAlive = false;
        }
    }

    private void detectarYForzarImpresionTicket() {
        try {
            List<Map<String, Object>> targets = TunnelConfig.obtenerTargets();
            boolean hayPreview = false;
            for (Map<String, Object> t : targets) {
                Object url = t.get("url");
                Object type = t.get("type");
                if (url == null || type == null)
                    continue;
                if (!"page".equals(type.toString()))
                    continue;
                if (url.toString().startsWith("chrome://print")) {
                    hayPreview = true;
                    break;
                }
            }
            if (!hayPreview)
                return;

            System.out.println("⚠️ Preview nativo detectado (Ctrl+P) → imprimiendo ticket 80mm...");
            imprimirTicket80mmPorCDP();
            cerrarPestanasPreview();
        } catch (Exception e) {
            System.err.println("detectarYForzarImpresionTicket: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void imprimirTicket80mmPorCDP() {
        if (!wsAlive || ws == null || tabId == null) {
            System.err.println("❌ WS no conectado, no puedo imprimir ticket.");
            return;
        }

        // 🔑 Evitar doble impresión si se dispara muy rápido
        long now = System.currentTimeMillis();
        if (now - lastPrintTs < 5000)
            return;
        lastPrintTs = now;

        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("landscape", false);
            params.put("displayHeaderFooter", false);
            params.put("printBackground", true);
            params.put("preferCSSPageSize", true);
            params.put("paperWidth", 3.15);
            params.put("paperHeight", 11.69);
            params.put("marginTop", 0.0);
            params.put("marginBottom", 0.0);
            params.put("marginLeft", 0.0);
            params.put("marginRight", 0.0);
            params.put("scale", 1.0);

            Map<String, Object> resultado = enviar("Page.printToPDF", params, 12000);
            Map<String, Object> result = (Map<String, Object>) resultado.get("result");
            if (result == null) {
                System.err.println("❌ printToPDF sin resultado.");
                return;
            }
            String base64Pdf = (String) result.get("data");
            if (base64Pdf == null || base64Pdf.isEmpty()) {
                System.err.println("⚠️ No se generó el PDF del ticket.");
                return;
            }

            byte[] pdfBytes = java.util.Base64.getDecoder().decode(base64Pdf);
            File archivoTemporal = File.createTempFile("ticket_keyfacil_", ".pdf");
            archivoTemporal.deleteOnExit();
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(archivoTemporal)) {
                fos.write(pdfBytes);
            }
            System.out.println("📄 PDF ticket 80mm generado (" + pdfBytes.length + " bytes)");

            enviarPDFAImpresoraTermica(archivoTemporal);

        } catch (Exception e) {
            System.err.println("❌ Error imprimiendo ticket 80mm: " + e.getMessage());
        }
    }

    private void enviarPDFAImpresoraTermica(File pdfFile) {
        try {
            javax.print.PrintService impresora = null;
            String nombreImpresora = null;
            if (configManager != null) {
                try {
                    nombreImpresora = configManager.getImpresoraSeleccionada();
                } catch (Exception ignored) {
                }
            }

            javax.print.PrintService[] todos = javax.print.PrintServiceLookup.lookupPrintServices(null, null);
            if (nombreImpresora != null && !nombreImpresora.isBlank()) {
                for (javax.print.PrintService ps : todos) {
                    if (ps.getName().equalsIgnoreCase(nombreImpresora)) {
                        impresora = ps;
                        break;
                    }
                }
            }
            if (impresora == null)
                impresora = javax.print.PrintServiceLookup.lookupDefaultPrintService();
            if (impresora == null) {
                System.err.println("⚠️ No se encontró impresora para el ticket.");
                return;
            }

            javax.print.attribute.PrintRequestAttributeSet attrs = new javax.print.attribute.HashPrintRequestAttributeSet();
            javax.print.attribute.standard.MediaSizeName mediaName = javax.print.attribute.standard.MediaSize.findMedia(
                    80, 297, javax.print.attribute.standard.MediaSize.MM);
            if (mediaName != null)
                attrs.add(mediaName);

            attrs.add(new javax.print.attribute.standard.MediaPrintableArea(
                    0f, 0f, 80f, 297f, javax.print.attribute.standard.MediaPrintableArea.MM));
            attrs.add(javax.print.attribute.standard.OrientationRequested.PORTRAIT);
            attrs.add(javax.print.attribute.standard.PrintQuality.NORMAL);

            javax.print.DocPrintJob job = impresora.createPrintJob();
            javax.print.DocFlavor flavor = javax.print.DocFlavor.INPUT_STREAM.AUTOSENSE;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(pdfFile)) {
                javax.print.SimpleDoc doc = new javax.print.SimpleDoc(fis, flavor, null);
                job.print(doc, attrs);
            }
            System.out.println("✅ Ticket 80mm enviado a: " + impresora.getName());

        } catch (Exception e) {
            System.err.println("❌ Error enviando ticket a impresora: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cerrarPestanasPreview() {
        try {
            for (Map<String, Object> t : TunnelConfig.obtenerTargets()) {
                Object url = t.get("url");
                Object id = t.get("id");
                Object type = t.get("type");
                if (url == null || id == null || type == null)
                    continue;
                if (!"page".equals(type.toString()))
                    continue;
                if (!url.toString().startsWith("chrome://print"))
                    continue;
                try {
                    cerrarTarget(id.toString());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("cerrarPestanasPreview: " + e.getMessage());
        }
    }

    private void cerrarTarget(String targetId) throws Exception {
        final WebSocket[] wsRef = new WebSocket[1];
        String browserWs = TunnelConfig.obtenerBrowserWsUrl();

        wsRef[0] = TunnelConfig.HTTP.newWebSocketBuilder()
                .buildAsync(URI.create(browserWs), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket w) {
                        w.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket w, CharSequence d, boolean last) {
                        w.request(1);
                        return null;
                    }
                }).get(3000, TimeUnit.MILLISECONDS);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("targetId", targetId);

        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("id", 1);
        cmd.put("method", "Target.closeTarget");
        cmd.put("params", params);

        wsRef[0].sendText(TunnelConfig.GSON.toJson(cmd), true);
        Thread.sleep(150);
        try {
            wsRef[0].sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
        }
    }
}