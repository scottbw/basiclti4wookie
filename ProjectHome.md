Provides adapter code for connecting Apache Wookie to systems using IMS Basic LTI; this enables VLEs/LMSs to use W3C Widgets served by Apache Wookie.

For more information on Apache Wookie see http://incubator.apache.org/wookie

For more information on IMS Basic LTI see http://groups.google.com/group/ims-dev

To use this adapter, add BasicLTIServlet.java to your Apache Wookie src tree, and add the following to web.xml:

```
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
```