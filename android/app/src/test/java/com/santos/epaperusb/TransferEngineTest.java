package com.santos.epaperusb;

import com.santos.epaperusb.protocol.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class TransferEngineTest {
    static class FakeClock implements TransferEngine.Clock {
        long now; int sleeps;
        public long millis() { return now; }
        public void sleep(long ms) { assertEquals(100, ms); sleeps++; now += ms; }
    }
    static class Port implements TransferEngine.Transport {
        final FakeClock clock; List<byte[]> writes = new ArrayList<>(); ArrayDeque<byte[]> reads = new ArrayDeque<>();
        int failWrite = -1; boolean failRead, closed; Runnable onWrite = () -> {};
        Port(FakeClock clock) { this.clock = clock; }
        public void write(byte[] bytes, int timeout) throws IOException {
            assertTrue(timeout >= 4000);
            if (writes.size() == failWrite) throw new IOException("Disconnected during write");
            writes.add(bytes.clone()); onWrite.run();
        }
        public int read(byte[] bytes, int timeout) throws IOException {
            if (failRead) throw new IOException("Disconnected during read");
            clock.now += timeout;
            byte[] data = reads.poll(); if (data == null) return 0;
            System.arraycopy(data, 0, bytes, 0, data.length); return data.length;
        }
        public void close() { closed = true; }
    }
    static class Observer implements TransferEngine.Observer {
        int blocks; public void log(String message) {} public void progress(int n) { blocks = n; }
    }
    @Test public void fragmentedAckThenExactPacketsAndDelays() throws Exception {
        FakeClock clock = new FakeClock(); Port port = new Port(clock); Observer observer = new Observer();
        byte[] ack = ProtocolTest.ack();
        port.reads.add(new byte[]{(byte)0xa0}); port.reads.add(Arrays.copyOfRange(ack, 1, 5)); port.reads.add(Arrays.copyOfRange(ack, 5, 10));
        new TransferEngine(clock).send(port, ProtocolTest.chart(), () -> false, observer);
        assertEquals(25, port.writes.size()); assertEquals(24, observer.blocks); assertEquals(23, clock.sleeps); assertFalse(port.closed);
        assertArrayEquals(EpaperProtocol.handshake(true), port.writes.get(0));
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        for (int i = 1; i < 25; i++) {
            byte[] p = port.writes.get(i); assertEquals(i == 24 ? 1794 : 4098, p.length);
            assertEquals(13, p[p.length-2]); assertEquals(10, p[p.length-1]); payload.write(p, 0, p.length-2);
        }
        assertArrayEquals(ProtocolTest.chart().payload(), payload.toByteArray());
    }
    @Test public void timeoutAndInvalidAckNeverSendPixels() {
        for (boolean corrupt : new boolean[]{false, true}) {
            FakeClock clock = new FakeClock(); Port port = new Port(clock);
            if (corrupt) { byte[] ack = ProtocolTest.ack(); ack[8]++; port.reads.add(ack); }
            assertThrows(IOException.class, () -> new TransferEngine(clock).send(port, ProtocolTest.chart(), () -> false, new Observer()));
            assertEquals(1, port.writes.size()); assertTrue(port.closed); assertEquals(10000, clock.now);
        }
    }
    @Test public void incompleteAckAtDeadlineCannotStartPayload() {
        FakeClock clock = new FakeClock(); Port port = new Port(clock);
        port.reads.add(Arrays.copyOf(ProtocolTest.ack(), 9));
        assertThrows(IOException.class, () -> new TransferEngine(clock).send(port, ProtocolTest.chart(), () -> false, new Observer()));
        assertEquals(1, port.writes.size()); assertTrue(port.closed);
    }
    @Test public void disconnectDuringAckClosesWithoutPayload() {
        FakeClock clock = new FakeClock(); Port port = new Port(clock); port.failRead = true;
        assertThrows(IOException.class, () -> new TransferEngine(clock).send(port, ProtocolTest.chart(), () -> false, new Observer()));
        assertEquals(1, port.writes.size()); assertTrue(port.closed);
    }
    @Test public void partialWriteIsNeverRetried() {
        FakeClock clock = new FakeClock(); Port port = new Port(clock); port.failWrite = 4; port.reads.add(ProtocolTest.ack());
        assertThrows(IOException.class, () -> new TransferEngine(clock).send(port, ProtocolTest.chart(), () -> false, new Observer()));
        assertEquals(4, port.writes.size()); assertTrue(port.closed);
    }
    @Test public void cancelStopsBeforeNextBlockAndCloses() {
        FakeClock clock = new FakeClock(); Port port = new Port(clock); port.reads.add(ProtocolTest.ack());
        AtomicBoolean cancel = new AtomicBoolean(); port.onWrite = () -> { if (port.writes.size() == 3) cancel.set(true); };
        assertThrows(IOException.class, () -> new TransferEngine(clock).send(port, ProtocolTest.chart(), cancel::get, new Observer()));
        assertEquals(3, port.writes.size()); assertTrue(port.closed);
    }
    @Test public void concurrentSendRejectedWithoutTouchingSecondPort() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FakeClock clock = new FakeClock(); Port first = new Port(clock); first.reads.add(ProtocolTest.ack());
        first.onWrite = () -> { if (first.writes.size() == 1) { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new RuntimeException(e); } } };
        TransferEngine engine = new TransferEngine(clock); AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { engine.send(first, ProtocolTest.chart(), () -> false, new Observer()); } catch (Throwable e) { failure.set(e); } });
        worker.start(); assertTrue(entered.await(5, TimeUnit.SECONDS));
        Port second = new Port(new FakeClock());
        try { assertThrows(IOException.class, () -> engine.send(second, ProtocolTest.chart(), () -> false, new Observer())); }
        finally { release.countDown(); worker.join(5000); }
        assertNull(failure.get()); assertTrue(second.writes.isEmpty()); assertFalse(second.closed);
    }
}
