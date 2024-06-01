package image;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import stream.StreamPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandLineImageGenerator implements ImageGenerator {
    public Optional<InputStream> imageFromBpmn(BpmnModelInstance modelInstance) throws IOException {
        File tempModelFile = File.createTempFile("bpmn-layouter-", ".bpmn.tmp");
        File tempOutputFile = File.createTempFile("bpmn-layouter-", ".png");

        tempModelFile.deleteOnExit();
        tempOutputFile.deleteOnExit();

        System.out.println("Writing model to temporary file ...");

        Bpmn.writeModelToFile(tempModelFile, modelInstance);

        System.out.println("Getting runtime ...");


        String delimiter;
        List<String> conversionCommand = new ArrayList<>();
        if (System.getProperty("os.name").startsWith("Windows")) {
            conversionCommand.add("cmd");
            conversionCommand.add("/c");
            delimiter = ";";
        } else {
            conversionCommand.add("/bin/bash");
            conversionCommand.add("-c");
            delimiter = ":";
        }
        conversionCommand.add(
                String.format(
                        "bpmn-to-image %s%s%s --no-footer",
                        tempModelFile.getAbsolutePath(),
                        delimiter,
                        tempOutputFile.getAbsolutePath()
                )
        );

        try {
            int exitCode = execCommand(conversionCommand.toArray(new String[0]));
            if(exitCode != 0) {
                return Optional.empty();
            }
        } catch(InterruptedException | IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }

        return Optional.of(new FileInputStream(tempOutputFile));
    }

    private int execCommand(String[] command) throws InterruptedException, IOException {
        Runtime runtime = Runtime.getRuntime();

        System.out.format("Running command \"%s\"...\n", String.join(" ", command));

        Process conversionProcess = runtime.exec(command);

        System.out.println("Setting up stream readers ...");
        new StreamPrinter(conversionProcess.getErrorStream(), "ERROR").start();
        new StreamPrinter(conversionProcess.getInputStream(), "INFO ").start();

        System.out.println("Waiting for command to exit ...");
        int exitCode = conversionProcess.waitFor();
        System.out.format("Command exited with code %d.\n", exitCode);

        return exitCode;
    }
}
