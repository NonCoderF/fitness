# ML Kit Pose Detection 18.0.0-beta5 initializes model/graph classes
# reflectively. Preserve these classes when an app minifies an SDK consumer.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
