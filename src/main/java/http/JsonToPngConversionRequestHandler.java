package http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import data.Document;
import diagram.BpmnDiagramGenerator;
import image.CommandLineImageGenerator;
import model.BpmnModelGenerator;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Conversion of json 2.0 data format to PNG
 */
public class JsonToPngConversionRequestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        BpmnModelGenerator modelGenerator = new BpmnModelGenerator();
        BpmnDiagramGenerator diagramGenerator = new BpmnDiagramGenerator();
        CommandLineImageGenerator imageGenerator = new CommandLineImageGenerator();

        try {
            System.out.println("Reading json ...");
            String jsonBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Parsing document ...");
            Document document = Document.fromJson(jsonBody);

            System.out.println("Generating BPMN 2.0 model ...");
            BpmnModelInstance modelInstance = modelGenerator.getModelInstance(document);
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
