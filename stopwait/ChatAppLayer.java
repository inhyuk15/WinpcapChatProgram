

import java.util.ArrayList;

public class ChatAppLayer implements BaseLayer {
    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    _CHAT_APP m_sHeader;

    private byte[] fragBytes;
    private int fragCount = 0;
    private ArrayList<Boolean> ackChk = new ArrayList<Boolean>();

    private class _CHAT_APP {
        byte[] capp_totlen;
        byte capp_type;
        byte capp_unused;
        byte[] capp_data;

        public _CHAT_APP() {
            this.capp_totlen = new byte[2];
            this.capp_type = 0x00;
            this.capp_unused = 0x00;
            this.capp_data = null;
        }
    }

    public ChatAppLayer(String pName) {
        // super(pName);
        pLayerName = pName;
        ResetHeader();
        ackChk.add(true);
    }

    private void ResetHeader() {
        m_sHeader = new _CHAT_APP();
    }

    private byte[] objToByte(_CHAT_APP Header, byte[] input, int length) {
        byte[] buf = new byte[length + 4];

        buf[0] = Header.capp_totlen[0];
        buf[1] = Header.capp_totlen[1];
        buf[2] = Header.capp_type;
        buf[3] = Header.capp_unused;

        if (length >= 0) System.arraycopy(input, 0, buf, 4, length);

        return buf;
    }

    public byte[] RemoveCappHeader(byte[] input, int length) {
        byte[] cpyInput = new byte[length - 4];
        System.arraycopy(input, 4, cpyInput, 0, length - 4);
        input = cpyInput;
        return input;
    }

    private void waitACK() { //ACK 체크
        while (ackChk.size() <= 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        ackChk.remove(0);
    }

    private void fragSend(byte[] input, int length) {
        byte[] bytes = new byte[10];
        int i = 0;
        m_sHeader.capp_totlen = intToByte2(length);
        m_sHeader.capp_type = (byte) (0x01);

        // 첫번째 전송
        System.arraycopy(input, 0, bytes, 0, 10);
        bytes = objToByte(m_sHeader, bytes, 10);
        this.GetUnderLayer().Send(bytes, bytes.length);

        int maxLen = length / 10;
        // 중간 전송
        for(i = 0; i < maxLen -2; i++) {
 
        	System.arraycopy(input, 10 *(i+1), bytes, 0, 10);
        	m_sHeader.capp_type = (byte) (0x02);
        	bytes = objToByte(m_sHeader, bytes, 10);
    		this.GetUnderLayer().Send(bytes, bytes.length);
           	waitACK();
        }
        
        // 마지막 전송
        if (length % 10 != 0) {

        	System.arraycopy(input, 10 *(maxLen-1), bytes, 0, length % 10);
            m_sHeader.capp_type = (byte) (0x03);
        	bytes = objToByte(m_sHeader, bytes, length % 10);
    		this.GetUnderLayer().Send(bytes, bytes.length);
        	waitACK();
        }
        else {
        	
        	System.arraycopy(input, 10 *(maxLen-1), bytes, 0, 10);
            m_sHeader.capp_type = (byte) (0x03);
        	bytes = objToByte(m_sHeader, bytes, 10);
    		this.GetUnderLayer().Send(bytes, bytes.length);
    		waitACK();
        }
    }
 

    public boolean Send(byte[] input, int length) {
        byte[] bytes;
        m_sHeader.capp_totlen = intToByte2(length);
        m_sHeader.capp_type = (byte) (0x00);
 
        try {
	        if(length > 10) {
	        	fragSend(input, length);
	        }
	        else {
	    	    // 크기가 작으면 그냥 통째로 보냄
	        	m_sHeader.capp_type = (byte) (0x00);
	        	bytes = objToByte(m_sHeader, input, length);
	    		this.GetUnderLayer().Send(bytes, length +4);
	    		waitACK();
	        }
        }
		catch(Exception e) {
			e.printStackTrace();
		}
        return true;
    }
    
    public synchronized boolean Receive(byte[] input) {
        byte[] data, tempBytes;
        int tempType = 0;

        if (input == null) {
        	ackChk.add(true);
        	return true;
        }
        
        tempType |= (byte) (input[2] & 0xFF);
        int length = byte2ToInt(input[0], input[1]);
        
        if(tempType == 0) {
        	ackChk.add(true);
    		data = RemoveCappHeader(input, input.length);
    		this.GetUpperLayer(0).Receive(data);
        }
        else{
        	tempBytes = RemoveCappHeader(input, input.length);
        	if(tempType == 0x01) {
    			fragCount = 1;
    			fragBytes = tempBytes;
        	}
        	else if(tempType == 0x02) {
        		byte[] nextBuffer = new byte[fragCount *10 +10];
        		System.arraycopy(fragBytes, 0, nextBuffer, 0, fragCount *10);
        		System.arraycopy(tempBytes, 0, nextBuffer, fragCount *10, 10);
        		fragCount ++;
        		fragBytes = nextBuffer;
        	}
        	else if(tempType == 0x03) {
        		if(length % 10 != 0) {
	        		byte[] nextBuffer = new byte[length % 10 + 1 + fragCount *10];
	        		System.arraycopy(fragBytes, 0, nextBuffer, 0, fragCount *10);
	        		System.arraycopy(tempBytes, 0, nextBuffer, fragCount *10, length % 10);
	        		
	        		fragBytes = nextBuffer;
        		}
        		else {
	        		byte[] nextBuffer = new byte[(fragCount + 1) * 10 +1];
	        		System.arraycopy(fragBytes, 0, nextBuffer, 0, fragCount *10);
	        		System.arraycopy(tempBytes, 0, nextBuffer, fragCount *10, 10);
	        		
	        		fragBytes = nextBuffer;
        		}
        		
        		data = fragBytes;
        		this.GetUpperLayer(0).Receive(data);
    		}
        }
        this.GetUnderLayer().Send(null, 0); // ack 송신
        return true;
    }
    
    private byte[] intToByte2(int value) {
        byte[] temp = new byte[2];
        temp[0] |= (byte) ((value & 0xFF00) >> 8);
        temp[1] |= (byte) (value & 0xFF);

        return temp;
    }

    private int byte2ToInt(byte value1, byte value2) {
        return (int)((value1 << 8) | (value2));
    }

    @Override
    public String GetLayerName() {
        return pLayerName;
    }

    @Override
    public BaseLayer GetUnderLayer() {
        if (p_UnderLayer == null)
            return null;
        return p_UnderLayer;
    }

    @Override
    public BaseLayer GetUpperLayer(int nindex) {
        if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
            return null;
        return p_aUpperLayer.get(nindex);
    }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) {
        if (pUnderLayer == null)
            return;
        this.p_UnderLayer = pUnderLayer;
    }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) {
        if (pUpperLayer == null)
            return;
        this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
    }

    @Override
    public void SetUpperUnderLayer(BaseLayer pUULayer) {
        this.SetUpperLayer(pUULayer);
        pUULayer.SetUnderLayer(this);
    }
}
