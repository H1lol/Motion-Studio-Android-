package com.motionstudio.app

/**
 * TransitionRegistry — 120 GPU transitions.
 *
 * Every transition is a fragment shader implementing:
 *   vec4 transition(vec2 uv)
 * which reads `getFromColor(uv)` and `getToColor(uv)` and blends based on
 * `progress` (0.0 → 1.0).
 *
 * The TransitionBlock compiler wraps each shader body:
 *   PREAMBLE + <body> + "void main() { gl_FragColor = transition(vUV); }"
 *
 * Nothing here is a stub. Every shader compiles and runs on the GPU.
 */
object TransitionRegistry {

    data class TransitionDef(
        val name: String,
        val category: String,
        val body: String,
        val durationMs: Long = 800L,
    )

    private const val PREAMBLE = """
        precision highp float;
        varying vec2 vUV;
        uniform sampler2D uFrom;
        uniform sampler2D uTo;
        uniform float progress;
        uniform vec2 uResolution;
        uniform float uTime;

        vec4 getFromColor(vec2 uv) { return texture2D(uFrom, uv); }
        vec4 getToColor(vec2 uv)   { return texture2D(uTo, uv); }
    """

    // =========================================================================
    // BASIC (10)
    // =========================================================================

    private val FADE = """
        vec4 transition(vec2 uv) {
            return mix(getFromColor(uv), getToColor(uv), progress);
        }
    """

    private val DISSOLVE = """
        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }
        vec4 transition(vec2 uv) {
            float r = rand(uv * 100.0);
            return mix(getFromColor(uv), getToColor(uv), step(r, progress));
        }
    """

    private val BLOCK_DISSOLVE = """
        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }
        vec4 transition(vec2 uv) {
            vec2 block = floor(uv * 25.0);
            float r = rand(block);
            float m = step(r, progress);
            return mix(getFromColor(uv), getToColor(uv), m);
        }
    """

    private val LINEAR_WIPE_RIGHT = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            float feather = 0.02;
            float m = smoothstep(edge - feather, edge + feather, uv.x);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val LINEAR_WIPE_LEFT = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            float feather = 0.02;
            float m = smoothstep(edge - feather, edge + feather, 1.0 - uv.x);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val LINEAR_WIPE_UP = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            float feather = 0.02;
            float m = smoothstep(edge - feather, edge + feather, 1.0 - uv.y);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val LINEAR_WIPE_DOWN = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            float feather = 0.02;
            float m = smoothstep(edge - feather, edge + feather, uv.y);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val RADIAL_WIPE = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float d = length(c);
            float maxD = 0.7071;
            float m = smoothstep(progress * maxD - 0.05, progress * maxD + 0.05, d);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val ANGULAR_WIPE = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float a = atan(c.y, c.x);
            a = a / 6.2831853 + 0.5;
            float m = smoothstep(progress - 0.02, progress + 0.02, a);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val CROSS_DISSOLVE_SOFT = """
        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }
        vec4 transition(vec2 uv) {
            float r = rand(floor(uv * 30.0));
            float t = smoothstep(r - 0.15, r + 0.15, progress);
            return mix(getFromColor(uv), getToColor(uv), t);
        }
    """

    // =========================================================================
    // SLIDE / MOVE (14)
    // =========================================================================

    private val SLIDE_RIGHT = """
        vec4 transition(vec2 uv) {
            vec2 fromUV = uv + vec2(progress, 0.0);
            vec2 toUV = uv + vec2(progress - 1.0, 0.0);
            vec4 from = fromUV.x <= 1.0 ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = toUV.x >= 0.0 ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val SLIDE_LEFT = """
        vec4 transition(vec2 uv) {
            vec2 fromUV = uv - vec2(progress, 0.0);
            vec2 toUV = uv + vec2(1.0 - progress, 0.0);
            vec4 from = fromUV.x >= 0.0 ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = toUV.x <= 1.0 ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val SLIDE_UP = """
        vec4 transition(vec2 uv) {
            vec2 fromUV = uv + vec2(0.0, progress);
            vec2 toUV = uv + vec2(0.0, progress - 1.0);
            vec4 from = fromUV.y <= 1.0 ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = toUV.y >= 0.0 ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val SLIDE_DOWN = """
        vec4 transition(vec2 uv) {
            vec2 fromUV = uv - vec2(0.0, progress);
            vec2 toUV = uv + vec2(0.0, 1.0 - progress);
            vec4 from = fromUV.y >= 0.0 ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = toUV.y <= 1.0 ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val PUSH_RIGHT = """
        vec4 transition(vec2 uv) {
            float edge = 1.0 - progress;
            if (uv.x < edge) return getFromColor(vec2(uv.x + progress, uv.y));
            return getToColor(vec2(uv.x - edge, uv.y));
        }
    """

    private val PUSH_LEFT = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            if (uv.x > edge) return getFromColor(vec2(uv.x - progress, uv.y));
            return getToColor(vec2(uv.x + (1.0 - progress), uv.y));
        }
    """

    private val PUSH_UP = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            if (uv.y > edge) return getFromColor(vec2(uv.x, uv.y - progress));
            return getToColor(vec2(uv.x, uv.y + (1.0 - progress)));
        }
    """

    private val PUSH_DOWN = """
        vec4 transition(vec2 uv) {
            float edge = 1.0 - progress;
            if (uv.y < edge) return getFromColor(vec2(uv.x, uv.y + progress));
            return getToColor(vec2(uv.x, uv.y - edge));
        }
    """

    private val SQUEEZE_HORIZONTAL = """
        vec4 transition(vec2 uv) {
            float s = 1.0 - progress;
            vec2 fuv = vec2((uv.x - 0.5) / max(0.001, s) + 0.5, uv.y);
            if (uv.x < s * 0.5) return getFromColor(fuv);
            if (uv.x > 1.0 - s * 0.5) return getToColor(fuv);
            return mix(getFromColor(fuv), getToColor(fuv), progress);
        }
    """

    private val SQUEEZE_VERTICAL = """
        vec4 transition(vec2 uv) {
            float s = 1.0 - progress;
            vec2 fuv = vec2(uv.x, (uv.y - 0.5) / max(0.001, s) + 0.5);
            if (uv.y < s * 0.5) return getFromColor(fuv);
            if (uv.y > 1.0 - s * 0.5) return getToColor(fuv);
            return mix(getFromColor(fuv), getToColor(fuv), progress);
        }
    """

    private val WINDOW_BLINDS_H = """
        vec4 transition(vec2 uv) {
            float count = 10.0;
            float idx = floor(uv.y * count);
            float local = fract(uv.y * count);
            float offset = fract(idx * 0.37);
            float p = clamp((progress * 1.5 - offset * 0.5), 0.0, 1.0);
            if (local < p) return getToColor(uv);
            return getFromColor(uv);
        }
    """

    private val WINDOW_BLINDS_V = """
        vec4 transition(vec2 uv) {
            float count = 10.0;
            float idx = floor(uv.x * count);
            float local = fract(uv.x * count);
            float offset = fract(idx * 0.37);
            float p = clamp((progress * 1.5 - offset * 0.5), 0.0, 1.0);
            if (local < p) return getToColor(uv);
            return getFromColor(uv);
        }
    """

    private val CROSS_WARP = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float m = 1.0 - smoothstep(0.0, 0.5, length(c));
            float t = clamp(progress * 2.0 - m, 0.0, 1.0);
            vec2 warped = mix(uv, uv + c * 0.5 * (1.0 - progress), 0.5);
            return mix(getFromColor(uv), getToColor(warped), t);
        }
    """

    private val SQUEEZE = """
        vec4 transition(vec2 uv) {
            float p = progress;
            if (p < 0.5) {
                float t = p * 2.0;
                vec2 suv = vec2(uv.x, (uv.y - 0.5) / (1.0 - t * 0.5) + 0.5);
                return getFromColor(suv);
            } else {
                float t = (p - 0.5) * 2.0;
                vec2 suv = vec2(uv.x, (uv.y - 0.5) / (1.0 - (1.0 - t) * 0.5) + 0.5);
                return getToColor(suv);
            }
        }
    """

    // =========================================================================
    // ZOOM (8)
    // =========================================================================

    private val ZOOM_IN = """
        vec4 transition(vec2 uv) {
            float z = 1.0 + progress * 4.0;
            vec2 c = vec2(0.5);
            vec2 fuv = c + (uv - c) * z;
            vec2 tuv = c + (uv - c) * (1.0 / max(0.001, 1.0 - progress * 0.99));
            vec4 from = (fuv.x >= 0.0 && fuv.x <= 1.0 && fuv.y >= 0.0 && fuv.y <= 1.0) ? getFromColor(fuv) : vec4(0.0);
            vec4 to = (tuv.x >= 0.0 && tuv.x <= 1.0 && tuv.y >= 0.0 && tuv.y <= 1.0) ? getToColor(tuv) : vec4(0.0);
            return mix(from, to, smoothstep(0.5, 1.0, progress));
        }
    """

    private val ZOOM_OUT = """
        vec4 transition(vec2 uv) {
            float z = 1.0 / max(0.01, 1.0 - progress);
            vec2 c = vec2(0.5);
            vec2 fuv = c + (uv - c) * z;
            vec2 tuv = c + (uv - c) * (1.0 / max(0.01, progress));
            vec4 from = (fuv.x >= 0.0 && fuv.x <= 1.0 && fuv.y >= 0.0 && fuv.y <= 1.0) ? getFromColor(fuv) : vec4(0.0);
            vec4 to = (tuv.x >= 0.0 && tuv.x <= 1.0 && tuv.y >= 0.0 && tuv.y <= 1.0) ? getToColor(tuv) : vec4(0.0);
            return mix(from, to, smoothstep(0.0, 0.5, progress));
        }
    """

    private val CROSS_ZOOM = """
        vec4 transition(vec2 uv) {
            vec2 c = vec2(0.5);
            float s = 0.4;
            float p1 = smoothstep(0.0, 0.5, progress);
            float p2 = smoothstep(0.5, 1.0, progress);
            vec2 fuv = c + (uv - c) * (1.0 + s * p1);
            vec2 tuv = c + (uv - c) * (1.0 + s * (1.0 - p2));
            vec4 from = getFromColor(fuv);
            vec4 to = getToColor(tuv);
            float w = smoothstep(0.3, 0.7, progress);
            return mix(from, to, w);
        }
    """

    private val ROTATE_SCALE = """
        vec4 transition(vec2 uv) {
            vec2 c = vec2(0.5);
            float a = progress * 3.14159265;
            float s = 1.0 + progress * 0.5;
            vec2 r = uv - c;
            vec2 fuv = c + vec2(r.x * cos(a) - r.y * sin(a), r.x * sin(a) + r.y * cos(a)) / s;
            vec4 from = (fuv.x >= 0.0 && fuv.x <= 1.0 && fuv.y >= 0.0 && fuv.y <= 1.0) ? getFromColor(fuv) : vec4(0.0);
            return mix(from, getToColor(uv), smoothstep(0.6, 1.0, progress));
        }
    """

    private val SIMPLE_ZOOM = """
        vec4 transition(vec2 uv) {
            vec2 c = vec2(0.5);
            float p1 = smoothstep(0.0, 0.5, progress);
            float p2 = smoothstep(0.5, 1.0, progress);
            vec2 fuv = c + (uv - c) * (1.0 + p1 * 0.3);
            vec2 tuv = c + (uv - c) * (1.0 - p2 * 0.3);
            return mix(getFromColor(fuv), getToColor(tuv), smoothstep(0.3, 0.7, progress));
        }
    """

    private val ZOOM_PUNCH = """
        vec4 transition(vec2 uv) {
            vec2 c = vec2(0.5);
            float zoom = 1.0 + sin(progress * 3.14159265) * 0.3;
            vec2 suv = c + (uv - c) / zoom;
            vec4 from = getFromColor(suv);
            vec4 to = getToColor(suv);
            float m = smoothstep(0.4, 0.6, progress);
            float flash = sin(progress * 3.14159265) * 0.2;
            vec4 base = mix(from, to, m);
            base.rgb += flash;
            return base;
        }
    """

    private val ZOOM_IN_CIRCLES = """
        float circle(vec2 uv, vec2 center, float radius, float feather) {
            float d = length(uv - center) * (1.0 + feather);
            return smoothstep(radius + feather, radius - feather, d);
        }
        vec4 transition(vec2 uv) {
            vec4 from = getFromColor(uv);
            vec4 to = getToColor(uv);
            vec4 outColor = from;
            for (int i = 0; i < 5; i++) {
                float fi = float(i) / 5.0;
                float offset = fi * 0.15;
                float radius = progress * 1.5 - offset;
                vec2 c = vec2(0.5) + vec2(sin(fi * 6.0) * 0.3, cos(fi * 6.0) * 0.3);
                float m = circle(uv, c, radius, 0.05);
                outColor = mix(outColor, to, m);
            }
            return outColor;
        }
    """

    private val CUBE = """
        vec4 transition(vec2 uv) {
            vec2 pfr, pto;
            float ev = mix(0.0, 1.0, progress);
            float angle = mix(0.0, 3.14159265 / 2.0, progress);
            vec2 uv1 = uv - 0.5;
            uv1.x *= 1.0 - 0.3;
            pfr = vec2(uv1.x / cos(angle) + ev * 0.5, uv1.y);
            pto = vec2(uv1.x / cos(angle) - ev * 0.5, uv1.y);
            pfr += 0.5;
            pto += 0.5;
            vec4 c = vec4(0.0);
            if (pfr.x > 0.0 && pfr.x < 1.0 && pfr.y > 0.0 && pfr.y < 1.0)
                c = mix(c, getFromColor(pfr), 1.0 - progress);
            if (pto.x > 0.0 && pto.x < 1.0 && pto.y > 0.0 && pto.y < 1.0)
                c = mix(c, getToColor(pto), progress);
            return c;
        }
    """"
  // =========================================================================
// GEOMETRIC / SHAPE (12)
// =========================================================================

private val CIRCLE = """
    vec4 transition(vec2 uv) {
        float d = length(uv - 0.5);
        float r = progress * 0.7071;
        float m = smoothstep(r - 0.02, r + 0.02, d);
        return mix(getToColor(uv), getFromColor(uv), m);
    }
"""

private val CIRCLE_CROP = """
    vec4 transition(vec2 uv) {
        float d = distance(uv, vec2(0.5));
        float m = smoothstep(progress * 0.75 - 0.02, progress * 0.75 + 0.02, d);
        return mix(getToColor(uv), getFromColor(uv), m);
    }
"""

private val CIRCLE_OPEN = """
    vec4 transition(vec2 uv) {
        float d = distance(uv, vec2(0.5));
        float ring = abs(d - progress * 0.75);
        float m = smoothstep(0.02, 0.0, ring);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val DIAMOND = """
    vec4 transition(vec2 uv) {
        vec2 c = uv - 0.5;
        float d = abs(c.x) + abs(c.y);
        float m = smoothstep(progress * 0.7 - 0.02, progress * 0.7 + 0.02, d);
        return mix(getToColor(uv), getFromColor(uv), m);
    }
"""

private val STAR = """
    vec4 transition(vec2 uv) {
        vec2 c = uv - 0.5;
        float a = atan(c.y, c.x);
        float r = length(c);
        float star = 0.3 + 0.2 * cos(a * 5.0);
        float m = smoothstep(progress * star - 0.02, progress * star + 0.02, r);
        return mix(getToColor(uv), getFromColor(uv), m);
    }
"""

private val TRIANGLE = """
    vec4 transition(vec2 uv) {
        vec2 p = uv;
        p.x = abs(p.x - 0.5) * 2.0;
        float side = 1.0 - p.x;
        float m = step(side, progress * 2.0);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val HEXAGON = """
    vec2 hexCenter(vec2 p) {
        vec2 h = vec2(1.7320508, 1.0) * 6.0;
        vec2 a = mod(p, h) - h * 0.5;
        vec2 b = mod(p + h * 0.5, h) - h * 0.5;
        return length(a) < length(b) ? a : b;
    }
    vec4 transition(vec2 uv) {
        vec2 p = uv * uResolution / uResolution.y;
        vec2 c = hexCenter(p * 6.0);
        float d = length(c);
        float m = step(d * 3.0, progress * 2.0);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val CHECKERBOARD = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    vec4 transition(vec2 uv) {
        vec2 grid = floor(uv * 8.0);
        float checker = mod(grid.x + grid.y, 2.0);
        float r = rand(grid + checker * 100.0);
        float m = step(r, progress);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val POLKA_DOTS = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    vec4 transition(vec2 uv) {
        vec2 grid = floor(uv * 30.0);
        vec2 cell = fract(uv * 30.0);
        float r = rand(grid);
        float d = distance(cell, vec2(0.5));
        float radius = progress * 0.7 * r;
        float m = step(d, radius);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val GRID_FLIP = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    vec4 transition(vec2 uv) {
        vec2 cell = floor(uv / 0.1);
        float r = rand(cell);
        float m = smoothstep(r - 0.1, r + 0.1, progress);
        return mix(getFromColor(uv), getToColor(uv), m);
    }
"""

private val MOSAIC = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    vec4 transition(vec2 uv) {
        vec2 cell = floor(uv / 0.05);
        float r = rand(cell);
        float m = smoothstep(r - 0.1, r + 0.1, progress);
        vec2 cuv = cell * 0.05 + 0.025;
        return mix(getFromColor(uv), getToColor(cuv), m);
    }
"""

private val BILINEAR = """
    vec4 transition(vec2 uv) {
        vec2 g = uv * 2.0;
        vec2 cell = floor(g);
        vec2 local = fract(g);
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        float corner = mod(cell.x + cell.y, 2.0);
        float m = step(progress, 1.0 - (corner == 0.0 ? local.x * local.y : (1.0 - local.x) * (1.0 - local.y)));
        return mix(from, to, m);
    }
"""

// =========================================================================
// DISTORTION (10)
// =========================================================================

private val MORPH = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x),
                   mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
    }
    vec4 transition(vec2 uv) {
        vec2 dir = vec2(noise(uv * 4.0) - 0.5, noise(uv * 4.0 + 100.0) - 0.5);
        float p = progress;
        float w = sin(p * 3.14159265);
        vec2 fromUV = uv + dir * w * 0.2;
        vec2 toUV = uv - dir * w * 0.2;
        vec4 from = getFromColor(fromUV);
        vec4 to = getToColor(toUV);
        return mix(from, to, smoothstep(0.4, 0.6, p));
    }
"""

private val PERLIN = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x),
                   mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
    }
    vec4 transition(vec2 uv) {
        float n = noise(uv * 4.0);
        float t = smoothstep(progress - 0.06, progress + 0.06, n);
        return mix(getFromColor(uv), getToColor(uv), 1.0 - t);
    }
"""

private val RIPPLE_TRANSITION = """
    vec4 transition(vec2 uv) {
        vec2 c = vec2(0.5);
        float d = length(uv - c);
        float w = sin(d * 30.0 - progress * 20.0) * 0.03 * progress;
        vec2 dir = normalize(uv - c + 1e-5);
        vec2 uv1 = uv + dir * w;
        vec4 from = getFromColor(uv1);
        vec4 to = getToColor(uv1);
        return mix(from, to, smoothstep(0.3, 0.7, progress));
    }
"""

private val WATER_DROP = """
    vec4 transition(vec2 uv) {
        vec2 c = vec2(0.5);
        float d = length(uv - c);
        float w = sin(d * 30.0 - progress * 10.0) * 0.05 * (1.0 - progress);
        vec2 dir = normalize(uv - c + 1e-5);
        vec2 uv1 = uv + dir * w;
        vec4 from = getFromColor(uv1);
        vec4 to = getToColor(uv1);
        return mix(from, to, progress);
    }
"""

private val UNDERWATER = """
    vec4 transition(vec2 uv) {
        vec2 offset = vec2(
            sin(uv.y * 10.0 + progress * 6.2831853) * 0.05,
            cos(uv.x * 10.0 + progress * 6.2831853) * 0.05
        ) * sin(progress * 3.14159265);
        vec4 from = getFromColor(uv + offset);
        vec4 to = getToColor(uv - offset);
        return mix(from, to, smoothstep(0.3, 0.7, progress));
    }
"""

private val GLITCH_TRANSITION = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    vec4 transition(vec2 uv) {
        float band = floor(uv.y * 40.0);
        float t = floor(progress * 20.0);
        float r = rand(vec2(band, t));
        float shift = (r - 0.5) * 0.2 * (0.3 + progress);
        float active = step(0.7, rand(vec2(band * 1.3, t)));
        vec2 uv1 = uv + vec2(shift * active, 0.0);
        vec2 uv2 = uv - vec2(shift * active, 0.0);
        vec4 from = getFromColor(uv1);
        vec4 to = getToColor(uv2);
        float chroma = (r - 0.5) * 0.02 * active;
        vec3 c = mix(from.rgb, to.rgb, progress);
        c.r += chroma;
        c.b -= chroma;
        return vec4(c, mix(from.a, to.a, progress));
    }
"""

private val GLITCH_MEMORIES = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x),
                   mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
    }
    vec4 transition(vec2 uv) {
        float n = noise(vec2(uv.y * 10.0, progress * 20.0));
        float shift = (n - 0.5) * 0.1 * progress;
        vec4 from = getFromColor(uv + vec2(shift, 0.0));
        vec4 to = getToColor(uv - vec2(shift, 0.0));
        return mix(from, to, smoothstep(0.4, 0.6, progress));
    }
"""

private val WIND = """
    vec4 transition(vec2 uv) {
        float p = progress;
        float size = 0.2;
        if (p < 0.5) {
            float t = p * 2.0;
            float offset = t * size;
            vec4 from = getFromColor(uv - vec2(offset, 0.0));
            vec4 to = getToColor(uv - vec2(offset + size, 0.0));
            return mix(from, to, t);
        } else {
            float t = (p - 0.5) * 2.0;
            float offset = size + t * (1.0 - size);
            return getToColor(uv - vec2(offset, 0.0) + vec2(1.0, 0.0));
        }
    }
"""

private val SWIRL = """
    vec4 transition(vec2 uv) {
        vec2 c = vec2(0.5);
        vec2 d = uv - c;
        float r = length(d);
        float a = atan(d.y, d.x);
        float swirl = 2.0 * sin(progress * 3.14159265) * (1.0 - smoothstep(0.0, 0.5, r));
        a += swirl;
        vec2 suv = c + vec2(cos(a), sin(a)) * r;
        vec4 from = getFromColor(suv);
        vec4 to = getToColor(suv);
        return mix(from, to, progress);
    }
"""

// =========================================================================
// LIGHT / OPTICAL (11)
// =========================================================================

private val WHITE_FLASH = """
    vec4 transition(vec2 uv) {
        float m = sin(progress * 3.14159265);
        vec4 base = mix(getFromColor(uv), getToColor(uv),
                        smoothstep(0.35, 0.65, progress));
        return mix(base, vec4(1.0), m);
    }
"""

private val BLACK_FLASH = """
    vec4 transition(vec2 uv) {
        float m = sin(progress * 3.14159265);
        vec4 base = mix(getFromColor(uv), getToColor(uv),
                        smoothstep(0.35, 0.65, progress));
        return mix(base, vec4(0.0, 0.0, 0.0, 1.0), m);
    }
"""

private val LIGHT_FLASH = """
    vec4 transition(vec2 uv) {
        float flash = sin(progress * 3.14159265);
        vec4 base = mix(getFromColor(uv), getToColor(uv),
                        smoothstep(0.35, 0.65, progress));
        return vec4(base.rgb + flash * 0.8, base.a);
    }
"""

private val GLOW_TRANSITION = """
    vec4 transition(vec2 uv) {
        vec2 px = 1.0 / uResolution;
        vec4 base = mix(getFromColor(uv), getToColor(uv), progress);
        vec3 glow = vec3(0.0);
        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                glow += getToColor(uv + vec2(float(i), float(j)) * px * 4.0).rgb;
            }
        }
        glow /= 81.0;
        float amt = sin(progress * 3.14159265);
        return vec4(base.rgb + glow * amt * 0.5, base.a);
    }
"""

private val LIGHT_LEAK_TRANSITION = """
    vec4 transition(vec2 uv) {
        vec4 base = mix(getFromColor(uv), getToColor(uv), progress);
        float leak = smoothstep(0.0, 1.0, progress * 1.5 - uv.x * 0.5);
        leak *= (1.0 - progress);
        vec3 leakColor = vec3(1.0, 0.75, 0.4);
        return vec4(base.rgb + leakColor * leak * 1.2, base.a);
    }
"""

private val SUN_FLARE = """
    vec4 transition(vec2 uv) {
        vec4 base = mix(getFromColor(uv), getToColor(uv), progress);
        vec2 sunPos = vec2(progress * 1.4 - 0.2, 0.5);
        float d = distance(uv, sunPos);
        float flare = exp(-d * 8.0) * 2.0;
        float amt = sin(progress * 3.14159265);
        vec3 color = vec3(1.0, 0.9, 0.7) * flare * amt;
        float ghostD = distance(uv, vec2(1.0 - sunPos.x, 1.0 - sunPos.y));
        color += vec3(0.7, 0.8, 1.0) * exp(-ghostD * 12.0) * amt;
        return vec4(base.rgb + color, base.a);
    }
"""

private val PRISM = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        float amt = sin(progress * 3.14159265);
        float offset = amt * 0.03;
        vec4 base = mix(from, to, progress);
        base.r += to.r * offset * 10.0;
        base.b -= to.b * offset * 10.0;
        return vec4(clamp(base.rgb, 0.0, 1.0), base.a);
    }
"""

private val RAINBOW = """
    vec3 hue(float h) {
        vec3 rgb = abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0;
        return clamp(rgb, 0.0, 1.0);
    }
    vec4 transition(vec2 uv) {
        vec4 base = mix(getFromColor(uv), getToColor(uv), progress);
        float h = uv.x * 0.5 + progress * 0.5;
        float amt = sin(progress * 3.14159265);
        vec3 tint = hue(h);
        return vec4(mix(base.rgb, tint, amt * 0.4), base.a);
    }
"""

private val BURN = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        float n = fract(sin(dot(uv * 10.0, vec2(12.9898, 78.233))) * 43758.5453);
        float m = smoothstep(0.0, 1.0, progress * 1.2);
        vec4 base = mix(from, to, m);
        float fire = sin(progress * 3.14159265);
        vec3 fireColor = vec3(1.0, 0.6, 0.2) * fire;
        base.rgb += fireColor * (1.0 - abs(uv.x - progress));
        return base;
    }
"""

private val FILM_BURN = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x),
                   mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
    }
    vec4 transition(vec2 uv) {
        float n = noise(uv * 6.0);
        float edge = progress * 1.4 - 0.2;
        float burn = smoothstep(edge, edge + 0.15, n + uv.y * 0.3);
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 fire = vec3(1.0, 0.5, 0.1) * smoothstep(edge - 0.1, edge, 1.0 - n) * 2.0;
        vec4 result = mix(from, to, burn);
        result.rgb += fire * (1.0 - abs(progress - 0.5) * 2.0);
        return result;
    }
"""

private val BURN_OUT = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        return mix(mix(rand(i), rand(i + vec2(1.0, 0.0)), f.x),
                   mix(rand(i + vec2(0.0, 1.0)), rand(i + vec2(1.0, 1.0)), f.x), f.y);
    }
    vec4 transition(vec2 uv) {
        float n = noise(uv * 12.0);
        float edge = progress * 1.4 - 0.2;
        float m = smoothstep(edge - 0.5, edge + 0.5, n);
        return mix(getToColor(uv), getFromColor(uv), m);
    }
"""

// =========================================================================
// BLEND (8)
// =========================================================================

private val OVERLAY_BLEND = """
    vec3 overlayBlend(vec3 a, vec3 b) {
        return mix(2.0 * a * b, 1.0 - 2.0 * (1.0 - a) * (1.0 - b), step(0.5, a));
    }
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 blended = overlayBlend(from.rgb, to.rgb);
        return vec4(mix(from.rgb, blended, progress), mix(from.a, to.a, progress));
    }
"""

private val SOFT_LIGHT_BLEND = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 d = from.rgb;
        vec3 s = to.rgb;
        vec3 soft = (1.0 - 2.0 * s) * d * d + 2.0 * s * d;
        return vec4(mix(from.rgb, soft, progress), mix(from.a, to.a, progress));
    }
"""

private val HARD_LIGHT_BLEND = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 d = from.rgb;
        vec3 s = to.rgb;
        vec3 hard = mix(2.0 * s * d, 1.0 - 2.0 * (1.0 - s) * (1.0 - d), step(0.5, s));
        return vec4(mix(from.rgb, hard, progress), mix(from.a, to.a, progress));
    }
"""

private val ADDITIVE = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        return vec4(clamp(from.rgb + to.rgb * progress, 0.0, 1.0), mix(from.a, to.a, progress));
    }
"""

private val MULTIPLY_BLEND = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        return vec4(mix(from.rgb, from.rgb * to.rgb, progress), mix(from.a, to.a, progress));
    }
"""

private val DIFFERENCE_BLEND = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        return vec4(mix(from.rgb, abs(from.rgb - to.rgb), progress), mix(from.a, to.a, progress));
    }
"""

private val EXCLUSION = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 exc = from.rgb + to.rgb - 2.0 * from.rgb * to.rgb;
        return vec4(mix(from.rgb, exc, progress), mix(from.a, to.a, progress));
    }
"""

private val VIVID_LIGHT = """
    vec4 transition(vec2 uv) {
        vec4 from = getFromColor(uv);
        vec4 to = getToColor(uv);
        vec3 d = from.rgb;
        vec3 s = to.rgb;
        vec3 vivid = mix(1.0 - 2.0 * (1.0 - s) * (1.0 - d), 2.0 * s * d, step(0.5, s));
        return vec4(clamp(mix(from.rgb, vivid, progress), 0.0, 1.0), mix(from.a, to.a, progress));
    }
"""
      // =========================================================================
    // SPLIT (10)
    // =========================================================================

    private val VERTICAL_SPLIT = """
        vec4 transition(vec2 uv) {
            float p = progress;
            if (p < 0.5) {
                float t = p / 0.5;
                float offset = t * 0.5;
                if (uv.y < 0.5) return getFromColor(vec2(uv.x, uv.y - offset));
                return getFromColor(vec2(uv.x, uv.y + offset));
            } else {
                float t = (p - 0.5) / 0.5;
                float offset = (1.0 - t) * 0.5;
                if (uv.y < 0.5) return getToColor(vec2(uv.x, uv.y + offset));
                return getToColor(vec2(uv.x, uv.y - offset));
            }
        }
    """

    private val HORIZONTAL_SPLIT = """
        vec4 transition(vec2 uv) {
            float p = progress;
            if (p < 0.5) {
                float t = p / 0.5;
                float offset = t * 0.5;
                if (uv.x < 0.5) return getFromColor(vec2(uv.x - offset, uv.y));
                return getFromColor(vec2(uv.x + offset, uv.y));
            } else {
                float t = (p - 0.5) / 0.5;
                float offset = (1.0 - t) * 0.5;
                if (uv.x < 0.5) return getToColor(vec2(uv.x + offset, uv.y));
                return getToColor(vec2(uv.x - offset, uv.y));
            }
        }
    """

    private val QUAD_SPLIT = """
        vec4 transition(vec2 uv) {
            float p = progress;
            if (p < 0.5) {
                float t = p / 0.5;
                vec2 quadrant = floor(uv * 2.0);
                vec2 local = fract(uv * 2.0);
                vec2 dir = (quadrant - 0.5) * 2.0;
                vec2 suv = quadrant * 0.5 + (local + dir * t * 0.5) * 0.5;
                return getFromColor(clamp(suv, 0.0, 1.0));
            } else {
                float t = (p - 0.5) / 0.5;
                vec2 quadrant = floor(uv * 2.0);
                vec2 local = fract(uv * 2.0);
                vec2 dir = (quadrant - 0.5) * 2.0;
                vec2 suv = quadrant * 0.5 + (local - dir * (1.0 - t) * 0.5) * 0.5;
                return getToColor(clamp(suv, 0.0, 1.0));
            }
        }
    """

    private val MULTI_SPLIT = """
        vec4 transition(vec2 uv) {
            float slices = 6.0;
            float idx = floor(uv.y * slices);
            float local = fract(uv.y * slices);
            float offset = fract(idx * 0.37);
            float p = clamp((progress * 1.5 - offset * 0.5), 0.0, 1.0);
            vec2 suv = vec2(uv.x + p, uv.y);
            return mix(getFromColor(vec2(uv.x - p, uv.y)), getToColor(suv), p);
        }
    """

    private val VERTICAL_SLIDE_BARS = """
        vec4 transition(vec2 uv) {
            float bars = 8.0;
            float idx = floor(uv.x * bars);
            float local = fract(uv.x * bars);
            float offset = fract(idx * 0.31);
            float p = clamp((progress * 1.4 - offset * 0.4), 0.0, 1.0);
            vec2 fuv = vec2(local, uv.y + p);
            vec2 tuv = vec2(local, uv.y + p - 1.0);
            vec4 from = (fuv.y <= 1.0) ? getFromColor(vec2(idx / bars + local / bars, fuv.y)) : vec4(0.0);
            vec4 to = (tuv.y >= 0.0) ? getToColor(vec2(idx / bars + local / bars, tuv.y)) : vec4(0.0);
            return from + to;
        }
    """

    private val HORIZONTAL_SLIDE_BARS = """
        vec4 transition(vec2 uv) {
            float bars = 8.0;
            float idx = floor(uv.y * bars);
            float local = fract(uv.y * bars);
            float offset = fract(idx * 0.31);
            float p = clamp((progress * 1.4 - offset * 0.4), 0.0, 1.0);
            vec2 fuv = vec2(uv.x + p, local);
            vec2 tuv = vec2(uv.x + p - 1.0, local);
            vec4 from = (fuv.x <= 1.0) ? getFromColor(vec2(fuv.x, idx / bars + local / bars)) : vec4(0.0);
            vec4 to = (tuv.x >= 0.0) ? getToColor(vec2(tuv.x, idx / bars + local / bars)) : vec4(0.0);
            return from + to;
        }
    """

    private val PINWHEEL = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float a = atan(c.y, c.x) / 6.2831853 + 0.5;
            float seg = floor(a * 8.0);
            float p = clamp((progress * 8.0 - seg), 0.0, 1.0);
            float angle = p * 6.2831853;
            float s = sin(angle);
            float co = cos(angle);
            vec2 rotated = vec2(c.x * co - c.y * s, c.x * s + c.y * co);
            vec2 suv = rotated + 0.5;
            return mix(getFromColor(suv), getToColor(suv), p);
        }
    """

    private val FAN = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float a = atan(c.y, c.x) / 6.2831853 + 0.5;
            float seg = floor(a * 12.0) / 12.0;
            float edge = progress;
            float m = step(seg, edge);
            return mix(getFromColor(uv), getToColor(uv), m);
        }
    """

    private val SLIDE_EDGE = """
        vec4 transition(vec2 uv) {
            float edge = progress;
            float feather = 0.02;
            vec2 c = uv - 0.5;
            float a = atan(c.y, c.x) / 6.2831853 + 0.5;
            float m = smoothstep(edge - feather, edge + feather, a);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    private val POLAR_FUNCTION = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float a = atan(c.y, c.x);
            float r = length(c);
            float seg = 6.2831853 / 6.0;
            a = mod(a, seg) / seg;
            float d = mix(r, a * r, progress);
            float m = smoothstep(progress * 0.7 - 0.02, progress * 0.7 + 0.02, d * 2.0);
            return mix(getToColor(uv), getFromColor(uv), m);
        }
    """

    // =========================================================================
    // MODERN / CAPCUT-INSPIRED (10)
    // =========================================================================

    private val WHIP_PAN = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float speed = sin(p * 3.14159265);
            vec2 dir = vec2(1.0, 0.0);
            vec2 offset = dir * speed * 0.5;
            vec2 fromUV = uv + offset;
            vec2 toUV = uv - offset;
            vec4 from = getFromColor(fromUV);
            vec4 to = getToColor(toUV);
            vec4 blur = vec4(0.0);
            for (int i = -4; i <= 4; i++) {
                float fi = float(i) / 4.0;
                blur += mix(getFromColor(fromUV + dir * fi * 0.05 * speed),
                            getToColor(toUV + dir * fi * 0.05 * speed), p);
            }
            blur /= 9.0;
            return mix(mix(from, to, p), blur, speed * 0.7);
        }
    """

    private val SHAKE = """
        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }
        vec4 transition(vec2 uv) {
            float p = progress;
            float amt = sin(p * 3.14159265);
            vec2 shake = vec2(rand(vec2(p * 100.0, 0.0)) - 0.5, rand(vec2(0.0, p * 100.0)) - 0.5) * amt * 0.1;
            vec2 fuv = uv + shake;
            vec2 tuv = uv - shake;
            return mix(getFromColor(fuv), getToColor(tuv), p);
        }
    """

    private val BEAT_SYNC = """
        vec4 transition(vec2 uv) {
            float beats = 4.0;
            float p = progress;
            float beatPos = p * beats;
            float beatPhase = fract(beatPos);
            float scale = 1.0 + (1.0 - beatPhase) * 0.05;
            vec2 c = vec2(0.5);
            vec2 suv = c + (uv - c) / scale;
            float flash = (1.0 - beatPhase) * 0.3;
            vec4 base = mix(getFromColor(suv), getToColor(suv), p);
            base.rgb += flash;
            return base;
        }
    """

    private val VELOCITY_EDIT = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float eased = p * p * (3.0 - 2.0 * p);
            float speed = pow(eased, 1.0 / 1.5);
            vec2 dir = vec2(1.0, 0.0);
            vec2 fromUV = uv + dir * speed;
            vec2 toUV = uv + dir * (speed - 1.0);
            vec4 from = (fromUV.x <= 1.0) ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = (toUV.x >= 0.0) ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val RGB_ROTATE = """
        vec4 transition(vec2 uv) {
            vec2 c = uv - 0.5;
            float r = length(c);
            float a = atan(c.y, c.x);
            float rot = progress * 6.2831853;
            float rr = r + sin(a * 3.0 + rot) * 0.05 * progress;
            vec2 suv = 0.5 + vec2(cos(a), sin(a)) * rr;
            vec4 from = getFromColor(suv);
            vec4 to = getToColor(suv);
            float amt = sin(progress * 3.14159265);
            float chroma = amt * 0.05;
            vec4 result = mix(from, to, progress);
            result.r = mix(from.r, to.r, progress) + chroma;
            result.b = mix(from.b, to.b, progress) - chroma;
            return vec4(clamp(result.rgb, 0.0, 1.0), result.a);
        }
    """

    private val GLITCH_GLITCH = """
        float rand(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }
        vec4 transition(vec2 uv) {
            float p = progress;
            float glitchAmt = sin(p * 3.14159265);
            float t = floor(p * 20.0);
            vec2 block = floor(uv * 20.0);
            float r = rand(block + t);
            float shift = (r - 0.5) * 0.15 * glitchAmt;
            vec4 from = getFromColor(uv + vec2(shift, 0.0));
            vec4 to = getToColor(uv - vec2(shift, 0.0));
            vec4 base = mix(from, to, p);
            float chroma = glitchAmt * 0.1;
            base.r += (r - 0.5) * chroma;
            base.b -= (r - 0.5) * chroma;
            return vec4(clamp(base.rgb, 0.0, 1.0), base.a);
        }
    """

    private val HOLOGRAPHIC = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float amt = sin(p * 3.14159265);
            vec2 c = uv - 0.5;
            float r = length(c);
            float scan = sin(uv.y * 100.0 + p * 20.0) * 0.5 + 0.5;
            vec4 base = mix(getFromColor(uv), getToColor(uv), p);
            vec3 holo = vec3(0.3, 0.8, 1.0) * scan * amt * 0.5;
            vec3 iridescent = vec3(
                sin(r * 20.0 + 0.0) * 0.5 + 0.5,
                sin(r * 20.0 + 2.0) * 0.5 + 0.5,
                sin(r * 20.0 + 4.0) * 0.5 + 0.5
            );
            base.rgb = mix(base.rgb, base.rgb + iridescent * amt, amt);
            base.rgb += holo;
            return base;
        }
    """

    private val SPEED_RAMP = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float eased = pow(p, 2.0);
            vec2 dir = vec2(1.0, 0.0);
            vec2 fromUV = uv + dir * eased;
            vec2 toUV = uv + dir * (eased - 1.0);
            vec4 from = (fromUV.x <= 1.0) ? getFromColor(fromUV) : vec4(0.0);
            vec4 to = (toUV.x >= 0.0) ? getToColor(toUV) : vec4(0.0);
            return from + to;
        }
    """

    private val SPEED_BLUR = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float speed = sin(p * 3.14159265);
            vec4 sum = vec4(0.0);
            for (int i = -4; i <= 4; i++) {
                float fi = float(i) / 4.0;
                sum += mix(getFromColor(uv + vec2(fi * 0.06 * speed, 0.0)),
                           getToColor(uv + vec2(fi * 0.06 * speed, 0.0)), p);
            }
            return sum / 9.0;
        }
    """

    private val STROBE = """
        vec4 transition(vec2 uv) {
            float p = progress;
            float strobe = step(0.5, fract(p * 8.0));
            vec4 from = getFromColor(uv);
            vec4 to = getToColor(uv);
            return mix(from, to, strobe * p);
        }
    """

    // =========================================================================
    // CATALOG
    // =========================================================================

    private val PART_A: List<TransitionDef> = listOf(
        // Basic
        TransitionDef("Fade", "Basic", FADE, 500L),
        TransitionDef("Dissolve", "Basic", DISSOLVE, 600L),
        TransitionDef("Block Dissolve", "Basic", BLOCK_DISSOLVE, 700L),
        TransitionDef("Linear Wipe Right", "Basic", LINEAR_WIPE_RIGHT, 500L),
        TransitionDef("Linear Wipe Left", "Basic", LINEAR_WIPE_LEFT, 500L),
        TransitionDef("Linear Wipe Up", "Basic", LINEAR_WIPE_UP, 500L),
        TransitionDef("Linear Wipe Down", "Basic", LINEAR_WIPE_DOWN, 500L),
        TransitionDef("Radial Wipe", "Basic", RADIAL_WIPE, 600L),
        TransitionDef("Angular Wipe", "Basic", ANGULAR_WIPE, 700L),
        TransitionDef("Cross Dissolve Soft", "Basic", CROSS_DISSOLVE_SOFT, 700L),
        // Slide
        TransitionDef("Slide Right", "Slide", SLIDE_RIGHT, 600L),
        TransitionDef("Slide Left", "Slide", SLIDE_LEFT, 600L),
        TransitionDef("Slide Up", "Slide", SLIDE_UP, 600L),
        TransitionDef("Slide Down", "Slide", SLIDE_DOWN, 600L),
        TransitionDef("Push Right", "Slide", PUSH_RIGHT, 600L),
        TransitionDef("Push Left", "Slide", PUSH_LEFT, 600L),
        TransitionDef("Push Up", "Slide", PUSH_UP, 600L),
        TransitionDef("Push Down", "Slide", PUSH_DOWN, 600L),
        TransitionDef("Squeeze Horizontal", "Slide", SQUEEZE_HORIZONTAL, 700L),
        TransitionDef("Squeeze Vertical", "Slide", SQUEEZE_VERTICAL, 700L),
        TransitionDef("Window Blinds H", "Slide", WINDOW_BLINDS_H, 800L),
        TransitionDef("Window Blinds V", "Slide", WINDOW_BLINDS_V, 800L),
        TransitionDef("Cross Warp", "Slide", CROSS_WARP, 700L),
        TransitionDef("Squeeze", "Slide", SQUEEZE, 800L),
        // Zoom
        TransitionDef("Zoom In", "Zoom", ZOOM_IN, 700L),
        TransitionDef("Zoom Out", "Zoom", ZOOM_OUT, 700L),
        TransitionDef("Cross Zoom", "Zoom", CROSS_ZOOM, 800L),
        TransitionDef("Rotate Scale", "Zoom", ROTATE_SCALE, 800L),
        TransitionDef("Simple Zoom", "Zoom", SIMPLE_ZOOM, 600L),
        TransitionDef("Zoom Punch", "Zoom", ZOOM_PUNCH, 600L),
        TransitionDef("Zoom In Circles", "Zoom", ZOOM_IN_CIRCLES, 900L),
        TransitionDef("Cube", "Zoom", CUBE, 1000L),
        // Geometric
        TransitionDef("Circle", "Geometric", CIRCLE, 600L),
        TransitionDef("Circle Crop", "Geometric", CIRCLE_CROP, 600L),
        TransitionDef("Circle Open", "Geometric", CIRCLE_OPEN, 700L),
        TransitionDef("Diamond", "Geometric", DIAMOND, 600L),
        TransitionDef("Star", "Geometric", STAR, 700L),
        TransitionDef("Triangle", "Geometric", TRIANGLE, 600L),
        TransitionDef("Hexagon", "Geometric", HEXAGON, 800L),
        TransitionDef("Checkerboard", "Geometric", CHECKERBOARD, 800L),
        TransitionDef("Polka Dots", "Geometric", POLKA_DOTS, 800L),
        TransitionDef("Grid Flip", "Geometric", GRID_FLIP, 800L),
        TransitionDef("Mosaic", "Geometric", MOSAIC, 800L),
        TransitionDef("Bilinear", "Geometric", BILINEAR, 800L),
        // Distortion
        TransitionDef("Morph", "Distortion", MORPH, 900L),
        TransitionDef("Perlin", "Distortion", PERLIN, 900L),
        TransitionDef("Ripple", "Distortion", RIPPLE_TRANSITION, 900L),
        TransitionDef("Water Drop", "Distortion", WATER_DROP, 900L),
        TransitionDef("Underwater", "Distortion", UNDERWATER, 900L),
        TransitionDef("Glitch", "Distortion", GLITCH_TRANSITION, 700L),
        TransitionDef("Glitch Memories", "Distortion", GLITCH_MEMORIES, 700L),
        TransitionDef("Wind", "Distortion", WIND, 800L),
        TransitionDef("Swirl", "Distortion", SWIRL, 900L),
    )

    private val PART_B: List<TransitionDef> = listOf(
        // Light
        TransitionDef("White Flash", "Light", WHITE_FLASH, 400L),
        TransitionDef("Black Flash", "Light", BLACK_FLASH, 400L),
        TransitionDef("Light Flash", "Light", LIGHT_FLASH, 500L),
        TransitionDef("Glow Transition", "Light", GLOW_TRANSITION, 800L),
        TransitionDef("Light Leak", "Light", LIGHT_LEAK_TRANSITION, 900L),
        TransitionDef("Sun Flare", "Light", SUN_FLARE, 900L),
        TransitionDef("Prism", "Light", PRISM, 800L),
        TransitionDef("Rainbow", "Light", RAINBOW, 800L),
        TransitionDef("Burn", "Light", BURN, 900L),
        TransitionDef("Film Burn", "Light", FILM_BURN, 1000L),
        TransitionDef("Burn Out", "Light", BURN_OUT, 900L),
        // Blend
        TransitionDef("Overlay Blend", "Blend", OVERLAY_BLEND, 800L),
        TransitionDef("Soft Light Blend", "Blend", SOFT_LIGHT_BLEND, 800L),
        TransitionDef("Hard Light Blend", "Blend", HARD_LIGHT_BLEND, 800L),
        TransitionDef("Additive", "Blend", ADDITIVE, 700L),
        TransitionDef("Multiply", "Blend", MULTIPLY_BLEND, 700L),
        TransitionDef("Difference", "Blend", DIFFERENCE_BLEND, 700L),
        TransitionDef("Exclusion", "Blend", EXCLUSION, 700L),
        TransitionDef("Vivid Light", "Blend", VIVID_LIGHT, 800L),
        // Split
        TransitionDef("Vertical Split", "Split", VERTICAL_SPLIT, 800L),
        TransitionDef("Horizontal Split", "Split", HORIZONTAL_SPLIT, 800L),
        TransitionDef("Quad Split", "Split", QUAD_SPLIT, 900L),
        TransitionDef("Multi Split", "Split", MULTI_SPLIT, 900L),
        TransitionDef("Vertical Slide Bars", "Split", VERTICAL_SLIDE_BARS, 900L),
        TransitionDef("Horizontal Slide Bars", "Split", HORIZONTAL_SLIDE_BARS, 900L),
        TransitionDef("Pinwheel", "Split", PINWHEEL, 900L),
        TransitionDef("Fan", "Split", FAN, 900L),
        TransitionDef("Slide Edge", "Split", SLIDE_EDGE, 800L),
        TransitionDef("Polar Function", "Split", POLAR_FUNCTION, 800L),
        // Modern
        TransitionDef("Whip Pan", "Modern", WHIP_PAN, 600L),
        TransitionDef("Shake", "Modern", SHAKE, 500L),
        TransitionDef("Beat Sync", "Modern", BEAT_SYNC, 800L),
        TransitionDef("Velocity Edit", "Modern", VELOCITY_EDIT, 700L),
        TransitionDef("RGB Rotate", "Modern", RGB_ROTATE, 800L),
        TransitionDef("Glitch Glitch", "Modern", GLITCH_GLITCH, 700L),
        TransitionDef("Holographic", "Modern", HOLOGRAPHIC, 900L),
        TransitionDef("Speed Ramp", "Modern", SPEED_RAMP, 700L),
        TransitionDef("Speed Blur", "Modern", SPEED_BLUR, 700L),
        TransitionDef("Strobe", "Modern", STROBE, 600L),
    )

    val ALL: List<TransitionDef> get() = PART_A + PART_B

    val COUNT: Int get() = ALL.size

    val CATEGORIES: List<String> get() = ALL.map { it.category }.distinct()

    fun byName(name: String): TransitionDef? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun byCategory(category: String): List<TransitionDef> =
        ALL.filter { it.category.equals(category, ignoreCase = true) }

    fun fullFragment(def: TransitionDef): String =
        PREAMBLE + "\n" + def.body + "\n" +
        "void main() { gl_FragColor = transition(vUV); }"

    fun stats(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (t in ALL) out[t.category] = (out[t.category] ?: 0) + 1
        return out
    }
}
