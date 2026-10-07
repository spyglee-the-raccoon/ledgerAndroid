
# ML Kit (text recognition and the Gemini Nano Prompt API) registers its internal services with a
# component registry keyed by class, found through manifest metadata at runtime. R8's full mode
# (the AGP default) merges and rewrites those classes, so lookups such as MLTaskExecutor return null
# and every receipt scan fails in release builds. Keep them as they are.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.firebase.components.** { *; }
