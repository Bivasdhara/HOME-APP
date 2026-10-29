# Add project specific ProGuard rules here.
# For Google Gson:
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.smarthome.app.DeviceStatus { *; }
-keep class com.smarthome.app.DeviceStatus$RelayStatus { *; }
