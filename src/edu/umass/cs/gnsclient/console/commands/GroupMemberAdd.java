
package edu.umass.cs.gnsclient.console.commands;

import edu.umass.cs.gnsclient.client.GNSClientCommands;
import edu.umass.cs.gnsclient.console.ConsoleModule;
import edu.umass.cs.gnscommon.utils.StringUtil;

import java.util.StringTokenizer;


public class GroupMemberAdd extends ConsoleCommand
{


  public GroupMemberAdd(ConsoleModule module)
  {
    super(module);
  }

  @Override
  public String getCommandDescription()
  {
    return "Add a new member to a group GUID (the current GUID/alias must have permissions to change group membership in the group GUID)";
  }

  @Override
  public String getCommandName()
  {
    return "group_member_add";
  }

  @Override
  public String getCommandParameters()
  {
    return "[group_guid_or_alias] guid_to_add";
  }


  @Override
  public void execute(String commandText) throws Exception
  {
    if (!module.isCurrentGuidSetAndVerified())
    {
      return;
    }
    super.execute(commandText);
  }

  @Override
  public void parse(String commandText) throws Exception
  {
    GNSClientCommands gnsClient = module.getGnsClient();
    try
    {
      StringTokenizer st = new StringTokenizer(commandText.trim());
      String groupGuid;
      if (st.countTokens() == 1)
      {
        groupGuid = module.getCurrentGuid().getGuid();
      }
      else if (st.countTokens() == 2)
      {
        groupGuid = st.nextToken();
        if (!StringUtil.isValidGuidString(groupGuid))
        {
          // We probably have an alias, lookup the GUID
          groupGuid = gnsClient.lookupGuid(groupGuid);
        }
      }
      else
      {
        console.printString("Wrong number of arguments for this command.\n");
        return;
      }
      String guidToAdd = st.nextToken();

      gnsClient.groupAddGuid(groupGuid, guidToAdd, module.getCurrentGuid());
      printString("GUID " + guidToAdd + " added to group " + groupGuid + "\n");
    }
    catch (Exception e)
    {
      printString("Failed to access GNS ( " + e + ")\n");
    }
  }
}
