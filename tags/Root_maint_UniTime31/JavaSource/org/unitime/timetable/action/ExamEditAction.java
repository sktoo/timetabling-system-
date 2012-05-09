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
package org.unitime.timetable.action;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessages;
import org.apache.struts.util.MessageResources;
import org.unitime.commons.Debug;
import org.unitime.commons.User;
import org.unitime.commons.web.Web;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.form.ExamEditForm;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.DistributionPref;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamEvent;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.Preference;
import org.unitime.timetable.model.Roles;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SolverParameterDef;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.InstrOfferingConfigDAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.LookupTables;
import org.unitime.timetable.webutil.BackTracker;
import org.unitime.timetable.webutil.Navigation;
import org.unitime.timetable.webutil.RequiredTimeTable;

public class ExamEditAction extends PreferencesAction {
    
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        ExamEditForm frm = (ExamEditForm) form;
        try {
            
            // Set common lookup tables
            super.execute(mapping, form, request, response);
            
            HttpSession httpSession = request.getSession();
            User user = Web.getUser(httpSession);
            Long sessionId = (Long) user.getAttribute(Constants.SESSION_ID_ATTR_NAME);
            MessageResources rsc = getResources(request);
            ActionMessages errors = new ActionMessages();
            
            // Read parameters
            String examId = request.getParameter("examId");
            String op = frm.getOp();            
            String reloadCause = request.getParameter("reloadCause");
            String deleteType = request.getParameter("deleteType");
            
            // Read subpart id from form
            if(op.equals(rsc.getMessage("button.reload"))
                    || op.equals(rsc.getMessage("button.addObject"))
                    || op.equals(rsc.getMessage("button.addPeriod"))
                    || op.equals(rsc.getMessage("button.addRoomPref"))
                    || op.equals(rsc.getMessage("button.addBldgPref"))
                    || op.equals(rsc.getMessage("button.addRoomFeaturePref"))
                    || op.equals(rsc.getMessage("button.addDistPref")) 
                    || op.equals(rsc.getMessage("button.addRoomGroupPref"))
                    || op.equals(rsc.getMessage("button.addInstructor"))
                    || op.equals(rsc.getMessage("button.updateExam")) 
                    || op.equals(rsc.getMessage("button.cancel")) 
                    || op.equals(rsc.getMessage("button.clearExamPrefs"))                 
                    || op.equals(rsc.getMessage("button.delete"))
                    || op.equals(rsc.getMessage("button.saveExam"))
                    || op.equals(rsc.getMessage("button.addExam"))
                    || op.equals(rsc.getMessage("button.returnToDetail"))
                    || op.equals(rsc.getMessage("button.nextExam"))
                    || op.equals(rsc.getMessage("button.previousExam"))) {
                examId = frm.getExamId();
            }
            
            // Determine if initial load
            if(op==null || op.trim().length()==0 
                    || ( op.equals(rsc.getMessage("button.reload")) 
                         && (reloadCause==null || reloadCause.trim().length()==0) )) {
            	if (deleteType!=null && deleteType.length()>0)
            		op = "delete";
            	else
            		op = "init";
            }
            
            // Check op exists
            if(op==null || op.trim()=="") 
                throw new Exception ("Null Operation not supported.");
            
            Exam exam = (examId==null || examId.trim().length()==0 ? null: new ExamDAO().get(Long.valueOf(examId)));
            
            // Cancel - Go back to Instructors Detail Screen
            if(op.equals(rsc.getMessage("button.returnToDetail"))) {
                if (BackTracker.hasBack(request, 1)) {
                    BackTracker.doBack(request, response);
                    return null;
                }
                if (examId!=null && examId.trim()!="") {
                    request.setAttribute("examId", examId);
                    request.setAttribute("fromChildScreen", "true");
                    return mapping.findForward("showDetail");
                } else {
                    return mapping.findForward("showList");
                }
            }
            
            // Clear all preferences
            if(exam!=null && op.equals(rsc.getMessage("button.clearExamPrefs"))) { 
                Set s = exam.getPreferences();
                s.clear();
                exam.setPreferences(s);            
                new ExamDAO().update(exam);
                op = "init";                
                request.setAttribute("examId", examId);
                
                ChangeLog.addChange(
                        null, 
                        request,
                        exam, 
                        ChangeLog.Source.EXAM_EDIT, 
                        ChangeLog.Operation.CLEAR_PREF,
                        exam.firstSubjectArea(),
                        exam.firstDepartment());
                
                return mapping.findForward("showDetail");
            }
            
            // Reset form for initial load
            if(op.equals("init")) { 
                frm.reset(mapping, request);
                // Load form attributes that are constant
                doLoad(request, frm, exam);
                if ("true".equals(request.getParameter("clone"))) {
                    frm.setExamId(null);
                    frm.setClone(true);
                }
            }
            
            if ("true".equals(
                    ApplicationProperties.getProperty("tmtbl.exam.useLimit."+Exam.sExamTypes[frm.getExamType()],
                    (frm.getExamType()==Exam.sExamTypeFinal?"false":"true"))))
                frm.setSizeNote("A number of enrolled students or a total limit of selected classes/courses (whichever is bigger) is used when blank");
            else
                frm.setSizeNote("A number of enrolled students is used when blank");

            
            frm.setLabel(frm.getClone() || exam==null?"New Examination":exam.getLabel());
            
            if (op.equals(rsc.getMessage("button.addInstructor"))) {
                List lst = frm.getInstructors();
                for (int i=0; i<Constants.PREF_ROWS_ADDED; i++) {
                    frm.getInstructors().add(Preference.BLANK_PREF_VALUE);
                }
            }
            
            if (op.equals(rsc.getMessage("button.addObject"))) {
                for (int i=0; i<Constants.PREF_ROWS_ADDED; i++) {
                    frm.addExamOwner(null);
                }
                request.setAttribute("hash", "objects");
            }
            
            int deleteId = -1;
            try {
                deleteId = Integer.parseInt(request.getParameter("deleteId"));
            } catch(Exception e) { deleteId = -1; }
           
            if ("instructor".equals(deleteType)  && deleteId>=0) {
                frm.getInstructors().remove(deleteId);
            } else if ("examType".equals(deleteType)  && deleteId>=0) {
                frm.setExamType(deleteId);
            } else if ("objects".equals(deleteType)  && deleteId>=0) {
                frm.deleteExamOwner(deleteId);
            }
            
            if(op.equals(rsc.getMessage("button.updateExam")) ||  op.equals(rsc.getMessage("button.saveExam"))
                     || op.equals(rsc.getMessage("button.nextExam")) || op.equals(rsc.getMessage("button.previousExam"))
                    ) {  
                // Validate input prefs
                errors = frm.validate(mapping, request);
                
                // No errors - save
                if(errors.size()==0) {
                    doUpdate(request, frm, exam);
                    
                    request.setAttribute("examId", frm.getExamId());
                    request.setAttribute("fromChildScreen", "true");
                    
                    if (op.equals(rsc.getMessage("button.nextExam")))
                        response.sendRedirect(response.encodeURL("examEdit.do?examId="+frm.getNextId()));
                    
                    if (op.equals(rsc.getMessage("button.previousExam")))
                        response.sendRedirect(response.encodeURL("examEdit.do?examId="+frm.getPreviousId()));
                    
                    //response.sendRedirect(response.encodeURL("examDetail.do?examId="+examId));
                    if (op.equals(rsc.getMessage("button.saveExam")) && BackTracker.hasBack(request, 2) && !frm.getClone()) {
                        request.setAttribute("backType", "PreferenceGroup");
                        request.setAttribute("backId", frm.getExamId());
                        BackTracker.doBack(request, response);
                        return null;
                    }
                    
                    return mapping.findForward("showDetail");
                }
                else {
                    saveErrors(request, errors);
                }
            }
            
            // Initialize Preferences for initial load 
            frm.setAvailableTimePatterns(null);
            if(op.equals("init")) {
                initPrefs(user, frm, exam, null, true);
            }
            boolean timeVertical = RequiredTimeTable.getTimeGridVertical(Web.getUser(httpSession));
            generateExamPeriodGrid(request, frm, (frm.getClone()?null:exam), op, timeVertical, true);
            
            // Process Preferences Action
            processPrefAction(request, frm, errors);
            
            setupInstructors(request, frm, exam);
            
            if (exam!=null) {
                LookupTables.setupRooms(request, exam);      // Room Prefs
                LookupTables.setupBldgs(request, exam);      // Building Prefs
                LookupTables.setupRoomFeatures(request, exam); // Preference Levels
                LookupTables.setupRoomGroups(request, exam);   // Room Groups
            } else {
                Exam dummy = new Exam(); 
                dummy.setSession(Session.getCurrentAcadSession(user));
                dummy.setExamType(frm.getExamType());
                LookupTables.setupRooms(request, dummy);      // Room Prefs
                LookupTables.setupBldgs(request, dummy);      // Building Prefs
                LookupTables.setupRoomFeatures(request, dummy); // Preference Levels
                LookupTables.setupRoomGroups(request, dummy);   // Room Groups
            }
            
            frm.setAllowHardPrefs(user.isAdmin() || user.hasRole(Roles.EXAM_MGR_ROLE));
            
            frm.setSubjectAreas(TimetableManager.getSubjectAreas(user));
        
            if (!frm.getClone() && exam!=null) {
                BackTracker.markForBack(
                    request,
                    "examDetail.do?examId="+frm.getExamId(),
                    "Exam ("+ (frm.getName()==null || frm.getName().length()==0?frm.getLabel().trim():frm.getName().trim()) +")",
                    true, false);
            }

            return (frm.getClone() || exam==null?mapping.findForward("showAdd"):mapping.findForward("showEdit"));
            
        } catch (Exception e) {
            Debug.error(e);
            throw e;
        }
    }
        
    protected void doLoad(HttpServletRequest request, ExamEditForm frm, Exam exam) {
        if (exam!=null) {
            frm.setExamId(exam.getUniqueId().toString());
            frm.setExamType(exam.getExamType());
            
            frm.setName(exam.generateName().equals(exam.getName())?null:exam.getName());
            frm.setNote(exam.getNote());
            frm.setLength(exam.getLength());
            frm.setSize(exam.getExamSize()==null?null:exam.getExamSize().toString());
            frm.setPrintOffset(exam.getPrintOffset()==null || exam.getPrintOffset()==0?null:exam.getPrintOffset().toString());
            frm.setSeatingType(Exam.sSeatingTypes[exam.getSeatingType()]);
            frm.setMaxNbrRooms(exam.getMaxNbrRooms());
            
            TreeSet instructors = new TreeSet(exam.getInstructors());

            for (Iterator i = instructors.iterator(); i.hasNext(); ) {
                DepartmentalInstructor instr = (DepartmentalInstructor)i.next();
                frm.getInstructors().add(instr.getUniqueId().toString());
            }
            
            User user = Web.getUser(request.getSession());

            frm.setEditable(exam.isEditableBy(user));

            Long nextId = Navigation.getNext(request.getSession(), Navigation.sInstructionalOfferingLevel, exam.getUniqueId());
            Long prevId = Navigation.getPrevious(request.getSession(), Navigation.sInstructionalOfferingLevel, exam.getUniqueId());
            frm.setPreviousId(prevId==null?null:prevId.toString());
            frm.setNextId(nextId==null?null:nextId.toString());
            
            for (Iterator i=new TreeSet(exam.getOwners()).iterator();i.hasNext();)
                frm.addExamOwner((ExamOwner)i.next());
        } else {
            try {
                TreeSet periods = ExamPeriod.findAll(request, (frm.getExamType()==null?Exam.sExamTypeFinal:frm.getExamType()));
                if (!periods.isEmpty())
                    frm.setLength(Constants.SLOT_LENGTH_MIN*((ExamPeriod)periods.first()).getLength());
                SolverParameterDef maxRoomsParam = SolverParameterDef.findByName("Exams.MaxRooms");
                if (maxRoomsParam!=null && maxRoomsParam.getDefault()!=null) 
                    frm.setMaxNbrRooms(Integer.valueOf(maxRoomsParam.getDefault()));
            } catch (Exception e) {}
        }
        
        if (request.getParameter("firstType")!=null && request.getParameter("firstId")!=null) {
            String firstType = request.getParameter("firstType");
            Long firstId = Long.valueOf(request.getParameter("firstId"));
            if ("Class_".equals(firstType)) {
                Class_ clazz = new Class_DAO().get(firstId);
                frm.getSubjectArea().add(clazz.getSchedulingSubpart().getControllingCourseOffering().getSubjectArea().getUniqueId());
                frm.getCourseNbr().add(clazz.getSchedulingSubpart().getControllingCourseOffering().getUniqueId());
                frm.getItype().add(clazz.getSchedulingSubpart().getUniqueId());
                frm.getClassNumber().add(clazz.getUniqueId());
            } else if ("SchedulingSubpart".equals(firstType)) {
                SchedulingSubpart subpart = new SchedulingSubpartDAO().get(firstId);
                InstrOfferingConfig config = subpart.getInstrOfferingConfig();
                frm.getSubjectArea().add(config.getControllingCourseOffering().getSubjectArea().getUniqueId());
                frm.getCourseNbr().add(config.getControllingCourseOffering().getUniqueId());
                frm.getItype().add(-config.getUniqueId());
                frm.getClassNumber().add(new Long(-1));
            } else if ("InstrOfferingConfig".equals(firstType)) {
                InstrOfferingConfig config = new InstrOfferingConfigDAO().get(firstId);
                frm.getSubjectArea().add(config.getControllingCourseOffering().getSubjectArea().getUniqueId());
                frm.getCourseNbr().add(config.getControllingCourseOffering().getUniqueId());
                frm.getItype().add(-config.getUniqueId());
                frm.getClassNumber().add(new Long(-1));
            } else if ("InstructionalOffering".equals(firstType)) {
                InstructionalOffering offering = new InstructionalOfferingDAO().get(firstId);
                frm.getSubjectArea().add(offering.getControllingCourseOffering().getSubjectArea().getUniqueId());
                frm.getCourseNbr().add(offering.getControllingCourseOffering().getUniqueId());
                frm.getItype().add(Long.MIN_VALUE+1);
                frm.getClassNumber().add(new Long(-1));
            } else if ("CourseOffering".equals(firstType)) {
                CourseOffering course = new CourseOfferingDAO().get(firstId);
                frm.getSubjectArea().add(course.getSubjectArea().getUniqueId());
                frm.getCourseNbr().add(course.getUniqueId());
                frm.getItype().add(Long.MIN_VALUE);
                frm.getClassNumber().add(new Long(-1));
            }
        }
        
        for (int i=0;i<Constants.PREF_ROWS_ADDED;i++) {
            frm.addExamOwner(null);
            frm.getInstructors().add(Constants.BLANK_OPTION_VALUE);
        }
    }

    protected void setupInstructors(HttpServletRequest request, ExamEditForm frm, Exam exam) throws Exception {

        List instructors = frm.getInstructors();
        if(instructors.size()==0) return;
        
        HashSet deptIds = new HashSet();
        
        if (exam!=null) {
            for (Iterator i = exam.getInstructors().iterator(); i.hasNext(); ) {
                DepartmentalInstructor instr = (DepartmentalInstructor)i.next();
                deptIds.add(instr.getDepartment().getUniqueId());
            }
            for (Iterator i = exam.getOwners().iterator(); i.hasNext(); ) {
                ExamOwner own = (ExamOwner)i.next();
                deptIds.add(own.getCourse().getDepartment().getUniqueId());
            }
        } else {
            for (int i=0;i<frm.getSubjectAreaList().size();i++) {
                ExamOwner own = frm.getExamOwner(i);
                if (own!=null) deptIds.add(own.getCourse().getDepartment().getUniqueId());
            }
            if (deptIds.isEmpty()) {
                for (Iterator i = TimetableManager.getManager(Web.getUser(request.getSession())).getDepartments().iterator();i.hasNext();) {
                    Department dept = (Department)i.next();
                    deptIds.add(dept.getUniqueId());
                }
            }
        }
        
        Long[] deptsIdsArray = new Long[deptIds.size()]; int idx = 0;
        for (Iterator i=deptIds.iterator();i.hasNext();)
            deptsIdsArray[idx++]=(Long)i.next();

        LookupTables.setupInstructors(request, deptsIdsArray);
        Vector deptInstrList = (Vector) request.getAttribute(DepartmentalInstructor.INSTR_LIST_ATTR_NAME);

        // For each instructor set the instructor list
        for (int i=0; i<instructors.size(); i++) {
            request.setAttribute(DepartmentalInstructor.INSTR_LIST_ATTR_NAME + i, deptInstrList);
        }
    }
    
    protected void doUpdate(HttpServletRequest request, ExamEditForm frm, Exam exam) throws Exception {
        boolean add = false;
        if (exam==null) {
            add = true;
            exam = new Exam();
            exam.setSession(Session.getCurrentAcadSession(Web.getUser(request.getSession())));
        }
        
        Set s = exam.getPreferences();
        if (s==null) s = new HashSet();
        
        // Clear all old prefs
        for (Iterator i=s.iterator();i.hasNext();) {
            Preference p = (Preference)i.next();
            if (p instanceof DistributionPref) {
                //skip distribution preferences
            } else
                i.remove();
        }

        exam.setExamType(frm.getExamType());

        super.doUpdate(request, frm, exam, s, false);
        
        exam.setNote(frm.getNote());
        exam.setSeatingType(frm.getSeatingTypeIdx());
        Integer oldLength = exam.getLength();
        exam.setLength(Integer.valueOf(frm.getLength()));
        if (frm.getSize()==null || frm.getSize().length()==0) {
            exam.setExamSize(null);
        } else {
            exam.setExamSize(Integer.valueOf(frm.getSize()));
        }
        int oldPrintOffset = (exam.getPrintOffset()==null?0:exam.getPrintOffset());
        if (frm.getPrintOffset()==null || frm.getPrintOffset().length()==0) {
            exam.setPrintOffset(null);
        } else {
            exam.setPrintOffset(Integer.valueOf(frm.getPrintOffset()));
        }
        exam.setMaxNbrRooms(Integer.valueOf(frm.getMaxNbrRooms()));
        
        if (exam.getInstructors()==null) exam.setInstructors(new HashSet());
        for (Iterator i=exam.getInstructors().iterator();i.hasNext();) {
            DepartmentalInstructor instructor = (DepartmentalInstructor)i.next();
            instructor.getExams().remove(exam);
            i.remove();
        }
        for (Iterator i=frm.getInstructors().iterator();i.hasNext();) {
            String instructorId = (String)i.next();
            if (!Constants.BLANK_OPTION_VALUE.equals(instructorId) && !Preference.BLANK_PREF_VALUE.equals(instructorId)) {
                DepartmentalInstructor instructor = new DepartmentalInstructorDAO().get(Long.valueOf(instructorId));
                if (instructor!=null) {
                    exam.getInstructors().add(instructor);
                    instructor.getExams().add(exam);
                }
           }
        }
        
        frm.setExamOwners(exam);

        if (frm.getName()==null || frm.getName().trim().length()==0) {
            exam.setName(exam.generateName());            
        } else {
            exam.setName(frm.getName());
        }
        ExamEvent event = exam.getEvent();
        if (event!=null) {
            event.setEventName(exam.getName());
            if (exam.getAssignedPeriod()!=null && (!exam.getLength().equals(oldLength) || oldPrintOffset!=(exam.getPrintOffset()==null?0:exam.getPrintOffset()))) {
                for (Iterator i=event.getMeetings().iterator();i.hasNext();) {
                    Meeting m = (Meeting)i.next();
                    m.setStartOffset(new Integer(exam.getAssignedPeriod().getExamEventStartOffsetForExam(exam)));
                    m.setStopOffset(new Integer(exam.getAssignedPeriod().getExamEventStopOffsetForExam(exam)));
                }
            }
        }
        
        exam.generateDefaultPreferences(false);
        
        new ExamDAO().saveOrUpdate(exam);
        
                ChangeLog.addChange(
                null, 
                request,
                exam, 
                ChangeLog.Source.EXAM_EDIT, 
                (add?ChangeLog.Operation.CREATE:ChangeLog.Operation.UPDATE), 
                exam.firstSubjectArea(), 
                exam.firstDepartment());

        if (add) frm.setExamId(exam.getUniqueId().toString());
    }
}