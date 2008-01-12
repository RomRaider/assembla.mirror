/*
 *
 * Enginuity Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006 Enginuity.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package enginuity.logger.ecu.comms.io.connection;

import java.util.Collection;
import enginuity.io.connection.ConnectionProperties;
import enginuity.io.connection.SerialConnection;
import enginuity.io.connection.SerialConnectionImpl;
import enginuity.logger.ecu.comms.io.protocol.LoggerProtocol;
import enginuity.logger.ecu.comms.io.protocol.SSMLoggerProtocol;
import enginuity.logger.ecu.comms.query.EcuQuery;
import enginuity.logger.ecu.exception.SerialCommunicationException;
import static enginuity.util.HexUtil.asHex;
import static enginuity.util.ParamChecker.checkNotNull;
import static enginuity.util.ParamChecker.checkNotNullOrEmpty;
import static enginuity.util.ThreadUtil.sleep;
import org.apache.log4j.Logger;

public final class SSMLoggerConnection implements LoggerConnection {
    private static final Logger LOGGER = Logger.getLogger(SSMLoggerConnection.class);
    private LoggerProtocol protocol;
    private SerialConnection serialConnection;

    public SSMLoggerConnection(String portName, ConnectionProperties connectionProperties) {
        checkNotNullOrEmpty(portName, "portName");
        checkNotNull(connectionProperties);
        protocol = new SSMLoggerProtocol();

        // Use TestSSMConnectionImpl for testing!!
        serialConnection = new SerialConnectionImpl(connectionProperties, portName);
//        serialConnection = new TestSSMConnectionImpl(connectionProperties, portName);
    }

    public void sendAddressReads(Collection<EcuQuery> queries) {
        try {
            byte[] request = protocol.constructReadAddressRequest(queries);
            byte[] response = protocol.constructReadAddressResponse(queries);

            serialConnection.readStaleData();
            serialConnection.write(request);
            int timeout = 1000;
            while (serialConnection.available() < response.length) {
                sleep(1);
                timeout -= 1;
                if (timeout <= 0) {
                    byte[] badBytes = new byte[serialConnection.available()];
                    serialConnection.read(badBytes);
                    LOGGER.debug("Bad response (read timeout): " + asHex(badBytes));
                    break;
                }
            }
            serialConnection.read(response);

            byte[] processedResponse = protocol.preprocessResponse(request, response);

//            LOGGER.trace("ECU Request  ---> " + asHex(request));
//            LOGGER.trace("ECU Response <--- " + asHex(processedResponse));

            protocol.processReadAddressResponses(queries, processedResponse);
        } catch (Exception e) {
            close();
            throw new SerialCommunicationException(e);
        }
    }

    public void close() {
        serialConnection.close();
    }

}
