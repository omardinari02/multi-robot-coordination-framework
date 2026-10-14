package it.univaq.disim.mrs.synthesis.core;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnDiagram;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import it.univaq.disim.mrs.synthesis.model.*;
import it.univaq.disim.mrs.synthesis.generator.BpmndiGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Step3BpmEnhancer {
    // ADDED: Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(Step3BpmEnhancer.class);

    public void generateEnhancedModels(MissionContext context) {
        logger.info("--- [Step 3] Starting task injection (Enhancer) ---");

        Map<String, List<InteractionMatch>> taskOutputs = new HashMap<>();
        context.getValidatedMatches().forEach(m -> 
            taskOutputs.computeIfAbsent(m.getSenderTaskId(), k -> new ArrayList<>()).add(m));

        taskOutputs.forEach((taskId, matches) -> {
            String robotName = matches.get(0).getSenderRobot();
            BpmnModelInstance model = getRobotModel(context, robotName);

            if (model != null) {
                BpmnPlane plane = ensureDIInitialized(model);
                if (matches.size() > 1) {
                    logger.info(" [Enhancer] Injecting Parallel Gateway for task: {} in robot: {}", taskId, robotName);
                    injectParallelGateway(model, plane, taskId, matches);
                } else {
                    logger.info(" [Enhancer] Injecting SendTask for: {} after {}", matches.get(0).getMessageName(), taskId);
                    injectSingleTask(model, plane, taskId, matches.get(0).getMessageName(), true);
                }
            } else {
                logger.error(" [Error] BPMN Model not found for sender: {}", robotName);
            }
        });

        for (InteractionMatch m : context.getValidatedMatches()) {
            String robotName = m.getReceiverRobot();
            BpmnModelInstance model = getRobotModel(context, robotName);

            if (model != null) {
                logger.info(" [Enhancer] Injecting ReceiveTask for: {} before {}", m.getMessageName(), m.getReceiverTaskId());
                BpmnPlane plane = ensureDIInitialized(model);
                injectSingleTask(model, plane, m.getReceiverTaskId(), m.getMessageName(), false);
            } else {
                logger.error(" [Error] BPMN Model not found for receiver: {}", robotName);
            }
        }
        logger.info("------------------------------------------------");
    }

    private BpmnModelInstance getRobotModel(MissionContext context, String robotName) {
        Map<String, BpmnModelInstance> models = context.getAllRobotModels();
        if (models.containsKey(robotName)) return models.get(robotName);
        if (models.containsKey(robotName + ".bpmn")) return models.get(robotName + ".bpmn");

        String cleanName = robotName.replace("Part_", "");
        if (models.containsKey(cleanName)) return models.get(cleanName);
        if (models.containsKey(cleanName + ".bpmn")) return models.get(cleanName + ".bpmn");

        for (String key : models.keySet()) {
            if (key.toLowerCase().contains(cleanName.toLowerCase())) return models.get(key);
        }
        return null;
    }

    private void injectParallelGateway(BpmnModelInstance model, BpmnPlane plane, String taskId, List<InteractionMatch> matches) {
        ServiceTask source = model.getModelElementById(taskId);
        if (source == null) return;

        ParallelGateway fork = model.newInstance(ParallelGateway.class);
        fork.setId("Fork_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        source.getParentElement().addChildElement(fork);

        double sourceX = getX(model, source);
        double sourceY = getY(model, source);

        BpmndiGenerator.createShape(model, plane, fork, sourceX + 150, sourceY + 15, 50, 50);
        reconnect(model, plane, source, fork);

        double startY = sourceY - (matches.size() * 50);
        for (int i = 0; i < matches.size(); i++) {
            InteractionMatch m = matches.get(i);
            SendTask send = model.newInstance(SendTask.class);
            send.setId("Send_" + java.util.UUID.randomUUID().toString().replace("-", ""));
            send.setName("Invia: " + m.getMessageName());
            fork.getParentElement().addChildElement(send);

            BpmndiGenerator.createShape(model, plane, send, sourceX + 300, startY + (i * 100), 100, 80);
            connect(model, plane, fork, send);
        }
    }

    private void injectSingleTask(BpmnModelInstance model, BpmnPlane plane, String taskId, String msg, boolean isSender) {
        ServiceTask task = model.getModelElementById(taskId);
        if (task == null) {
            logger.error(" [Error] Task ID '{}' not found in BPMN model!", taskId);
            return;
        }

        Task interaction = isSender ? model.newInstance(SendTask.class) : model.newInstance(ReceiveTask.class);
        interaction.setId((isSender ? "Send_" : "Receive_") + java.util.UUID.randomUUID().toString().replace("-", ""));
        interaction.setName((isSender ? "Invia: " : "Ricevi: ") + msg);
        task.getParentElement().addChildElement(interaction);

        double x = getX(model, task);
        double y = getY(model, task);

        BpmndiGenerator.createShape(model, plane, interaction, isSender ? x + 150 : x - 150, y, 100, 80);

        if (isSender) reconnect(model, plane, task, interaction); 
        else reconnectReceiver(model, plane, task, interaction);
    }

    private BpmnPlane ensureDIInitialized(BpmnModelInstance model) {
        BpmnPlane plane = model.getModelElementsByType(BpmnPlane.class).stream().findFirst().orElse(null);
        if (plane == null) {
            BpmnDiagram diagram = model.newInstance(BpmnDiagram.class);
            plane = model.newInstance(BpmnPlane.class);
            org.camunda.bpm.model.bpmn.instance.Process process = model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next();
            plane.setBpmnElement(process);
            diagram.setBpmnPlane(plane);
            model.getDefinitions().addChildElement(diagram);
        }
        return plane;
    }

    private void reconnect(BpmnModelInstance model, BpmnPlane plane, FlowNode src, FlowNode trg) {
        if (!src.getOutgoing().isEmpty()) {
            SequenceFlow out = src.getOutgoing().iterator().next();
            out.setSource(trg); 
        }
        connect(model, plane, src, trg);
    }

    private void reconnectReceiver(BpmnModelInstance model, BpmnPlane plane, FlowNode cons, FlowNode rec) {
        if (!cons.getIncoming().isEmpty()) {
            SequenceFlow in = cons.getIncoming().iterator().next();
            in.setTarget(rec);
        }
        connect(model, plane, rec, cons);
    }

    private void connect(BpmnModelInstance model, BpmnPlane plane, FlowNode from, FlowNode to) {
        SequenceFlow flow = model.newInstance(SequenceFlow.class);
        flow.setId("Flow_Injected_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        flow.setSource(from);
        flow.setTarget(to);
        from.getParentElement().addChildElement(flow);

        double w1 = from instanceof Gateway ? 50 : 100;
        double h1 = from instanceof Gateway ? 50 : 80;
        double w2 = to instanceof Gateway ? 50 : 100;
        double h2 = to instanceof Gateway ? 50 : 80;

        double startX = getX(model, from) + w1;      
        double startY = getY(model, from) + (h1 / 2); 
        double endX = getX(model, to);               
        double endY = getY(model, to) + (h2 / 2);    

        BpmndiGenerator.createEdge(model, plane, flow, startX, startY, endX, endY);
    }

    private double getX(BpmnModelInstance m, BaseElement e) {
        return m.getModelElementsByType(BpmnShape.class).stream()
             .filter(s -> e.equals(s.getBpmnElement()))
             .map(s -> s.getBounds().getX()).findFirst().orElse(100.0);
    }

    private double getY(BpmnModelInstance m, BaseElement e) {
        return m.getModelElementsByType(BpmnShape.class).stream()
             .filter(s -> e.equals(s.getBpmnElement()))
             .map(s -> s.getBounds().getY()).findFirst().orElse(100.0);
    }
}