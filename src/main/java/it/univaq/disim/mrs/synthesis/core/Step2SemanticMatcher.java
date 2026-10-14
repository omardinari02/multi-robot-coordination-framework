package it.univaq.disim.mrs.synthesis.core;

import org.camunda.bpm.model.bpmn.instance.*;
import it.univaq.disim.mrs.synthesis.model.*;
import it.univaq.disim.mrs.synthesis.semantic.OntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Step2SemanticMatcher {
    // ADDED: Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(Step2SemanticMatcher.class);

    public void findMatches(MissionContext context, OntologyManager ontology) {
        for (RobotTaskMetadata pMeta : context.getExtractedMetadata()) {
            if (pMeta.getProducedDataType() == null) continue;

            for (MessageFlow mf : context.getGlobalChoreography().getModelElementsByType(MessageFlow.class)) {
                String reqMsgName = mf.getMessage().getName();
                
                String targetRobot = getParticipantName(mf.getTarget());
                String sourceRobot = getParticipantName(mf.getSource());

                if (!pMeta.getRobotId().toLowerCase().contains(sourceRobot.toLowerCase())) {
                    continue; 
                }

                if (ontology.isCompatible(pMeta.getProducedDataType(), reqMsgName)) {
                    boolean receiverFound = false;

                    for (RobotTaskMetadata cMeta : context.getExtractedMetadata()) {
                        if (cMeta.getRobotId().toLowerCase().contains(targetRobot.toLowerCase())) {
                            if (cMeta.getRequiredDataTypes() != null && cMeta.getRequiredDataTypes().contains(reqMsgName)) {
                                context.getValidatedMatches().add(new InteractionMatch(
                                    pMeta.getRobotId(), pMeta.getTaskId(), targetRobot, cMeta.getTaskId(), reqMsgName
                                ));
                                logger.info("  [Perfect Match] {} -> {} (Task: {})", pMeta.getRobotId(), targetRobot, cMeta.getTaskId());
                                receiverFound = true;
                                break;
                            }
                        }
                    }

                    if (!receiverFound) {
                        for (RobotTaskMetadata cMeta : context.getExtractedMetadata()) {
                            if (cMeta.getRobotId().toLowerCase().contains(targetRobot.toLowerCase())) {
                                context.getValidatedMatches().add(new InteractionMatch(
                                    pMeta.getRobotId(), pMeta.getTaskId(), targetRobot, cMeta.getTaskId(), reqMsgName
                                ));
                                logger.info("  [Fallback Match] {} -> {} (Default Task: {})", pMeta.getRobotId(), targetRobot, cMeta.getTaskId());
                                break; 
                            }
                        }
                    }
                }
            }
        }
    }

    private String getParticipantName(org.camunda.bpm.model.bpmn.instance.InteractionNode node) {
        if (node instanceof org.camunda.bpm.model.bpmn.instance.Participant) {
            return ((org.camunda.bpm.model.bpmn.instance.Participant) node).getName();
        }
        return node.getId();
    }
}