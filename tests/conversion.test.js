import test from 'node:test';
import assert from 'node:assert/strict';
import { quantize, packPixels, toCArray, validateDimensions } from '../src/conversion.js';
const rgba = (...colors) => Uint8ClampedArray.from(colors.flatMap(c => [...c,255]));
test('monochrome uses MSB first, row stride and zero padding', () => {
  assert.deepEqual([...packPixels(Uint8Array.from([1,0,1,0,1,0,1,0,1, 0,1,0,1,0,1,0,1,0]),9,2,'mono')],[0xaa,0x80,0x55,0]);
});
test('tri-color produces BW followed by red plane using vendor polarity', () => {
  const bytes = packPixels(Uint8Array.from([0,1,2,1,0,2,1,1]),8,1,'tri');
  assert.deepEqual([...bytes],[0x53,0xdb]);
});
test('four-color packs 00,01,10,11 and rejects misaligned rows', () => {
  assert.deepEqual([...packPixels(Uint8Array.from([0,1,2,3]),4,1,'four')],[0x1b]);
  assert.throws(() => packPixels(new Uint8Array(6),3,2,'four'),/divisible/);
});
test('six-color maps vendor codes and clockwise rotation', () => {
  assert.deepEqual([...packPixels(Uint8Array.from([0,1,2,3,4,5]),3,2,'six')],[0xe2,0,0x96,0xff,0x1d,0x4c]);
});
test('transparent pixels composite onto white', () => {
  const q = quantize(Uint8ClampedArray.from([0,0,0,0,0,0,0,255]),2,1,{dither:false});
  assert.deepEqual([...q.pixels],[1,0]);
});
test('exact palette colors survive quantization and dithering', () => {
  const q = quantize(rgba([0,0,0],[255,255,255],[255,0,0],[255,255,0]),4,1,{mode:'four',dither:true});
  assert.deepEqual([...q.pixels],[0,1,2,3]);
});
test('dithering distributes an intermediate gray across black and white', () => {
  const colors = Array.from({length:64}, () => [128,128,128]);
  const q = quantize(rgba(...colors),8,8,{dither:true});
  const whites = q.pixels.reduce((a,b)=>a+b,0);
  assert.ok(whites>=24 && whites<=40);
  assert.ok(q.preview.every((v,i)=> i%4===3 ? v===255 : v===0 || v===255));
});
test('invalid dimensions, pixels, and options are rejected', () => {
  for (const dims of [[0,20],[10.5,20],[2049,20],[2000,2000]]) assert.throws(()=>validateDimensions(...dims));
  assert.throws(()=>packPixels(Uint8Array.from([3]),1,1,'mono'));
  assert.throws(()=>quantize(new Uint8Array(4),1,1,{mode:'missing'}));
  assert.throws(()=>quantize(new Uint8Array(4),1,1,{contrast:NaN}));
});
test('C export includes dimensions, byte count, and exact data', () => {
  const text = toCArray(Uint8Array.from([0x00,0xab,0xff]),{width:24,height:1,mode:'mono'});
  assert.match(text,/epd_image_length = 3/); assert.match(text,/0x00, 0xab, 0xff/);
  assert.throws(()=>toCArray(new Uint8Array(1),{name:'not-valid'}));
});
