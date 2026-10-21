package team.terrafirmagreg.konf.internal;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.io.ConfigParser;
import com.electronwill.nightconfig.core.io.ConfigWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.val;

/** Delegating format whose writer separates entries with a blank line (before comment blocks and table headers). */
public record SpacedFormat<C extends CommentedConfig>(ConfigFormat<C> delegate) implements ConfigFormat<C> {

    @Override
    public ConfigWriter createWriter() {
        val writer = delegate.createWriter();
        return (config, out) -> writer.write(config, new SpacingWriter(out));
    }

    @Override
    public ConfigParser<C> createParser() {
        return delegate.createParser();
    }

    @Override
    public C createConfig() {
        return delegate.createConfig();
    }

    @Override
    public C createConcurrentConfig() {
        return delegate.createConcurrentConfig();
    }

    @Override
    public C createConfig(Supplier<Map<String, Object>> mapCreator) {
        return delegate.createConfig(mapCreator);
    }

    @Override
    public boolean supportsComments() {
        return delegate.supportsComments();
    }

    @Override
    public boolean supportsType(Class<?> type) {
        return delegate.supportsType(type);
    }

    @RequiredArgsConstructor
    private static final class SpacingWriter extends Writer {
        private final Writer out;
        private final StringBuilder line = new StringBuilder();
        private boolean wrote;
        private boolean prevComment;
        private boolean prevBlank = true;

        @Override
        public void write(char[] buf, int off, int len) throws IOException {
            for (var i = off; i < off + len; i++) {
                val c = buf[i];
                if (c == '\n') {
                    emit();
                } else if (c != '\r') {
                    line.append(c);
                }
            }
        }

        private void emit() throws IOException {
            val s = line.toString();
            line.setLength(0);
            if (s.isBlank()) {
                prevComment = false;
                prevBlank = true;
                out.write('\n');
                return;
            }
            val t = s.stripLeading();
            if (wrote && !prevComment && !prevBlank && separates(t)) {
                out.write('\n');
            }
            wrote = true;
            prevBlank = false;
            prevComment = t.startsWith("#") || t.startsWith("//");
            out.write(s);
            out.write('\n');
        }

        private static boolean separates(String t) {
            return t.startsWith("#") || t.startsWith("//") || t.startsWith("[");
        }

        @Override
        public void flush() throws IOException {
            pending();
            out.flush();
        }

        @Override
        public void close() throws IOException {
            pending();
            out.close();
        }

        private void pending() throws IOException {
            if (!line.isEmpty()) {
                emit();
            }
        }
    }
}
