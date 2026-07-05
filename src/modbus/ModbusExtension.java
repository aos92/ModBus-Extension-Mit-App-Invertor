package com.omanasep.modbus;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Deleteable;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.YailList;

import com.omanasep.modbus.client.ModbusClient;
import com.omanasep.modbus.client.RtuSerialTransport;
import com.omanasep.modbus.client.TcpTransport;
import com.omanasep.modbus.driver.UsbSerialDriver;
import com.omanasep.modbus.driver.UsbSerialPort;
import com.omanasep.modbus.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension Modbus master (client) untuk MIT App Inventor.
 * <p>
 * Mendukung Modbus TCP lewat Wi-Fi/Ethernet ({@link #ConnectTcp(String, int)}) dan
 * Modbus RTU lewat adapter USB-to-serial seperti CH340, CP210x, FTDI, PL2303, atau
 * CDC-ACM ({@link #ConnectRtu(int, int, int, int)}).
 */
@DesignerComponent(
    version = 1,
    versionName = "1.0",
    description = "Modbus master (client): membaca dan menulis coil / register lewat " +
        "Modbus TCP (Wi-Fi/Ethernet) atau Modbus RTU (adapter USB-to-serial).",
    category = ComponentCategory.CONNECTIVITY,
    nonVisible = true,
    iconName = "aiwebres/icon.png")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class ModbusExtension extends AndroidNonvisibleComponent implements Deleteable {

  private static final String ACTION_USB_PERMISSION = "com.omanasep.modbus.USB_PERMISSION";

  private final Activity activity;
  private final Context context;
  private final UsbManager usbManager;

  private ModbusClient client;
  private UsbSerialPort openSerialPort;
  private UsbDeviceConnection openUsbConnection;

  private int slaveId = 1;
  private int responseTimeout = 1000;
  private int connectTimeout = 3000;
  private int pauseMs = 0;
  private boolean keepConnection = true;

  private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context ctx, Intent intent) {
      if (!ACTION_USB_PERMISSION.equals(intent.getAction()))
        return;
      UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
      if (granted && (device != null))
        UsbPermissionGranted(device.getDeviceName());
      else
        UsbPermissionDenied();
    }
  };

  public ModbusExtension(ComponentContainer container) {
    super(container.$form());
    this.activity = container.$context();
    this.context = container.$context();
    this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    context.registerReceiver(usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));
  }

  // ================================================================
  // Properti
  // ================================================================

  @SimpleProperty(category = PropertyCategory.BEHAVIOR,
      description = "ID slave/unit Modbus (1-247) yang dipakai di setiap permintaan (request).")
  public int SlaveId() {
    return slaveId;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER, defaultValue = "1")
  @SimpleProperty
  public void SlaveId(int id) {
    this.slaveId = id;
  }

  @SimpleProperty(category = PropertyCategory.BEHAVIOR,
      description = "Batas waktu (milidetik) menunggu balasan (response) dari perangkat.")
  public int ResponseTimeout() {
    return responseTimeout;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER, defaultValue = "1000")
  @SimpleProperty
  public void ResponseTimeout(int ms) {
    this.responseTimeout = ms;
  }

  @SimpleProperty(category = PropertyCategory.BEHAVIOR,
      description = "Batas waktu (milidetik) saat membuka koneksi TCP.")
  public int ConnectTimeout() {
    return connectTimeout;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER, defaultValue = "3000")
  @SimpleProperty
  public void ConnectTimeout(int ms) {
    this.connectTimeout = ms;
  }

  @SimpleProperty(category = PropertyCategory.BEHAVIOR,
      description = "Jeda (milidetik) yang disisipkan sebelum setiap permintaan (berguna untuk gateway RS485 yang lambat).")
  public int Pause() {
    return pauseMs;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER, defaultValue = "0")
  @SimpleProperty
  public void Pause(int ms) {
    this.pauseMs = ms;
  }

  @SimpleProperty(category = PropertyCategory.BEHAVIOR,
      description = "Jika true, koneksi (socket TCP / port serial) tetap terbuka di antara permintaan.")
  public boolean KeepConnection() {
    return keepConnection;
  }

  @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "true")
  @SimpleProperty
  public void KeepConnection(boolean keep) {
    this.keepConnection = keep;
  }

  @SimpleProperty(description = "Bernilai true setelah ConnectTcp atau ConnectRtu berhasil mengatur koneksi.")
  public boolean IsConnected() {
    return client != null;
  }

  // ================================================================
  // Koneksi: TCP
  // ================================================================

  @SimpleFunction(description = "Mengatur koneksi Modbus TCP ke host dan port yang diberikan (gunakan 502 untuk port Modbus standar).")
  public void ConnectTcp(String host, int port) {
    try {
      closeInternal();
      TcpTransport transport = new TcpTransport(host, port, null, 0,
          connectTimeout, responseTimeout, pauseMs, keepConnection);
      client = new ModbusClient();
      client.setTransport(transport);
      Connected();
    } catch (Exception e) {
      OperationError("ConnectTcp", String.valueOf(e.getMessage()));
    }
  }

  // ================================================================
  // Koneksi: RTU lewat USB serial
  // ================================================================

  @SimpleFunction(description = "Menampilkan daftar nama perangkat USB serial yang kompatibel dengan Modbus RTU " +
      "yang sedang terpasang (CH340, CP210x, FTDI, PL2303, CDC-ACM).")
  public YailList ListUsbSerialDevices() {
    List<String> names = new ArrayList<String>();
    for (UsbSerialDriver d : UsbSerialProber.getDefaultProber().findAllDrivers(usbManager))
      names.add(d.getDevice().getDeviceName());
    return YailList.makeList(names);
  }

  @SimpleFunction(description = "Meminta izin Android untuk mengakses adapter USB serial pertama yang " +
      "terpasang. Akan memicu event UsbPermissionGranted atau UsbPermissionDenied.")
  public void RequestUsbPermission() {
    List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
    if (drivers.isEmpty()) {
      OperationError("RequestUsbPermission", "Tidak ditemukan adapter USB serial yang kompatibel.");
      return;
    }
    UsbDevice device = drivers.get(0).getDevice();
    if (usbManager.hasPermission(device)) {
      UsbPermissionGranted(device.getDeviceName());
      return;
    }
    // 0x02000000 == PendingIntent.FLAG_MUTABLE (API 31+). Ditulis sebagai literal angka
    // supaya tetap bisa dikompilasi terhadap android.jar lama yang belum punya konstanta ini.
    int flags = (Build.VERSION.SDK_INT >= 31) ? 0x02000000 : 0;
    PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), flags);
    usbManager.requestPermission(device, pi);
  }

  @SimpleFunction(description = "Mengatur koneksi Modbus RTU lewat adapter USB serial pertama yang " +
      "terpasang dan sudah diberi izin. Panggil RequestUsbPermission terlebih dahulu.")
  public void ConnectRtu(int baudRate, int dataBits, int stopBits, int parity) {
    try {
      closeInternal();
      List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
      if (drivers.isEmpty()) {
        OperationError("ConnectRtu", "Tidak ditemukan adapter USB serial yang kompatibel.");
        return;
      }
      UsbSerialDriver driver = drivers.get(0);
      UsbDevice device = driver.getDevice();
      if (!usbManager.hasPermission(device)) {
        OperationError("ConnectRtu", "Izin USB belum diberikan. Panggil RequestUsbPermission terlebih dahulu.");
        return;
      }
      openUsbConnection = usbManager.openDevice(device);
      if (openUsbConnection == null) {
        OperationError("ConnectRtu", "Tidak bisa membuka perangkat USB.");
        return;
      }
      UsbSerialPort port = driver.getPorts().get(0);
      port.open(openUsbConnection);
      port.setParameters(baudRate, dataBits, stopBits, parity);
      openSerialPort = port;

      RtuSerialTransport transport = new RtuSerialTransport(port, responseTimeout, pauseMs, keepConnection);
      client = new ModbusClient();
      client.setTransport(transport);
      Connected();
    } catch (Exception e) {
      OperationError("ConnectRtu", String.valueOf(e.getMessage()));
    }
  }

  @SimpleFunction(description = "Menutup koneksi yang sedang aktif (socket TCP atau port serial USB).")
  public void Disconnect() {
    closeInternal();
    Disconnected();
  }

  private void closeInternal() {
    if (client != null) {
      client.close();
      client = null;
    }
    if (openSerialPort != null) {
      try {
        openSerialPort.close();
      } catch (Exception ignored) {
        // sudah tertutup
      }
      openSerialPort = null;
    }
    if (openUsbConnection != null) {
      openUsbConnection.close();
      openUsbConnection = null;
    }
  }

  // ================================================================
  // Fungsi baca
  // ================================================================

  @SimpleFunction(description = "Membaca sejumlah 'count' coil (function 0x01) mulai dari 'startAddress'. Hasil lewat event GotBits.")
  public void ReadCoils(int startAddress, int count) {
    readBits(startAddress, count, true);
  }

  @SimpleFunction(description = "Membaca sejumlah 'count' discrete input (function 0x02) mulai dari 'startAddress'. Hasil lewat event GotBits.")
  public void ReadDiscreteInputs(int startAddress, int count) {
    readBits(startAddress, count, false);
  }

  private void readBits(int startAddress, int count, boolean coils) {
    String op = coils ? "ReadCoils" : "ReadDiscreteInputs";
    if (!requireClient(op))
      return;
    try {
      if (coils)
        client.InitReadCoilsRequest(slaveId, startAddress, count);
      else
        client.InitReadDInputsRequest(slaveId, startAddress, count);
      if (client.execRequest()) {
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < count; i++)
          values.add(client.getResponseBit(startAddress + i));
        GotBits(YailList.makeList(values));
      } else {
        OperationError(op, client.getResultAsString());
      }
    } catch (Exception e) {
      OperationError(op, String.valueOf(e.getMessage()));
    }
  }

  @SimpleFunction(description = "Membaca sejumlah 'count' holding register (function 0x03) mulai dari 'startAddress'. Hasil lewat event GotRegisters.")
  public void ReadHoldingRegisters(int startAddress, int count, boolean unsigned) {
    readRegisters(startAddress, count, unsigned, true);
  }

  @SimpleFunction(description = "Membaca sejumlah 'count' input register (function 0x04) mulai dari 'startAddress'. Hasil lewat event GotRegisters.")
  public void ReadInputRegisters(int startAddress, int count, boolean unsigned) {
    readRegisters(startAddress, count, unsigned, false);
  }

  private void readRegisters(int startAddress, int count, boolean unsigned, boolean holding) {
    String op = holding ? "ReadHoldingRegisters" : "ReadInputRegisters";
    if (!requireClient(op))
      return;
    try {
      if (holding)
        client.InitReadHoldingsRequest(slaveId, startAddress, count);
      else
        client.InitReadAInputsRequest(slaveId, startAddress, count);
      if (client.execRequest()) {
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < count; i++)
          values.add(client.getResponseRegister(startAddress + i, unsigned));
        GotRegisters(YailList.makeList(values));
      } else {
        OperationError(op, client.getResultAsString());
      }
    } catch (Exception e) {
      OperationError(op, String.valueOf(e.getMessage()));
    }
  }

  // ================================================================
  // Fungsi tulis
  // ================================================================

  @SimpleFunction(description = "Menulis satu coil (function 0x05).")
  public void WriteCoil(int address, boolean value) {
    if (!requireClient("WriteCoil"))
      return;
    try {
      client.InitWriteCoilRequest(slaveId, address, value);
      boolean ok = client.execRequest();
      WriteCompleted("WriteCoil", ok, ok ? "" : client.getResultAsString());
    } catch (Exception e) {
      OperationError("WriteCoil", String.valueOf(e.getMessage()));
    }
  }

  @SimpleFunction(description = "Menulis satu holding register (function 0x06). Nilai harus 0-65535.")
  public void WriteRegister(int address, int value) {
    if (!requireClient("WriteRegister"))
      return;
    try {
      client.InitWriteRegisterRequest(slaveId, address, value);
      boolean ok = client.execRequest();
      WriteCompleted("WriteRegister", ok, ok ? "" : client.getResultAsString());
    } catch (Exception e) {
      OperationError("WriteRegister", String.valueOf(e.getMessage()));
    }
  }

  @SimpleFunction(description = "Menulis beberapa coil sekaligus (function 0x0F) mulai dari 'startAddress'. " +
      "'values' berupa list berisi true/false.")
  public void WriteCoils(int startAddress, YailList values) {
    if (!requireClient("WriteCoils"))
      return;
    try {
      Object[] arr = values.toArray();
      boolean[] bools = new boolean[arr.length];
      for (int i = 0; i < arr.length; i++) {
        String s = String.valueOf(arr[i]).trim();
        bools[i] = Boolean.parseBoolean(s) || "1".equals(s) || "true".equalsIgnoreCase(s);
      }
      client.InitWriteCoilsRequest(slaveId, startAddress, bools);
      boolean ok = client.execRequest();
      WriteCompleted("WriteCoils", ok, ok ? "" : client.getResultAsString());
    } catch (Exception e) {
      OperationError("WriteCoils", String.valueOf(e.getMessage()));
    }
  }

  @SimpleFunction(description = "Menulis beberapa holding register sekaligus (function 0x10) mulai dari 'startAddress'. " +
      "'values' berupa list berisi angka (0-65535).")
  public void WriteRegisters(int startAddress, YailList values) {
    if (!requireClient("WriteRegisters"))
      return;
    try {
      Object[] arr = values.toArray();
      int[] ints = new int[arr.length];
      for (int i = 0; i < arr.length; i++)
        ints[i] = Integer.parseInt(String.valueOf(arr[i]).trim());
      client.InitWriteRegistersRequest(slaveId, startAddress, ints);
      boolean ok = client.execRequest();
      WriteCompleted("WriteRegisters", ok, ok ? "" : client.getResultAsString());
    } catch (Exception e) {
      OperationError("WriteRegisters", String.valueOf(e.getMessage()));
    }
  }

  private boolean requireClient(String op) {
    if (client == null) {
      OperationError(op, "Belum terkoneksi. Panggil ConnectTcp atau ConnectRtu terlebih dahulu.");
      return false;
    }
    return true;
  }

  // ================================================================
  // Event
  // ================================================================

  @SimpleEvent(description = "Dipicu setelah ConnectTcp/ConnectRtu berhasil mengatur koneksi.")
  public void Connected() {
    EventDispatcher.dispatchEvent(this, "Connected");
  }

  @SimpleEvent(description = "Dipicu setelah Disconnect() dipanggil.")
  public void Disconnected() {
    EventDispatcher.dispatchEvent(this, "Disconnected");
  }

  @SimpleEvent(description = "Dipicu dengan hasil dari ReadCoils/ReadDiscreteInputs: list berisi nilai true/false.")
  public void GotBits(YailList values) {
    EventDispatcher.dispatchEvent(this, "GotBits", values);
  }

  @SimpleEvent(description = "Dipicu dengan hasil dari ReadHoldingRegisters/ReadInputRegisters: list berisi angka.")
  public void GotRegisters(YailList values) {
    EventDispatcher.dispatchEvent(this, "GotRegisters", values);
  }

  @SimpleEvent(description = "Dipicu setelah fungsi tulis selesai dijalankan, baik berhasil maupun gagal.")
  public void WriteCompleted(String operation, boolean success, String errorMessage) {
    EventDispatcher.dispatchEvent(this, "WriteCompleted", operation, success, errorMessage);
  }

  @SimpleEvent(description = "Dipicu saat pengguna mengizinkan akses USB yang diminta oleh RequestUsbPermission.")
  public void UsbPermissionGranted(String deviceName) {
    EventDispatcher.dispatchEvent(this, "UsbPermissionGranted", deviceName);
  }

  @SimpleEvent(description = "Dipicu saat pengguna menolak izin akses USB yang diminta oleh RequestUsbPermission.")
  public void UsbPermissionDenied() {
    EventDispatcher.dispatchEvent(this, "UsbPermissionDenied");
  }

  @SimpleEvent(description = "Dipicu setiap kali operasi koneksi, baca, atau tulis mengalami kegagalan.")
  public void OperationError(String operation, String message) {
    EventDispatcher.dispatchEvent(this, "OperationError", operation, message);
  }

  // ================================================================
  // Pembersihan (cleanup)
  // ================================================================

  @Override
  public void onDelete() {
    closeInternal();
    try {
      context.unregisterReceiver(usbPermissionReceiver);
    } catch (Exception ignored) {
      // receiver sudah tidak terdaftar
    }
  }

}
