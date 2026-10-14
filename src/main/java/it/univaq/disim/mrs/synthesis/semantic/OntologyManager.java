package it.univaq.disim.mrs.synthesis.semantic;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OntologyManager {
    
    // ADDED: Initialize SLF4J Logger
    private static final Logger logger = LoggerFactory.getLogger(OntologyManager.class);
    
    private Map<String, List<String>> taxonomy = new HashMap<>();

    public void loadOntology(File file) {
        try {
            File jsonFile = file;
            
            if (jsonFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                taxonomy = mapper.readValue(jsonFile, new TypeReference<Map<String, List<String>>>(){});
                // FIXED: Replace System.out.println with logger.info
                logger.info("[Semantic] taxonomy.json successfully loaded.");
            } else {
                logger.error("[Error] Cannot find JSON file: {}", jsonFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("[Error] JSON Parsing error: {}", e.getMessage());
        }
    }

    public boolean isCompatible(String produced, String required) {
        if (produced == null || required == null) return false;
        String p = produced.trim();
        String r = required.trim();

        // 1. Direct match
        if (p.equalsIgnoreCase(r)) {
            logger.debug("[MATCH] Exact: {} == {}", p, r);
            return true;
        }

        // 2. Ontology match via JSON
        if (taxonomy.containsKey(p) && taxonomy.get(p).contains(r)) {
            logger.debug("[MATCH] Found in JSON: {} includes {}", p, r);
            return true;
        }
        
        logger.debug("[NO MATCH] {} is not compatible with {}", p, r);
        return false;
    }
}