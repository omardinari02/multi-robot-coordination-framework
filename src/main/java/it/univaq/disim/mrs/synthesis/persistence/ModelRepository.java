package it.univaq.disim.mrs.synthesis.persistence;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import it.univaq.disim.mrs.synthesis.model.MissionContext;
import it.univaq.disim.mrs.synthesis.generator.UnifiedDiagramBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ModelRepository {
    private static final Logger logger = LoggerFactory.getLogger(ModelRepository.class);
    
    private final UnifiedDiagramBuilder unifiedBuilder = new UnifiedDiagramBuilder();
    private static final String OUTPUT_DIR = "target/output/";

    public BpmnModelInstance loadModel(File file) {
        if (!file.exists()) {
            throw new RuntimeException("File not found: " + file.getAbsolutePath());
        }
        try {
            return Bpmn.readModelFromFile(file);
        } catch (Exception e) {
            logger.error("[Parser Error] Error in file {}: {}", file.getName(), e.getMessage());
            throw new RuntimeException("Fatal parsing error in: " + file.getName(), e);
        }
    }

    public void saveModel(BpmnModelInstance model, String fileName) {
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) directory.mkdirs();
            Bpmn.writeModelToFile(new File(OUTPUT_DIR + fileName), model);
            logger.info("[Output] File saved: {}", fileName);
        } catch (Exception e) {
            logger.error("Error saving {}: {}", fileName, e.getMessage());
        }
    }

    public void saveUnifiedDiagram(MissionContext context) {
        logger.info("Generating Unified Collaboration Diagram...");
        try {
            File directory = new File(OUTPUT_DIR);
            if (!directory.exists()) directory.mkdirs();
            BpmnModelInstance unified = unifiedBuilder.build(context);
            Bpmn.writeModelToFile(new File(OUTPUT_DIR + "UnifiedMission.bpmn"), unified);
            logger.info("[Output] UnifiedMission.bpmn saved in {}", OUTPUT_DIR);
        } catch (Exception e) {
            logger.error("Error generating unified diagram: {}", e.getMessage());
        }
    }
}