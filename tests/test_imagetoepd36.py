import unittest
from unittest.mock import patch
from tools.imagetoepd36 import handshake_packet, valid_reply, send, await_handshake, HandshakeTimeout


class ProtocolTest(unittest.TestCase):
    def test_known_request(self):
        self.assertEqual(handshake_packet().hex(), 'aa55e10000f0c400032001e000000000000098ff0d0a')
        with self.assertRaises(ValueError):
            handshake_packet(400, 300)

    def test_reply_checksum(self):
        reply = bytearray.fromhex('a050f10000000000e1ff')
        self.assertTrue(valid_reply(reply))
        reply[3] = 128
        self.assertFalse(valid_reply(reply))
        reply[8] = sum(reply[:8]) & 255
        self.assertTrue(valid_reply(reply))
        self.assertFalse(valid_reply(reply[:9]))

    def test_transfer_layout_and_polarity(self):
        class Port:
            def __init__(self): self.writes = []
            def reset_input_buffer(self): pass
            def write(self, data): self.writes.append(data); return len(data)
        port = Port()
        report = {}
        reply = bytes.fromhex('a050f10000000000e1ff')
        with patch('tools.imagetoepd36.await_handshake', return_value=(reply, reply)), patch('tools.imagetoepd36.time.sleep'):
            send(port, bytes([255])*48000, 800, 480, report)
        self.assertEqual(port.writes[0], handshake_packet())
        self.assertEqual(len(port.writes),25)
        for i in range(1,23,2):
            self.assertEqual(port.writes[i], bytes(4096))
            self.assertEqual(port.writes[i+1], b'\r\n')
        self.assertEqual(port.writes[23], bytes(2944))
        self.assertEqual(port.writes[24], b'\r\n')
        self.assertEqual(report['bytes_sent'],48000)
        self.assertEqual(report['wire_bytes_sent'],48046)

    def test_no_image_after_failed_handshake(self):
        class Port:
            def __init__(self): self.writes=[]
            def reset_input_buffer(self): pass
            def write(self, data): self.writes.append(data); return len(data)
        port=Port()
        with patch('tools.imagetoepd36.await_handshake',side_effect=TimeoutError('No reply')):
            with self.assertRaises(TimeoutError):
                send(port, bytes(48000), 800, 480, {})
        self.assertEqual(port.writes,[handshake_packet()])

    def test_fragmented_reply_after_noise(self):
        reply = bytes.fromhex('a050f10000000000e1ff')
        class Port:
            in_waiting = 1
            def __init__(self): self.chunks = iter([b'noise', reply[:3], reply[3:7], reply[7:]])
            def read(self, count): return next(self.chunks, b'')
        self.assertEqual(await_handshake(Port(), .1), (reply, b'noise'+reply))

    def test_timeout_records_received_bytes_without_sending_image(self):
        class Port:
            def __init__(self): self.writes = []
            def reset_input_buffer(self): pass
            def write(self, data): self.writes.append(data); return len(data)
        port = Port()
        report = {}
        with patch('tools.imagetoepd36.await_handshake', side_effect=HandshakeTimeout(b'noise')) as wait:
            with self.assertRaises(HandshakeTimeout):
                send(port, bytes(48000), 800, 480, report, handshake_timeout=12)
        wait.assert_called_once_with(port, timeout=12)
        self.assertEqual(report['bytes_sent'], 0)
        self.assertEqual(report['wire_bytes_sent'], 22)
        self.assertEqual(report['handshake_received_hex'], '6e 6f 69 73 65')
        self.assertFalse(report['transfer_complete'])
        self.assertEqual(port.writes, [handshake_packet()])

    def test_ssd_reply_does_not_send_uc_payload(self):
        class Port:
            def __init__(self): self.writes = []
            def reset_input_buffer(self): pass
            def write(self, data): self.writes.append(data); return len(data)
        port = Port()
        reply = bytes.fromhex('a050f1800000000061ff')
        with patch('tools.imagetoepd36.await_handshake', return_value=(reply, reply)):
            with self.assertRaisesRegex(ValueError, 'SSD'):
                send(port, bytes(48000), 800, 480, {})
        self.assertEqual(port.writes, [handshake_packet()])

if __name__ == '__main__':
    unittest.main()
