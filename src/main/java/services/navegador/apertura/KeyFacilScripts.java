package services.navegador.apertura;

final class KeyFacilScripts {

    private KeyFacilScripts() {
    }

    static final String CONSOLE_HOOK = """
            (function() {
                if (!Array.isArray(window.__keyfacilLogs)) window.__keyfacilLogs = [];

                function stringifySeguro(a) {
                    if (a === null || a === undefined) return String(a);
                    const t = typeof a;
                    if (t === 'string') return a;
                    if (t === 'number' || t === 'boolean') return String(a);
                    if (t === 'bigint') return a.toString() + 'n';
                    if (t === 'function') return '[Function]';
                    if (t === 'symbol') return a.toString();
                    if (a instanceof Error) {
                        return JSON.stringify({ name: a.name, message: a.message, stack: a.stack });
                    }
                    const seen = new WeakSet();
                    try {
                        return JSON.stringify(a, function(k, v) {
                            if (typeof v === 'bigint') return v.toString();
                            if (typeof v === 'function') return undefined;
                            if (typeof v === 'symbol') return v.toString();
                            if (typeof v === 'object' && v !== null) {
                                if (seen.has(v)) return '[Circular]';
                                seen.add(v);
                            }
                            return v;
                        });
                    } catch(e) {
                        return '[Unserializable:' + (e && e.message) + ']';
                    }
                }

                // 🔑 Guard POR MÉTODO (no global): si alguien sobrescribe console.log,
                // la próxima pasada lo vuelve a envolver sin tocar los demás.
                ['log','info','warn','error','debug'].forEach(function(nombre) {
                    const actual = console[nombre];
                    if (actual && actual.__keyfacilWrapped) return;
                    const original = actual;
                    const wrapper = function() {
                        const args = Array.prototype.slice.call(arguments);
                        const str = args.map(stringifySeguro);
                        try {
                            window.__keyfacilLogs.push({ t: Date.now(), m: nombre, a: str });
                            if (window.__keyfacilLogs.length > 200) {
                                window.__keyfacilLogs.splice(0, window.__keyfacilLogs.length - 200);
                            }
                        } catch(e) {}
                        try { original.apply(console, str); } catch(e) {}
                    };
                    wrapper.__keyfacilWrapped = true;
                    console[nombre] = wrapper;
                });
            })();
            """;
}