# Apache POI uses reflection-heavy XML parsing
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.batik.**
# POI transitively references log4j / OSGi / desktop-Java / build-time
# annotation classes that don't exist on Android. They sit on code paths the
# app never executes, so silence R8's missing-class errors (full-mode R8
# treats these as hard build failures otherwise).
-dontwarn aQute.bnd.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.graphbuilder.**
-dontwarn java.awt.**

# Keep entity classes for Room
-keep @androidx.room.Entity class * { *; }
