# R8 rules for the release build.
#
# Most libraries here ship their own rules (Retrofit, OkHttp, Hilt, Coil and
# ML Kit all do), so this file only covers what is specific to this app: the
# serializable models that make up the API contract, and the Retrofit
# interface whose generic return types R8's full mode would otherwise erase.

# ---------------------------------------------------------------- the contract
#
# Every model is reached through a generated serializer rather than by name, so
# the generated `$serializer` and the `Companion` that hands it out have to
# survive. Without these a release build parses nothing and every screen shows
# an error that does not reproduce in debug.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.budgettracker.data.** {
    *** Companion;
}

-keepclasseswithmembers class com.budgettracker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class com.budgettracker.data.**
-keep, allowobfuscation, allowoptimization class <1>$$serializer {
    *** INSTANCE;
    *** serializer(...);
}

# Enum values are matched by their @SerialName, which needs the enum entries.
-keepclassmembers enum com.budgettracker.data.** {
    **[] $VALUES;
    public *;
}

# ------------------------------------------------------------------- Retrofit
#
# The service interface's suspend functions carry their response type in a
# generic signature; losing it leaves Retrofit unable to pick a converter.
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.budgettracker.data.remote.BudgetApi { *; }

# Same again for the release manifest and APK download. A release build that
# cannot read its own update manifest is a build that can never be replaced.
-keep,allowobfuscation interface com.budgettracker.data.remote.ReleaseApi { *; }

# --------------------------------------------------------------- Google sign-in
#
# The credential library reflects over the Google ID token type by name.
-keep class com.google.android.libraries.identity.googleid.** { *; }

# -------------------------------------------------------------------- niceties
#
# Keep line numbers so a release crash report points at real source lines, and
# hide the original file name, which says nothing useful once obfuscated.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
