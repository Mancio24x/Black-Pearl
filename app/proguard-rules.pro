# Black Pearl R8 rules.
# Tesseract4Android ships no consumer rules; its native code looks up Java fields/methods by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
