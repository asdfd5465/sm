#===============================================================================
# DEFAULT ANDROID RULES (usually included via getDefaultProguardFile)
#===============================================================================
# This assumes you have 'getDefaultProguardFile("proguard-android-optimize.txt")'
# in your build.gradle.kts. That file handles keeping Activities, Services, etc.
# referenced in the Manifest, and other common Android framework needs.

#===============================================================================
# KEEP KOTLIN SPECIFIC METADATA AND REFLECTION
#===============================================================================
# Keep Kotlin metadata for reflection and extension functions to work correctly.
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }

# Keep all classes and interfaces in kotlin.reflect package and their members, and subpackages
-keep class kotlin.reflect.** { *; }
-keep interface kotlin.reflect.** { *; }

# Keep things related to coroutines
-keepclassmembers class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    kotlin.coroutines.Continuation continuation;
    java.lang.Object L$0;
    int label;
}
-keepclassmembers class kotlin.coroutines.jvm.internal.DebugMetadata {
    <fields>;
    <methods>;
}
-keepclassmembers class kotlin.coroutines.jvm.internal.ContinuationImpl {
    <fields>;
    <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CommonPool # If you use older coroutine versions or specific dispatchers
-keepnames class kotlinx.coroutines.DefaultScheduler # If you use older coroutine versions
-keepclassmembernames class kotlinx.coroutines.flow.internal.FlowExceptions {
    <fields>; # For some specific internal exception handling in Flows
}

#===============================================================================
# KEEP RULES FOR APP-SPECIFIC CLASSES AND LIBRARIES
#===============================================================================

# --- Your Application's Data Classes (for Gson and general keeping) ---
# Ensure your package name is correct.
-keep public class com.offlinedictionary.pro.WordDefinitionEntry { *; }
-keep public class com.offlinedictionary.pro.DefinitionDetail { *; }

# --- Gson ---
# Keep attributes needed by Gson
# -keepattributes Signature (already covered above)
# -keepattributes InnerClasses (already covered above)
# -keepattributes *Annotation* (already covered above)

# Keep Gson specific types (often needed)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# For Gson, keeping the model classes with all members (`{ *; }`) is generally
# the safest approach, especially with Kotlin data classes.
# The rules for WordDefinitionEntry and DefinitionDetail above cover this.


# --- AndroidX Libraries (many are covered by default rules or aapt_rules) ---
# AppCompat, Core-KTX, ConstraintLayout, RecyclerView, Lifecycle.
# If you use Data Binding or View Binding, different rules might be needed.
# For standard usage, explicit rules are often not required beyond the defaults.

# Keep ViewModels if you use them and they are instantiated via reflection by the framework.
# -keep class * extends androidx.lifecycle.ViewModel { <init>(); }


# --- TextToSpeech ---
# Ensure your OnInitListener implementation is kept.
-keep class com.offlinedictionary.pro.MainActivity {
   public void onInit(int);
}

# --- Keep Activities, Services, etc. ---
# Although often covered by default rules, explicit keeps are safe.
-keep public class com.offlinedictionary.pro.MainActivity
-keep public class com.offlinedictionary.pro.FavoritesActivity

# Keep your DatabaseHelper
-keep class com.offlinedictionary.pro.DatabaseHelper { *; }


#===============================================================================
# OTHER COMMON RULES (Good to have as general practice)
#===============================================================================

# Keep enums (values() and valueOf() methods are often used by reflection).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations (if you use any).
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep classes that are serialized (e.g. with java.io.Serializable).
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep custom views referenced in XML.
# If your views are in com.offlinedictionary.pro and used in XML:
-keep public class com.offlinedictionary.pro.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Prevent R8 from warning about classes from JavaSE not being found (common for Android)
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn java.lang.instrument.**
-dontwarn javax.annotation.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.sql.**
-dontwarn javax.xml.**
-dontwarn org.ietf.jgss.**
-dontwarn org.omg.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.w3c.dom.events.**

# For newer versions of AGP, R8 might be more strict about some Kotlin intrinsics
# or synthetic classes. These are general safety nets.
-keepclassmembers class kotlin.jvm.internal.DefaultConstructorMarker { *; }
-dontwarn kotlin.Unit # Or -keep class kotlin.Unit { *; }

# If you use Jetpack Compose (you are not currently, but for future reference)
# -keepclass 京*پردازنده* { *; }
# -keepclass 京*ترکیب* { *; }
# -keepclass androidx.compose.runtime.Composable

# It is CRITICAL that the problematic line (previously around line 49) containing
# any non-ASCII characters or syntax errors like the Korean character `묽` is
# completely removed or corrected to a valid ProGuard rule.
# The section "KEEP KOTLIN SPECIFIC METADATA AND REFLECTION" above is the revised, clean version.
