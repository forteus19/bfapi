package dev.vuis.bfapi.util;

import java.lang.reflect.Field;

public final class ReflectionUtil {
	private ReflectionUtil() {
	}

	@SuppressWarnings("unchecked")
	public static <T> T getField(Object obj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
		Class<?> clazz = obj.getClass();
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (T) field.get(obj);
	}
}
