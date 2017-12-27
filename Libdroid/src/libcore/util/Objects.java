package libcore.util;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class Objects
{
    public static boolean equal(Object a, Object b)
    {
        return (a == b) || ((a != null) && (a.equals(b)));
    }

    public static int hashCode(Object o)
    {
        return o == null ? 0 : o.hashCode();
    }

    public static String toString(Object o)
    {
        Class<?> c = o.getClass();
        StringBuilder sb = new StringBuilder();
        sb.append(c.getSimpleName()).append('[');
        int i = 0;
        for (Field f : c.getDeclaredFields()) {
            if ((f.getModifiers() & 0x88) == 0)
            {
                f.setAccessible(true);
                try
                {
                    Object value = f.get(o);
                    if (i++ > 0) {
                        sb.append(',');
                    }
                    sb.append(f.getName());
                    sb.append('=');
                    if (value.getClass().isArray())
                    {
                        if (value.getClass() == boolean[].class) {
                            sb.append(Arrays.toString((boolean[])value));
                        } else if (value.getClass() == byte[].class) {
                            sb.append(Arrays.toString((byte[])value));
                        } else if (value.getClass() == char[].class) {
                            sb.append(Arrays.toString((char[])value));
                        } else if (value.getClass() == double[].class) {
                            sb.append(Arrays.toString((double[])value));
                        } else if (value.getClass() == float[].class) {
                            sb.append(Arrays.toString((float[])value));
                        } else if (value.getClass() == int[].class) {
                            sb.append(Arrays.toString((int[])value));
                        } else if (value.getClass() == long[].class) {
                            sb.append(Arrays.toString((long[])value));
                        } else if (value.getClass() == short[].class) {
                            sb.append(Arrays.toString((short[])value));
                        } else {
                            sb.append(Arrays.toString((Object[])value));
                        }
                    }
                    else if (value.getClass() == Character.class) {
                        sb.append('\'').append(value).append('\'');
                    } else if (value.getClass() == String.class) {
                        sb.append('"').append(value).append('"');
                    } else {
                        sb.append(value);
                    }
                }
                catch (IllegalAccessException unexpected)
                {
                    throw new AssertionError(unexpected);
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
