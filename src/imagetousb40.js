// Recovered from the user's ImageToUSB v4.0.exe; see docs/imagetousb40-protocol.md.
// Its long-image sender explicitly handles 800x480. Do not extrapolate to other sizes.
const COLORS = { mono: 2, tri: 3, four: 4 };
const PLANE_SIZE = 48000;

export function validateImageToUSB40(width, height, mode) {
  if (width !== 800 || height !== 480) {
    throw new Error('ImageToUSB v4.0 upload supports 800 × 480. Select that canvas size, or choose export only.');
  }
  if (!Object.hasOwn(COLORS, mode)) throw new Error('ImageToUSB v4.0 supports 2, 3, or 4 colors.');
  return mode === 'mono' ? PLANE_SIZE : PLANE_SIZE * 2;
}

export function packImageToUSB40(pixels, width, height, mode) {
  const bytes = new Uint8Array(validateImageToUSB40(width, height, mode));
  if (!(pixels instanceof Uint8Array) || pixels.length !== width * height || pixels.some(p => p >= COLORS[mode])) {
    throw new Error('Invalid ImageToUSB palette pixels.');
  }
  for (let i = 0; i < pixels.length; i++) {
    const pixel = pixels[i];
    if (mode === 'four') {
      // The app palette is black, white, red, yellow; the EXE sends 00, 01, 11, 10.
      const value = [0, 1, 3, 2][pixel];
      bytes[i >> 2] |= value << (6 - (i % 4) * 2);
    } else {
      const mask = 1 << (7 - i % 8);
      // GetPictureData: only black is 0. Red is 1 in the BW plane.
      if (pixel !== 0) bytes[i >> 3] |= mask;
      if (mode === 'tri' && pixel !== 2) bytes[PLANE_SIZE + (i >> 3)] |= mask;
    }
  }
  return bytes;
}

export function imageToUSB40Handshake(bytes, { width, height, mode }) {
  const length = validateImageToUSB40(width, height, mode);
  if (!(bytes instanceof Uint8Array) || bytes.length !== length) throw new Error('ImageToUSB image size mismatch.');
  let hasRed = false;
  if (mode === 'tri') hasRed = bytes.subarray(PLANE_SIZE).some(b => b !== 0xff);
  if (mode === 'four') hasRed = bytes.some(b => [0, 2, 4, 6].some(shift => ((b >> shift) & 3) === 3));
  const packet = Uint8Array.of(0xaa, 0x55, 0xe1, 0xbb, 0x80, COLORS[mode], 0xc4, Number(hasRed), 0, 0xff, 0x0d, 0x0a);
  packet[8] = packet.subarray(0, 8).reduce((sum, byte) => sum + byte, 0) & 255;
  return packet;
}

export function validImageToUSB40Reply(frame) {
  return frame.length === 10 && frame[0] === 0xa0 && frame[1] === 0x50 && frame[9] === 0xff &&
    (frame.subarray(0, 8).reduce((sum, byte) => sum + byte, 0) & 255) === frame[8];
}

export const hexBytes = bytes => Array.from(bytes, b => b.toString(16).padStart(2, '0')).join(' ');
export const serialText = bytes => Array.from(bytes, b => b === 10 ? '\n' : b === 13 ? '\r' : b >= 32 && b <= 126 ? String.fromCharCode(b) : '.').join('');

// A single reader owns the input stream for the whole connection, including image
// writes and refresh. Firmware debug output must not collect until the next ACK wait.
export class ImageToUSB40Input {
  constructor(port, { onReceive = () => {}, onError = () => {} } = {}) {
    if (!port.readable) throw new Error('Serial input is unavailable. Reconnect the display.');
    this.reader = port.readable.getReader();
    this.pending = null; this.failure = null; this.stopping = false;
    this.onReceive = onReceive; this.onError = onError;
    this.task = this.read();
  }
  throwIfFailed() { if (this.failure) throw this.failure; }
  async read() {
    try {
      while (!this.stopping) {
        const { value, done } = await this.reader.read();
        if (this.stopping) break;
        if (done) throw new Error('Serial input closed. Reset the display and reconnect.');
        this.pending?.feed(value);
        this.onReceive(value);
      }
    } catch (error) {
      if (!this.stopping) {
        this.failure = error;
        this.pending?.finish(error);
        this.onError(error);
      }
    } finally { this.reader.releaseLock(); }
  }
  waitForReply({ signal, timeoutMs = 10000 } = {}) {
    signal?.throwIfAborted();
    this.throwIfFailed();
    if (this.stopping || this.pending) throw new Error('Serial input cannot start another handshake.');
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new Error('Invalid handshake timeout.');
    return new Promise((resolve, reject) => {
      let buffer = [], received = [], receivedCount = 0;
      const finish = (error, frame) => {
        clearTimeout(timer);
        signal?.removeEventListener('abort', onAbort);
        this.pending = null;
        if (error) reject(error); else resolve(frame);
      };
      const onAbort = () => finish(signal.reason ?? new DOMException('Transfer stopped.', 'AbortError'));
      const timer = setTimeout(() => {
        const detail = receivedCount
          ? `Received ${receivedCount} serial bytes, but no valid F1 acknowledgment. RX tail: ${hexBytes(received)}. Text: ${JSON.stringify(serialText(received))}. The display may still be busy or use different firmware.`
          : 'No serial response received. Check the selected port, cable and display power.';
        finish(new Error(`No valid ImageToUSB v4.0 handshake reply within ${timeoutMs / 1000}s. ${detail} No image pixels were sent.`));
      }, timeoutMs);
      this.pending = { finish, feed: bytes => {
        for (const byte of bytes) {
          receivedCount++;
          received.push(byte);
          if (received.length > 128) received.shift();
          buffer.push(byte);
          if (buffer.length < 10) continue;
          const frame = Uint8Array.from(buffer);
          if (validImageToUSB40Reply(frame)) {
            buffer = [];
            // Only F1 starts pixels in the EXE's active receive callback.
            if (frame[2] === 0xf1) { finish(null, frame); return; }
          } else buffer.shift();
        }
      } };
      signal?.addEventListener('abort', onAbort, { once: true });
    });
  }
  async close() {
    this.stopping = true;
    this.pending?.finish(new Error('Serial session closed.'));
    try { await this.reader.cancel(); } catch {}
    await this.task;
  }
}

export function pauseTransfer(ms, signal) {
  signal?.throwIfAborted();
  return new Promise((resolve, reject) => {
    const finish = () => { signal?.removeEventListener('abort', abort); resolve(); };
    const timer = setTimeout(finish, ms);
    const abort = () => { clearTimeout(timer); signal.removeEventListener('abort', abort); reject(signal.reason); };
    signal?.addEventListener('abort', abort, { once: true });
  });
}
