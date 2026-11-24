package com.example.media.gpu

/**
 * Real-time OpenGL ES 3.0 (#version 300 es) and GLES 2.0 fallback GLSL shaders
 * for hardware-accelerated video color grading, sharpening, blur, vignette,
 * grayscale, and sepia effects inside Media3 VideoFrameProcessor.
 */
object ExcevoGlslShaders {

    val VERTEX_SHADER_ES3: String = """
        #version 300 es
        in vec4 aFramePosition;
        out vec2 vTexSamplingCoord;
        void main() {
          gl_Position = aFramePosition;
          vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
        }
    """.trimIndent()

    val FRAGMENT_SHADER_ES3: String = """
        #version 300 es
        precision highp float;
        uniform sampler2D uTexSampler;
        uniform vec2 uTexelSize;
        uniform float uExposure;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uTint;
        uniform float uHighlights;
        uniform float uShadows;
        uniform float uSharpness;
        uniform float uBlur;
        uniform float uGlow;
        uniform float uVignette;
        uniform float uGrayscale;
        uniform float uSepia;
        in vec2 vTexSamplingCoord;
        out vec4 outColor;

        void main() {
          vec4 baseSample = texture(uTexSampler, vTexSamplingCoord);
          vec3 rgb = baseSample.rgb;

          if (uBlur > 0.001) {
            vec2 stepUv = uTexelSize * (1.0 + uBlur * 5.5);
            vec3 acc = rgb * 0.25;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(stepUv.x, 0.0), 0.0, 1.0)).rgb * 0.125;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord - vec2(stepUv.x, 0.0), 0.0, 1.0)).rgb * 0.125;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, stepUv.y), 0.0, 1.0)).rgb * 0.125;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, stepUv.y), 0.0, 1.0)).rgb * 0.125;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(stepUv.x, stepUv.y), 0.0, 1.0)).rgb * 0.0625;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(-stepUv.x, stepUv.y), 0.0, 1.0)).rgb * 0.0625;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(stepUv.x, -stepUv.y), 0.0, 1.0)).rgb * 0.0625;
            acc += texture(uTexSampler, clamp(vTexSamplingCoord + vec2(-stepUv.x, -stepUv.y), 0.0, 1.0)).rgb * 0.0625;
            rgb = mix(rgb, acc, clamp(uBlur * 1.15, 0.0, 1.0));
          } else if (uSharpness > 0.001) {
            float amt = uSharpness * 0.75;
            vec3 n = texture(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
            vec3 s = texture(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
            vec3 e = texture(uTexSampler, clamp(vTexSamplingCoord + vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
            vec3 w = texture(uTexSampler, clamp(vTexSamplingCoord - vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
            rgb = rgb * (1.0 + 4.0 * amt) - (n + s + e + w) * amt;
          }

          rgb *= exp2(uExposure);
          rgb += vec3(uBrightness * 0.32);

          float lumaTonemap = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          float shadowMask = pow(1.0 - clamp(lumaTonemap, 0.0, 1.0), 2.0);
          float highlightMask = pow(clamp(lumaTonemap, 0.0, 1.0), 2.0);
          rgb += vec3(uShadows * 0.25 * shadowMask + uHighlights * 0.25 * highlightMask);

          rgb.r *= (1.0 + uTemperature * 0.16 + uTint * 0.07);
          rgb.g *= (1.0 - uTint * 0.14);
          rgb.b *= (1.0 - uTemperature * 0.16 + uTint * 0.07);

          float contrastScale = clamp(1.0 + uContrast * 0.75, 0.2, 2.2);
          rgb = (rgb - 0.5) * contrastScale + 0.5;

          float lumaSat = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          rgb = mix(vec3(lumaSat), rgb, clamp(1.0 + uSaturation, 0.0, 2.2));

          float lumaGray = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          rgb = mix(rgb, vec3(lumaGray), clamp(uGrayscale, 0.0, 1.0));

          if (uSepia > 0.001) {
            vec3 sep = vec3(
              dot(rgb, vec3(0.393, 0.769, 0.189)),
              dot(rgb, vec3(0.349, 0.686, 0.168)),
              dot(rgb, vec3(0.272, 0.534, 0.131))
            );
            rgb = mix(rgb, sep, clamp(uSepia, 0.0, 1.0));
          }

          if (uGlow > 0.001) {
            vec2 gStep = uTexelSize * (3.0 + uGlow * 9.0);
            vec3 bloom = vec3(0.0);
            vec3 s1 = texture(uTexSampler, clamp(vTexSamplingCoord + vec2(gStep.x, 0.0), 0.0, 1.0)).rgb;
            vec3 s2 = texture(uTexSampler, clamp(vTexSamplingCoord - vec2(gStep.x, 0.0), 0.0, 1.0)).rgb;
            vec3 s3 = texture(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, gStep.y), 0.0, 1.0)).rgb;
            vec3 s4 = texture(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, gStep.y), 0.0, 1.0)).rgb;
            bloom = (max(s1 - vec3(0.35), vec3(0.0)) +
                     max(s2 - vec3(0.35), vec3(0.0)) +
                     max(s3 - vec3(0.35), vec3(0.0)) +
                     max(s4 - vec3(0.35), vec3(0.0))) * 0.35;
            rgb = rgb + bloom * (uGlow * 1.15);
          }

          if (uVignette > 0.001) {
            float dist = distance(vTexSamplingCoord, vec2(0.5, 0.5));
            float vig = smoothstep(0.85, 0.22, dist);
            rgb *= mix(1.0, vig, clamp(uVignette, 0.0, 1.0));
          }

          outColor = vec4(clamp(rgb, 0.0, 1.0), baseSample.a);
        }
    """.trimIndent()

    val VERTEX_SHADER_ES2: String = """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
          gl_Position = aFramePosition;
          vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
        }
    """.trimIndent()

    val FRAGMENT_SHADER_ES2: String = """
        precision mediump float;
        uniform sampler2D uTexSampler;
        uniform vec2 uTexelSize;
        uniform float uExposure;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uTint;
        uniform float uHighlights;
        uniform float uShadows;
        uniform float uSharpness;
        uniform float uBlur;
        uniform float uGlow;
        uniform float uVignette;
        uniform float uGrayscale;
        uniform float uSepia;
        varying vec2 vTexSamplingCoord;

        void main() {
          vec4 baseSample = texture2D(uTexSampler, vTexSamplingCoord);
          vec3 rgb = baseSample.rgb;

          if (uBlur > 0.001) {
            vec2 stepUv = uTexelSize * (1.0 + uBlur * 5.0);
            vec3 acc = rgb * 0.4;
            acc += texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(stepUv.x, 0.0), 0.0, 1.0)).rgb * 0.15;
            acc += texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(stepUv.x, 0.0), 0.0, 1.0)).rgb * 0.15;
            acc += texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, stepUv.y), 0.0, 1.0)).rgb * 0.15;
            acc += texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, stepUv.y), 0.0, 1.0)).rgb * 0.15;
            rgb = mix(rgb, acc, clamp(uBlur * 1.1, 0.0, 1.0));
          } else if (uSharpness > 0.001) {
            float amt = uSharpness * 0.65;
            vec3 n = texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
            vec3 s = texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
            vec3 e = texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
            vec3 w = texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
            rgb = rgb * (1.0 + 4.0 * amt) - (n + s + e + w) * amt;
          }

          rgb *= pow(2.0, uExposure);
          rgb += vec3(uBrightness * 0.32);

          float lumaTonemap = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          float shadowMask = pow(1.0 - clamp(lumaTonemap, 0.0, 1.0), 2.0);
          float highlightMask = pow(clamp(lumaTonemap, 0.0, 1.0), 2.0);
          rgb += vec3(uShadows * 0.25 * shadowMask + uHighlights * 0.25 * highlightMask);

          rgb.r *= (1.0 + uTemperature * 0.16 + uTint * 0.07);
          rgb.g *= (1.0 - uTint * 0.14);
          rgb.b *= (1.0 - uTemperature * 0.16 + uTint * 0.07);

          float contrastScale = clamp(1.0 + uContrast * 0.75, 0.2, 2.2);
          rgb = (rgb - 0.5) * contrastScale + 0.5;

          float lumaSat = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          rgb = mix(vec3(lumaSat), rgb, clamp(1.0 + uSaturation, 0.0, 2.2));

          float lumaGray = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
          rgb = mix(rgb, vec3(lumaGray), clamp(uGrayscale, 0.0, 1.0));

          if (uSepia > 0.001) {
            vec3 sep = vec3(
              dot(rgb, vec3(0.393, 0.769, 0.189)),
              dot(rgb, vec3(0.349, 0.686, 0.168)),
              dot(rgb, vec3(0.272, 0.534, 0.131))
            );
            rgb = mix(rgb, sep, clamp(uSepia, 0.0, 1.0));
          }

          if (uGlow > 0.001) {
            vec2 gStep = uTexelSize * (3.0 + uGlow * 8.0);
            vec3 s1 = texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(gStep.x, 0.0), 0.0, 1.0)).rgb;
            vec3 s2 = texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(gStep.x, 0.0), 0.0, 1.0)).rgb;
            vec3 s3 = texture2D(uTexSampler, clamp(vTexSamplingCoord + vec2(0.0, gStep.y), 0.0, 1.0)).rgb;
            vec3 s4 = texture2D(uTexSampler, clamp(vTexSamplingCoord - vec2(0.0, gStep.y), 0.0, 1.0)).rgb;
            vec3 bloom = (max(s1 - vec3(0.35), vec3(0.0)) +
                          max(s2 - vec3(0.35), vec3(0.0)) +
                          max(s3 - vec3(0.35), vec3(0.0)) +
                          max(s4 - vec3(0.35), vec3(0.0))) * 0.35;
            rgb = rgb + bloom * (uGlow * 1.1);
          }

          if (uVignette > 0.001) {
            float dist = distance(vTexSamplingCoord, vec2(0.5, 0.5));
            float vig = smoothstep(0.85, 0.22, dist);
            rgb *= mix(1.0, vig, clamp(uVignette, 0.0, 1.0));
          }

          gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), baseSample.a);
        }
    """.trimIndent()
}
