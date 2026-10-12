# Ngram's own classes are reached from the Telegram code through hooks and by name.
-keep class org.ngram.messenger.** { *; }

# Plugins reach Telegram's classes by name from Python, the way exteraGram plugins do, so the
# names and members of the whole app stay as they are in the sources.
-keep class org.telegram.** { *; }
-keep class org.ngram.** { *; }
-keep class com.chaquo.python.** { *; }
