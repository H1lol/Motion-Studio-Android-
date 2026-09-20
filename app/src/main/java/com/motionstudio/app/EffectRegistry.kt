package com.motionstudio.app

/**
 * EffectRegistry — every effect is a real GLSL fragment shader.
 *
 * Uniforms available to every shader:
 *   uniform sampler2D uTexture   — the input frame
 *   uniform sampler2D uDepthMap  — optional depth map (bound if requiresDepth)
 *   uniform vec2 uResolution     — viewport size in pixels
 *   uniform float uTime          — seconds since playback start
 *   uniform float uP0, uP1, uP2  — three adjustable parameters
 */
object EffectRegistry {

    data class EffectDef(
        val name: String,
        val category: String,
        val fragment: String,
        val defaultParams: FloatArray = floatArrayOf(1f, 0f, 0f),
        val paramRanges: Array<FloatArray> = arrayOf(
            floatArrayOf(0f, 2f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, 1f),
        ),
        val requiresDepth: Boolean = false,
    )

    private const val HEADER = """
        precision highp float;
        varying vec2 vUV;
        uniform sampler2D uTexture;
        uniform sampler2D uDepthMap;
        uniform vec2 uResolution;
        uniform float uTime;
        uniform float uP0;
        uniform float uP1;
        uniform float uP2;
    """

    // =========================================================================
    // BLUR FAMILY (6)
    // =========================================================================

    private val BLUR_BOX = """
        void main() {
            vec2 px = 1.0 / uResolution;
            float r = max(0.5, uP0 * 8.0);
            vec4 s = vec4(0.0); float n = 0.0;
            for (float y = -4.0; y <= 4.0; y += 1.0)
            for (float x = -4.0; x <= 4.0; x += 1.0) {
                s += texture2D(uTexture, vUV + vec2(x, y) * px * r * 0.5);
                n += 1.0;
            }
            gl_FragColor = s / n;
        }
    """

    private val BLUR_GAUSSIAN = """
        void main() {
            vec2 px = 1.0 / uResolution;
            float r = max(1.0, uP0 * 6.0);
            vec4 sum = vec4(0.0);
            float wsum = 0.0;
            for (int i = -6; i <= 6; i++) {
                float fi = float(i);
                float w = exp(-fi*fi / (2.0 * r*r));
                sum += texture2D(uTexture, vUV + vec2(fi, 0.0) * px) * w;
                sum += texture2D(uTexture, vUV + vec2(0.0, fi) * px) * w;
                wsum += 2.0 * w;
            }
            gl_FragColor = sum / wsum;
        }
    """

    private val BLUR_DIRECTIONAL = """
        void main() {
            vec2 px = 1.0 / uResolution;
            float angle = uP1 * 6.2831853;
            vec2 dir = vec2(cos(angle), sin(angle));
            float r = max(0.5, uP0 * 10.0);
            vec4 s = vec4(0.0); float n = 0.0;
            for (int i = -6; i <= 6; i++) {
                float fi = float(i) / 6.0;
                s += texture2D(uTexture, vUV + dir * fi * px * r);
                n += 1.0;
            }
            gl_FragColor = s / n;
        }
    """

    private val BLUR_RADIAL = """
        void main() {
            vec2 center = vec2(0.5);
            vec2 dir = vUV - center;
            float r = max(0.5, uP0 * 8.0);
            vec4 s = vec4(0.0); float n = 0.0;
            for (int i = -6; i <= 6; i++) {
                float fi = float(i) / 6.0;
                s += texture2D(uTexture, vUV - dir * fi * 0.05 * r);
                n += 1.0;
            }
            gl_FragColor = s / n;
        }
    """

    private val BLUR_ZOOM = """
        void main() {
            vec2 center = vec2(0.5);
            float strength = uP0 * 0.1;
            vec4 sum = vec4(0.0);
            for (int i = 0; i < 12; i++) {
                float fi = float(i) / 11.0;
                vec2 uv = mix(vUV, center, fi * strength);
                sum += texture2D(uTexture, uv);
            }
            gl_FragColor = sum / 12.0;
        }
    """

    private val BLUR_MOTION = """
        void main() {
            vec2 px = 1.0 / uResolution;
            float angle = uP1 * 6.2831853;
            vec2 dir = vec2(cos(angle), sin(angle));
            float r = max(0.5, uP0 * 20.0);
            vec4 s = vec4(0.0); float n = 0.0;
            for (int i = -8; i <= 8; i++) {
                float fi = float(i) / 8.0;
                s += texture2D(uTexture, vUV + dir * fi * px * r);
                n += 1.0;
            }
            gl_FragColor = s / n;
        }
    """

    // =========================================================================
    // LIGHT FAMILY (7)
    // =========================================================================

    private val GLOW = """
        void main() {
            vec2 px = 1.0 / uResolution;
            vec4 base = texture2D(uTexture, vUV);
            vec4 glow = vec4(0.0);
            float r = max(1.0, uP0 * 6.0);
            for (float y = -6.0; y <= 6.0; y += 2.0)
            for (float x = -6.0; x <= 6.0; x += 2.0) {
                glow += texture2D(uTexture, vUV + vec2(x, y) * px * r);
            }
            glow /= 16.0;
            vec3 bright = max(glow.rgb - 0.5, 0.0) * 2.0;
            gl_FragColor = vec4(base.rgb + bright * uP1, base.a);
        }
    """

    private val BLOOM = """
        void main() {
            vec2 px = 1.0 / uResolution;
            vec4 base = texture2D(uTexture, vUV);
            vec4 bloom = vec4(0.0);
            float r = max(2.0, uP0 * 10.0);
            for (int i = 0; i < 16; i++) {
                float a = float(i) * 0.3926991;
                vec2 off = vec2(cos(a), sin(a)) * px * r;
                bloom += texture2D(uTexture, vUV + off);
            }
            bloom /= 16.0;
            float lum = dot(bloom.rgb, vec3(0.299, 0.587, 0.114));
            bloom.rgb *= smoothstep(0.6, 1.0, lum);
            gl_FragColor = vec4(base.rgb + bloom.rgb * uP1, base.a);
        }
    """

    private val GODRAYS = """
        void main() {
            vec2 lightPos = vec2(uP1, uP2);
            vec2 dir = (lightPos - vUV) * 0.05;
            vec4 sum = vec4(0.0);
            float decay = 1.0;
            vec2 uv = vUV;
            for (int i = 0; i < 24; i++) {
                uv += dir;
                vec4 s = texture2D(uTexture, uv);
                sum += s * decay;
                decay *= 0.94;
            }
            vec3 rays = max(sum.rgb / 24.0 - 0.3, 0.0) * uP0;
            gl_FragColor = vec4(texture2D(uTexture, vUV).rgb + rays, 1.0);
        }
    """

    private val LENS_FLARE = """
        void main() {
            vec4 base = texture2D(uTexture, vUV);
            vec2 lp = vec2(uP1, uP2);
            float d = length(vUV - lp);
            float flare = exp(-d * 12.0 / max(0.1, uP0));
            vec3 color = vec3(1.0, 0.85, 0.6) * flare;
            float ghost = exp(-length(vUV - (1.0 - lp)) * 20.0);
            color += vec3(0.6, 0.7, 1.0) * ghost * 0.5;
            gl_FragColor = vec4(base.rgb + color, base.a);
        }
    """

    private val LIGHT_LEAK = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float leak = smoothstep(uP0, uP1, vUV.x + sin(vUV.y * 3.0 + uTime) * 0.1);
            vec3 leakColor = vec3(1.0, 0.7, 0.4);
            gl_FragColor = vec4(c.rgb + leakColor * leak * uP2, c.a);
        }
    """

    private val SOFT_GLOW = """
        void main() {
            vec2 px = 1.0 / uResolution;
            vec4 base = texture2D(uTexture, vUV);
            vec3 soft = vec3(0.0);
            float r = max(1.0, uP0 * 4.0);
            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    vec3 c = texture2D(uTexture, vUV + vec2(float(i), float(j)) * px * r).rgb;
                    soft += c * c;
                }
            }
            soft /= 81.0;
            gl_FragColor = vec4(base.rgb * 0.6 + soft * uP1, base.a);
        }
    """

    private val ANAMORPHIC = """
        void main() {
            vec2 px = 1.0 / uResolution;
            vec4 base = texture2D(uTexture, vUV);
            vec3 streak = vec3(0.0);
            for (int i = -12; i <= 12; i++) {
                vec3 c = texture2D(uTexture, vUV + vec2(float(i) * px.x * uP0 * 3.0, 0.0)).rgb;
                streak += c * exp(-abs(float(i)) / 6.0);
            }
            streak /= 13.0;
            streak *= vec3(0.4, 0.6, 1.4);
            gl_FragColor = vec4(base.rgb + streak * uP1, base.a);
        }
    """

    // =========================================================================
    // DISTORTION FAMILY (10)
    // =========================================================================

    private val LENS_DISTORT = """
        void main() {
            vec2 c = vUV - 0.5;
            float r2 = dot(c, c);
            float k = (uP0 - 1.0) * 0.5;
            vec2 uv = 0.5 + c * (1.0 + k * r2);
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            } else {
                gl_FragColor = texture2D(uTexture, uv);
            }
        }
    """

    private val BARREL = """
        void main() {
            vec2 c = vUV - 0.5;
            float r2 = dot(c, c);
            vec2 uv = 0.5 + c * (1.0 + uP0 * 0.5 * r2);
            uv = clamp(uv, 0.0, 1.0);
            gl_FragColor = texture2D(uTexture, uv);
        }
    """

    private val PINCUSHION = """
        void main() {
            vec2 c = vUV - 0.5;
            float r2 = dot(c, c);
            vec2 uv = 0.5 + c * (1.0 - uP0 * 0.5 * r2);
            uv = clamp(uv, 0.0, 1.0);
            gl_FragColor = texture2D(uTexture, uv);
        }
    """

    private val WAVE = """
        void main() {
            vec2 uv = vUV;
            uv.x += sin(uv.y * uP1 * 20.0 + uTime * 2.0) * uP0 * 0.05;
            uv.y += cos(uv.x * uP1 * 20.0 + uTime * 2.0) * uP0 * 0.05;
            gl_FragColor = texture2D(uTexture, clamp(uv, 0.0, 1.0));
        }
    """

    private val RIPPLE = """
        void main() {
            vec2 center = vec2(uP1, uP2);
            float d = distance(vUV, center);
            float wave = sin(d * 40.0 - uTime * 6.0) * 0.02 * uP0;
            vec2 dir = normalize(vUV - center + 1e-5);
            vec2 uv = vUV + dir * wave;
            gl_FragColor = texture2D(uTexture, clamp(uv, 0.0, 1.0));
        }
    """

    private val TWIRL = """
        void main() {
            vec2 center = vec2(0.5);
            vec2 c = vUV - center;
            float d = length(c);
            float angle = uP0 * 3.14159 * (1.0 - smoothstep(0.0, 0.5, d));
            float s = sin(angle), co = cos(angle);
            vec2 rotated = vec2(c.x * co - c.y * s, c.x * s + c.y * co);
            gl_FragColor = texture2D(uTexture, center + rotated);
        }
    """

    private val BULGE = """
        void main() {
            vec2 c = vUV - 0.5;
            float d = length(c);
            float s = pow(1.0 - smoothstep(0.0, 0.5, d), 2.0) * uP0;
            vec2 uv = vUV - c * s;
            gl_FragColor = texture2D(uTexture, clamp(uv, 0.0, 1.0));
        }
    """

    private val PINCH = """
        void main() {
            vec2 c = vUV - 0.5;
            float d = length(c);
            float s = pow(smoothstep(0.0, 0.5, d), 2.0) * uP0;
            vec2 uv = vUV + c * s;
            gl_FragColor = texture2D(uTexture, clamp(uv, 0.0, 1.0));
        }
    """

    private val KALEIDOSCOPE = """
        void main() {
            vec2 c = vUV - 0.5;
            float a = atan(c.y, c.x);
            float r = length(c);
            float seg = 6.2831853 / max(2.0, floor(uP0 * 10.0) + 2.0);
            a = mod(a, seg);
            a = abs(a - seg * 0.5);
            vec2 uv = 0.5 + vec2(cos(a), sin(a)) * r;
            gl_FragColor = texture2D(uTexture, uv);
        }
    """

    private val MIRROR = """
        void main() {
            vec2 uv = vUV;
            if (uP0 > 0.5) {
                uv.x = uv.x < 0.5 ? uv.x * 2.0 : (1.0 - uv.x) * 2.0;
            } else {
                uv.y = uv.y < 0.5 ? uv.y * 2.0 : (1.0 - uv.y) * 2.0;
            }
            gl_FragColor = texture2D(uTexture, uv);
        }
    """

    // ===== END OF PART 1 =====
  // =========================================================================
// STYLIZE FAMILY (12)
// =========================================================================

private val POSTERIZE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float levels = max(2.0, floor(uP0 * 20.0) + 2.0);
        gl_FragColor = vec4(floor(c.rgb * levels) / levels, c.a);
    }
"""

private val HALFTONE = """
    void main() {
        float cell = max(3.0, uP0 * 12.0);
        vec2 g = vUV * uResolution / cell;
        vec2 gc = fract(g) - 0.5;
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        float r = length(gc);
        float dot = step(r, lum * 0.5);
        gl_FragColor = vec4(vec3(dot), c.a);
    }
"""

private val PIXELATE = """
    void main() {
        float size = max(2.0, uP0 * 40.0);
        vec2 px = size / uResolution;
        vec2 uv = floor(vUV / px) * px + px * 0.5;
        gl_FragColor = texture2D(uTexture, uv);
    }
"""

private val EDGE_DETECT = """
    void main() {
        vec2 px = 1.0 / uResolution * max(1.0, uP0 * 3.0);
        float tl = dot(texture2D(uTexture, vUV + vec2(-px.x, -px.y)).rgb, vec3(0.3));
        float t  = dot(texture2D(uTexture, vUV + vec2(0.0, -px.y)).rgb, vec3(0.6));
        float tr = dot(texture2D(uTexture, vUV + vec2(px.x, -px.y)).rgb, vec3(0.3));
        float l  = dot(texture2D(uTexture, vUV + vec2(-px.x, 0.0)).rgb, vec3(0.6));
        float r  = dot(texture2D(uTexture, vUV + vec2(px.x, 0.0)).rgb, vec3(0.6));
        float bl = dot(texture2D(uTexture, vUV + vec2(-px.x, px.y)).rgb, vec3(0.3));
        float b  = dot(texture2D(uTexture, vUV + vec2(0.0, px.y)).rgb, vec3(0.6));
        float br = dot(texture2D(uTexture, vUV + vec2(px.x, px.y)).rgb, vec3(0.3));
        float gx = tr + 2.0 * r + br - tl - 2.0 * l - bl;
        float gy = bl + 2.0 * b + br - tl - 2.0 * t - tr;
        float e = sqrt(gx * gx + gy * gy);
        gl_FragColor = vec4(vec3(e), 1.0);
    }
"""

private val EMBOSS = """
    void main() {
        vec2 px = 1.0 / uResolution;
        float tl = dot(texture2D(uTexture, vUV + vec2(-px.x, -px.y)).rgb, vec3(0.333));
        float tr = dot(texture2D(uTexture, vUV + vec2(px.x, -px.y)).rgb, vec3(0.333));
        float bl = dot(texture2D(uTexture, vUV + vec2(-px.x, px.y)).rgb, vec3(0.333));
        float br = dot(texture2D(uTexture, vUV + vec2(px.x, px.y)).rgb, vec3(0.333));
        float e = (tl - br) * 0.5 + (tr - bl) * 0.5;
        gl_FragColor = vec4(vec3(0.5 + e), 1.0);
    }
"""

private val SHARPEN = """
    void main() {
        vec2 px = 1.0 / uResolution;
        vec4 c = texture2D(uTexture, vUV);
        vec4 n = texture2D(uTexture, vUV + vec2(0.0, -px.y))
               + texture2D(uTexture, vUV + vec2(0.0, px.y))
               + texture2D(uTexture, vUV + vec2(-px.x, 0.0))
               + texture2D(uTexture, vUV + vec2(px.x, 0.0));
        gl_FragColor = c + (c * 4.0 - n) * uP0;
    }
"""

private val CARTOON = """
    void main() {
        vec2 px = 1.0 / uResolution;
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        float quantized = floor(lum * 6.0) / 6.0;
        vec3 posterized = vec3(quantized) * (c.rgb / max(lum, 0.001));
        vec2 e = vec2(1.0, 0.0) * px;
        float gx = length(texture2D(uTexture, vUV + e).rgb - texture2D(uTexture, vUV - e).rgb);
        float gy = length(texture2D(uTexture, vUV + e.yx).rgb - texture2D(uTexture, vUV - e.yx).rgb);
        float edge = step(uP0, length(vec2(gx, gy)) * 4.0);
        gl_FragColor = vec4(posterized * (1.0 - edge), c.a);
    }
"""

private val SKETCH = """
    void main() {
        vec2 px = 1.0 / uResolution;
        float lum = dot(texture2D(uTexture, vUV).rgb, vec3(0.299, 0.587, 0.114));
        float inv = 1.0 - lum;
        float tl = dot(texture2D(uTexture, vUV + vec2(-px.x, -px.y)).rgb, vec3(0.299));
        float br = dot(texture2D(uTexture, vUV + vec2(px.x, px.y)).rgb, vec3(0.299));
        float dodge = lum / max(1.0 - inv, 0.001);
        gl_FragColor = vec4(vec3(clamp(dodge * (1.0 - abs(tl - br) * 3.0), 0.0, 1.0)), 1.0);
    }
"""

private val VIGNETTE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float d = distance(vUV, vec2(0.5));
        float v = smoothstep(0.7, 0.25, d * uP0);
        gl_FragColor = vec4(c.rgb * v, c.a);
    }
"""

private val FILM_GRAIN = """
    float rand(vec2 co) {
        return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
    }
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float n = rand(vUV * uResolution + uTime) - 0.5;
        gl_FragColor = vec4(c.rgb + n * uP0, c.a);
    }
"""

private val SCANLINES = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float line = sin(vUV.y * uResolution.y * 0.8) * 0.5 + 0.5;
        line = smoothstep(0.4, 0.6, line);
        float darken = 1.0 - line * uP0 * 0.4;
        gl_FragColor = vec4(c.rgb * darken, c.a);
    }
"""

private val CRT = """
    void main() {
        vec2 uv = vUV;
        uv.x += sin(uv.y * 80.0) * 0.002 * uP0;
        vec4 c = texture2D(uTexture, uv);
        float scanline = sin(vUV.y * uResolution.y * 1.5) * 0.5 + 0.5;
        c.rgb *= 0.9 + scanline * 0.15;
        float mask = mod(vUV.x * uResolution.x, 3.0) < 1.0 ? 0.9 : 1.0;
        c.rgb *= mask;
        gl_FragColor = c;
    }
"""

// =========================================================================
// COLOR FAMILY (10)
// =========================================================================

private val INVERT = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        gl_FragColor = vec4(1.0 - c.rgb, c.a);
    }
"""

private val BW = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        gl_FragColor = vec4(vec3(lum), c.a);
    }
"""

private val SEPIA = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        vec3 s;
        s.r = dot(c.rgb, vec3(0.393, 0.769, 0.189));
        s.g = dot(c.rgb, vec3(0.349, 0.686, 0.168));
        s.b = dot(c.rgb, vec3(0.272, 0.534, 0.131));
        gl_FragColor = vec4(clamp(s, 0.0, 1.0), c.a);
    }
"""

private val DUOTONE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        vec3 dark = vec3(0.1, 0.05, 0.3);
        vec3 light = vec3(1.0, 0.8, 0.5);
        gl_FragColor = vec4(mix(dark, light, lum), c.a);
    }
"""

private val CROSS_PROCESS = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        vec3 x = c.rgb;
        x.r = clamp((x.r - 0.5) * 1.4 + 0.5, 0.0, 1.0);
        x.g = clamp((x.g - 0.5) * 1.2 + 0.5, 0.0, 1.0);
        x.b = clamp((x.b - 0.5) * 0.8 + 0.55, 0.0, 1.0);
        x.rgb *= vec3(1.05, 1.0, 0.95);
        gl_FragColor = vec4(x, c.a);
    }
"""

private val TEAL_ORANGE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        vec3 shadow = vec3(0.0, 0.15, 0.25);
        vec3 mid = vec3(0.6, 0.5, 0.4);
        vec3 high = vec3(1.0, 0.7, 0.4);
        vec3 graded = lum < 0.5
            ? mix(shadow, mid, lum * 2.0)
            : mix(mid, high, (lum - 0.5) * 2.0);
        gl_FragColor = vec4(mix(c.rgb, graded, uP0), c.a);
    }
"""

private val VINTAGE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        vec3 fade = mix(vec3(0.9, 0.85, 0.7), vec3(0.3, 0.25, 0.2), lum);
        gl_FragColor = vec4(mix(c.rgb, fade, 0.4), c.a);
    }
"""

private val FADED = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        vec3 faded = c.rgb * 0.6 + 0.25;
        gl_FragColor = vec4(mix(c.rgb, faded, uP0), c.a);
    }
"""

private val SATURATE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        float s = mix(1.0, uP0 * 2.0, 0.5);
        gl_FragColor = vec4(mix(vec3(lum), c.rgb, s), c.a);
    }
"""

private val TEMPERATURE = """
    void main() {
        vec4 c = texture2D(uTexture, vUV);
        float t = uP0 - 1.0;
        c.r += t * 0.1;
        c.b -= t * 0.1;
        gl_FragColor = c;
    }
"""

// =========================================================================
// GLITCH FAMILY (5)
// =========================================================================

private val RGB_SPLIT = """
    void main() {
        float amt = uP0 * 0.02;
        float angle = uP1 * 6.2831853;
        vec2 off = vec2(cos(angle), sin(angle)) * amt;
        float r = texture2D(uTexture, vUV + off).r;
        float g = texture2D(uTexture, vUV).g;
        float b = texture2D(uTexture, vUV - off).b;
        gl_FragColor = vec4(r, g, b, 1.0);
    }
"""

private val GLITCH = """
    float rand(float x) {
        return fract(sin(x * 12.9898) * 43758.5453);
    }
    void main() {
        float band = floor(vUV.y * 30.0);
        float t = floor(uTime * 12.0);
        float r = rand(band + t);
        float shift = (r - 0.5) * uP0 * 0.1 * step(0.7, rand(band * 1.3 + t));
        gl_FragColor = texture2D(uTexture, vUV + vec2(shift, 0.0));
    }
"""

private val VHS = """
    void main() {
        vec2 uv = vUV;
        uv.x += sin(uv.y * 200.0 + uTime * 10.0) * 0.001 * uP0;
        vec4 c = texture2D(uTexture, uv);
        float chroma = sin(uv.y * 30.0) * 0.003 * uP0;
        c.r = texture2D(uTexture, uv + vec2(chroma, 0.0)).r;
        c.b = texture2D(uTexture, uv - vec2(chroma, 0.0)).b;
        float noise = fract(sin(dot(uv, vec2(12.9898, 78.233)) + uTime) * 43758.5453);
        c.rgb += (noise - 0.5) * 0.1 * uP1;
        gl_FragColor = c;
    }
"""

private val DATAMOSH = """
    void main() {
        vec2 uv = vUV;
        float block = floor(uv.y * 20.0);
        float t = floor(uTime * 8.0);
        float shift = (fract(sin(block * t) * 43758.5453) - 0.5) * uP0 * 0.15;
        uv.x += shift;
        uv = clamp(uv, 0.0, 1.0);
        gl_FragColor = texture2D(uTexture, uv);
    }
"""

private val CHROMA_SHIFT = """
    void main() {
        vec2 uv = vUV;
        float t = uTime * uP0;
        float r = texture2D(uTexture, uv + vec2(sin(t * 3.0) * 0.01, 0.0)).r;
        float g = texture2D(uTexture, uv).g;
        float b = texture2D(uTexture, uv + vec2(cos(t * 3.0) * 0.01, 0.0)).b;
        gl_FragColor = vec4(r, g, b, 1.0);
    }
"""

// ===== END OF PART 2 =====
    // =========================================================================
    // KEYER FAMILY (3)
    // =========================================================================

    private val CHROMA_KEY = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float key = c.g - max(c.r, c.b);
            float alpha = smoothstep(uP0, uP1, key);
            gl_FragColor = vec4(c.rgb, 1.0 - alpha);
        }
    """

    private val LUMA_KEY = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
            float alpha = smoothstep(uP0, uP1, lum);
            gl_FragColor = vec4(c.rgb, alpha);
        }
    """

    private val DIFFERENCE_MATTE = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float lum = dot(c.rgb, vec3(0.299, 0.587, 0.114));
            float alpha = 1.0 - smoothstep(uP0 - 0.1, uP0 + 0.1, abs(lum - uP1));
            gl_FragColor = vec4(c.rgb, alpha);
        }
    """

    // =========================================================================
    // COMPOSITE FAMILY (2)
    // =========================================================================

    private val NOISE = """
        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
        }
        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                       mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
        }
        void main() {
            float n = noise(vUV * 10.0 + uTime * 0.5);
            gl_FragColor = vec4(vec3(n), 1.0);
        }
    """

    private val TURBULENCE = """
        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }
        void main() {
            vec2 off = vec2(hash(vUV + uTime) - 0.5, hash(vUV - uTime) - 0.5);
            gl_FragColor = texture2D(uTexture, vUV + off * uP0 * 0.05);
        }
    """

    // =========================================================================
    // DEPTH-AWARE FAMILY (6) — need a depth map bound to uDepthMap
    // =========================================================================

    private val DEPTH_OF_FIELD = """
        void main() {
            float focusDepth = uP0;
            float maxBlur = uP1 * 20.0;
            float depth = texture2D(uDepthMap, vUV).r;
            float blur = min(maxBlur, abs(depth - focusDepth) * maxBlur);
            vec2 px = blur / uResolution;
            vec4 sum = vec4(0.0);
            float n = 0.0;
            for (int i = -4; i <= 4; i++) {
                for (int j = -4; j <= 4; j++) {
                    sum += texture2D(uTexture, vUV + vec2(float(i), float(j)) * px);
                    n += 1.0;
                }
            }
            gl_FragColor = sum / n;
        }
    """

    private val RACK_FOCUS = """
        void main() {
            float focusDepth = uP0;
            float maxBlur = uP1 * 24.0;
            float depth = texture2D(uDepthMap, vUV).r;
            float blur = min(maxBlur, abs(depth - focusDepth) * maxBlur);
            vec2 px = blur / uResolution;
            vec4 sum = vec4(0.0);
            float n = 0.0;
            for (int i = -5; i <= 5; i++) {
                for (int j = -5; j <= 5; j++) {
                    sum += texture2D(uTexture, vUV + vec2(float(i), float(j)) * px);
                    n += 1.0;
                }
            }
            gl_FragColor = sum / n;
        }
    """

    private val DEPTH_FOG = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float depth = texture2D(uDepthMap, vUV).r;
            float fog = smoothstep(0.0, 1.0, depth) * uP0;
            vec3 fogColor = vec3(uP1, uP1, uP2);
            gl_FragColor = vec4(mix(c.rgb, fogColor, fog), c.a);
        }
    """

    private val DEPTH_GRADE = """
        void main() {
            vec4 c = texture2D(uTexture, vUV);
            float depth = texture2D(uDepthMap, vUV).r;
            vec3 nearTint = vec3(1.0, 0.9, 0.7);
            vec3 farTint = vec3(0.6, 0.7, 1.0);
            vec3 tint = mix(farTint, nearTint, depth);
            gl_FragColor = vec4(c.rgb * mix(vec3(1.0), tint, uP0), c.a);
        }
    """

    private val DEPTH_BOKEH = """
        void main() {
            float focusDepth = uP0;
            float maxBlur = uP1 * 30.0;
            float depth = texture2D(uDepthMap, vUV).r;
            float blur = min(maxBlur, abs(depth - focusDepth) * maxBlur);
            vec2 px = blur / uResolution;
            vec4 sum = vec4(0.0);
            for (int i = 0; i < 12; i++) {
                float a = float(i) * 0.5235988;
                vec2 off = vec2(cos(a), sin(a)) * px;
                sum += texture2D(uTexture, vUV + off);
            }
            gl_FragColor = sum / 12.0;
        }
    """

    private val DEPTH_PARALLAX = """
        void main() {
            float depth = texture2D(uDepthMap, vUV).r;
            float strength = uP0 * 0.1;
            vec2 offset = vec2(uP1 - 0.5, uP2 - 0.5) * strength * (1.0 - depth);
            gl_FragColor = texture2D(uTexture, clamp(vUV + offset, 0.0, 1.0));
        }
    """

    // =========================================================================
    // THE CATALOG
    // =========================================================================

    val ALL: List<EffectDef> = listOf(
        // Blur (6)
        EffectDef("Box Blur", "Blur", BLUR_BOX),
        EffectDef("Gaussian Blur", "Blur", BLUR_GAUSSIAN),
        EffectDef("Directional Blur", "Blur", BLUR_DIRECTIONAL),
        EffectDef("Radial Blur", "Blur", BLUR_RADIAL),
        EffectDef("Zoom Blur", "Blur", BLUR_ZOOM),
        EffectDef("Motion Blur", "Blur", BLUR_MOTION),
        // Light (7)
        EffectDef("Glow", "Light", GLOW),
        EffectDef("Bloom", "Light", BLOOM),
        EffectDef("God Rays", "Light", GODRAYS),
        EffectDef("Lens Flare", "Light", LENS_FLARE),
        EffectDef("Light Leak", "Light", LIGHT_LEAK),
        EffectDef("Soft Glow", "Light", SOFT_GLOW),
        EffectDef("Anamorphic Flare", "Light", ANAMORPHIC),
        // Distortion (10)
        EffectDef("Lens Distort", "Distortion", LENS_DISTORT),
        EffectDef("Barrel", "Distortion", BARREL),
        EffectDef("Pincushion", "Distortion", PINCUSHION),
        EffectDef("Wave", "Distortion", WAVE),
        EffectDef("Ripple", "Distortion", RIPPLE),
        EffectDef("Twirl", "Distortion", TWIRL),
        EffectDef("Bulge", "Distortion", BULGE),
        EffectDef("Pinch", "Distortion", PINCH),
        EffectDef("Kaleidoscope", "Distortion", KALEIDOSCOPE),
        EffectDef("Mirror", "Distortion", MIRROR),
        // Stylize (12)
        EffectDef("Posterize", "Stylize", POSTERIZE),
        EffectDef("Halftone", "Stylize", HALFTONE),
        EffectDef("Pixelate", "Stylize", PIXELATE),
        EffectDef("Edge Detect", "Stylize", EDGE_DETECT),
        EffectDef("Emboss", "Stylize", EMBOSS),
        EffectDef("Sharpen", "Stylize", SHARPEN),
        EffectDef("Cartoon", "Stylize", CARTOON),
        EffectDef("Sketch", "Stylize", SKETCH),
        EffectDef("Vignette", "Stylize", VIGNETTE),
        EffectDef("Film Grain", "Stylize", FILM_GRAIN),
        EffectDef("Scanlines", "Stylize", SCANLINES),
        EffectDef("CRT", "Stylize", CRT),
        // Color (10)
        EffectDef("Invert", "Color", INVERT),
        EffectDef("Black & White", "Color", BW),
        EffectDef("Sepia", "Color", SEPIA),
        EffectDef("Duotone", "Color", DUOTONE),
        EffectDef("Cross Process", "Color", CROSS_PROCESS),
        EffectDef("Teal & Orange", "Color", TEAL_ORANGE),
        EffectDef("Vintage", "Color", VINTAGE),
        EffectDef("Faded", "Color", FADED),
        EffectDef("Saturate", "Color", SATURATE),
        EffectDef("Temperature", "Color", TEMPERATURE),
        // Glitch (5)
        EffectDef("RGB Split", "Glitch", RGB_SPLIT),
        EffectDef("Glitch", "Glitch", GLITCH),
        EffectDef("VHS", "Glitch", VHS),
        EffectDef("Datamosh", "Glitch", DATAMOSH),
        EffectDef("Chroma Shift", "Glitch", CHROMA_SHIFT),
        // Keyer (3)
        EffectDef("Chroma Key", "Keyer", CHROMA_KEY),
        EffectDef("Luma Key", "Keyer", LUMA_KEY),
        EffectDef("Difference Matte", "Keyer", DIFFERENCE_MATTE),
        // Composite (2)
        EffectDef("Noise", "Composite", NOISE),
        EffectDef("Turbulence", "Composite", TURBULENCE),
        // Depth (6) — locked until a depth map is generated
        EffectDef("Depth of Field", "Depth", DEPTH_OF_FIELD, requiresDepth = true),
        EffectDef("Rack Focus", "Depth", RACK_FOCUS, requiresDepth = true),
        EffectDef("Depth Fog", "Depth", DEPTH_FOG, requiresDepth = true),
        EffectDef("Depth Grade", "Depth", DEPTH_GRADE, requiresDepth = true),
        EffectDef("Depth Bokeh", "Depth", DEPTH_BOKEH, requiresDepth = true),
        EffectDef("Depth Parallax", "Depth", DEPTH_PARALLAX, requiresDepth = true),
    )

    /** 60 effects total. Every one is a working GPU shader. */
    val COUNT: Int get() = ALL.size

    fun byName(name: String): EffectDef? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun byCategory(category: String): List<EffectDef> =
        ALL.filter { it.category.equals(category, ignoreCase = true) }

    val CATEGORIES: List<String> get() = ALL.map { it.category }.distinct()

    /** Returns the full fragment source (header + body) for a given effect. */
    fun fullFragment(def: EffectDef): String = HEADER + "\n" + def.fragment
}
