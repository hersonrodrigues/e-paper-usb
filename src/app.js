import { quantize, packPixels, toCArray, validateDimensions } from './conversion.js';
import { SerialConnection } from './serial.js';
import { packImageToUSB40, hexBytes, serialText } from './imagetousb40.js';
const $ = id => document.getElementById(id);
const serial = new SerialConnection();
let source, converted, original, viewOriginal = false, loading = false, active = false, aborter, lostPort;
let loadId = 0, renderTimer, refreshTimer;
const messages = [];
function notice(text, error = false) { $('notice').textContent = text; $('notice').classList.toggle('error', error); }
function log(text) {
  messages.push(`${new Date().toLocaleTimeString()}  ${text}`);
  if (messages.length > 200) messages.shift();
  $('log').textContent = messages.slice(-50).join('\n'); $('log').scrollTop = $('log').scrollHeight;
  $('export-log').disabled = false;
}
function supported() { return Boolean(navigator.serial && window.isSecureContext); }
function refreshUI() {
  const connected = Boolean(serial.port);
  const refreshSeconds = Math.ceil(serial.refreshRemainingMs / 1000);
  const protocol = $('protocol').value;
  const compatible = protocol === 'imagetousb40' || (protocol === 'good-display-usb' && $('compatible').checked);
  $('connect').disabled = connected || active || !supported() || protocol === 'unknown';
  $('disconnect').disabled = !connected || active;
  $('send').disabled = !converted || !connected || !compatible || active || loading || serial.tainted || refreshSeconds > 0;
  $('send').textContent = refreshSeconds > 0 ? `Wait ${refreshSeconds}s` : 'Send image ↗';
  $('refresh-wait').hidden = !connected || refreshSeconds <= 0;
  $('refresh-wait').textContent = `Giving the display time to refresh: ${refreshSeconds}s before another upload. Keep USB powered and check the screen. This wait does not confirm the refresh.`;
  $('cancel').hidden = !active || !aborter;
  $('cancel').disabled = Boolean(aborter?.signal.aborted);
  $('image-controls').disabled = active;
  $('protocol').disabled = active || connected;
  $('compatible').disabled = active;
  $('export-c').disabled = !converted || loading || active;
  $('export-png').disabled = !converted || loading || active;
  $('connection-state').textContent = connected ? 'Port connected' : 'Not connected';
  $('connection-state').classList.toggle('connected', connected);
}
function watchRefresh() {
  clearInterval(refreshTimer);
  refreshUI();
  refreshTimer = setInterval(() => {
    refreshUI();
    if (serial.refreshRemainingMs <= 0) {
      clearInterval(refreshTimer);
      log('Refresh waiting period ended. Check the physical display before sending again.');
    }
  }, 250);
}
function settings() {
  const width = Number($('width').value), height = Number($('height').value);
  validateDimensions(width, height);
  return { width, height, mode: $('mode').value, protocol: $('protocol').value, dither: $('dither').checked, contrast: Number($('contrast').value)/100, threshold: Number($('threshold').value) };
}
function drawPreview() {
  if (!converted) return;
  const canvas = $('preview');
  canvas.width = converted.width; canvas.height = converted.height;
  canvas.getContext('2d').putImageData(viewOriginal ? original : new ImageData(converted.preview, converted.width, converted.height),0,0);
  canvas.hidden = false; $('empty').hidden = true;
  $('show-output').setAttribute('aria-pressed', String(!viewOriginal));
  $('show-original').setAttribute('aria-pressed', String(viewOriginal));
}
function render() {
  converted = null;
  $('contrast-value').value = `${$('contrast').value}%`; $('threshold-value').value = $('threshold').value;
  $('threshold').disabled = $('mode').value !== 'mono';
  if (!source) { refreshUI(); return; }
  try {
    const opts = settings();
    const canvas = document.createElement('canvas'); canvas.width = opts.width; canvas.height = opts.height;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = '#fff'; ctx.fillRect(0,0,canvas.width,canvas.height);
    const angle = Number($('rotation').value), quarter = angle % 180 !== 0;
    const sw = quarter ? source.height : source.width, sh = quarter ? source.width : source.height;
    const fit = $('fit').value;
    let dw = opts.width, dh = opts.height;
    if (fit !== 'stretch') {
      const scale = (fit === 'cover' ? Math.max : Math.min)(opts.width/sw, opts.height/sh);
      dw = sw*scale; dh = sh*scale;
    }
    ctx.translate(opts.width/2,opts.height/2); ctx.rotate(angle*Math.PI/180);
    ctx.imageSmoothingEnabled = true; ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(source, -(quarter ? dh : dw)/2, -(quarter ? dw : dh)/2, quarter ? dh : dw, quarter ? dw : dh);
    original = ctx.getImageData(0,0,opts.width,opts.height);
    const result = quantize(original.data, opts.width, opts.height, opts);
    const bytes = (opts.protocol === 'imagetousb40' ? packImageToUSB40 : packPixels)(result.pixels, opts.width, opts.height, opts.mode);
    converted = { ...opts, ...result, bytes };
    $('image-meta').textContent = `${opts.width} × ${opts.height} px · ${opts.mode === 'mono' ? '2' : opts.mode === 'tri' ? '3' : opts.mode === 'four' ? '4' : '6'} colors`;
    $('byte-meta').textContent = `${bytes.length.toLocaleString()} bytes`;
    drawPreview();
    notice(opts.protocol === 'imagetousb40'
      ? 'Image ready for ImageToUSB v4.0. Choose your USB serial port, then send the image.'
      : 'Image ready. Export the C array or send to a compatible board.');
  } catch (error) {
    $('preview').hidden = true; $('empty').hidden = false;
    $('image-meta').textContent = 'Check conversion settings'; $('byte-meta').textContent = '—';
    notice(error.message, true);
  }
  refreshUI();
}
function scheduleRender() {
  clearTimeout(renderTimer); converted = null; refreshUI();
  renderTimer = setTimeout(render, 80);
}
async function loadFile(file) {
  if (!file || active) return;
  if (file.size > 25*1024*1024) { notice('Choose an image smaller than 25 MB.', true); return; }
  if (!/^image\/(png|jpeg|webp|bmp|x-ms-bmp)$/.test(file.type) && !/\.(png|jpe?g|webp|bmp)$/i.test(file.name)) {
    notice('Choose a PNG, JPEG, WebP, or BMP image.',true); return;
  }
  const token = ++loadId;
  loading = true; refreshUI();
  const url = URL.createObjectURL(file);
  try {
    const image = new Image(); image.src = url; await image.decode();
    if (token !== loadId) return;
    if (image.naturalWidth * image.naturalHeight > 50_000_000) throw new Error('Choose an image with fewer than 50 million pixels.');
    source = image; $('file-name').textContent = `${file.name} · ${image.naturalWidth} × ${image.naturalHeight}`;
    render();
  } catch (error) { if (token === loadId) notice(`Unable to load image: ${error.message}`,true); }
  finally { URL.revokeObjectURL(url); if (token === loadId) { loading = false; refreshUI(); } }
}
$('image-file').addEventListener('change', e => loadFile(e.target.files[0]));
for (const event of ['dragover','dragleave','drop']) $('drop-zone').addEventListener(event, e => {
  e.preventDefault(); $('drop-zone').classList.toggle('drag',event==='dragover');
  if (event === 'drop') loadFile(e.dataTransfer.files[0]);
});
$('sample').addEventListener('click', () => {
  ++loadId; loading = false;
  const canvas = document.createElement('canvas'); canvas.width = 800; canvas.height = 480;
  const c = canvas.getContext('2d'); c.fillStyle = '#f5f2e9'; c.fillRect(0,0,800,480);
  c.fillStyle = '#ed432d'; c.beginPath(); c.arc(600,160,92,0,Math.PI*2); c.fill();
  c.fillStyle = '#1c3f34'; c.beginPath(); c.moveTo(330,480); c.lineTo(510,205); c.lineTo(745,480); c.fill();
  c.fillStyle = '#63866d'; c.beginPath(); c.moveTo(485,480); c.lineTo(710,270); c.lineTo(800,400); c.lineTo(800,480); c.fill();
  c.fillStyle = '#1c3f34'; c.font = '14px sans-serif'; c.fillText('A LITTLE LESS SCREEN. A LITTLE MORE STILLNESS.',42,60);
  c.font = 'bold 66px Georgia'; c.fillText('Stay',42,184); c.fillText('curious.',42,255);
  c.font = '16px sans-serif'; c.fillText('Good things take a different pace.',45,315);
  c.fillRect(45,390,65,2); c.font = '13px sans-serif'; c.fillText('E-PAPER STUDIO / 01',45,426);
  source = canvas; $('file-name').textContent = 'Studio sample · 800 × 480'; render();
});
for (const id of ['width','height','mode','fit','rotation','contrast','threshold','dither']) $(id).addEventListener('input', () => {
  if (id === 'width' || id === 'height') $('size').value = 'custom';
  if (['width','height','mode'].includes(id)) $('compatible').checked = false;
  scheduleRender();
});
$('size').addEventListener('change', () => {
  if ($('size').value !== 'custom') [$('width').value,$('height').value] = $('size').value.split(',');
  $('compatible').checked = false; scheduleRender();
});
$('show-output').addEventListener('click', () => { viewOriginal = false; drawPreview(); });
$('show-original').addEventListener('click', () => { viewOriginal = true; drawPreview(); });
function download(blob, name) {
  const link = document.createElement('a'); const url = URL.createObjectURL(blob);
  link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url),1000);
}
$('export-c').addEventListener('click', () => {
  if (converted) download(new Blob([toCArray(converted.bytes,converted)],{type:'text/plain'}),`epd_${converted.width}x${converted.height}_${converted.mode}_${converted.protocol === 'imagetousb40' ? 'imagetousb40' : 'raw'}.c`);
});
$('export-png').addEventListener('click', () => {
  if (!converted) return;
  const canvas = document.createElement('canvas'); canvas.width = converted.width; canvas.height = converted.height;
  canvas.getContext('2d').putImageData(new ImageData(converted.preview,converted.width,converted.height),0,0);
  canvas.toBlob(blob => { if (blob) download(blob,'epaper-preview.png'); },'image/png');
});
$('export-log').addEventListener('click', () => {
  download(new Blob([messages.join('\n') + '\n'], { type: 'text/plain' }), 'epaper-connection-log.txt');
});
function updateProtocol() {
  const protocol = $('protocol').value;
  $('compatibility-row').hidden = protocol !== 'good-display-usb'; $('compatible').checked = false;
  $('protocol-help').textContent = protocol === 'imagetousb40'
    ? 'GDP075FU1 uses 800 × 480 and 4 colors: black, white, red, yellow. Connect its Micro USB cable and close the Windows uploader before choosing the port. Other ImageToUSB v4.0 displays can use their matching 2- or 3-color setting.'
    : protocol === 'good-display-usb'
      ? 'Only for ESP32 firmware compatible with Good Display’s USB web tool (ESP32E6-E01 family). This profile sends raw image bytes.'
      : 'Convert and export images without uploading to a display.';
  document.querySelector('#mode option[value="six"]').disabled = protocol === 'imagetousb40';
  if (protocol === 'imagetousb40' && $('mode').value === 'six') $('mode').value = 'four';
  refreshUI();
}
$('protocol').addEventListener('change', () => {
  updateProtocol();
  render();
});
$('compatible').addEventListener('change',refreshUI);
$('connect').addEventListener('click',async () => {
  active = true; refreshUI();
  try {
    const info = await serial.connect({ protocol: $('protocol').value,
      onReceive: bytes => {
        const tail = bytes.subarray(Math.max(0, bytes.length - 128));
        log(`Device RX (${bytes.length} bytes${bytes.length > 128 ? ', last 128 shown' : ''}): ${hexBytes(tail)} | Text: ${JSON.stringify(serialText(tail))}`);
      },
      onReadError: error => {
        log(`Serial input failed: ${error.message}`);
        if (!active) notice(`${error.message} Unplug the display from power, then reconnect.`, true);
        refreshUI();
      },
    });
    log(`Serial port opened at 115200 baud (USB vendor ${info.usbVendorId?.toString(16) ?? 'unknown'}). No image sent.`);
    notice($('protocol').value === 'imagetousb40'
      ? 'Port connected. Check the preview and color setting, then send the image.'
      : 'Port connected. Confirm the display settings before sending.');
  } catch (error) { if (error.name !== 'NotFoundError') { notice(error.message,true); log(`Connection failed: ${error.message}`); } }
  finally { active = false; refreshUI(); }
});
$('disconnect').addEventListener('click',async () => {
  active = true; refreshUI();
  try { await serial.disconnect(); log('Port disconnected.'); notice('Serial port disconnected.'); }
  catch (error) { notice(error.message,true); }
  finally { active = false; refreshUI(); }
});
$('send').addEventListener('click',async () => {
  if (!converted || active || $('protocol').value === 'unknown' || ($('protocol').value === 'good-display-usb' && !$('compatible').checked)) return;
  active = true; aborter = new AbortController(); refreshUI(); $('progress').value = 0;
  log(`Preparing ${converted.bytes.length} bytes, ${converted.width} × ${converted.height}, ${converted.mode}, ${converted.protocol}.`);
  try {
    await serial.send(converted.bytes, { width: converted.width, height: converted.height, mode: converted.mode,
      signal: aborter.signal, onStatus: ({ phase, message }) => {
        log(message);
        notice(phase === 'handshake' ? 'Waiting for the display to accept the connection…' : 'Display accepted. Sending image…');
      }, onProgress: (sent,total) => {
      $('progress').value = sent/total*100; notice(`Sending image… ${Math.round(sent/total*100)}%`);
    }});
    notice(converted.protocol === 'imagetousb40'
      ? 'Image data sent. Keep USB powered and check the physical display. The screen result is not confirmed.'
      : 'Image sent. Check the display and wait for its refresh to finish before sending again.');
    log('Transfer finished. Screen refresh is not verified.');
    if (serial.refreshRemainingMs > 0) watchRefresh();
  } catch (error) {
    const text = error.name === 'AbortError' ? 'Transfer stopped.' : `Transfer failed: ${error.message}`;
    notice(`${text} Reset the board and reconnect before retrying.`,true); log(text);
  } finally {
    if (lostPort) { try { await lostPort.close(); } catch {} lostPort = null; }
    active = false; aborter = null; refreshUI();
  }
});
$('cancel').addEventListener('click', () => { aborter?.abort(); $('cancel').disabled = true; notice('Stopping after the current serial write…'); });
navigator.serial?.addEventListener('disconnect', async e => {
  if (e.target !== serial.port) return;
  aborter?.abort(); log('Device unplugged.'); notice('Device unplugged. Reset and reconnect before retrying.',true);
  if (!serial.busy) { try { await serial.disconnect(); } catch {} }
  // When a write is in flight, it rejects and releases its lock before cleanup.
  else { lostPort = serial.port; serial.port = null; }
  refreshUI();
});
if (!supported()) notice('Image conversion is available. For serial upload, open this app in desktop Chrome or Edge at localhost.');
updateProtocol();
refreshUI();
