# Keep NewPipe Extractor / Rhino internals (needed if minification is ever enabled).
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.schabi.newpipe.extractor.** { *; }

# Keep custom rules here if required by the selected conversion library.
