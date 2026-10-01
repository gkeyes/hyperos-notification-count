# Test-only Error Prone annotations use this Java compiler enum with CLASS
# retention. Android has no compiler model API; the annotation is never executed.
# https://github.com/google/error-prone/blob/master/annotations/src/main/java/com/google/errorprone/annotations/IncompatibleModifiers.java
-dontwarn javax.lang.model.element.Modifier
