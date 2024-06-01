package http;

import diagram.BpmnDiagramGenerator;
import image.CommandLineImageGenerator;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;

public class BpmnToPngConversionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        AsyncContext context = req.startAsync();
        ServletOutputStream output = resp.getOutputStream();

        output.setWriteListener(new WriteListener() {
            @Override
            public void onWritePossible() throws IOException {
                BpmnDiagramGenerator diagramGenerator = new BpmnDiagramGenerator();
                CommandLineImageGenerator imageGenerator = new CommandLineImageGenerator();

                try {
                    System.out.println("Reading body as model ...");
                    BpmnModelInstance modelInstance = Bpmn.readModelFromStream(req.getInputStream());

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

                    System.out.format("Sending response of %d ...\n", content.length());

                    resp.setContentType("text/html");
                    resp.setStatus(HttpServletResponse.SC_OK);

                    resp.getWriter().println(content);

                    System.out.println("Request handled successfully!");
                } catch (Exception e) {
                    e.printStackTrace();
                    String response = e.toString();

                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().println(response);
                }
            }

            @Override
            public void onError(Throwable t) {
                context.complete();
            }
        });

    }
}
