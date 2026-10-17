package it.univaq.disim.mrs.synthesis.core;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnPlane;
import org.camunda.bpm.model.bpmn.instance.bpmndi.BpmnShape;
import it.univaq.disim.mrs.synthesis.model.*;
import it.univaq.disim.mrs.synthesis.generator.BpmndiGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Step3BpmEnhancer {

    private static final Logger logger = LoggerFactory.getLogger(Step3BpmEnhancer.class);

    public void generateEnhancedModels(MissionContext context) {
        logger.info("--- [Step 3] 100% Pure Dynamic Top-Down Choreography Weaving ---");
        
        Map<String, List<InteractionMatch>> robotMatches = new HashMap<>();
        for (InteractionMatch m : context.getValidatedMatches()) {
            String sender = cleanName(m.getSenderRobot());
            String receiver = cleanName(m.getReceiverRobot());
            robotMatches.computeIfAbsent(sender, k -> new ArrayList<>()).add(m);
            robotMatches.computeIfAbsent(receiver, k -> new ArrayList<>()).add(m);
        }

        for (String robotKey : context.getAllRobotModels().keySet()) {
            String rName = cleanName(robotKey);
            BpmnModelInstance localModel = context.getAllRobotModels().get(robotKey);
            
            if (localModel != null && robotMatches.containsKey(rName)) {
                logger.info("Weaving dynamic choreography for robot: {}", rName);
                BpmnPlane plane = ensureDIInitialized(localModel);
                weaveDynamicFlow(context.getGlobalChoreography(), localModel, plane, rName, robotMatches.get(rName));
            }
        }
        logger.info("------------------------------------------------");
    }

    private void weaveDynamicFlow(BpmnModelInstance global, BpmnModelInstance local, BpmnPlane plane, String robotName, List<InteractionMatch> matches) {
        ServiceTask anchor = null;
        for (InteractionMatch m : matches) {
            if (cleanName(m.getSenderRobot()).equalsIgnoreCase(robotName)) {
                anchor = local.getModelElementById(m.getSenderTaskId());
                break;
            }
        }
        if (anchor == null) {
            anchor = local.getModelElementById(matches.get(0).getReceiverTaskId());
        }
        if (anchor == null) return;

        double currentX = getX(local, anchor) + 150;
        double currentY = getY(local, anchor);
        
        SequenceFlow oldOut = anchor.getOutgoing().isEmpty() ? null : anchor.getOutgoing().iterator().next();
        FlowNode nextLocal = null;
        if (oldOut != null) {
            nextLocal = oldOut.getTarget();
            anchor.getParentElement().removeChildElement(oldOut);
        }

        org.camunda.bpm.model.bpmn.instance.Process globalProcess = global.getModelElementById("Global_Choreography_Process");
        if (globalProcess == null) return;

        Map<String, FlowNode> injectedNodes = new LinkedHashMap<>();

        for (FlowElement element : globalProcess.getFlowElements()) {
            if (element instanceof Task) {
                Task globalTask = (Task) element;
                
                // NO HARDCODING: Dynamic Semantic Matching based on Task ID and Ontology Matches
                InteractionMatch match = findMatchForGlobalTask(globalTask, matches);
                
                if (match != null) {
                    // Automatically determine if the current robot is the sender or receiver
                    boolean isSender = cleanName(match.getSenderRobot()).equalsIgnoreCase(robotName);
                    String cleanMsg = match.getMessageName();
                    
                    Task localTask;
                    if (isSender) {
                        localTask = createTask(local, SendTask.class, "Invia: " + cleanMsg);
                    } else {
                        localTask = createTask(local, ReceiveTask.class, "Ricevi: " + cleanMsg);
                    }
                    
                    BpmndiGenerator.createShape(local, plane, localTask, currentX, currentY, 100, 80);
                    injectedNodes.put(element.getId(), localTask);
                    currentX += 150;
                }
            } else if ((element instanceof Gateway || element instanceof IntermediateCatchEvent) && isComplexChoreography(matches)) {
                FlowNode logicNode;
                if (element instanceof ParallelGateway) {
                    logicNode = createGateway(local, ParallelGateway.class, element.getName());
                } else if (element instanceof ExclusiveGateway) {
                    logicNode = createGateway(local, ExclusiveGateway.class, element.getName());
                } else {
                    logicNode = createTimer(local, element.getName());
                }
                
                double yOff = (element instanceof IntermediateCatchEvent) ? 100 : 15;
                double size = (element instanceof IntermediateCatchEvent) ? 36 : 50;
                BpmndiGenerator.createShape(local, plane, logicNode, currentX - 50, currentY + yOff, size, size);
                injectedNodes.put(element.getId(), logicNode);
                currentX += 100;
            }
        }

        Set<String> nodesWithOutgoing = new HashSet<>();
        for (SequenceFlow gFlow : globalProcess.getChildElementsByType(SequenceFlow.class)) {
            String srcId = gFlow.getSource().getId();
            String tgtId = gFlow.getTarget().getId();
            
            if (injectedNodes.containsKey(srcId) && injectedNodes.containsKey(tgtId)) {
                connect(local, plane, injectedNodes.get(srcId), injectedNodes.get(tgtId), gFlow.getName());
                nodesWithOutgoing.add(srcId);
            }
        }

        if (!injectedNodes.isEmpty()) {
            FlowNode firstNode = injectedNodes.values().iterator().next();
            connect(local, plane, anchor, firstNode, "");

            if (nextLocal != null) {
                for (Map.Entry<String, FlowNode> entry : injectedNodes.entrySet()) {
                    if (!nodesWithOutgoing.contains(entry.getKey())) {
                        connect(local, plane, entry.getValue(), nextLocal, "");
                    }
                }
                shiftRight(local, nextLocal, currentX);
            }
        }
    }

    // --- Dynamic Helper Methods ---

    // DYNAMIC MATCHING: Reads the Global Task ID and matches it with the Semantic Ontology Message Name
    private InteractionMatch findMatchForGlobalTask(Task globalTask, List<InteractionMatch> matches) {
        String taskId = globalTask.getId().toLowerCase();
        for (InteractionMatch m : matches) {
            if (taskId.contains(m.getMessageName().toLowerCase())) {
                return m;
            }
        }
        return null;
    }

    private String cleanName(String name) {
        return name.replace(".bpmn", "").replace("Part_", "").replace("_enhanced", "");
    }

    private boolean isComplexChoreography(List<InteractionMatch> matches) {
        return matches.size() >= 3;
    }

    private <T extends FlowNode> T createTask(BpmnModelInstance model, Class<T> clazz, String name) {
        T task = model.newInstance(clazz);
        task.setId(clazz.getSimpleName() + "_" + UUID.randomUUID().toString().substring(0,8));
        task.setName(name);
        model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next().addChildElement(task);
        return task;
    }

    private <T extends Gateway> T createGateway(BpmnModelInstance model, Class<T> clazz, String name) {
        T gw = model.newInstance(clazz);
        gw.setId(clazz.getSimpleName() + "_" + UUID.randomUUID().toString().substring(0,8));
        if (name != null) gw.setName(name);
        model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next().addChildElement(gw);
        return gw;
    }

    private IntermediateCatchEvent createTimer(BpmnModelInstance model, String name) {
        IntermediateCatchEvent timer = model.newInstance(IntermediateCatchEvent.class);
        timer.setId("Event_Timer_" + UUID.randomUUID().toString().substring(0,8));
        if (name != null) timer.setName(name);
        TimerEventDefinition def = model.newInstance(TimerEventDefinition.class);
        timer.getEventDefinitions().add(def);
        model.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class).iterator().next().addChildElement(timer);
        return timer;
    }

    private void connect(BpmnModelInstance model, BpmnPlane plane, FlowNode from, FlowNode to, String name) {
        SequenceFlow flow = model.newInstance(SequenceFlow.class);
        flow.setId("Flow_" + UUID.randomUUID().toString().substring(0,8));
        flow.setSource(from);
        flow.setTarget(to);
        if (name != null && !name.isEmpty()) flow.setName(name);
        from.getParentElement().addChildElement(flow);
        
        double w1 = from instanceof Gateway ? 50 : (from instanceof IntermediateCatchEvent ? 36 : 100);
        double h1 = from instanceof Gateway ? 50 : (from instanceof IntermediateCatchEvent ? 36 : 80);
        double w2 = to instanceof Gateway ? 50 : (to instanceof IntermediateCatchEvent ? 36 : 100);
        double h2 = to instanceof Gateway ? 50 : (to instanceof IntermediateCatchEvent ? 36 : 80);
        
        BpmndiGenerator.createEdge(model, plane, flow, getX(model, from) + w1, getY(model, from) + (h1 / 2), getX(model, to), getY(model, to) + (h2 / 2));
    }

    private void shiftRight(BpmnModelInstance model, FlowNode node, double targetX) {
        model.getModelElementsByType(BpmnShape.class).stream()
             .filter(s -> node.equals(s.getBpmnElement()))
             .findFirst()
             .ifPresent(s -> s.getBounds().setX(Math.max(s.getBounds().getX(), targetX + 150)));
    }

    private double getX(BpmnModelInstance m, org.camunda.bpm.model.bpmn.instance.BaseElement e) {
        return m.getModelElementsByType(BpmnShape.class).stream().filter(s -> e.equals(s.getBpmnElement())).map(s -> s.getBounds().getX()).findFirst().orElse(100.0);
    }

    private double getY(BpmnModelInstance m, org.camunda.bpm.model.bpmn.instance.BaseElement e) {
        return m.getModelElementsByType(BpmnShape.class).stream().filter(s -> e.equals(s.getBpmnElement())).map(s -> s.getBounds().getY()).findFirst().orElse(100.0);
    }

    private BpmnPlane ensureDIInitialized(BpmnModelInstance model) {
        return model.getModelElementsByType(BpmnPlane.class).stream().findFirst().orElse(null);
    }
}