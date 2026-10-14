package it.univaq.disim.mrs.synthesis.core;

import it.univaq.disim.mrs.synthesis.model.RobotTaskMetadata;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Step1IOAnalyzerTest {

    @Test
    public void testAnalyze_ExtractsCorrectMetadata() {
        Step1IOAnalyzer analyzer = new Step1IOAnalyzer();
        
        // NUOVO PERCORSO AGGIORNATO
        File droneFile = new File("src/main/resources/bpmn/Drone.bpmn");
        assertTrue(droneFile.exists(), "Il file Drone.bpmn deve esistere nella cartella resources/bpmn/");

        BpmnModelInstance model = Bpmn.readModelFromFile(droneFile);
        List<RobotTaskMetadata> metadataList = analyzer.analyze("Drone.bpmn", model);

        assertNotNull(metadataList);
        assertFalse(metadataList.isEmpty(), "L'analyzer deve trovare almeno un task");

        boolean foundCoordinateTask = false;
        for (RobotTaskMetadata meta : metadataList) {
            if ("Task_Coordinate".equals(meta.getTaskId())) {
                foundCoordinateTask = true;
                assertEquals("Coordinate", meta.getProducedDataType(), "Il producedDataType deve essere 'Coordinate'");
            }
        }
        assertTrue(foundCoordinateTask, "L'analyzer deve trovare 'Task_Coordinate'");
    }
}