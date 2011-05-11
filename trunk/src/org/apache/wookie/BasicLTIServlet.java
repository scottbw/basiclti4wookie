package org.apache.wookie;
/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */



import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.wookie.Messages;
import org.apache.wookie.beans.IPreference;
import org.apache.wookie.beans.IWidget;
import org.apache.wookie.beans.IWidgetInstance;
import org.apache.wookie.beans.util.IPersistenceManager;
import org.apache.wookie.beans.util.PersistenceManagerFactory;
import org.apache.wookie.controller.Controller;
import org.apache.wookie.controller.ParticipantsController;
import org.apache.wookie.controller.PropertiesController;
import org.apache.wookie.controller.WidgetInstancesController;
import org.apache.wookie.helpers.SharedDataHelper;
import org.apache.wookie.helpers.WidgetInstanceFactory;
import org.apache.wookie.server.LocaleHandler;


/**
 * A controller that enables Wookie to act as a "Tool Provider" for a BasicLTI "Tool Consumer"
 * 
 * Basically this is a shim onto the existing REST API.
 * 
 * For more information see: http://www.imsglobal.org/lti/index.html
 * 
 * Enable in web.xml with:
 */
 /*
  	<servlet>
 		<description></description>
 		<display-name>LTI</display-name>
 		<servlet-name>LTI</servlet-name>
 		<servlet-class>org.apache.wookie.BasicLTIServlet</servlet-class>
 		<load-on-startup>2</load-on-startup>
 	</servlet>	
 	<servlet-mapping>
 		<servlet-name>LTI</servlet-name>
 		<url-pattern>/basiclti/*</url-pattern>
 	</servlet-mapping>
 */
public class BasicLTIServlet extends WidgetInstancesController {

	private static final long serialVersionUID = 8702047252664571786L;

	private static final String[] BASICLTI_ADMIN_ROLES = {"instructor","moderator", "teachingassistant", "administrator", "mentor", "manager", "content developer"};

	private static final String[] BASICLTI_PARAMETERS = {"launch_presentation_document_target","tool_consumer_instance_name","tool_consumer_instance_description","tool_consumer_instance_url","context_type","context_title","context_label", "resource_link_description", "resource_link_title", "lis_person_name_given", "lis_person_name_full","lis_person_contact_email_primary","tool_consumer_instance_contact_email"};
	
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		
		//debugParameters(request);
		String userId = request.getParameter("user_id"); //$NON-NLS-1$
		String sharedDataKey = request.getParameter("resource_link_id"); //$NON-NLS-1$
		// We use this mapping as BasicLTI assumes one "tool provider" per endpoint URL
		// We map the value onto the actual widget id (IRI)
		String widgetId = Controller.getResourceId(request); //$NON-NLS-1$
		widgetId = getWidgetGuid(widgetId);
		// TODO Get the oAuth token, for now just use the consumer key as a quick demo hack
		String token = request.getParameter("oauth_consumer_key");
		String locale = request.getParameter("launch_presentation_locale");//replace with real one
		// Construct the internal key
        sharedDataKey = SharedDataHelper.getInternalSharedDataKey(token, widgetId, sharedDataKey);

		HttpSession session = request.getSession(true);
		Messages localizedMessages = LocaleHandler.localizeMessages(request);

		if(userId==null || sharedDataKey==null || widgetId==null) error(500,request,response);
		
		// Construct a proxy URL for this instance
		checkProxy(request);

		// Get an instance
		IPersistenceManager persistenceManager = PersistenceManagerFactory.getPersistenceManager();
		IWidgetInstance instance = persistenceManager.findWidgetInstanceByGuid(token, userId, sharedDataKey, widgetId);	
		
		// Widget instance exists?
		if(instance==null){
			instance = WidgetInstanceFactory.getWidgetFactory(session, localizedMessages).newInstance(token, userId, sharedDataKey, "", widgetId,locale);
			response.setStatus(HttpServletResponse.SC_CREATED);
		}

		// Not a valid widget
		if(instance==null){
			error(HttpServletResponse.SC_NOT_FOUND, request, response);
			return;
		}
		
		response.setStatus(HttpServletResponse.SC_OK);		

		// Set some properties, e.g. participant info and roles
		setParticipant(instance, userId, request);
	
		boolean isModerator = false;
		if (request.getParameter("roles")!=null){
			for (String role:BASICLTI_ADMIN_ROLES){
				if (StringUtils.containsIgnoreCase(request.getParameter("roles"), role)) isModerator=true; 
			}
		}
		if (isModerator) setOwner(instance, userId);
		// Set any other properties as preferences
		setPreferences(instance,request);

		// Redirect to the instance URL
		try {
			// Construct widget instance URL
			// For some reason this doesn't work:
			// String url = URLDecoder.decode(super.getUrl(request, instance),"UTF-8");
			String url = super.getUrl(request, instance).toString().replace("&amp;", "&");
			response.sendRedirect(url);
		} catch (Exception e) {
			error(500,request,response);
			return;
		}

	}

	/**
	 * Translate the supplied widget id into a Widget IRI.
	 * @param id 
	 * @return the IRI of the widget, or null if no match is found
	 */
	private String getWidgetGuid(String id){
		IPersistenceManager persistenceManager = PersistenceManagerFactory.getPersistenceManager();
		IWidget widget = persistenceManager.findById(IWidget.class, id);
		if (widget == null){
			id = null;
		} else {
			id = widget.getGuid();
		}
		return id;
	}
	
	/**
	 * Send an error; this will use any preferred URL supplied by the requester by preference,
	 * otherwise sends a standard HTTP error
	 * @param statusCode the error code
	 * @param request the  request
	 * @param response the response
	 * @throws IOException
	 */
	private void error(int statusCode, HttpServletRequest request, HttpServletResponse response) throws IOException{
		if (request.getParameter("launch_presentation_return_url")!=null && !request.getParameter("launch_presentation_return_url").trim().equals("")){
			response.sendRedirect(request.getParameter("launch_presentation_return_url"));
			return;
		} else {
			response.sendError(statusCode);
			return;
		}
	}

	/**
	 * Set the owner of the widget instance group - FIXME when isHost() is implemented
	 * @param instance
	 * @param participantId
	 */
	private void setOwner(IWidgetInstance instance, String participantId){
		// Does the widget support the "isModerator" preference?
		setPreference(instance,"moderator","true");
		// TODO Set the isHost parameter
	}
	
	/**
	 * Sets any preferences that match the names of BasicLTI parameters
	 * TODO implement custom fields
	 * @param instance
	 * @param request
	 */
	private void setPreferences(IWidgetInstance instance, HttpServletRequest request){
		for (String name:BASICLTI_PARAMETERS){
			String value = request.getParameter(name);
			if (value!=null) setPreference(instance,name,value);
		}
	}
	
	/**
	 * Sets any preferences declared by the widget where it matches the supplied name
	 * @param instance the widget instance
	 * @param name the name to set
	 * @param value the value to set
	 */
	private void setPreference(IWidgetInstance instance, String name, String value){
		Collection<IPreference> prefs = instance.getPreferences();
		if (prefs == null) return;
		for (IPreference pref:prefs){
			if (pref.getDkey().equals(name)){
				PropertiesController.updatePreference(instance, name, value);
			}
		}
	}
	
	/**
	 * Sets the participant of the widget using BasicLTI information. Note this does
	 * not contain a thumbnail URL, and is not guaranteed to have a name either.
	 * @param instance
	 * @param userId
	 * @param request
	 */
	private void setParticipant(IWidgetInstance instance, String userId, HttpServletRequest request){
		String name = "unknown";
		if (request.getParameter("lis_person_name_full")!=null){
			name = request.getParameter("lis_person_name_full");
		}
		String thumbnail = null;
		if (request.getParameter("user_image")!=null){
			name = request.getParameter("user_image");
		}
		ParticipantsController.addParticipantToWidgetInstance(instance, userId, name, thumbnail);
	}
}