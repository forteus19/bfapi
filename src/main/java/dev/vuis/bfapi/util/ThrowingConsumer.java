package dev.vuis.bfapi.util;

@FunctionalInterface
public interface ThrowingConsumer<T> {
	void accept(T t) throws Exception;
}
