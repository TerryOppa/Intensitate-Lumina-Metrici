package lumina2;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.io.IOException;

public class SerialComm {

    private SerialPort sp;
    private SerialDataListener dataListener;
    

    private StringBuilder buffer = new StringBuilder();


    public interface SerialDataListener {
        void onDataReceived(String data);
    }


    public SerialComm(String portName, int baudRate) {
        sp = SerialPort.getCommPort(portName);
        sp.setComPortParameters(baudRate, 8, 1, 0);


        sp.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
    }


    public boolean openPort() {
        return sp.openPort();
    }


    public void closePort() {
        sp.closePort();
    }


    public void writeByte(byte data) throws IOException {
        sp.getOutputStream().write(data);
        sp.getOutputStream().flush();
    }


    public void addDataListener(SerialDataListener listener) {
        this.dataListener = listener;
        
        sp.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {

                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }
            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;
                

                byte[] newData = new byte[sp.bytesAvailable()];
                int numRead = sp.readBytes(newData, newData.length);
                

                buffer.append(new String(newData, 0, numRead));
                

                int newlineIndex;
                while ((newlineIndex = findNewline(buffer)) != -1) {

                    String line = buffer.substring(0, newlineIndex).trim();

                    removeLine(buffer, newlineIndex);

                    if (dataListener != null && !line.isEmpty()) {
                        dataListener.onDataReceived(line);
                    }
                }
            }
        });
    }
    

    private int findNewline(StringBuilder sb) {
        int idx = sb.indexOf("\n");
        if (idx == -1) {
            return -1;
        }

        if (idx > 0 && sb.charAt(idx - 1) == '\r') {
            idx--; // ne întoarcem peste \r
        }
        return idx;
    }


    private void removeLine(StringBuilder sb, int indexEndLine) {

        if (indexEndLine < sb.length() && sb.charAt(indexEndLine) == '\r') {
            indexEndLine++;
        }
        if (indexEndLine < sb.length() && sb.charAt(indexEndLine) == '\n') {
            indexEndLine++;
        }
        sb.delete(0, indexEndLine);
    }
}
