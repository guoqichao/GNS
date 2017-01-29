
package edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.data;

import edu.umass.cs.gnscommon.CommandType;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.UpdateOperation;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.CommandModule;


public class CreateIndex extends AbstractUpdate {


  public CreateIndex(CommandModule module) {
    super(module);
  }
  

  @Override
  public CommandType getCommandType() {
    return CommandType.CreateIndex;
  }


  @Override
  public UpdateOperation getUpdateOperation() {
    return UpdateOperation.CREATE_INDEX;
  }

}
