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

//DOM XML parser for ROMs

package enginuity.newmaps.definition.translate;

import enginuity.Settings;
import enginuity.maps.DataCell;
import enginuity.maps.Rom;
import enginuity.maps.RomID;
import enginuity.maps.Scale;
import enginuity.maps.Table;
import enginuity.maps.Table1D;
import enginuity.maps.Table2D;
import enginuity.maps.Table3D;
import enginuity.maps.TableSwitch;
import enginuity.util.ObjectCloner;
import static enginuity.xml.DOMHelper.unmarshallAttribute;
import static enginuity.xml.DOMHelper.unmarshallText;
import enginuity.xml.RomAttributeParser;
import enginuity.xml.TableIsOmittedException;
import org.apache.log4j.Logger;
import org.w3c.dom.Node;
import static org.w3c.dom.Node.ELEMENT_NODE;
import org.w3c.dom.NodeList;

import javax.management.modelmbean.XMLParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

public final class FirstGenDefinitionHandler {
    private static final Logger LOGGER = Logger.getLogger(FirstGenDefinitionHandler.class);
    private List<Scale> scales = new ArrayList<Scale>();
    private Settings settings = new Settings();
    private Vector<Rom> roms = new Vector<Rom>();

    public Vector<Rom> unmarshallXMLDefinition(Node rootNode) throws Exception {
        Node n;
        NodeList nodes = rootNode.getChildNodes();

        // unmarshall scales first
        for (int i = 0; i < nodes.getLength(); i++) {
            n = nodes.item(i);

            if (n.getNodeType() == ELEMENT_NODE && n.getNodeName().equalsIgnoreCase("scalingbase")) {
                scales.add(unmarshallScale(n, new Scale()));
            }
        }

        // now unmarshall roms
        for (int i = 0; i < nodes.getLength(); i++) {
            n = nodes.item(i);

            if (n.getNodeType() == ELEMENT_NODE && n.getNodeName().equalsIgnoreCase("rom")) {
                Rom rom = unmarshallRom(n, new Rom());
                add(rom);
                LOGGER.debug("Added " + rom.getRomIDString() + " (#" + roms.size() + ")");
            }
        }

        return roms;
    }

    public static boolean foundMatch(RomID romID, byte[] file) {

        String idString = romID.getInternalIdString();

        // romid is hex string
        if (idString.length() > 2 && idString.substring(0, 2).equalsIgnoreCase("0x")) {

            try {
                // put romid in to byte array to check for match
                idString = idString.substring(2); // remove "0x"
                int[] romIDBytes = new int[idString.length() / 2];

                for (int i = 0; i < romIDBytes.length; i++) {
                    // check to see if each byte matches

                    if ((file[romID.getInternalIdAddress() + i] & 0xff) !=
                            Integer.parseInt(idString.substring(i * 2, i * 2 + 2), 16)) {

                        return false;
                    }
                }
                // if no mismatched bytes found, return true
                return true;
            } catch (Exception ex) {
                // if any exception is encountered, names do not match
                ex.printStackTrace();
                return false;
            }

            // else romid is NOT hex string
        } else {
            try {
                String ecuID = new String(file, romID.getInternalIdAddress(), romID.getInternalIdString().length());
                return foundMatchByString(romID, ecuID);
            } catch (Exception ex) {
                // if any exception is encountered, names do not match
                return false;
            }
        }
    }

    public static boolean foundMatchByString(RomID romID, String ecuID) {

        try {
            if (ecuID.equalsIgnoreCase(romID.getInternalIdString())) {
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            // if any exception is encountered, names do not match
            return false;
        }
    }

    public Rom unmarshallRom(Node rootNode, Rom rom) throws Exception {
        Node n;
        NodeList nodes = rootNode.getChildNodes();

        try {
            if (!unmarshallAttribute(rootNode, "base", "none").equalsIgnoreCase("none")) {
                rom = get(unmarshallAttribute(rootNode, "base", "none"));
                rom.getRomID().setObsolete(false);
                rom.setParent(unmarshallAttribute(rootNode, "base", ""));
            }

            rom.setAbstract(Boolean.parseBoolean(unmarshallAttribute(rootNode, "abstract", "false")));


            for (int i = 0; i < nodes.getLength(); i++) {
                n = nodes.item(i);

                // update progress
                int currProgress = (int) ((double) i / (double) nodes.getLength() * 40);

                if (n.getNodeType() == ELEMENT_NODE) {
                    if (n.getNodeName().equalsIgnoreCase("romid")) {
                        rom.setRomID(unmarshallRomID(n, rom.getRomID()));

                    } else if (n.getNodeName().equalsIgnoreCase("table")) {
                        Table table = null;
                        try {
                            table = rom.getTable(unmarshallAttribute(n, "name", "unknown"));
                        } catch (Exception e) { /* table does not already exist (do nothing) */ }

                        try {
                            table = unmarshallTable(n, table, rom);
                            table.setRom(rom);
                            rom.addTable(table);
                        } catch (TableIsOmittedException ex) {
                            // table is not supported in inherited def (skip)
                            if (table != null) {
                                rom.removeTable(table.getName());
                            }
                        } catch (XMLParseException ex) {
                            ex.printStackTrace();
                        }

                    } else { /* unexpected element in Rom (skip)*/ }
                } else { /* unexpected node-type in Rom (skip)*/ }
            }
            return rom;
        } catch (Exception ex) {
            LOGGER.error("Failed: " + unmarshallAttribute(rootNode, "base", "none"), ex);
        }
        throw new Exception();
    }

    public void add(Rom rom) {
        roms.add(rom);
    }

    public Rom get(String name) throws Exception {
        Iterator it = roms.iterator();
        while (it.hasNext()) {
            Rom rom = (Rom) it.next();
            if (rom.getRomIDString().equalsIgnoreCase(name)) {
                return (Rom) ObjectCloner.deepCopy(rom);
            }
        }
        throw new Exception();
    }

    public RomID unmarshallRomID(Node romIDNode, RomID romID) {
        Node n;
        NodeList nodes = romIDNode.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            n = nodes.item(i);

            if (n.getNodeType() == ELEMENT_NODE) {

                if (n.getNodeName().equalsIgnoreCase("xmlid")) {
                    romID.setXmlid(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("internalidaddress")) {
                    romID.setInternalIdAddress(RomAttributeParser.parseHexString(unmarshallText(n)));

                } else if (n.getNodeName().equalsIgnoreCase("internalidstring")) {
                    romID.setInternalIdString(unmarshallText(n));
                    if (romID.getInternalIdString() == null) {
                        romID.setInternalIdString("");
                    }

                } else if (n.getNodeName().equalsIgnoreCase("caseid")) {
                    romID.setCaseId(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("ecuid")) {
                    romID.setEcuId(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("make")) {
                    romID.setMake(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("market")) {
                    romID.setMarket(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("model")) {
                    romID.setModel(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("submodel")) {
                    romID.setSubModel(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("transmission")) {
                    romID.setTransmission(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("year")) {
                    romID.setYear(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("flashmethod")) {
                    romID.setFlashMethod(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("memmodel")) {
                    romID.setMemModel(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("filesize")) {
                    romID.setFileSize(RomAttributeParser.parseFileSize(unmarshallText(n)));

                } else if (n.getNodeName().equalsIgnoreCase("obsolete")) {
                    romID.setObsolete(Boolean.parseBoolean(unmarshallText(n)));

                } else { /* unexpected element in RomID (skip) */ }
            } else { /* unexpected node-type in RomID (skip) */ }
        }
        return romID;
    }

    private Table unmarshallTable(Node tableNode, Table table, Rom rom) throws Exception {

        if (!unmarshallAttribute(tableNode, "base", "none").equalsIgnoreCase("none")) { // copy base table for inheritance      
            table = (Table) ObjectCloner.deepCopy((Object) rom.getTable(unmarshallAttribute(tableNode, "base", "none")));
        }

        try {
            if (table.getType() < 1) {
            }
        } catch (NullPointerException ex) { // if type is null or less than 0, create new instance (otherwise it is inherited)
            if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("3D")) {
                table = new Table3D(settings);

            } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("2D")) {
                table = new Table2D(settings);

            } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("1D")) {
                table = new Table1D(settings);

            } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("X Axis") ||
                    unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Y Axis")) {
                table = new Table1D(settings);

            } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Static Y Axis") ||
                    unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Static X Axis")) {
                table = new Table1D(settings);
                table.setIsStatic(true);

            } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Switch")) {
                table = new TableSwitch(settings);

            } else {
                throw new XMLParseException("Error loading table " + unmarshallAttribute(tableNode, "name", "unknown name") + " in " + rom.getRomID().getXmlid());
            }
        }

        // unmarshall table attributes                    
        table.setName(unmarshallAttribute(tableNode, "name", table.getName()));
        table.setType(RomAttributeParser.parseTableType(unmarshallAttribute(tableNode, "type", String.valueOf(table.getType()))));
        if (unmarshallAttribute(tableNode, "beforeram", "false").equalsIgnoreCase("true")) {
            table.setBeforeRam(true);
        }

        if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Static X Axis") ||
                unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Static Y Axis")) {
            table.setIsStatic(true);
            ((Table1D) table).setIsAxis(true);
        } else if (unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("X Axis") ||
                unmarshallAttribute(tableNode, "type", "unknown").equalsIgnoreCase("Y Axis")) {
            ((Table1D) table).setIsAxis(true);
        }

        table.setCategory(unmarshallAttribute(tableNode, "category", table.getCategory()));
        table.setStorageType(RomAttributeParser.parseStorageType(unmarshallAttribute(tableNode, "storagetype", String.valueOf(table.getStorageType()))));
        table.setEndian(RomAttributeParser.parseEndian(unmarshallAttribute(tableNode, "endian", String.valueOf(table.getEndian()))));
        table.setStorageAddress(RomAttributeParser.parseHexString(unmarshallAttribute(tableNode, "storageaddress", String.valueOf(table.getStorageAddress()))));
        table.setDescription(unmarshallAttribute(tableNode, "description", table.getDescription()));
        table.setDataSize(unmarshallAttribute(tableNode, "sizey", unmarshallAttribute(tableNode, "sizex", table.getDataSize())));
        table.setFlip(unmarshallAttribute(tableNode, "flipy", unmarshallAttribute(tableNode, "flipx", table.getFlip())));
        table.setUserLevel(unmarshallAttribute(tableNode, "userlevel", table.getUserLevel()));
        table.setLocked(unmarshallAttribute(tableNode, "locked", table.isLocked()));
        table.setLogParam(unmarshallAttribute(tableNode, "logparam", table.getLogParam()));

        if (table.getType() == Table.TABLE_3D) {
            ((Table3D) table).setFlipX(unmarshallAttribute(tableNode, "flipx", ((Table3D) table).getFlipX()));
            ((Table3D) table).setFlipY(unmarshallAttribute(tableNode, "flipy", ((Table3D) table).getFlipY()));
            ((Table3D) table).setSizeX(unmarshallAttribute(tableNode, "sizex", ((Table3D) table).getSizeX()));
            ((Table3D) table).setSizeY(unmarshallAttribute(tableNode, "sizey", ((Table3D) table).getSizeY()));
        }

        Node n;
        NodeList nodes = tableNode.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            n = nodes.item(i);

            if (n.getNodeType() == ELEMENT_NODE) {
                if (n.getNodeName().equalsIgnoreCase("table")) {

                    if (table.getType() == Table.TABLE_2D) { // if table is 2D, parse axis

                        if (RomAttributeParser.parseTableType(unmarshallAttribute(n, "type", "unknown")) == Table.TABLE_Y_AXIS ||
                                RomAttributeParser.parseTableType(unmarshallAttribute(n, "type", "unknown")) == Table.TABLE_X_AXIS) {

                            Table1D tempTable = (Table1D) unmarshallTable(n, ((Table2D) table).getAxis(), rom);
                            if (tempTable.getDataSize() != table.getDataSize()) {
                                tempTable.setDataSize(table.getDataSize());
                            }
                            tempTable.setData(((Table2D) table).getAxis().getData());
                            tempTable.setAxisParent(table);
                            ((Table2D) table).setAxis(tempTable);

                        }
                    } else if (table.getType() == Table.TABLE_3D) { // if table is 3D, populate axiis
                        if (RomAttributeParser.parseTableType(unmarshallAttribute(n, "type", "unknown")) == Table.TABLE_X_AXIS) {

                            Table1D tempTable = (Table1D) unmarshallTable(n, ((Table3D) table).getXAxis(), rom);
                            if (tempTable.getDataSize() != ((Table3D) table).getSizeX()) {
                                tempTable.setDataSize(((Table3D) table).getSizeX());
                            }
                            tempTable.setData(((Table3D) table).getXAxis().getData());
                            tempTable.setAxisParent(table);
                            ((Table3D) table).setXAxis(tempTable);

                        } else if (RomAttributeParser.parseTableType(unmarshallAttribute(n, "type", "unknown")) == Table.TABLE_Y_AXIS) {

                            Table1D tempTable = (Table1D) unmarshallTable(n, ((Table3D) table).getYAxis(), rom);
                            if (tempTable.getDataSize() != ((Table3D) table).getSizeY()) {
                                tempTable.setDataSize(((Table3D) table).getSizeY());
                            }
                            tempTable.setData(((Table3D) table).getYAxis().getData());
                            tempTable.setAxisParent(table);
                            ((Table3D) table).setYAxis(tempTable);

                        }
                    }

                } else if (n.getNodeName().equalsIgnoreCase("scaling")) {
                    // check whether scale already exists. if so, modify, else use new instance
                    Scale baseScale = new Scale();
                    try {
                        baseScale = table.getScaleByName(unmarshallAttribute(n, "name", "x"));
                    } catch (Exception ex) {
                    }

                    table.setScale(unmarshallScale(n, baseScale));
                    table.getScale().setTable(table);

                } else if (n.getNodeName().equalsIgnoreCase("data")) {
                    // parse and add data to table
                    DataCell dataCell = new DataCell();
                    dataCell.setDisplayValue(unmarshallText(n));
                    dataCell.setTable(table);
                    table.setIsStatic(true);
                    table.addStaticDataCell(dataCell);

                } else if (n.getNodeName().equalsIgnoreCase("description")) {
                    table.setDescription(unmarshallText(n));

                } else if (n.getNodeName().equalsIgnoreCase("state")) {
                    // set on/off values for switch type
                    if (unmarshallAttribute(n, "name", "").equalsIgnoreCase("on")) {
                        ((TableSwitch) table).setOnValues(unmarshallAttribute(n, "data", "0"));

                    } else if (unmarshallAttribute(n, "name", "").equalsIgnoreCase("off")) {
                        ((TableSwitch) table).setOffValues(unmarshallAttribute(n, "data", "0"));

                    }

                } else { /*unexpected element in Table (skip) */ }
            } else { /* unexpected node-type in Table (skip) */ }
        }

        return table;
    }

    private Scale unmarshallScale(Node scaleNode, Scale scale) {

        // look for base scale first
        String base = unmarshallAttribute(scaleNode, "base", "none");
        if (!base.equalsIgnoreCase("none")) {
            for (Scale scaleItem : scales) {

                // check whether name matches base and set scale if so
                if (scaleItem.getName().equalsIgnoreCase(base)) {
                    try {
                        scale = (Scale) ObjectCloner.deepCopy(scaleItem);

                    } catch (Exception ex) {
                    }
                }
            }
        }

        // set remaining attributes
        scale.setName(unmarshallAttribute(scaleNode, "name", scale.getName()));
        scale.setUnit(unmarshallAttribute(scaleNode, "units", scale.getUnit()));
        scale.setExpression(unmarshallAttribute(scaleNode, "expression", scale.getExpression()));
        scale.setByteExpression(unmarshallAttribute(scaleNode, "to_byte", scale.getByteExpression()));
        scale.setFormat(unmarshallAttribute(scaleNode, "format", "#"));
        scale.setMax(unmarshallAttribute(scaleNode, "max", 0.0));
        scale.setMin(unmarshallAttribute(scaleNode, "min", 0.0));

        // get coarse increment with new attribute name (coarseincrement), else look for old (increment)
        scale.setCoarseIncrement(unmarshallAttribute(scaleNode, "coarseincrement",
                unmarshallAttribute(scaleNode, "increment", scale.getCoarseIncrement())));

        scale.setFineIncrement(unmarshallAttribute(scaleNode, "fineincrement", scale.getFineIncrement()));

        return scale;
    }

}