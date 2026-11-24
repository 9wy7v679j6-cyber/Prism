# Prism Docs R8 / ProGuard keep rules

# Preserve readable crash stack traces (lines + obfuscated source file name)
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Type-safe Navigation Compose @Serializable route classes & companions
-keep,includedescriptorclasses class com.prism.docs.navigation.** { *; }
-keepclassmembers class com.prism.docs.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer {
    kotlinx.serialization.KSerializer[] childSerializers();
}
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class ** { *; }

# WorkManager + @HiltWorker background workers (reflection-instantiated by HiltWorkerFactory)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.prism.docs.core.data.indexer.DocumentIndexWorker { *; }
-keep class com.prism.docs.core.data.storage.FileTransferWorker { *; }

# Room generated database implementation & DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.prism.docs.core.data.db.PrismDatabase_Impl { *; }

# Keep Parcelables / runtime enum values accessed by serialization or SavedStateHandle
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
