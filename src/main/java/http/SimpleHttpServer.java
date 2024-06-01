package http;

import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import java.util.EnumSet;

public class SimpleHttpServer {
    public static void main(String[] args) throws Exception {
        int port = 8765;
        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(JsonToPngConversionServlet.class, "/convert");
        context.addServlet(JsonToPngConversionServlet.class, "/convert/json");
        context.addServlet(BpmnToPngConversionServlet.class, "/convert/xml");
        context.addServlet(BpmnToPngConversionServlet.class, "/convert/bpmn");
        context.addServlet(BpmnToPngConversionServlet.class, "/convert/bpmn2");
        context.addServlet(JsonToBpmnIOConversionServlet.class, "/convert/json/xml");

        FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

        server.setHandler(context);

        System.out.format("Starting server, listening on port %d ...\n", port);

        server.start();
        server.join();
    }
}
