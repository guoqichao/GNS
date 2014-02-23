package edu.umass.cs.gns.nameserver;

import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.packet.Transport;
import edu.umass.cs.gns.util.ConfigFileInfo;
import org.json.JSONObject;

/**
 * This class listens on a UDP port for messages from local name server.
 * It expects a single UDP packet to contain a well-formatted JSON object.
 * Therefore, it must be used only for small packets < 1500 bytes which
 * can reach the name server without getting fragmented.
 *
 * @author abhigyan
 * 
 */
public class NSListenerUDP extends Thread {

  public static Transport udpTransport;


  public NSListenerUDP() {
    GNS.getLogger().info("NS Node " + NameServer.nodeID + " starting NSListenerUDP on port " +
            ConfigFileInfo.getNSUdpPort(NameServer.nodeID));
    udpTransport = new Transport(NameServer.nodeID,
            ConfigFileInfo.getNSUdpPort(NameServer.nodeID));
  }

  @Override
  public void run() {
    while (true) {
      try {
        JSONObject incomingJSON = udpTransport.readPacket();
        NameServer.nsDemultiplexer.handleJSONObject(incomingJSON);
      } catch (Exception e) {
        GNS.getLogger().fine("Exception in thread: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }
}
