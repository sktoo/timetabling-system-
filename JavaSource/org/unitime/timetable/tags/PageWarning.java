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
package org.unitime.timetable.tags;

import javax.servlet.ServletRequest;
import javax.servlet.jsp.tagext.TagSupport;

import org.apache.struts.Globals;
import org.unitime.commons.Debug;
import org.unitime.timetable.ApplicationProperties;

/**
 * @author Tomas Muller
 */
public class PageWarning extends TagSupport {
	private static final long serialVersionUID = 1L;
	
	private String iPrefix;
	private String iStyle;
	
	public String getPrefix() { return iPrefix; }
	public void setPrefix(String prefix) { iPrefix = prefix; }
	
	public String getStyle() { return iStyle; }
	public void setStyle(String styleName) { iStyle = styleName; }
	
	
	public String getPageWarning(ServletRequest request) {
		String page = request.getParameter("page");
		if (page != null) {
			if ("admin".equals(page)) {
				String type = request.getParameter("type");
				if (type != null) {
					String warning = ApplicationProperties.getProperty(getPrefix() + page + "." + type);
					if (warning != null && !warning.isEmpty()) return warning;
				}
			}
			String warning = ApplicationProperties.getProperty(getPrefix() + page);
			if (warning != null && !warning.isEmpty()) return warning;
		}
		String action = (String)request.getAttribute(Globals.ORIGINAL_URI_KEY);
		if (action != null && action.endsWith(".do")) {
			String warning = ApplicationProperties.getProperty(getPrefix() + action.substring(action.lastIndexOf('/') + 1, action.length() - 3));
			if (warning != null && !warning.isEmpty()) return warning;
		}
		return null;
	}
	
	public int doStartTag() {
		try {
			String warning = getPageWarning(pageContext.getRequest());
			if (warning != null) {
				pageContext.getOut().println("<div class=\"" + getStyle() + "\">");
				pageContext.getOut().println(warning);
				pageContext.getOut().println("</div>");
			}
			return SKIP_BODY;
		} catch (Exception e) {
			Debug.error(e);
			return SKIP_BODY;
		}
	}
	
	public int doEndTag() {
		return EVAL_PAGE;
	}

}
