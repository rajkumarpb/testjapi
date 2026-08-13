package javapi.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import javapi.json.JsonOutput;

/**
 * Streams JSON tokens straight into a pooled {@link ByteBuf}, skipping the
 * intermediate {@code String} and the second encode pass. Structural characters
 * are single ASCII bytes; string content is written in raw runs so surrogate
 * pairs encode correctly.
 */
final class ByteBufJsonOutput implements JsonOutput {

    private final ByteBuf buf;

    ByteBufJsonOutput(ByteBuf buf) {
        this.buf = buf;
    }

    @Override
    public void append(String value) {
        ByteBufUtil.writeUtf8(buf, value);
    }

    @Override
    public void append(String value, int start, int end) {
        ByteBufUtil.writeUtf8(buf, value, start, end);
    }

    @Override
    public void append(char c) {
        if (c < 0x80) {
            buf.writeByte((byte) c);
        } else {
            append(String.valueOf(c));
        }
    }
}
