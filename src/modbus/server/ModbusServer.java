package com.omanasep.modbus.server;

/**
 * Minimal placeholder for a Modbus server instance, passed back to a
 * {@link ModbusWriteHandler} so the handler knows which server triggered
 * the write callback.
 *
 * NOTE: The original project referenced a type called "AModbusServer" in
 * ModbusWriteHandler.java, but no such class existed anywhere in the
 * source tree, which caused the "cannot find symbol" compile errors.
 * This stub restores compilation. Flesh this out (or replace it with your
 * real server implementation) once the Modbus server feature is built.
 */
public class ModbusServer {

    private final RegistersTable coils;
    private final RegistersTable holdingRegisters;

    public ModbusServer(RegistersTable coils, RegistersTable holdingRegisters) {
        this.coils = coils;
        this.holdingRegisters = holdingRegisters;
    }

    public RegistersTable getCoils() {
        return coils;
    }

    public RegistersTable getHoldingRegisters() {
        return holdingRegisters;
    }
}
