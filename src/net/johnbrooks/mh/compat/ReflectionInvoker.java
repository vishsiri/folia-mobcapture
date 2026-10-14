package net.johnbrooks.mh.compat;

import java.lang.reflect.Method;

public final class ReflectionInvoker {
    private ReflectionInvoker() {
    }

    public static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class[0], new Object[0]);
    }

    public static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object invokeStatic(Class<?> target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = target.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            return invokeStatic(Class.forName(className), methodName, parameterTypes, args);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
