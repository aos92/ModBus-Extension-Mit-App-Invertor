package com.omanasep.modbus.client;

import static com.omanasep.modbus.ModbusConstants.*;

import org.slf4j.LoggerFactory;

import com.omanasep.modbus.driver.UsbSerialPort;

import java.io.IOException;
import java.util.Arrays;

/**
 * Transport Modbus RTU yang berjalan lewat adapter USB-to-serial (CH340, CP210x,
 * FTDI, PL2303, atau CDC-ACM) menggunakan kelas-kelas driver yang ada di paket
 * {@code com.omanasep.modbus.driver} (diporting dari mik3y/usb-serial-for-android).
 * <p>
 * {@link UsbSerialPort} yang diberikan ke constructor harus sudah dalam keadaan
 * {@link UsbSerialPort#open(android.hardware.usb.UsbDeviceConnection) terbuka}
 * dan sudah dikonfigurasi lewat {@link UsbSerialPort#setParameters(int, int, int, int)}.
 * Biasanya ini dilakukan oleh kelas extension segera setelah pengguna memberi
 * izin USB untuk perangkat tersebut.
 */
public class RtuSerialTransport extends AbstractRtuTransport {

	private final UsbSerialPort port;
	private volatile boolean portOpen = true;

	public RtuSerialTransport(UsbSerialPort port, int timeout, int pause, boolean keepConnection) {
		super(timeout, pause, keepConnection, LoggerFactory.getLogger(RtuSerialTransport.class));
		this.port = port;
	}

	@Override
	protected boolean openPort() {
		// Port sudah dibuka satu kali oleh kelas extension, karena proses membuka
		// port memerlukan android.hardware.usb.UsbDeviceConnection yang tidak
		// dimiliki oleh transport ini. Di sini kita hanya memeriksa apakah masih bisa dipakai.
		return portOpen;
	}

	@Override
	protected void clearInput() throws IOException {
		port.purgeHwBuffers(true, false);
	}

	@Override
	protected void sendData(int size) throws IOException {
		port.write(Arrays.copyOf(buffer, size), timeout);
	}

	@Override
	protected boolean readToBuffer(int start, int length, ModbusClient modbusClient) throws IOException {
		byte[] tmp = new byte[MAX_PDU_SIZE + 3];
		long deadline = System.currentTimeMillis() + timeout;
		int offset = start;
		int remaining = length;
		while (remaining > 0) {
			long left = deadline - System.currentTimeMillis();
			if (left <= 0)
				break;
			int n = port.read(tmp, (int) left);
			if (n > 0) {
				System.arraycopy(tmp, 0, buffer, offset, Math.min(n, remaining));
				offset += n;
				remaining -= n;
			}
		}
		return remaining <= 0;
	}

	@Override
	public void close() {
		portOpen = false;
		try {
			port.close();
		} catch (IOException e) {
			log.error("Gagal menutup port serial: {}", e.getMessage());
		}
	}

}
