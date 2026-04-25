# Apache POI uses reflection-heavy XML parsing
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.stream.**
-dontwarn org.apache.batik.**

# Keep entity classes for Room
-keep @androidx.room.Entity class * { *; }
