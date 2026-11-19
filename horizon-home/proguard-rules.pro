# R8 rules for Horizon Home.
# Goal: drop library code nothing uses (most of Guava and the AndroidX support libraries), while keeping
# everything that could matter for playback on ANY IPTV provider.

# The whole video player (ExoPlayer / androidx.media3), untouched: every stream format (HLS .m3u8, MPEG-TS,
# MP4, MKV, ...) and every part it loads by name at run time stays exactly as it is now.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Horizon Home's own code (small): screens, the remote-button helper, everything referenced from the manifest.
-keep class home.horizon.tv.** { *; }

# Keep names readable (crash logs stay useful); only remove unused code, don't rename or rewrite it.
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Library references to classes that only exist on newer Android versions or at build time (annotations):
# expected, harmless.
-dontwarn **
-ignorewarnings
