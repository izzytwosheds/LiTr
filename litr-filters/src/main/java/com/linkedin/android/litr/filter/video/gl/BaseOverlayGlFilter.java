/*
 * Copyright 2019 LinkedIn Corporation
 * All Rights Reserved.
 *
 * Licensed under the BSD 2-Clause License (the "License").  See License in the project root for
 * license information.
 */
package com.linkedin.android.litr.filter.video.gl;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.linkedin.android.litr.filter.GlFilter;
import com.linkedin.android.litr.render.GlRenderUtils;

abstract class BaseOverlayGlFilter implements GlFilter {

    private static final String VERTEX_SHADER =
        "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String FRAGMENT_OVERLAY_SHADER =
        "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTextureCoord);\n" +
            "}\n";

    private final PointF topLeftPosition;
    private final PointF size;
    private final float rotation;

    private int glOverlayProgram;
    private int overlayMvpMatrixHandle;
    private int overlayUstMatrixHandle;

    private float[] mvpMatrix;
    private int mvpMatrixOffset;

    private float[] stMatrix = new float[16];

    BaseOverlayGlFilter(@Nullable RectF bitmapRect) {
        if (bitmapRect == null) {
            size = new PointF(1, 1);
            topLeftPosition = new PointF(0, 0);
        } else {
            size = new PointF(bitmapRect.right - bitmapRect.left, bitmapRect.bottom - bitmapRect.top);
            topLeftPosition = new PointF(bitmapRect.left, bitmapRect.top);
        }
        rotation = 0;
    }

    BaseOverlayGlFilter(@NonNull PointF size, @NonNull PointF topLeftPosition, float rotation) {
        this.size = size;
        this.topLeftPosition = topLeftPosition;
        this.rotation = rotation;
    }

    @Override
    @CallSuper
    public void init(@NonNull float[] mvpMatrix, int mvpMatrixOffset) {
        Matrix.setIdentityM(stMatrix, 0);
        // flip the bitmap vertically
        Matrix.scaleM(stMatrix, 0, 1, -1, 1);

        this.mvpMatrix = mvpMatrix;
        this.mvpMatrixOffset = mvpMatrixOffset;

        float scaleX = size.x;
        float scaleY = size.y;
        float translateX = (topLeftPosition.x * 2 + scaleX - 1) / scaleX;
        float translateY = (1 - topLeftPosition.y * 2 - scaleY) / scaleY;
        Matrix.scaleM(mvpMatrix, mvpMatrixOffset, scaleX, scaleY, 1);
        Matrix.translateM(mvpMatrix, mvpMatrixOffset, translateX, translateY, 0);
        Matrix.rotateM(mvpMatrix, mvpMatrixOffset, rotation, 0, 0, 1);
    }

    void renderOverlayTexture(int textureId) {
        // Switch to overlay texture
        GLES20.glUseProgram(glOverlayProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        GLES20.glUniformMatrix4fv(overlayMvpMatrixHandle, 1, false, mvpMatrix, mvpMatrixOffset);
        GLES20.glUniformMatrix4fv(overlayUstMatrixHandle, 1, false, stMatrix, 0);

        // Enable blending
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        // Call OpenGL to draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlRenderUtils.checkGlError("glDrawArrays");

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /**
     * Create a texture and load the overlay bitmap into this texture.
     */
    int createOverlayTexture(@NonNull Bitmap overlayBitmap) {
        int overlayTextureID;

        // Create program
        glOverlayProgram = GlRenderUtils.createProgram(VERTEX_SHADER, FRAGMENT_OVERLAY_SHADER);
        if (glOverlayProgram == 0) {
            throw new RuntimeException("failed creating glOverlayProgram");
        }

        // Get the location of our uniforms
        overlayMvpMatrixHandle = GLES20.glGetUniformLocation(glOverlayProgram, "uMVPMatrix");
        GlRenderUtils.checkGlError("glGetUniformLocation uMVPMatrix");
        if (overlayMvpMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        overlayUstMatrixHandle = GLES20.glGetUniformLocation(glOverlayProgram, "uSTMatrix");
        GlRenderUtils.checkGlError("glGetUniformLocation uSTMatrix");
        if (overlayUstMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        // Generate one texture for overlay
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        overlayTextureID = textures[0];

        // Tell OpenGL to bind this texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureID);
        GlRenderUtils.checkGlError("glBindTexture overlayTextureID");

        // Set default texture filtering parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GlRenderUtils.checkGlError("glTexParameter");

        // Load the bitmap and copy it over into the texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0);

        return overlayTextureID;
    }
}
