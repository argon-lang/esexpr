
export function valuesEqual(a: unknown, b: unknown): boolean {
    if(typeof a !== typeof b) {
        return false;
    }
    else if(typeof a === "object") {
        if(a === b) {
            return true;
        }

        if(a === null || b == null) {
            return false;
        }

        if(a instanceof Uint8Array) {
            if(!(b instanceof Uint8Array)) {
                return false;
            }

            if(a.length !== b.length) {
                return false;
            }

            for(let i = 0; i < a.length; ++i) {
                if(a[i] !== b[i]) {
                    return false;
                }
            }

            return true;
        }

        if(a instanceof Map) {
            if(!(b instanceof Map)) {
                return false;
            }

            for(const k of a.keys()) {
                if(!b.has(k)) {
                    return false;
                }

                if(!valuesEqual(a.get(k), b.get(k))) {
                    return false;
                }
            }

            for(const k of b.keys()) {
                if(!a.has(k)) {
                    return false;
                }
            }

            return true;
        }

        if(a instanceof Set) {
            if(!(b instanceof Set)) {
                return false;
            }

            for(const k of a) {
                if(!b.has(k)) {
                    return false;
                }
            }

            for(const k of b) {
                if(!a.has(k)) {
                    return false;
                }
            }

            return true;
        }
        

        const aKeys = Object.keys(a);
        const bKeys = Object.keys(b);
        if(!aKeys.every(k => bKeys.includes(k)) || !bKeys.every(k => aKeys.includes(k))) {
            return false;
        }

        for(const k of aKeys) {
            if(!valuesEqual((a as any)[k], (b as any)[k])) {
                return false;
            }
        }

        return true;
    }

    return a === b;
}
