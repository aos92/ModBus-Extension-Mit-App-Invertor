package com.omanasep.modbus.server;

public interface ModbusWriteHandler {
	// Caution: These methods will be called from various threads.
	// method must return true on success
	public boolean OnWriteCoil(ModbusServer server, int address, boolean value);
	public boolean OnWriteHReg(ModbusServer server, int address, int value);
}
