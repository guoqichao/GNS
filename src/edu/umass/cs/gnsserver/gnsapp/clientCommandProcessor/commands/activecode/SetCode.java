/*
 *
 *  Copyright (c) 2015 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): Westy
 *
 */
package edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.activecode;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.json.JSONException;
import org.json.JSONObject;
import edu.umass.cs.gnscommon.GNSCommandProtocol;
import edu.umass.cs.gnscommon.utils.Format;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.ClientRequestHandlerInterface;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.ActiveCode;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.CommandResponse;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.CommandModule;
import edu.umass.cs.gnscommon.CommandType;
import edu.umass.cs.gnscommon.GNSProtocol;

import edu.umass.cs.gnscommon.ResponseCode;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.AbstractCommand;
import java.text.ParseException;
import java.util.Date;

/**
 * The command to retrieve the active code for the specified GUID and action.
 *
 */
public class SetCode extends AbstractCommand {

  /**
   * Create the set instance.
   *
   * @param module
   */
  public SetCode(CommandModule module) {
    super(module);
  }

  /**
   *
   * @return the command type
   */
  @Override
  public CommandType getCommandType() {
    return CommandType.SetCode;
  }

  @Override
  public CommandResponse execute(JSONObject json,
          ClientRequestHandlerInterface handler) throws InvalidKeyException,
          InvalidKeySpecException, JSONException, NoSuchAlgorithmException,
          SignatureException, ParseException {
    String guid = json.getString(GNSCommandProtocol.GUID);
    String writer = json.getString(GNSCommandProtocol.WRITER);
    String action = json.getString(GNSCommandProtocol.AC_ACTION);
    String code = json.getString(GNSCommandProtocol.AC_CODE);
    String signature = json.getString(GNSCommandProtocol.SIGNATURE);
    String message = json.getString(GNSCommandProtocol.SIGNATUREFULLMESSAGE);
    Date timestamp = json.has(GNSCommandProtocol.TIMESTAMP)
            ? Format.parseDateISO8601UTC(json.getString(GNSCommandProtocol.TIMESTAMP)) : null; // can be null on older client
    ResponseCode response = ActiveCode.setCode(guid, action,
            code, writer, signature, message, timestamp, handler);

    if (response.isExceptionOrError()) {
      return new CommandResponse(response, GNSCommandProtocol.BAD_RESPONSE
              + " " + response.getProtocolCode());
    } else {
      return new CommandResponse(ResponseCode.NO_ERROR, GNSProtocol.OK_RESPONSE.toString());
    }
  }

}
