package dev.vuis.bfapi.util;

import java.lang.reflect.Field;
import lombok.SneakyThrows;

public final class ReflectionUtil {
	private ReflectionUtil() {
	}

	@SneakyThrows
	@SuppressWarnings("unchecked")
	public static <T> T getField(Object obj, String fieldName) {
		Class<?> clazz = obj.getClass();
		Field field = clazz.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (T) field.get(obj);
	}
}
