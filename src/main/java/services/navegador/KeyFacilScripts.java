package services.navegador;

/**
 * Hook idempotente pero RE-INSTALABLE:
 * ya no usamos una bandera global. Comprobamos console.X.__keyfacilWrapped.
 * Así, si KeyFacil sobrescribe console.log en runtime, lo volvemos a envolver
 * en la siguiente pasada (cada 1s) o al detectar un nuevo execution context.
 */
final class KeyFacilScripts {

    private KeyFacilScripts() {
    }

    static final String CONSOLE_HOOK = """
            (function() {
                const metodos = ['log', 'info', 'warn', 'error', 'debug'];

                metodos.forEach(function(nombre) {
                    const actual = console[nombre];
                    if (actual && actual.__keyfacilWrapped) return;

                    const original = actual;

                    const wrapper = function() {
                        const args = Array.prototype.slice.call(arguments);
                        const stringified = args.map(function(a) {
                            if (typeof a === 'string') return a;
                            if (a === null || a === undefined) return String(a);
                            if (typeof a === 'number' || typeof a === 'boolean') return String(a);
                            try { return JSON.stringify(a); } catch(e) { return String(a); }
                        });
                        try { original.apply(console, stringified); } catch(e) {}
                    };
                    wrapper.__keyfacilWrapped = true;
                    console[nombre] = wrapper;
                });
            })();
            """;
}