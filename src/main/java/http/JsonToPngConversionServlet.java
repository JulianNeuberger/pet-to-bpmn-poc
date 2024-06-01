package http;

import data.Document;
import diagram.BpmnDiagramGenerator;
import image.CommandLineImageGenerator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.BpmnModelGenerator;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;

public class JsonToPngConversionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        BpmnModelGenerator modelGenerator = new BpmnModelGenerator();
        BpmnDiagramGenerator diagramGenerator = new BpmnDiagramGenerator();
        CommandLineImageGenerator imageGenerator = new CommandLineImageGenerator();

        try {
            System.out.println("Reading json ...");
            String jsonBody = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
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
            String content = Base64.getEncoder().encodeToString(image);
            content = "data:image/png;base64," + content;
            System.out.format("Sending response of %d bytes ...\n", content.length());

            resp.setContentType("text/html");
            resp.setStatus(HttpServletResponse.SC_OK);


            resp.getWriter().println(content);
            System.out.println("Request handled successfully!");
        } catch (Exception e) {
            e.printStackTrace();

            resp.getWriter().println(e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
