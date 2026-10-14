# LifeStream release shrinking: unused code is removed, nothing is renamed (stack traces stay
# readable and nothing that's looked up by name can break).
-dontobfuscate

# NewPipeExtractor runs YouTube's player JavaScript through the Rhino engine, which finds many of
# its own classes by name, so the extractor and everything it parses with are kept whole.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.ocpsoft.prettytime.** { *; }

# Desktop-Java and annotation-only references inside those libraries that Android never uses.
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.**
-dontwarn org.jspecify.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.slf4j.**
-dontwarn sun.misc.Unsafe
-dontwarn org.re2j.**
