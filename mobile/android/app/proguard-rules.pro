# The bundled page calls these methods through WebView reflection.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# SMBJ's EL and Kerberos integrations are optional and are not available on Android.
# This app uses SMBJ's NTLM authentication path instead.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**

# MBassador discovers SMBJ event handlers through runtime annotations.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler <methods>;
}
