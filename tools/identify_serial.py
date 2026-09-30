"""Non-flashing STM32 UART bootloader identification (ST AN3155).
Does not force boot mode, pulse reset, erase, write memory, or alter protection.
Only sync and Get ID, and the latter only after a sync ACK.
"""
import argparse
import json
import time
from pathlib import Path
import serial
from serial.tools import list_ports

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--port',required=True)
parser.add_argument('--log',type=Path,required=True)
args=parser.parse_args()
report={'port':args.port,'time':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'probe':'STM32 AN3155 sync + Get ID','firmware_modified':False}
info=next((p for p in list_ports.comports() if p.device==args.port),None)
if info:report['usb']={'vid':info.vid,'pid':info.pid,'description':info.description,'manufacturer':info.manufacturer,'serial_number':info.serial_number}
s=serial.Serial(port=None,baudrate=115200,parity=serial.PARITY_EVEN,timeout=1.5,write_timeout=2,exclusive=True)
s.dtr=False;s.rts=False;s.port=args.port
try:
 s.open()
 startup=s.read(256);report['startup_hex']=startup.hex(' ')
 s.reset_input_buffer()
 s.write(b'\x7f')
 reply=s.read(1);report['sync_reply_hex']=reply.hex(' ')
 if reply==b'\x79':
  s.write(bytes.fromhex('02 fd'))
  ack=s.read(1);report['get_id_ack_hex']=ack.hex(' ')
  if ack==b'\x79':
   size=s.read(1)
   if not size:raise TimeoutError('Missing chip ID length')
   chip=s.read(size[0]+1);end=s.read(1)
   report['chip_id_hex']=chip.hex();report['get_id_complete']=len(chip)==size[0]+1 and end==b'\x79'
 else:
  report['conclusion']='No STM32 ROM bootloader acknowledgment in the current device state. MCU type remains unknown.'
except Exception as e:report['error']=str(e)
finally:
 s.close();args.log.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
