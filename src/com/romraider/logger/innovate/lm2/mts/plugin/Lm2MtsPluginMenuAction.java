/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2009 RomRaider.com
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
 */

package com.romraider.logger.innovate.lm2.mts.plugin;

import com.romraider.logger.ecu.EcuLogger;
import com.romraider.logger.ecu.external.ExternalDataSource;
import com.romraider.logger.ecu.ui.swing.menubar.action.AbstractAction;
import com.romraider.logger.innovate.generic.mts.io.MTS;
import static com.romraider.logger.innovate.generic.mts.io.MTSFactory.createMTS;
import static javax.swing.JOptionPane.QUESTION_MESSAGE;
import static javax.swing.JOptionPane.showInputDialog;
import java.awt.event.ActionEvent;

public final class Lm2MtsPluginMenuAction extends AbstractAction {
    private final ExternalDataSource dataSource;

    public Lm2MtsPluginMenuAction(EcuLogger logger, ExternalDataSource dataSource) {
        super(logger);
        this.dataSource = dataSource;
    }

    public void actionPerformed(ActionEvent actionEvent) {
        String port = (String) showInputDialog(logger, "Select MTS port:", dataSource.getName() + " Plugin Settings", QUESTION_MESSAGE, null,
                getPorts(), dataSource.getPort());
        if (port != null && port.length() > 0) dataSource.setPort(port);
    }

    private String[] getPorts() {
        MTS mts = createMTS();
        try {
            int portCount = mts.portCount();
            String[] result = new String[portCount];
            for (int i = 0; i < portCount; i++) {
                result[i] = "" + i;
                mts.currentPort(i);
                String name = mts.portName();
                System.out.println("MTS: found port [" + i + "]: " + name);
            }
            return result;
        } finally {
            mts.dispose();
        }
    }
}