
package edu.umass.cs.gnsserver.gnsapp;

import edu.umass.cs.gnscommon.exceptions.server.FailedDBOperationException;
import edu.umass.cs.gnscommon.exceptions.server.FieldNotFoundException;
import edu.umass.cs.gnsserver.database.AbstractRecordCursor;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.AccountAccess;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.GuidInfo;
import edu.umass.cs.gnsserver.gnsapp.deprecated.GNSApplicationInterface;
import edu.umass.cs.gnsserver.gnsapp.packet.Packet;
import edu.umass.cs.gnsserver.gnsapp.packet.admin.AdminRequestPacket;
import edu.umass.cs.gnsserver.gnsapp.packet.admin.DumpRequestPacket;
import edu.umass.cs.gnsserver.gnsapp.recordmap.NameRecord;
import edu.umass.cs.gnsserver.main.GNSConfig;
import edu.umass.cs.gnsserver.nodeconfig.GNSNodeConfig;
import edu.umass.cs.gnsserver.utils.Shutdownable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.ParseException;
import java.util.logging.Level;


@SuppressWarnings("unchecked")
public class AppAdmin extends Thread implements Shutdownable {


  private ServerSocket serverSocket;

  private final GNSApplicationInterface<String> app;

  private final GNSNodeConfig<String> gnsNodeConfig;


  public AppAdmin(GNSApplicationInterface<String> app, GNSNodeConfig<String> gnsNodeConfig) {
    super("NSListenerAdmin");
    this.app = app;
    this.gnsNodeConfig = gnsNodeConfig;
    try {
      this.serverSocket = new ServerSocket(gnsNodeConfig.getAdminPort(app.getNodeID()));
    } catch (IOException e) {
      GNSConfig.getLogger().log(Level.SEVERE,
              "Unable to create NSListenerAdmin server on port {0}: {1}",
              new Object[]{gnsNodeConfig.getAdminPort(app.getNodeID()), e});
    }
  }


  @Override
  public void run() {
    int numRequest = 0;
    GNSConfig.getLogger().log(Level.INFO,
            "NS Node {0} starting Admin Request Server on port {1}",
            new Object[]{app.getNodeID(), serverSocket.getLocalPort()});
    while (true) {
      try {
        //Read the packet from the input stream
        try (Socket socket = serverSocket.accept()) {
          //Read the packet from the input stream
          JSONObject incomingJSON = Packet.getJSONObjectFrame(socket);
          switch (Packet.getPacketType(incomingJSON)) {

            case DUMP_REQUEST:

              DumpRequestPacket<String> dumpRequestPacket
                      = new DumpRequestPacket<>(incomingJSON, gnsNodeConfig);

              dumpRequestPacket.setPrimaryNameServer(app.getNodeID());
              JSONArray jsonArray = new JSONArray();
              // if there is an argument it is a TAGNAME we return all the records that have that tag
              if (dumpRequestPacket.getArgument() != null) {
                String tag = dumpRequestPacket.getArgument();
                AbstractRecordCursor cursor = NameRecord.getAllRowsIterator(app.getDB());
                while (cursor.hasNext()) {
                  NameRecord nameRecord = null;
                  JSONObject json = cursor.nextJSONObject();
                  try {
                    nameRecord = new NameRecord(app.getDB(), json);
                  } catch (JSONException e) {
                    GNSConfig.getLogger().log(Level.SEVERE,
                            "Problem parsing json into NameRecord: {0} JSON is {1}",
                            new Object[]{e, json.toString()});
                  }
                  if (nameRecord != null) {
                    try {
                      if (nameRecord.containsUserKey(AccountAccess.GUID_INFO)) {
                        GuidInfo userInfo = new GuidInfo(nameRecord.getValuesMap().getJSONObject(AccountAccess.GUID_INFO));
                        //GuidInfo userInfo = new GuidInfo(nameRecord.getUserKeyAsArray(AccountAccess.GUID_INFO).toResultValueString());
                        if (userInfo.containsTag(tag)) {
                          jsonArray.put(nameRecord.toJSONObject());
                        }
                      }
                    } catch (FieldNotFoundException e) {
                      GNSConfig.getLogger().log(Level.SEVERE,
                              "FieldNotFoundException. Field Name =  {0}", e.getMessage());
                      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                  }
                }
                // OTHERWISE WE RETURN ALL THE RECORD
              } else {
                //for (NameRecord nameRecord : NameServer.getAllNameRecords()) {
                AbstractRecordCursor cursor = NameRecord.getAllRowsIterator(app.getDB());
                while (cursor.hasNext()) {
                  NameRecord nameRecord = null;
                  JSONObject json = cursor.nextJSONObject();
                  try {
                    nameRecord = new NameRecord(app.getDB(), json);
                  } catch (JSONException e) {
                    GNSConfig.getLogger().log(Level.SEVERE,
                            "Problem parsing record cursor into NameRecord: {0} JSON is {1}",
                            new Object[]{e, json.toString()});
                  }
                  if (nameRecord != null) {
                    jsonArray.put(nameRecord.toJSONObject());
                  }
                }
              }
              GNSConfig.getLogger().log(Level.FINER,
                      "NSListenrAdmin for {0} is {1}",
                      new Object[]{app.getNodeID(), jsonArray.toString()});

              dumpRequestPacket.setJsonArray(jsonArray);
              Packet.sendTCPPacket(dumpRequestPacket.toJSONObject(),
                      dumpRequestPacket.getReturnAddress());

              GNSConfig.getLogger().log(Level.FINE,
                      "NSListenrAdmin: Response to id:{0} --> {1}",
                      new Object[]{dumpRequestPacket.getId(), dumpRequestPacket.toString()});
              break;
            case ADMIN_REQUEST:
              AdminRequestPacket adminRequestPacket = new AdminRequestPacket(incomingJSON);
              switch (adminRequestPacket.getOperation()) {
                case DELETEALLRECORDS:
                  GNSConfig.getLogger().log(Level.FINE,
                          "NSListenerAdmin ({0}) : Handling DELETEALLRECORDS request",
                          app.getNodeID());
                  long startTime = System.currentTimeMillis();
                  int cnt = 0;
                  AbstractRecordCursor cursor = NameRecord.getAllRowsIterator(app.getDB());
                  while (cursor.hasNext()) {
                    NameRecord nameRecord = new NameRecord(app.getDB(), cursor.nextJSONObject());
                    //for (NameRecord nameRecord : NameServer.getAllNameRecords()) {
                    try {
                      NameRecord.removeNameRecord(app.getDB(), nameRecord.getName());
                    } catch (FieldNotFoundException e) {
                      GNSConfig.getLogger().log(Level.SEVERE,
                              "FieldNotFoundException. Field Name =  {0}",
                              e.getMessage());
                      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    //DBNameRecord.removeNameRecord(nameRecord.getName());
                    cnt++;
                  }
                  GNSConfig.getLogger().log(Level.FINE,
                          "NSListenerAdmin ({0}) : Deleting {1} records took {2}ms",
                          new Object[]{app.getNodeID(), cnt, System.currentTimeMillis() - startTime});
                  break;
                case CHANGELOGLEVEL:
                  Level level = Level.parse(adminRequestPacket.getArgument());
                  GNSConfig.getLogger().log(Level.INFO,
                          "Changing log level to {0}",
                          level.getName());
                  GNSConfig.getLogger().setLevel(level);
                  break;
                case CLEARCACHE:
                  GNSConfig.getLogger().log(Level.WARNING,
                          "NSListenerAdmin ({0}) : Ignoring CLEARCACHE request", app.getNodeID());
                  break;
                case DUMPCACHE:
                  GNSConfig.getLogger().log(Level.WARNING,
                          "NSListenerAdmin ({0}) : Ignoring DUMPCACHE request", app.getNodeID());
                  break;

              }
              break;
          }
        }
      } catch (IOException | JSONException | FailedDBOperationException | ParseException | IllegalArgumentException | SecurityException e) {
        if (serverSocket.isClosed()) {
          GNSConfig.getLogger().warning("NS Admin shutting down.");
          return; // close this thread
        }
        e.printStackTrace();
      }
    }
  }


  @Override
  public void shutdown() {
    try {
      this.serverSocket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
