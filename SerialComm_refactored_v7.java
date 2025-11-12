package lumina;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class SerialComm {

    private static final int BAUD_RATE = 115200;
    private static final int READ_CHUNK_LEN = 512;
    private static final Pattern LINE_SPLIT = Pattern.compile("\\R");
    private static final Consumer<String> NOOP = s -> {};

    private SerialPort port;
    private final StringBuilder buffer = new StringBuilder();
    private Consumer<String> onLine = NOOP;

    public String[] listPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .toArray(String[]::new);
    }

    public boolean open(String systemName) {
        if (systemName == null) return false;
        close();

        SerialPort found = Arrays.stream(SerialPort.getCommPorts())
                .filter(p -> systemName.equals(p.getSystemPortName()))
                .findFirst()
                .orElse(null);

        if (found == null) return false;

        port = found;
        configure(port);
        if (!port.openPort()) {
            port = null;
            return false;
        }
        port.addDataListener(new DataListener());
        return true;
    }

    public void close() {
        if (port == null) return;
        try {
            port.removeDataListener();
            port.closePort();
        } finally {
            port = null;
            buffer.setLength(0);
        }
    }

    public void setOnLine(Consumer<String> listener) {
        this.onLine = listener == null ? NOOP : listener;
    }

    private static void configure(SerialPort sp) {
        sp.setComPortParameters(BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
        sp.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
    }

    private final class DataListener implements SerialPortDataListener {
        @Override public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }
        @Override public void serialEvent(SerialPortEvent event) {
            if (port == null || event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
            readAndEmitLines();
        }
    }

    private void readAndEmitLines() {
        int available = Math.max(0, port.bytesAvailable());
        byte[] chunk = new byte[Math.min(available, READ_CHUNK_LEN)];
        int n = port.readBytes(chunk, chunk.length);

        buffer.append(new String(chunk, 0, Math.max(0, n), StandardCharsets.UTF_8));

        String[] parts = LINE_SPLIT.split(buffer.toString(), -1);
        int complete = parts.length - 1;
        for (int i = 0; i < complete; i++) {
            onLine.accept(parts[i].trim());
        }
        buffer.setLength(0);
        buffer.append(parts[parts.length - 1]);
    }
}
