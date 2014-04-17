package edu.umass.cs.gns.nsdesign.replicaController;

import edu.umass.cs.gns.database.BasicRecordCursor;
import edu.umass.cs.gns.database.ColumnField;
import edu.umass.cs.gns.exceptions.FailedUpdateException;
import edu.umass.cs.gns.exceptions.FieldNotFoundException;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nsdesign.replicationframework.ReplicationFrameworkType;
import edu.umass.cs.gns.nsdesign.recordmap.ReplicaControllerRecord;
import edu.umass.cs.gns.nsdesign.Config;
import edu.umass.cs.gns.nsdesign.packet.NewActiveProposalPacket;
import edu.umass.cs.gns.nsdesign.replicationframework.BeehiveReplication;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TimerTask;


/**
 *
 * For all names for which this node is a primary name server, this class periodically checks if the set of
 * active replicas needs to be changed depending on several factors: (1) rate of lookups (2) rates of updates
 * (3) which local name servers are receiving requests for a name.
 *
 * If it determines that the set of actives need to change, it starts the process by proposing a new set of
 * actives to other replica controllers.  Once replica controllers (aka primaries) have agreed upon a new set of
 * actives, they make this change in the database and start the process of informing old and new set of actives.
 *
 * For each name there will be multiple nodes who will be replica controllers. However, only one of these
 * nodes initiates the process of changing the set of active replica. Which node will do so is determined by:
 * <code>isSmallestNodeRunning</code> in <code>ReplicaController</code>.
 *
 * This class is implemented as a timer task which is periodically executed (once every <code>analysisInterval</code>.
 *
 * Note: 'primaries' and 'replica controllers' refer to same things in the code.
 * Note: we refer to the process of changing the set of active replicas as 'group change' at some places.
 *
 * If we start group change for a large number of names in a short interval, a large fraction of the node's resources
 * might be used in doing group change, which leaves less resources for actually executing client requests.
 * To limit the resource used in doing group changes, we sleep for a small interval (e.g. 10 ms) between successive
 * group change events.
 *
 * @author abhigyan
 */
public class ComputeNewActivesTask extends TimerTask {

  private static ArrayList<ColumnField> computeNewActivesFields = new ArrayList<ColumnField>();
  static {
    computeNewActivesFields.add(ReplicaControllerRecord.MARKED_FOR_REMOVAL);
    computeNewActivesFields.add(ReplicaControllerRecord.PRIMARY_NAMESERVERS);
    computeNewActivesFields.add(ReplicaControllerRecord.ACTIVE_NAMESERVERS);

    computeNewActivesFields.add(ReplicaControllerRecord.PREV_TOTAL_READ);
    computeNewActivesFields.add(ReplicaControllerRecord.PREV_TOTAL_WRITE);
    computeNewActivesFields.add(ReplicaControllerRecord.MOV_AVG_READ);
    computeNewActivesFields.add(ReplicaControllerRecord.MOV_AVG_WRITE);
    computeNewActivesFields.add(ReplicaControllerRecord.VOTES_MAP);
  }


  private static int replicationRound = 0;

  ReplicaController replicaController;

  public ComputeNewActivesTask(ReplicaController replicaController) {
    this.replicaController = replicaController;
  }

  @Override
  public void run() {

    replicationRound++;

    GNS.getLogger().severe("ComputeNewActives started: " + replicationRound);

    int numNamesRead = 0; // number of names read from db
    int numGroupChanges = 0;  // number of names for which group changes is started.

    try {
      GNS.getLogger().info("ComputeNewActives before getting iterator ... ");
      BasicRecordCursor iterator = replicaController.getDB().getIterator(ReplicaControllerRecord.NAME, computeNewActivesFields);
      GNS.getLogger().info("ComputeNewActives started iterating. ");
      long t0 = System.currentTimeMillis();
      while (iterator.hasNext()) {
        numNamesRead += 1;
        if (numNamesRead % 10000 == 0) {
          GNS.getLogger().info("ComputeNewActives iterated over " + numNamesRead + " names.");
        }

        HashMap<ColumnField, Object> hashMap = iterator.nextHashMap();
        ReplicaControllerRecord rcRecord = new ReplicaControllerRecord(replicaController.getDB(), hashMap);

        if (Config.debugMode) {
        GNS.getLogger().fine("\tComputeNewActivesConsidering\t" + rcRecord.getName() + "\tCount\t" + numNamesRead +
                  "\tRound\t" + replicationRound);
        }
        if (rcRecord.isMarkedForRemoval() || !rcRecord.getPrimaryNameservers().contains(replicaController.getNodeID())
                || !replicaController.isSmallestNodeRunning(rcRecord.getName(), rcRecord.getPrimaryNameservers())) {
          rcRecord.recomputeAverageReadWriteRate(); // this will keep moving average calculation updated.
          continue;
        }

        GNS.getLogger().fine("I will select new actives for name = " + rcRecord.getName());
        Set<Integer> newActiveNameServers = getNewActiveNameServers(rcRecord, rcRecord.getActiveNameservers(), replicationRound);
        if (isActiveSetModified(rcRecord.getActiveNameservers(), newActiveNameServers)) {
          numGroupChanges += 1;
          GNS.getLogger().fine("\tComputeNewActives\t" + rcRecord.getName() + "\tCount\t" + numNamesRead +
                  "\tRound\t" + replicationRound + "\tUpdatingOtherActives");

          int newActiveVersion = replicaController.getNewActiveVersion(rcRecord.getActiveVersion());
          NewActiveProposalPacket activePropose = new NewActiveProposalPacket(rcRecord.getName(), replicaController.getNodeID(),
                  newActiveNameServers, newActiveVersion);
          // to propose request for coordination we send it to ourselves, so that coordinator
          replicaController.getNioServer().sendToID(replicaController.getNodeID(), activePropose.toJSONObject());
//          if (replicaController.getRcCoordinator() != null) {
//            GroupChange.executeNewActivesProposed(activePropose, replicaController);
//          } else {
//            int x = replicaController.getRcCoordinator().coordinateRequest(activePropose.toJSONObject());
//            GNS.getLogger().fine("Coordination PROPOSAL: Proposal done. Response: " + x);
//          }
          try {
            Thread.sleep(5); // sleep between successive names so we do not start a large number of group changes
            // at the same time and a large fraction of the system resources are used in just doing group changes.
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        } else {
          if (Config.debugMode) {
            GNS.getLogger().fine("Old and new active name servers are same. No Operation.");
          }
        }
      }
      GNS.getLogger().info(" Compute New Actives Summary. Total Names = " + numGroupChanges +
              " Group Change Names = " + numGroupChanges + "\tDuration = " + (System.currentTimeMillis() - t0));
    } catch (FieldNotFoundException e) {
      GNS.getLogger().severe("Field Not Found Exception: " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      GNS.getLogger().severe("Exception Exception Exception " + e.getMessage());
      e.printStackTrace();
    }
  }


  /************************ Methods below are private methods ************************/

  /**
   * Returns true if the set of new actives is identical to the set of old actives. False otherwise.
   */
  private boolean isActiveSetModified(Set<Integer> oldActives, Set<Integer> newActives) {
    if (oldActives.size() != newActives.size()) {
      return true;
    }
    for (int x : oldActives) {
      if (!newActives.contains(x)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Calculates new set of active name servers for a name depending on replication framework.
   * @param rcRecord ReplicaControllerRecord for the name
   * @param oldActiveNameServers previous set of active name servers
   * @param count number of times group changes have been done
   * @return set of active replicas for this name
   */
  private Set<Integer> getNewActiveNameServers(ReplicaControllerRecord rcRecord, Set<Integer> oldActiveNameServers,
                                               int count) throws FieldNotFoundException, FailedUpdateException {

    Set<Integer> newActiveNameServers;

    int numReplica = numberOfReplica(rcRecord);

    // used for beehive.
    if (Config.replicationFramework == ReplicationFrameworkType.BEEHIVE) {
      numReplica = BeehiveReplication.numActiveNameServers(rcRecord.getName()) - 3;
    }

    //Get a new set of active name servers for this record
    newActiveNameServers = replicaController.getReplicationFramework().newActiveReplica(replicaController, rcRecord, numReplica, count);

    GNS.getStatLogger().info("ComputeNewActives: Round:" + count + " Name:" + rcRecord.getName()
            + " OldActive:" + oldActiveNameServers.toString() + " NumberReplica:" + numReplica
            + " NewReplica:" + newActiveNameServers.toString());
    return newActiveNameServers;
  }

  /**
   * ***********************************************************
   * Returns the size of active replica set that should exist for this name record
   * depending on the lookup and update rate of this name record.
   *
   * @param rcRecord ReplicaControllerRecord for this name
   ***********************************************************
   */
  private int numberOfReplica(ReplicaControllerRecord rcRecord) throws FieldNotFoundException, FailedUpdateException {
    double[] readWrites = rcRecord.recomputeAverageReadWriteRate();
    double lookup = readWrites[0];
    double update = readWrites[1];

    int replicaCount;
    if (update == 0 && lookup == 0) {
      // no requests seen, replicate at minimum number of locations.
      replicaCount = Config.minReplica;
    } else if (update == 0) {
      // no updates, replicate everywhere.
      replicaCount = replicaController.getGnsNodeConfig().getNameServerIDs().size();
    } else {
      replicaCount = StrictMath.round(StrictMath.round(
              (lookup / (update * Config.normalizingConstant) + Config.minReplica)));

      if (replicaCount > replicaController.getGnsNodeConfig().getNameServerIDs().size()) {
        replicaCount = replicaController.getGnsNodeConfig().getNameServerIDs().size();
      }
    }

    // put in here for DNS experiments.
    if (replicaCount > Config.maxReplica) {
      replicaCount = Config.maxReplica;
    }

    GNS.getStatLogger().info("\tComputeNewActives-ReplicaCount\tName\t"
            + rcRecord.getName() + "\tLookup\t" + lookup + "\tUpdate\t" + update
            + "\tReplicaCount\t" + replicaCount);

    return replicaCount;
  }



}