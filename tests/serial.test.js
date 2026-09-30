import test from 'node:test';
import assert from 'node:assert/strict';
import { SerialConnection } from '../src/serial.js';
function mock(write = async () => {}) {
  const chunks = []; let released = 0;
  const writer = { async write(b) { await write(b); chunks.push(b.slice()); }, releaseLock() { released++; } };
  const port = { writable: {getWriter:()=>writer}, async open(opts) { this.options=opts; }, async close() { this.closed=true; }, getInfo:()=>({usbVendorId:0x1a86}) };
  const connection = new SerialConnection({ requestPort:async()=>port });
  return { connection,port,chunks, get released() { return released; } };
}
test('serial sends exact bytes in 512-byte chunks at 115200 8N1', async () => {
  const m = mock(); await m.connection.connect();
  assert.deepEqual(m.port.options,{baudRate:115200,dataBits:8,stopBits:1,parity:'none',flowControl:'none'});
  const input = Uint8Array.from({length:1200},(_,i)=>i%256); const progress=[];
  const result = await m.connection.send(input,{onProgress:(n)=>progress.push(n)});
  assert.deepEqual(m.chunks.map(b=>b.length),[512,512,176]);
  assert.deepEqual(Uint8Array.from(m.chunks.flatMap(b=>[...b])),input);
  assert.deepEqual(progress,[512,1024,1200]); assert.equal(result.refreshConfirmed,false); assert.equal(m.released,1);
  await m.connection.disconnect(); assert.equal(m.port.closed,true); assert.equal(m.connection.port,null);
});
test('cancellation stops following chunks and requires board reset/reconnect',async()=>{
  const m=mock(); await m.connection.connect(); const controller=new AbortController();
  await assert.rejects(m.connection.send(new Uint8Array(1024),{signal:controller.signal,onProgress:()=>controller.abort()}),{name:'AbortError'});
  assert.equal(m.chunks.length,1); assert.equal(m.released,1); assert.equal(m.connection.busy,false);
  await assert.rejects(m.connection.send(new Uint8Array(1)),/Reset the board/);
});
test('write errors release the lock and do not claim success',async()=>{
  const m=mock(async()=>{throw new Error('USB removed');}); await m.connection.connect();
  await assert.rejects(m.connection.send(new Uint8Array(10)),/USB removed/);
  assert.equal(m.released,1); assert.equal(m.connection.busy,false); assert.equal(m.connection.tainted,true);
});
test('concurrent writes and disconnects are rejected',async()=>{
  let finish; const m=mock(()=>new Promise(resolve=>{finish=resolve;})); await m.connection.connect();
  const first=m.connection.send(new Uint8Array(10));
  await assert.rejects(m.connection.send(new Uint8Array(10)),/already running/);
  await assert.rejects(m.connection.disconnect(),/Wait/); finish(); await first;
});
test('missing API and cancelled picker are handled',async()=>{
  await assert.rejects(new SerialConnection(null).connect(),/Chrome or Edge/);
  const connection=new SerialConnection({requestPort:async()=>{throw new DOMException('Cancelled','NotFoundError');}});
  await assert.rejects(connection.connect(),{name:'NotFoundError'}); assert.equal(connection.busy,false); assert.equal(connection.port,null);
});
