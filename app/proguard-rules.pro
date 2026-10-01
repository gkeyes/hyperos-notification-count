# The framework loads java_init.list by name and invokes API 102 callbacks outside
# the app. Keep the small module package, including Hooker implementations and its
# settings/config contract. R8 can shrink, optimize and rename UI dependencies.
-keep class dev.hyperos.notificationcount.** { *; }
