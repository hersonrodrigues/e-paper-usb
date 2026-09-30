import { imageToUSB40Handshake, ImageToUSB40Input, pauseTransfer, hexBytes } from './imagetousb40.js';

// The manual estimates 20 seconds. This host-side pause adds a margin, but is
// not a device acknowledgment or proof of a completed screen refresh.
export const DISPLAY_SETTLE_MS = 25000;

export class SerialConnection {
  constructor(serial = globalThis.navigator?.serial, { now = () => performance.now() } = {}) {
    this.serial = serial; this.port = null; this.busy = false; this.tainted = false; this.protocol = null;
    this.input = null; this.now = now; this.refreshUntil = 0;
  }
  get refreshRemainingMs() { return Math.max(0, this.refreshUntil - this.now()); }
  async connect({ protocol = 'good-display-usb', onReceive, onReadError = () => {} } = {}) {
    if (!this.serial) throw new Error('Serial requires desktop Chrome or Edge at localhost or HTTPS.');
    if (this.busy || this.port) throw new Error('Disconnect the current port first.');
    if (!['good-display-usb', 'imagetousb40'].includes(protocol)) throw new Error('Select an upload protocol first.');
    this.busy = true;
    let port, opened = false;
    try {
      port = await this.serial.requestPort();
      await port.open({ baudRate: 115200, dataBits: 8, stopBits: 1, parity: 'none', flowControl: 'none' });
      opened = true;
      if (protocol === 'imagetousb40') await port.setSignals({ requestToSend: true, dataTerminalReady: false });
      this.port = port; this.tainted = false; this.protocol = protocol;
      if (protocol === 'imagetousb40') this.input = new ImageToUSB40Input(port, {
        onReceive, onError: error => { this.tainted = true; onReadError(error); },
      });
      return port.getInfo();
    } catch (error) {
      await this.input?.close(); this.input = null;
      if (opened) { try { await port.close(); } catch {} }
      this.port = null; this.protocol = null;
      throw error;
    } finally { this.busy = false; }
  }
  async disconnect() {
    if (this.busy) throw new Error('Wait for the transfer to stop before disconnecting.');
    const port = this.port;
    this.port = null;
    this.protocol = null;
    this.busy = true;
    try {
      await this.input?.close(); this.input = null;
      if (port) await port.close();
    } finally { this.busy = false; }
  }
  async send(bytes, { signal, onProgress = () => {}, onStatus = () => {}, width, height, mode, handshakeTimeoutMs = 10000 } = {}) {
    if (!this.port?.writable) throw new Error('Connect a serial port first.');
    if (this.busy) throw new Error('A transfer is already running.');
    if (this.tainted) throw new Error('The last transfer was interrupted. Reset the board and reconnect first.');
    if (this.refreshRemainingMs > 0) throw new Error(`Wait ${Math.ceil(this.refreshRemainingMs / 1000)} seconds before sending again, then check that the display has finished refreshing.`);
    if (!(bytes instanceof Uint8Array) || !bytes.length) throw new Error('No image bytes to send.');
    signal?.throwIfAborted();
    if (!Number.isFinite(handshakeTimeoutMs) || handshakeTimeoutMs <= 0) throw new Error('Invalid handshake timeout.');
    // Snapshot before the first await so edits cannot alter an in-flight image.
    bytes = bytes.slice();
    const packet = this.protocol === 'imagetousb40' ? imageToUSB40Handshake(bytes, { width, height, mode }) : null;
    const port = this.port;
    if (packet && !port.readable) throw new Error('Serial input is unavailable. Reconnect the display.');
    this.busy = true;
    let writer;
    try {
      writer = port.writable.getWriter();
      if (packet) {
        onStatus({ phase: 'handshake', message: `Handshake TX: ${hexBytes(packet)}` });
        signal?.throwIfAborted();
        // Arm before writing so even an immediate device reply is captured.
        const pending = this.input.waitForReply({ signal, timeoutMs: handshakeTimeoutMs });
        pending.catch(() => {}); // A write can fail before we await its reply.
        await writer.write(packet);
        const reply = await pending;
        onStatus({ phase: 'accepted', message: `Handshake accepted: ${hexBytes(reply)}` });
      }
      const blockSize = packet ? 4096 : 512;
      for (let offset=0;offset<bytes.length;offset+=blockSize) {
        signal?.throwIfAborted();
        this.input?.throwIfFailed();
        const block = bytes.subarray(offset, offset + blockSize);
        await writer.write(block);
        if (packet) {
          signal?.throwIfAborted();
          await writer.write(Uint8Array.of(0x0d, 0x0a));
        }
        onProgress(Math.min(offset+blockSize,bytes.length), bytes.length);
        if (packet && block.length === blockSize) await pauseTransfer(100, signal);
      }
      signal?.throwIfAborted();
      this.input?.throwIfFailed();
      if (packet) this.refreshUntil = this.now() + DISPLAY_SETTLE_MS;
      // Neither reference sender verifies a final physical refresh.
      return { bytesSent: bytes.length, handshakeAccepted: Boolean(packet), refreshConfirmed: false };
    } catch (error) { this.tainted = true; await this.input?.close(); throw error; }
    finally { writer?.releaseLock(); this.busy = false; }
  }
}
