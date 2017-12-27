/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;


public class ComposeShader extends Shader {

    private static final int TYPE_XFERMODE = 1;
    private static final int TYPE_PORTERDUFFMODE = 2;

    /**
     * Type of the ComposeShader: can be either TYPE_XFERMODE or TYPE_PORTERDUFFMODE
     */
    private int mType;

    private PorterDuff.Mode mPorterDuffMode;

    /**
     * Hold onto the shaders to avoid GC.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    private final Shader mShaderA;
    @SuppressWarnings({"UnusedDeclaration"})
    private final Shader mShaderB;


    /** Create a new compose shader, given shaders A, B, and a combining PorterDuff mode.
        When the mode is applied, it will be given the result from shader A as its
        "dst", and the result from shader B as its "src".
        @param shaderA  The colors from this shader are seen as the "dst" by the mode
        @param shaderB  The colors from this shader are seen as the "src" by the mode
        @param mode     The PorterDuff mode that combines the colors from the two shaders.
    */
    public ComposeShader(Shader shaderA, Shader shaderB, PorterDuff.Mode mode) {
        mType = TYPE_PORTERDUFFMODE;
        mShaderA = shaderA;
        mShaderB = shaderB;
        mPorterDuffMode = mode;
        native_instance = nativeCreate2(shaderA.native_instance, shaderB.native_instance,
                mode.nativeInt);
        native_shader = nativePostCreate2(native_instance, shaderA.native_shader,
                shaderB.native_shader, mode.nativeInt);
    }

    /**
     * @hide
     */
    @Override
    protected Shader copy() {
        final ComposeShader copy;
        switch (mType) {
            case TYPE_PORTERDUFFMODE:
                copy = new ComposeShader(mShaderA.copy(), mShaderB.copy(), mPorterDuffMode);
                break;
            default:
                throw new IllegalArgumentException(
                        "ComposeShader should be created with either Xfermode or PorterDuffMode");
        }
        copyLocalMatrix(copy);
        return copy;
    }

    private static native int nativeCreate1(int native_shaderA, int native_shaderB,
            int native_mode);
    private static native int nativeCreate2(int native_shaderA, int native_shaderB,
            int porterDuffMode);
    private static native int nativePostCreate1(int native_shader, int native_skiaShaderA,
            int native_skiaShaderB, int native_mode);
    private static native int nativePostCreate2(int native_shader, int native_skiaShaderA,
            int native_skiaShaderB, int porterDuffMode);
}
