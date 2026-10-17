package it.univaq.disim.mrs.synthesis.generator;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import it.univaq.disim.mrs.synthesis.model.*;

import java.util.HashMap;
import java.util.Map;

public class UnifiedDiagramBuilder {

    public BpmnModelInstance build(MissionContext context) {
        BpmnModelInstance modelInstance = Bpmn.createEmptyModel();
        
        Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://it.univaq.disim/mrs/unified");
        modelInstance.setDefinitions(definitions);
        
        Collaboration collaboration = modelInstance.newInstance(Collaboration.class);
        collaboration.setId("Collaboration_Global_MRS");
        definitions.addChildElement(collaboration);
        
        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        plane.setBpmnElement(collaboration);
        diagram.setBpmnPlane(plane);
        definitions.addChildElement(diagram);
        
        Map<String, Participant> pools = new HashMap<>();
        double currentY = 100;
        
        // 1. Creazione dei Processi vuoti e dei Pool (Black-box)
        for (Map.Entry<String, BpmnModelInstance> entry : context.getAllRobotModels().entrySet()) {
            String robotKey = entry.getKey().replace(".bpmn", "").replace("_enhanced", "");
            
            // Crea il processo
            org.camunda.bpm.model.bpmn.instance.Process unifiedProcess = modelInstance.newInstance(org.camunda.bpm.model.bpmn.instance.Process.class);
            unifiedProcess.setId("Process_" + robotKey);
            unifiedProcess.setExecutable(true);
            definitions.addChildElement(unifiedProcess);
            
            // Crea il Participant (Pool) collegato al Processo
            Participant pool = modelInstance.newInstance(Participant.class);
            pool.setName(robotKey);
            pool.setId("Pool_" + robotKey);
            pool.setProcess(unifiedProcess); 
            collaboration.addChildElement(pool);
            pools.put(robotKey.toLowerCase(), pool);
            
            BpmndiGenerator.createShape(modelInstance, plane, pool, 100, currentY, 800, 150);
            currentY += 200;
        }
        
        // 2. Creazione dei MessageFlow tra i Pool con il NOME DEL MESSAGGIO
        for (InteractionMatch match : context.getValidatedMatches()) {
            String senderRobot = match.getSenderRobot().replace(".bpmn", "").replace("_enhanced", "").toLowerCase();
            String receiverRobot = match.getReceiverRobot().replace(".bpmn", "").replace("_enhanced", "").toLowerCase();
            
            Participant sourcePool = pools.get(senderRobot);
            Participant targetPool = pools.get(receiverRobot);
            
            if (sourcePool != null && targetPool != null) {
                MessageFlow msgFlow = modelInstance.newInstance(MessageFlow.class);
                msgFlow.setId("Flow_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                
                // Colleghiamo i Pool tra loro
                msgFlow.setSource(sourcePool);
                msgFlow.setTarget(targetPool);
                
                // AGGIUNTA FONDAMENTALE: Impostiamo il nome del messaggio sulla freccia
                msgFlow.setName(match.getMessageName());
                
                collaboration.addChildElement(msgFlow);
            }
        }
        
        return modelInstance;
    }
}