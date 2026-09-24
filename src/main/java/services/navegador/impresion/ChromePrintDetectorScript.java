package services.navegador.impresion;

/**
 * Script JS que se inyecta en la pestaña de KeyFácil.
 * Solo clickea el botón IMPRIMIR en /#/ventas/nuevo, una vez por instancia.
 */
final class ChromePrintDetectorScript {

    private ChromePrintDetectorScript() {
    }

    static final String SCRIPT = """
            (function() {
                var HOST_OK = 'keyfacil-erp.vitekey.com';
                var HASH_OK = '/ventas/nuevo';
                var MARK    = 'data-kf-print-clicked';

                // --- limpiar instalación previa ---
                if (window.__kfPrintObserver) {
                    try { window.__kfPrintObserver.disconnect(); } catch (e) {}
                    window.__kfPrintObserver = null;
                }
                if (window.__kfPrintHashHandler) {
                    try { window.removeEventListener('hashchange', window.__kfPrintHashHandler); } catch (e) {}
                    window.__kfPrintHashHandler = null;
                }

                function enUrlCorrecta() {
                    return location.hostname === HOST_OK
                        && location.hash.indexOf(HASH_OK) !== -1;
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
}