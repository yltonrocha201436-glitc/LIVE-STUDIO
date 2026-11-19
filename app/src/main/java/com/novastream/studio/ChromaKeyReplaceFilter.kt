package com.novastream.studio

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GPU chroma-key replacement filter.
 *
 * It samples the camera frame and a user-selected background texture.
 * Green pixels are replaced by the background; foreground pixels remain.
 * The background texture is uploaded once on the GL render thread.
 */
class ChromaKeyReplaceFilter(
    private val backgroundBitmap: Bitmap,
    private val sensitivity: Float
) : BaseFilterRender() {

    private val vertices = floatArrayOf(
        -1f, -1f, 0f, 0f, 0f,
         1f, -1f, 0f, 1f, 0f,
        -1f,  1f, 0f, 0f, 1f,
         1f,  1f, 0f, 1f, 1f
    )

    private var program = 0
    private var positionHandle = -1
    private var textureCoordHandle = -1
    private var mvpHandle = -1
    private var stHandle = -1
    private var cameraSamplerHandle = -1
    private var backgroundSamplerHandle = -1
    private var thresholdHandle = -1
    private var softnessHandle = -1
    private var backgroundTexture = 0

    init {
        squareVertex = ByteBuffer.allocateDirect(vertices.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        squareVertex.put(vertices).position(0)
        Matrix.setIdentityM(MVPMatrix, 0)
        Matrix.setIdentityM(STMatrix, 0)
    }

    override fun initGlFilter(context: android.content.Context) {
        val vertexShader = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uCamera;
            uniform sampler2D uBackground;
            uniform float uThreshold;
            uniform float uSoftness;
            void main() {
                vec4 camera = texture2D(uCamera, vTextureCoord);
                vec4 background = texture2D(uBackground, vTextureCoord);
                float dominantGreen = max(0.0, camera.g - max(camera.r, camera.b));
                float greenAmount = smoothstep(uThreshold, uThreshold + uSoftness, dominantGreen);
                float keepForeground = 1.0 - greenAmount;
                gl_FragColor = vec4(mix(background.rgb, camera.rgb, keepForeground), 1.0);
            }
        """.trimIndent()

        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        val log = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        check(linkStatus[0] == GLES20.GL_TRUE) { "Chroma shader link failed: $log" }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        stHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        cameraSamplerHandle = GLES20.glGetUniformLocation(program, "uCamera")
        backgroundSamplerHandle = GLES20.glGetUniformLocation(program, "uBackground")
        thresholdHandle = GLES20.glGetUniformLocation(program, "uThreshold")
        softnessHandle = GLES20.glGetUniformLocation(program, "uSoftness")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        backgroundTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, backgroundBitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun drawFilter() {
        GLES20.glUseProgram(program)

        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            textureCoordHandle, 2, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex
        )
        GLES20.glEnableVertexAttribArray(textureCoordHandle)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(stHandle, 1, false, STMatrix, 0)
        GLES20.glUniform1f(thresholdHandle, 0.08f + (1f - sensitivity.coerceIn(0f, 1f)) * 0.20f)
        GLES20.glUniform1f(softnessHandle, 0.06f)

        GLES20.glUniform1i(cameraSamplerHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)

        GLES20.glUniform1i(backgroundSamplerHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture)
    }

    override fun disableResources() {
        GlUtil.disableResources(textureCoordHandle, positionHandle)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    override fun release() {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (backgroundTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(backgroundTexture), 0)
        }
        program = 0
        backgroundTexture = 0
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        val log = GLES20.glGetShaderInfoLog(shader)
        check(status[0] == GLES20.GL_TRUE) {
            "Chroma shader compile failed: $log"
        }
        return shader
    }
}
