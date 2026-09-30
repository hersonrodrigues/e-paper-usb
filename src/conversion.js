export const MODES = {
  mono: { label: 'Black & white', colors: [[0,0,0], [255,255,255]] },
  tri: { label: 'Black / white / red', colors: [[0,0,0], [255,255,255], [255,0,0]] },
  four: { label: 'Black / white / red / yellow', colors: [[0,0,0], [255,255,255], [255,0,0], [255,255,0]] },
  six: { label: 'Spectra 6 · USB format', colors: [[0,0,0], [255,255,255], [255,0,0], [255,255,0], [41,204,20], [0,0,255]] },
};
export function validateDimensions(width, height) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > 2048 || height > 2048 || width * height > 2_000_000) {
    throw new Error('Use whole-number dimensions from 1 to 2048, with at most 2 million pixels.');
  }
}
export function quantize(rgba, width, height, { mode = 'mono', dither = true, contrast = 1, threshold = 140 } = {}) {
  validateDimensions(width, height);
  if (!MODES[mode] || rgba.length !== width * height * 4) throw new Error('Invalid image data or color mode.');
  if (!Number.isFinite(contrast) || contrast < 0.1 || contrast > 3 || !Number.isFinite(threshold) || threshold < 1 || threshold > 254) throw new Error('Invalid contrast or threshold.');
  const colors = MODES[mode].colors;
  const work = new Float32Array(width * height * 3);
  for (let i = 0; i < width * height; i++) {
    const alpha = rgba[i * 4 + 3] / 255;
    for (let c = 0; c < 3; c++) {
      const value = rgba[i * 4 + c] * alpha + 255 * (1 - alpha);
      work[i * 3 + c] = Math.max(0, Math.min(255, (value - 128) * contrast + 128));
    }
  }
  const pixels = new Uint8Array(width * height);
  const preview = new Uint8ClampedArray(rgba.length);
  const spread = (x, y, c, error, weight) => {
    if (x >= 0 && x < width && y < height) work[(y * width + x) * 3 + c] += error * weight;
  };
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = y * width + x;
    const rgb = [0,1,2].map(c => Math.max(0, Math.min(255, work[i * 3 + c])));
    let chosen = 0;
    if (mode === 'mono') chosen = (rgb[0] * .299 + rgb[1] * .587 + rgb[2] * .114) >= threshold ? 1 : 0;
    else {
      let best = Infinity;
      colors.forEach((color, index) => {
        const distance = color.reduce((sum, value, c) => sum + (rgb[c] - value) ** 2, 0);
        if (distance < best) { best = distance; chosen = index; }
      });
    }
    pixels[i] = chosen;
    for (let c = 0; c < 3; c++) {
      preview[i * 4 + c] = colors[chosen][c];
      if (dither) {
        const error = rgb[c] - colors[chosen][c];
        spread(x+1,y,c,error,7/16); spread(x-1,y+1,c,error,3/16);
        spread(x,y+1,c,error,5/16); spread(x+1,y+1,c,error,1/16);
      }
    }
    preview[i * 4 + 3] = 255;
  }
  return { pixels, preview };
}
// Formats observed in Good Display's usb2epd.html (2026-09-29).
// These are NOT universal formats for all Good Display controllers.
export function packPixels(pixels, width, height, mode) {
  validateDimensions(width, height);
  if (!MODES[mode] || pixels.length !== width * height || pixels.some(p => p >= MODES[mode].colors.length)) throw new Error('Invalid palette pixels.');
  if (mode === 'six') {
    const values = [0x00, 0xff, 0x4c, 0xe2, 0x96, 0x1d];
    const bytes = new Uint8Array(width * height);
    for (let y=0;y<height;y++) for (let x=0;x<width;x++) bytes[x*height+height-1-y] = values[pixels[y*width+x]];
    return bytes;
  }
  if (mode === 'four') {
    // The vendor format requires rows aligned to groups of four pixels.
    if (width % 4) throw new Error('Four-color USB format requires a width divisible by 4.');
    const bytes = new Uint8Array(width * height / 4);
    pixels.forEach((p,i) => { bytes[i>>2] |= p << (6-(i%4)*2); });
    return bytes;
  }
  const stride = Math.ceil(width / 8);
  const planeSize = stride * height;
  const bytes = new Uint8Array(planeSize * (mode === 'tri' ? 2 : 1));
  for (let y=0;y<height;y++) for (let x=0;x<width;x++) {
    const p = pixels[y*width+x], index = y*stride+(x>>3), mask = 1 << (7-x%8);
    // In the vendor USB format, red is 0 in BOTH planes. Padding bits are 0.
    if (p === 1) bytes[index] |= mask;
    if (mode === 'tri' && p !== 2) bytes[planeSize+index] |= mask;
  }
  return bytes;
}
export function toCArray(bytes, { width, height, mode, name = 'epd_image', protocol }) {
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name)) throw new Error('Invalid C identifier.');
  const encoding = protocol === 'imagetousb40'
    ? 'Encoding: ImageToUSB v4.0, 800x480, row-major, MSB first.\n * Mono: white=1, black=0. Tri: BW then red; red=(1,0).\n * Four: 2-bit packed, black=0, white=1, red=3, yellow=2.\n * Payload only; serial handshake and CRLF block delimiters are not included.'
    : 'Encoding: Good Display USB web-tool format, not universal panel data.\n * Mono/tri: row-major, MSB first, zero padding; tri: BW then red.\n * Four: 2-bit packed; six: rotated byte-per-pixel vendor color codes.';
  const rows = [];
  for (let i=0;i<bytes.length;i+=16) rows.push('    ' + Array.from(bytes.subarray(i,i+16), b => '0x'+b.toString(16).padStart(2,'0')).join(', ') + ',');
  return `/* E-paper Studio | ${width} x ${height} | ${mode}\n * ${encoding}\n * Image data only: include in compatible firmware, not a flashable program.\n */\n#include <stdint.h>\n\nconst uint32_t ${name}_width = ${width};\nconst uint32_t ${name}_height = ${height};\nconst uint32_t ${name}_length = ${bytes.length};\nconst uint8_t ${name}[${bytes.length}] = {\n${rows.join('\n')}\n};\n`;
}
