
import java.util.ArrayList;

public class EthernetLayer implements BaseLayer {

	public int nUpperLayerCount = 0;
	public String pLayerName = null;
	public BaseLayer p_UnderLayer = null;
	public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
	_ETHERNET_Frame m_sHeader;
	
	public EthernetLayer(String pName) {
		// super(pName);
		pLayerName = pName;
		ResetHeader();
	}
	
	public void ResetHeader() {
		m_sHeader = new _ETHERNET_Frame();
	}
	
    private class _ETHERNET_ADDR {
        private byte[] addr = new byte[6];

        public _ETHERNET_ADDR() {
            this.addr[0] = (byte) 0x00;
            this.addr[1] = (byte) 0x00;
            this.addr[2] = (byte) 0x00;
            this.addr[3] = (byte) 0x00;
            this.addr[4] = (byte) 0x00;
            this.addr[5] = (byte) 0x00;

        }
    }
    
    private class _ETHERNET_Frame {
        _ETHERNET_ADDR enet_dstaddr;
        _ETHERNET_ADDR enet_srcaddr;
        byte[] enet_type;
        byte[] enet_data;

        public _ETHERNET_Frame() {
            this.enet_dstaddr = new _ETHERNET_ADDR();
            this.enet_srcaddr = new _ETHERNET_ADDR();
            this.enet_type = new byte[2];
            this.enet_data = null;
        }
    }
    
    public byte[] ObjToByte(_ETHERNET_Frame Header, byte[] input, int length) {
		byte[] buf = new byte[length + 14];
		for(int i = 0; i < 6; i++) {
			buf[i] = Header.enet_dstaddr.addr[i];
			buf[i+6] = Header.enet_srcaddr.addr[i];
		}			
		buf[12] = Header.enet_type[0];
		buf[13] = Header.enet_type[1];
		for (int i = 0; i < length; i++)
			buf[14 + i] = input[i];

		return buf;
	}

	public boolean Send(byte[] input, int length) {
		if (input == null && length == 0) // ack
			m_sHeader.enet_type = intToByte2(2);
		else if (isBroadcast(m_sHeader.enet_dstaddr.addr)) // broadcast
			m_sHeader.enet_type = intToByte2(0xff);
		else // nomal
			m_sHeader.enet_type = intToByte2(1);

		byte[] bytes = ObjToByte(m_sHeader, input, length);
		this.GetUnderLayer().Send(bytes, bytes.length);

		return true;
	}

	public byte[] RemoveEthernetHeader(byte[] input, int length) {
		byte[] cpyInput = new byte[length - 14];
		System.arraycopy(input, 14, cpyInput, 0, length - 14);
		input = cpyInput;
		return input;
	}

	public synchronized boolean Receive(byte[] input) {
		byte[] data;
		byte[] temp_src = m_sHeader.enet_srcaddr.addr;
		int temp_type = byte2ToInt(input[12], input[13]); 
		
		if(temp_type == 0x01) {
			data = RemoveEthernetHeader(input, input.length);
			this.GetUpperLayer(0).Receive(data);
			return true;
		}
		boolean MyPacket, Broadcast;
		MyPacket = isMyPacket(input);
		data = RemoveEthernetHeader(input, input.length);
		//for(int i =0; i <data.length; i++)
		//	System.out.print(Character.toChars(data[i]));
		
		if(MyPacket == true) {
			return false;
		}
		else {
			Broadcast = isBroadcast(input);
			if(Broadcast == false) {
				return false;
			}
		}
		
		data = RemoveEthernetHeader(input, input.length);
		this.GetUpperLayer(0).Receive(data);
		System.out.println("data entered in ethernetlayer");
		//for(int i =0; i <data.length; i++)
		//	System.out.print(data[i]);
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
	
	private boolean isBroadcast(byte[] bytes) {
		for(int i = 0; i< 6; i++)
			if (bytes[i] != (byte) 0xff)
				return false;
		return (bytes[12] == (byte) 0xff && bytes[13] == (byte) 0xff);
	}

	private boolean isMyPacket(byte[] input){
		for(int i = 0; i < 6; i++)
			if(m_sHeader.enet_srcaddr.addr[i] != input[6 + i])
				return false;
		return true;
	}

	private boolean chkAddr(byte[] input) {
		byte[] temp = m_sHeader.enet_srcaddr.addr;
		for(int i = 0; i< 6; i++)
			if(m_sHeader.enet_srcaddr.addr[i] != input[i])
				return false;
		return true;
	}
	
	public void SetEnetSrcAddress(byte[] srcAddress) {
		m_sHeader.enet_srcaddr.addr = srcAddress;
	}

	public void SetEnetDstAddress(byte[] dstAddress) {
		m_sHeader.enet_dstaddr.addr = dstAddress;
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
