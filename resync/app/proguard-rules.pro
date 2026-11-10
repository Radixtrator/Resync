# App-specific R8 rules. Libraries (kotlinx.serialization, OkHttp, Coil, Media3) bring their own.

# Keep line numbers in stack traces (crash reports), with every file named "SourceFile". Class and method names are
# still obfuscated: retrace them with the release's mapping.txt (attached to each GitHub release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
