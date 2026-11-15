# No reflection-based serialization is used (JSON is parsed with org.json),
# so the default optimize rules are enough.

# The hidden WebView calls WasabiBridge.done() from JavaScript (see RaWeb.kt).
# Nothing in Kotlin calls it, so without these rules R8 strips it and every
# Want to Play request would time out.
-keepattributes JavascriptInterface,*Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WorkManager (notifications, widget) creates its Room database and the
# background worker by reflection. Without these, R8 strips the no-argument
# WorkDatabase_Impl constructor and the app crashes at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
