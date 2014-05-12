package net.floodlightcontroller.cli.commands;

/*
* Copyright (c) 2013, California Institute of Technology
* ALL RIGHTS RESERVED.
* Based on Government Sponsored Research DE-SC0007346
* Author Michael Bredel <michael.bredel@cern.ch>
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*     http://www.apache.org/licenses/LICENSE-2.0
* 
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
* BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
* OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
* AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
* LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
* WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
* 
* Neither the name of the California Institute of Technology
* (Caltech) nor the names of its contributors may be used to endorse
* or promote products derived from this software without specific prior
* written permission.
*/

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.IFlowCacheService;


/**
 * The "show host" command shows information about hosts
 * that are connected to switches controlled by Floodlight.
 * 
 * The "show host" command uses the Floodlight.context service
 * to directly address the corresponding Floodlight module to
 * retrieve the needed information.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class UpdateFlowsCmd implements ICommand {
	/** The timeout to wait for queries. */
	private static final short QUERY_TIMEOUT = 5000;
	/** Required Module: Floodlight Provider Service. */
	private IFloodlightProviderService floodlightProvider;
	/** Required Module: */
	private IFlowCacheService flowCache;
	/** The command string. */
	private String commandString = "update flows";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public UpdateFlowsCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.flowCache = context.getServiceImpl(IFlowCacheService.class);
	}
	
	@Override
	public String getCommandString() {
		return commandString;
	}
	
	@Override
	public String getArguments() {
		return arguments;
	}

	@Override
	public String getHelpText() {
		return help;
	}
	
	@Override
	public Collection<Completer> getCompleter() {
		return null;
	}

	@Override
	public String execute(IConsole console, String arguments) {
		/* The resulting string. */
		StringBuilder result = new StringBuilder();
		
		for (long switchId : floodlightProvider.getAllSwitchDpids()) {
			this.flowCache.querySwitchFlowTable(switchId);
		}

		try {
			Thread.sleep(QUERY_TIMEOUT);
			Map<Long, Set<FlowCacheObj>> flowCacheObj = flowCache.getAllFlows();
			result.append(this.flowToTableString(flowCacheObj));
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		
		// Return
		return result.toString();
	}
	
	/**
	 * Creates a string table and returns a formated string that
	 * shows the device information as a table.
	 * 
	 * @param devices A collection of devices.
	 * @return A formated string that shows the device information as a table.
	 * @throws IOException
	 */
	private String flowToTableString(Map<Long, Set<FlowCacheObj>> flowCacheObj) throws IOException {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Switch");
        header.add("Cookie");
        header.add("Priority");
        header.add("Match");
        header.add("Action");
        header.add("Duration");
        header.add("Status");
        
        // Add header to string table.
        stringTable.setHeader(header);

		// Generate table entries and add them to string table.
        for (long switchId : flowCacheObj.keySet()) {
			for (FlowCacheObj fco : flowCacheObj.get(switchId)) {
				// Create StringTable
				List<String> row = new LinkedList<String>();
				row.add(HexString.toHexString(switchId));
				row.add("0x" + Long.toHexString(fco.getCookie()));
				row.add(String.valueOf(fco.getPriority()));
				row.add(parseMatch(fco.getMatch()));
				row.add(parseActions(fco.getActions()));
				row.add(this.parseDate(System.currentTimeMillis() - fco.getTimestamp()));
				row.add(fco.getStatus().toString());

				stringTable.addRow(row);
			}
        }
        
		// Return string table as a string.
        return stringTable.toString();
	}
	
	/**
	 * 
	 * @param ofm
	 * @return
	 */
	private String parseMatch(OFMatch ofm) {
		String ofmString = ofm.toString();
		int from   = "OFMatch[".length();
		int length = ofmString.length() - "]".length();
		return ofmString.substring(from, length);
	}
	
	private String parseActions(List<OFAction> actions) {
		StringBuilder sb = new StringBuilder();
		sb.append("actions=");
		
		if (actions == null || actions.isEmpty())
			sb.append("drop,");
		
		for (OFAction action : actions) {
			switch (action.getType()) {
				case OUTPUT:
					sb.append("output:");
					sb.append(((OFActionOutput) action).getPort() & 0xffff);
					sb.append(",");
					break;
				case STRIP_VLAN:
					sb.append("strip_vlan:");
					sb.append(",");
					break;
				case SET_VLAN_ID:
					sb.append("set_vlan_id:");
					sb.append(((OFActionVirtualLanIdentifier) action).getVirtualLanIdentifier());
					sb.append(",");
					break;
				default:
					sb.append(action.toString());
					sb.append(",");
					break;
			}
		}
		// Remove the last ",".
		sb.deleteCharAt(sb.length()-1);
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param date
	 * @return
	 */
	private String parseDate(long timestamp) {
		SimpleDateFormat dateformat;
		String unit = "";
		
		if (timestamp < (60 * 1000)) {
			dateformat = new SimpleDateFormat("ss");
			unit = " s";
		} else if (timestamp < (3600 * 1000)) {
			dateformat = new SimpleDateFormat("mm:ss");
			unit = " m";
		} else {
			dateformat = new SimpleDateFormat("HH:mm:ss");
			unit = " h";
		}
		Date date = new Date ();
		date.setTime(timestamp);
		return dateformat.format(date) + unit;
	}
	
}
