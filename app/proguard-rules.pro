# Keep NewPipe Extractor / Rhino internals (needed if minification is ever enabled).
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { *; }

# Keep custom rules here if required by the selected conversion library.

# ---- Off: keep what is loaded by name at runtime ----
# NewPipe Extractor builds its services and extractors reflectively from ServiceList.
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
# Chaquopy talks to the Python runtime through JNI and reflection.
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**
# The LoadingIndicator is a Material Components view, inflated and styled by name.
-keep class com.google.android.material.loadingindicator.** { *; }
-keep class com.google.android.material.shape.** { *; }
-dontwarn com.google.android.material.**
# OkHttp / Jsoup, pulled in by the extractor.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
# Keep the entry points the manifest refers to.
-keep class com.off.audioconverter.MainActivity { *; }
-keep class com.off.audioconverter.video.VideoDownloadService { *; }

# Rhino, the JavaScript engine inside NewPipe Extractor, references the JDK's
# dynalink package. Android has no such package, and R8 refuses to finish while
# those references are unresolved. They are never reached on device.
-dontwarn jdk.dynalink.**
-dontwarn java.lang.invoke.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**

# Shrinking is what makes the app small, but obfuscation is what makes R8 risky:
# it renames anything reached by reflection, and a renamed class shows up as a
# NoClassDefFoundError at startup. Off gains little from renaming, so it is off.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters,SourceFile,LineNumberTable
# Off's own code is small; keeping it whole removes a whole class of surprises.
-keep class com.off.audioconverter.** { *; }
