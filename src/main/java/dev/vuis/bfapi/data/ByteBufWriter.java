package dev.vuis.bfapi.data;

import io.netty.buffer.ByteBuf;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class ByteBufWriter extends Writer {
	private final ByteBuf buf;
	private final Charset charset;

	@Override
	public void write(char @NotNull [] cbuf, int off, int len) {
		Objects.requireNonNull(cbuf);
		if (off < 0 || len < 0 || off + len > cbuf.length) {
			throw new IndexOutOfBoundsException();
		}
		buf.writeCharSequence(CharBuffer.wrap(cbuf, off, len), charset);
	}

	@Override
	public void write(int c) {
		char[] a = {(char) c};
		buf.writeCharSequence(CharBuffer.wrap(a), charset);
	}

	@Override
	public void write(@NotNull String str) {
		Objects.requireNonNull(str);
		buf.writeCharSequence(str, charset);
	}

	@Override
	public void write(@NotNull String str, int off, int len) {
		Objects.requireNonNull(str);
		buf.writeCharSequence(str.subSequence(off, off + len), charset);
	}

	@Override
	public Writer append(@NotNull CharSequence csq) {
		Objects.requireNonNull(csq);
		buf.writeCharSequence(csq, charset);
		return this;
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}
}
