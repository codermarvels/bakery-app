# Project-specific R8 rules (release builds shrink and obfuscate the code).
# Most libraries ship their own consumer rules; these cover the gaps.

# Razorpay Checkout (from Razorpay's Android integration guide).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
    public void onPayment*(...);
}

# Type-safe navigation looks up enum route arguments (SignInRoute.role) by class name.
-keepnames class com.vsjbakery.app.domain.model.AppRole

# Ktor finds its HTTP engine (OkHttp) through ServiceLoader.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer

# Optional logging / JVM-only classes referenced by Ktor and Supabase but absent on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
