package http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import diagram.BpmnDiagramGenerator;
import image.CommandLineImageGenerator;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * Conversion of BPMN 2.0 data format to PNG
 */
public class BpmnToPngConversionRequestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        BpmnDiagramGenerator diagramGenerator = new BpmnDiagramGenerator();
        CommandLineImageGenerator imageGenerator = new CommandLineImageGenerator();

        try {
            System.out.println("Reading body as model ...");
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(exchange.getRequestBody());

            System.out.println("Auto layouting model and adding diagram ...");
            diagramGenerator.addDiagramToModel(modelInstance);
            System.out.println("Converting model to png ...");
            Optional<InputStream> inputStream = imageGenerator.imageFromBpmn(modelInstance);
            System.out.println("Encoding image as base64 ...");
            if (inputStream.isEmpty()) {
                throw new IOException("Unable to read from generated image input stream.");
            }
            byte[] image = inputStream.get().readAllBytes();
            String response = Base64.getEncoder().encodeToString(image);
            response = "data:image/png;base64," + response;
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            System.out.format("Sending response of %d ...\n", response.length());
            exchange.sendResponseHeaders(200, response.length());
            OutputStream responseBody = exchange.getResponseBody();
            responseBody.write(response.getBytes());
            responseBody.close();
            System.out.println("Request handled successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            String response = e.toString();
            exchange.sendResponseHeaders(500, response.length());
            exchange.getResponseBody().write(response.getBytes());
        }
    }
}
