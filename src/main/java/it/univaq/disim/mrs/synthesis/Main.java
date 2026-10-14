package it.univaq.disim.mrs.synthesis;

import it.univaq.disim.mrs.synthesis.core.MissionSynthesisEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        MissionSynthesisEngine engine = new MissionSynthesisEngine();
        
        // NUOVI PERCORSI: Ontologia in resources, BPMN in resources/bpmn/
        File ontology = new File("src/main/resources/taxonomy.json");
        File bpmnDir = new File("src/main/resources/bpmn/");
        
        File global = null;
        List<File> localProcesses = new ArrayList<>();
        
        if (!ontology.exists()) {
            logger.error("File taxonomy.json mancante in src/main/resources/.");
            return;
        }

        File[] files = bpmnDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                
                if (name.toLowerCase().contains("global") && name.endsWith(".bpmn")) {
                    global = file;
                } else if (name.endsWith(".bpmn") && !name.contains("enhanced") && !name.contains("Unified")) {
                    localProcesses.add(file);
                }
            }
        }
        
        if (global == null || localProcesses.isEmpty()) {
            logger.error("File BPMN mancanti. Assicurati che i file siano in src/main/resources/bpmn/.");
            return;
        }
        
        logger.info("Trovati {} processi locali da eseguire.", localProcesses.size());
        
        engine.runSynthesisPipeline(localProcesses, global, ontology);
    }
}