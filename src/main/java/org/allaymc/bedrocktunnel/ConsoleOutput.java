package org.allaymc.bedrocktunnel;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ConsoleOutput {
    private static final int MAX_BUFFER_CHARS = 200_000;
    private static final Object LOCK = new Object();
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final StringBuilder BUFFER = new StringBuilder();

    private static boolean installed;

    private ConsoleOutput() {
    }

    public static void install() {
        synchronized (LOCK) {
            if (installed) {
                return;
            }

            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            System.setOut(new PrintStream(new MirroringOutputStream(originalOut), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new MirroringOutputStream(originalErr), true, StandardCharsets.UTF_8));
            installed = true;
        }
    }

    public static String snapshot() {
        synchronized (LOCK) {
            return BUFFER.toString();
        }
    }

    public static Runnable addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
        String snapshot = snapshot();
        if (!snapshot.isEmpty()) {
            listener.accept(snapshot);
        }
        return () -> LISTENERS.remove(listener);
    }

    private static void publish(String text) {
        if (text.isEmpty()) {
            return;
        }

        synchronized (LOCK) {
            BUFFER.append(text);
            int excess = BUFFER.length() - MAX_BUFFER_CHARS;
            if (excess > 0) {
                BUFFER.delete(0, excess);
            }
        }

        for (Consumer<String> listener : LISTENERS) {
            listener.accept(text);
        }
    }

    private static final class MirroringOutputStream extends OutputStream {
        private final PrintStream delegate;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        private MirroringOutputStream(PrintStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void write(int value) {
            delegate.write(value);
            pending.write(value);
            if (value == '\n' || value == '\r') {
                drain();
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
            pending.write(bytes, offset, length);
            for (int index = offset; index < offset + length; index++) {
                int value = bytes[index];
                if (value == '\n' || value == '\r') {
                    drain();
                    break;
                }
            }
        }

        @Override
        public synchronized void flush() {
            delegate.flush();
            drain();
        }

        private void drain() {
            if (pending.size() == 0) {
                return;
            }
            publish(pending.toString(StandardCharsets.UTF_8));
            pending.reset();
        }
    }
}
