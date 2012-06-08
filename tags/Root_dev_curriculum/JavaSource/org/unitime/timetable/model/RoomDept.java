/*
 * UniTime 3.1 (University Timetabling Application)
 * Copyright (C) 2008, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
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
package org.unitime.timetable.model;

import org.unitime.timetable.model.base.BaseRoomDept;



public class RoomDept extends BaseRoomDept implements Comparable {
	private static final long serialVersionUID = 1L;

/*[CONSTRUCTOR MARKER BEGIN]*/
	public RoomDept () {
		super();
	}

	/**
	 * Constructor for primary key
	 */
	public RoomDept (java.lang.Long uniqueId) {
		super(uniqueId);
	}

	/**
	 * Constructor for required fields
	 */
	public RoomDept (
		java.lang.Long uniqueId,
		org.unitime.timetable.model.Location room,
		org.unitime.timetable.model.Department department,
		java.lang.Boolean control) {

		super (
			uniqueId,
			room,
			department,
			control);
	}

/*[CONSTRUCTOR MARKER END]*/

	public int compareTo(Object o) {
		if (o==null || !(o instanceof RoomDept)) return -1;
		RoomDept rd = (RoomDept)o;
		int cmp = getDepartment().getDeptCode().compareTo(rd.getDepartment().getDeptCode());
		if (cmp!=0) return cmp;
		return getUniqueId().compareTo(rd.getUniqueId());
	}

}