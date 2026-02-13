package TestIndex;

import java.lang.reflect.Field;

public final class TestReflectionUtils {
    private TestReflectionUtils() {}

    public static void resetStaticFieldIfPresent(Class<?> clazz, String fieldName) {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(null, null);
        } catch (NoSuchFieldException ignored) {
            // ok: il campo potrebbe non esistere più
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot reset " + clazz.getName() + "." + fieldName + ": " + e.getMessage(), e);
        }
    }
}
