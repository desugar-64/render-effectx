# R8 rules for the render-effectx demo app.
#
# The effect pipeline is plain GPU code (HardwareBuffer + OpenGL ES via androidx.graphics);
# shader sources are string literals and no part of it relies on reflection, so the defaults
# in proguard-android-optimize.txt plus the AGP/Compose consumer rules are enough.
#
# Keep line numbers so any demo-time crash is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# androidx.graphics:graphics-core ships a native lib (libgraphics-core.so) whose JNI_OnLoad
# RegisterNatives() binds to these classes/methods *by name*. R8 obfuscation renames them, so the
# native load fails (UnsatisfiedLinkError on androidx.opengl.EGLBindings) and the whole GL effect
# pipeline silently no-ops. Keep the JNI-bound surface intact.
-keep class androidx.graphics.** { *; }
-keep class androidx.opengl.** { *; }
-keep class androidx.hardware.** { *; }
