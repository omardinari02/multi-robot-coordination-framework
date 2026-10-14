package it.univaq.disim.mrs.synthesis.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class OntologyManagerTest {

    private OntologyManager ontologyManager;

    @BeforeEach
    public void setUp() {
        ontologyManager = new OntologyManager();
        // Load the taxonomy.json file located in the resources folder
        File jsonFile = new File("src/main/resources/taxonomy.json");
        ontologyManager.loadOntology(jsonFile);
    }

    @Test
    public void testIsCompatible_ExactMatch() {
        // Must return true because the strings are exactly the same
        assertTrue(ontologyManager.isCompatible("Coordinate", "Coordinate"));
        assertTrue(ontologyManager.isCompatible("Boolean", "Boolean"));
    }

    @Test
    public void testIsCompatible_TaxonomyMatch() {
        // Must return true because "Coordinate" in taxonomy includes "weedPosition" and "closestTractor"
        assertTrue(ontologyManager.isCompatible("Coordinate", "weedPosition"));
        assertTrue(ontologyManager.isCompatible("Coordinate", "closestTractor"));
        
        // Must return true because "Boolean" in taxonomy includes "availability" and "fieldReady"
        assertTrue(ontologyManager.isCompatible("Boolean", "availability"));
        assertTrue(ontologyManager.isCompatible("Boolean", "fieldReady"));
    }

    @Test
    public void testIsCompatible_NoMatch() {
        // Must return false because there is no relationship defined in the taxonomy
        assertFalse(ontologyManager.isCompatible("Coordinate", "digDone"));
        assertFalse(ontologyManager.isCompatible("RandomData", "weedPosition"));
        assertFalse(ontologyManager.isCompatible(null, "weedPosition"));
    }
}