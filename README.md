Android-FTDI
============

Android applications that interface with FTDI based boards



Build instructions using Ant
==============================


```bash
git clone https://github.com/accesio/Android-FTDI
cd Android-FTDI
ant clean
ant debug
adb install bin/com.UARTLoopback.UARTLoopbackActivity-debug-unaligned.apk
```

Attach a device and it should automatically load this sample and prompt you for permission
to use the USB device by default for this USB accessory. You should both approve and 
select this selection box.







