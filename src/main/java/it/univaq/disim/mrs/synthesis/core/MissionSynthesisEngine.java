package it.univaq.disim.mrs.synthesis.core;

import it.univaq.disim.mrs.synthesis.model.*;
import it.univaq.disim.mrs.synthesis.persistence.ModelRepository;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class MissionSynthesisEngine {
    
    // ADDED: Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(MissionSynthesisEngine.class);
    
    private final ModelRepository repository;
    private final OntologyManager ontologyManager;
    private final Step1IOAnalyzer ioAnalyzer;
    private final Step2SemanticMatcher semanticMatcher;
    private final Step3BpmEnhancer bpmEnhancer;

    public MissionSynthesisEngine() {
        this.repository = new ModelRepository();
        this.ontologyManager = new OntologyManager();
        this.ioAnalyzer = new Step1IOAnalyzer();
        this.semanticMatcher = new Step2SemanticMatcher();
        this.bpmEnhancer = new Step3BpmEnhancer();
    }

    public void runSynthesisPipeline(List<File> localProcesses, File globalChoreo, File ontologyFile) {
        logger.info("=== [Engine] Starting Automatic Synthesis Pipeline ===");

        MissionContext context = new MissionContext();
        context.setGlobalChoreography(repository.loadModel(globalChoreo));
        ontologyManager.loadOntology(ontologyFile);

        for (File robotFile : localProcesses) {
            String fileName = robotFile.getName();
            BpmnModelInstance model = repository.loadModel(robotFile);
            context.addRobotModel(fileName, model);
            
            logger.info("Analyzing operational tasks for: {}", fileName);
            List<RobotTaskMetadata> metadata = ioAnalyzer.analyze(fileName, model);
            context.getExtractedMetadata().addAll(metadata);
        }

        logger.info("Executing Semantic Matchmaking (Subsumption)...");
        semanticMatcher.findMatches(context, ontologyManager);

        logger.info("Transforming to Enhanced Diagrams and managing Gateways...");
        bpmEnhancer.generateEnhancedModels(context);

        logger.info("[Export] Saving synthesized artifacts...");
        
        context.getAllRobotModels().forEach((fileName, model) -> {
            String enhancedName = fileName.replace(".bpmn", "_enhanced.bpmn");
            repository.saveModel(model, enhancedName);
        });

        repository.saveUnifiedDiagram(context);
        logger.info("=== [Engine] Pipeline finished successfully. Check the output folder. ===");
    }
}