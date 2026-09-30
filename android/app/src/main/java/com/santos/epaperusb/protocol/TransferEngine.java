package com.santos.epaperusb.protocol;

import com.santos.epaperusb.image.PreparedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** No Android dependency, no retries, no synthesized device responses. */
public final class TransferEngine {
    public static final class Failure extends IOException {
        public enum Code { BUSY, ACK_TIMEOUT, ACK_LATE, INVALID_READ, CANCELLED }
        public final Code code;
        public Failure(Code code, String message) { super(message); this.code = code; }
        public Failure(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    }
    public interface Transport extends AutoCloseable {
        void write(byte[] bytes, int timeoutMs) throws IOException;
        int read(byte[] bytes, int timeoutMs) throws IOException;
        void close() throws IOException;
    }
    public interface Clock { long millis(); void sleep(long ms) throws InterruptedException; }
    public interface Observer { void log(String message); void progress(int blocks); }
    public static final int ACK_TIMEOUT_MS = 10000, WRITE_TIMEOUT_MS = 5000;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Clock clock;
    public TransferEngine() {
        this(new Clock() {
            public long millis() { return System.nanoTime() / 1_000_000; }
            public void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }
        });
    }
    public TransferEngine(Clock clock) { this.clock = clock; }
    public void send(Transport port, PreparedImage image, BooleanSupplier cancelled, Observer observer) throws IOException {
        if (!running.compareAndSet(false, true)) throw new Failure(Failure.Code.BUSY, "A transfer is already in progress");
        long start = clock.millis();
        try {
            check(cancelled);
            byte[] header = image.header();
            observer.log("Start: payload=96000 SHA-256=" + image.sha256());
            observer.log("TX header: " + hex(header, header.length));
            port.write(header, WRITE_TIMEOUT_MS);
            AckParser parser = new AckParser();
            byte[] read = new byte[256], ack = null;
            long deadline = clock.millis() + ACK_TIMEOUT_MS;
            while (ack == null) {
                check(cancelled);
                long remaining = deadline - clock.millis();
                if (remaining <= 0) throw new Failure(Failure.Code.ACK_TIMEOUT, "No valid F1 ACK within 10 s; no pixels sent");
                int count = port.read(read, (int)Math.min(250, remaining));
                if (count < 0 || count > read.length) throw new Failure(Failure.Code.INVALID_READ, "Invalid USB read");
                if (count > 0) {
                    observer.log("RX: " + hex(read, count));
                    ack = parser.accept(read, count);
                    if (clock.millis() > deadline) throw new Failure(Failure.Code.ACK_LATE, "ACK received after deadline; no pixels sent");
                }
            }
            observer.log("Valid F1 ACK: " + hex(ack, ack.length));
            int block = 0;
            for (byte[] packet : EpaperProtocol.packets(image.payload())) {
                check(cancelled);
                long writeStart = clock.millis();
                port.write(packet, WRITE_TIMEOUT_MS);
                check(cancelled);
                observer.log("TX block " + (++block) + "/24: " + (packet.length - 2) + " bytes + 0D 0A, " + (clock.millis() - writeStart) + " ms");
                observer.progress(block);
                if (packet.length == EpaperProtocol.BLOCK_SIZE + 2) clock.sleep(100);
            }
            check(cancelled);
            observer.log("Transfer finished: 96060 total bytes; " + (clock.millis() - start) + " ms. Physical display update not confirmed.");
        } catch (IOException | RuntimeException e) {
            observer.log("Transfer interrupted: " + e.getMessage());
            try { port.close(); } catch (IOException closeError) { observer.log("Close failed: " + closeError.getMessage()); }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try { port.close(); } catch (IOException ignored) { }
            throw new Failure(Failure.Code.CANCELLED, "Transfer cancelled; restart the display", e);
        } finally { running.set(false); }
    }
    private static void check(BooleanSupplier cancelled) throws IOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new Failure(Failure.Code.CANCELLED, "Transfer cancelled");
    }
    public static String hex(byte[] bytes, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i != 0) out.append(' ');
            out.append(String.format(java.util.Locale.ROOT, "%02X", bytes[i] & 255));
        }
        return out.toString();
    }
}
