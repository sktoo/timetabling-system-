/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.timetable.export.rooms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import org.unitime.localization.impl.Localization;
import org.unitime.timetable.defaults.CommonValues;
import org.unitime.timetable.defaults.UserProperty;
import org.unitime.timetable.events.EventAction.EventContext;
import org.unitime.timetable.export.ExportHelper;
import org.unitime.timetable.export.Exporter;
import org.unitime.timetable.gwt.client.rooms.RoomsComparator;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.EventInterface.FilterRpcRequest;
import org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse;
import org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse.Entity;
import org.unitime.timetable.gwt.shared.RoomInterface.DepartmentInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.ExamTypeInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.FeatureInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.FeatureTypeInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.GroupInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.RoomDetailInterface;
import org.unitime.timetable.gwt.shared.RoomInterface.RoomFilterRpcRequest;
import org.unitime.timetable.gwt.shared.RoomInterface.RoomsPageMode;
import org.unitime.timetable.model.RoomFeatureType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.RoomFeatureTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.UserAuthority;
import org.unitime.timetable.security.UserContext;
import org.unitime.timetable.security.context.UniTimeUserContext;
import org.unitime.timetable.server.rooms.RoomDetailsBackend;
import org.unitime.timetable.util.Formats;
import org.unitime.timetable.util.Formats.Format;
import org.unitime.timetable.webutil.RequiredTimeTable;

/**
 * @author Tomas Muller
 */
public abstract class RoomsExporter implements Exporter {
	protected static final GwtMessages MESSAGES = Localization.create(GwtMessages.class);
	protected static final GwtConstants CONSTANTS = Localization.create(GwtConstants.class);

	@Override
	public void export(ExportHelper helper) throws IOException {
		Long sessionId = helper.getAcademicSessionId();
		if (sessionId == null)
			throw new IllegalArgumentException("Academic session not provided, please set the term parameter.");
		
		Session session = SessionDAO.getInstance().get(sessionId);
		if (session == null)
			throw new IllegalArgumentException("Given academic session no longer exists.");

		RoomFilterRpcRequest request = new RoomFilterRpcRequest();
		request.setCommand(FilterRpcRequest.Command.ENUMERATE);
    	request.setSessionId(sessionId);
    	for (Enumeration<String> e = helper.getParameterNames(); e.hasMoreElements(); ) {
    		String command = e.nextElement();
    		if (command.equals("r:text")) {
    			request.setText(helper.getParameter("r:text"));
    		} else if (command.startsWith("r:")) {
    			for (String value: helper.getParameterValues(command))
    				request.addOption(command.substring(2), value);
    		}
    	}
    	request.setOption("flag", "gridAsText");
    	
    	UserContext u = helper.getSessionContext().getUser();
    	String user = helper.getParameter("user");
    	if (u == null && user != null && !checkRights(helper)) {
    		u = new UniTimeUserContext(user, null, null, null);
    		String role = helper.getParameter("role");
    		if (role != null) {
    			for (UserAuthority a: u.getAuthorities()) {
    				if (a.getAcademicSession() != null && a.getAcademicSession().getQualifierId().equals(sessionId) && role.equals(a.getRole())) {
    					u.setCurrentAuthority(a); break;
    				}
    			}
    		}
    	}
    	EventContext context = new EventContext(helper.getSessionContext(), u, sessionId);
    	if (u != null && u.getExternalUserId() != null)
    		request.setOption("user", u.getExternalUserId());
    	
    	ExportContext ec = new ExportContext();
    	if (helper.getParameter("dm") != null)
    		ec.setDepartmentMode(Integer.parseInt(helper.getParameter("dm")));
    	
    	FilterRpcResponse response = new FilterRpcResponse();
    	new RoomDetailsBackend().enumarate(request, response, context); 
    	
    	List<RoomDetailInterface> rooms = new ArrayList<RoomDetailInterface>();
    	if (response.hasResults()) {
    		for (Entity e: response.getResults())
    			rooms.add((RoomDetailInterface)e);
    	}
    	
    	if (helper.getParameter("sort") != null) {
    		RoomsComparator cmp = null;
    		try {
    			int sort = Integer.parseInt(helper.getParameter("sort"));
    			if (sort > 0) {
    				cmp = new RoomsComparator(RoomsComparator.Column.values()[sort - 1], true);
    			} else if (sort < 0) {
    				cmp = new RoomsComparator(RoomsComparator.Column.values()[-1 - sort], false);
    			}
    		} catch (Exception e) {}
    		if (cmp != null)
    			Collections.sort(rooms, cmp);
    	}
    	
    	ec.setRoomCookieFlags(helper.getParameter("flags") == null ? RoomsPageMode.COURSES.getFlags() : Integer.parseInt(helper.getParameter("flags")));
    	
    	if (helper.getParameter("horizontal") != null)
    		ec.setVertical("0".equals(helper.getParameter("horizontal")));
    	else if (context.getUser() != null)
    		ec.setVertical(CommonValues.VerticalGrid.eq(context.getUser().getProperty(UserProperty.GridOrientation)));
    	
    	ec.setMode(helper.getParameter("mode"));
    	if (ec.getMode() == null && context.getUser() != null)
    		ec.setMode(RequiredTimeTable.getTimeGridSize(context.getUser()));
    	
    	ec.setGridAsText(context.getUser() == null ? false : CommonValues.TextGrid.eq(UserProperty.GridOrientation.get(context.getUser())));
    	
    	for (RoomFeatureType type: new TreeSet<RoomFeatureType>(RoomFeatureTypeDAO.getInstance().findAll()))
    		ec.addRoomFeatureType(new FeatureTypeInterface(type.getUniqueId(), type.getReference(), type.getLabel(), type.isShowInEventManagement()));
    	
    	ec.setDepartment(request.getOption("department"));
    	
    	print(helper, rooms, ec);
	}
	
	protected abstract void print(ExportHelper helper, List<RoomDetailInterface> rooms, ExportContext context) throws IOException;
	
	protected boolean checkRights(ExportHelper helper) {
		return !helper.isRequestEncoded();
	}
	
	protected static class ExportContext {
		private String iDepartment = null;
		private List<FeatureTypeInterface> iFeatureTypes = new ArrayList<FeatureTypeInterface>();
		private int iDepartmentMode = 0;
		private int iRoomCookieFlags = 0;
		private boolean iGridAsText = false;
		private boolean iVertical = true;
		private String iMode = null;
		private String iSeparator = "\n";
		private Format<Number> iAreaFormat = Formats.getNumberFormat(CONSTANTS.roomAreaFormat());
		private Format<Number> iCoordinateFormat = Formats.getNumberFormat(CONSTANTS.roomCoordinateFormat());
		
		public void setDepartment(String department) { iDepartment = department; }
		public String getDepartment() { return iDepartment; }
		
		public int getDepartmentMode() { return iDepartmentMode; }
		public void setDepartmentMode(int deptMode) { iDepartmentMode = deptMode; }
		
		public void addRoomFeatureType(FeatureTypeInterface type) { iFeatureTypes.add(type); }
		public List<FeatureTypeInterface> getRoomFeatureTypes() { return iFeatureTypes; }
		
		public void setRoomCookieFlags(int flags) { iRoomCookieFlags = flags; }
		public int getRoomCookieFlags() { return iRoomCookieFlags; }
		
		public void setGridAsText(boolean gridAsText) { iGridAsText = gridAsText; }
		public boolean isGridAsText() { return iGridAsText; }
		
		public void setVertical(boolean vertical) { iVertical = vertical; }
		public boolean isVertical() { return iVertical; }
		
		public void setMode(String mode) { iMode = mode; }
		public String getMode() { return iMode; }
		
		public void setSeparator(String separator) { iSeparator = separator; }
		public String getSeparator() { return iSeparator; }
		
		public Format<Number> getAreaFormat() { return iAreaFormat; }
		public Format<Number> getCoordinateFormat() { return iCoordinateFormat; }
		
		protected String dept2string(DepartmentInterface d) {
			if (d == null) return "";
			switch (getDepartmentMode()) {
			case 0: return d.getDeptCode();
			case 1: return d.getExtAbbreviationWhenExist();
			case 2: return d.getExtLabelWhenExist();
			case 3: return d.getExtAbbreviationWhenExist() + " - " + d.getExtLabelWhenExist();
			case 4: return d.getDeptCode() + " - " + d.getExtLabelWhenExist();
			default: return d.getDeptCode();
			}
		}
		
		protected String dept2string(Collection<DepartmentInterface> departments) {
			if (departments == null || departments.isEmpty()) return "";
			String ret = "";
			for (DepartmentInterface d: departments) {
				ret += (ret.isEmpty() ? "" : getSeparator()) + dept2string(d);
			}
			return ret;
		}
		
		protected String pref2string(Collection<DepartmentInterface> departments) {
			if (departments == null || departments.isEmpty()) return "";
			String ret = "";
			for (DepartmentInterface d: departments) {
				if (d.getPreference() == null) continue;
				ret += (ret.isEmpty() ? "" : getSeparator()) + d.getPreference().getName() + " " + dept2string(d);
			}
			return ret;
		}
		
		protected String examTypes2string(Collection<ExamTypeInterface> types) {
			if (types == null || types.isEmpty()) return "";
			String ret = "";
			for (ExamTypeInterface t: types) {
				ret += (ret.isEmpty() ? "" : getSeparator()) + t.getLabel();
			}
			return ret;
		}
		
		protected String features2string(Collection<FeatureInterface> features, FeatureTypeInterface type) {
			if (features == null || features.isEmpty()) return "";
			String ret = "";
			for (FeatureInterface f: features) {
				if (type == null && f.getType() == null)
					ret += (ret.isEmpty() ? "" : getSeparator()) + f.getLabel();
				if (type != null && type.equals(f.getType())) {
					if (f.getDepartment() != null)
						ret += (ret.isEmpty() ? "" : getSeparator()) + f.getLabel() + " (" + dept2string(f.getDepartment()) + ")";
					else
						ret += (ret.isEmpty() ? "" : getSeparator()) + f.getLabel();
				}
			}
			return ret;
		}
		
		protected String groups2string(Collection<GroupInterface> groups) {
			if (groups == null || groups.isEmpty()) return "";
			String ret = "";
			for (GroupInterface g: groups) {
				ret += (ret.isEmpty() ? "" : getSeparator()) + g.getLabel() + (g.getDepartment() == null ? "" : " (" + dept2string(g.getDepartment()) + ")");
			}
			return ret;
		}
	}
}