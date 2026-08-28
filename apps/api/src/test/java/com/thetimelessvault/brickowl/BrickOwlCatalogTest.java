package com.thetimelessvault.brickowl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrickOwlCatalogTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesTheOnlySetBoid() throws Exception {
        var lookup = mapper.readTree("""
                {
                  "boids": [
                    {
                      "boid": "12345-38",
                      "name": "Millennium Falcon",
                      "ids": [{ "id_type": "set_number", "id": "75192-1" }]
                    }
                  ]
                }
                """);

        assertEquals("12345-38", BrickOwlCatalog.resolveSetBoid(lookup, "75192-1"));
    }

    @Test
    void prefersExactSetNumberWhenSeveralMatch() throws Exception {
        var lookup = mapper.readTree("""
                {
                  "boids": [
                    {
                      "boid": "111",
                      "name": "Falcon UCS",
                      "ids": [{ "id_type": "set_number", "id": "75192-1" }]
                    },
                    {
                      "boid": "222",
                      "name": "Falcon polybag",
                      "ids": [{ "id_type": "set_number", "id": "75192-2" }]
                    }
                  ]
                }
                """);

        assertEquals("111", BrickOwlCatalog.resolveSetBoid(lookup, "75192-1"));
    }

    @Test
    void failsWhenSeveralBoidsRemainAmbiguous() throws Exception {
        var lookup = mapper.readTree("""
                {
                  "boids": [
                    { "boid": "111", "name": "Falcon UCS" },
                    { "boid": "222", "name": "Falcon polybag" }
                  ]
                }
                """);

        ApiException error = assertThrows(ApiException.class, () -> BrickOwlCatalog.resolveSetBoid(lookup, "75192-1"));
        assertTrue(error.getMessage().contains("multiple catalog matches"));
        assertTrue(error.getMessage().contains("Falcon UCS"));
        assertTrue(error.getMessage().contains("Falcon polybag"));
    }

    @Test
    void failsWhenTheSetIsMissing() throws Exception {
        var lookup = mapper.readTree("""
                { "boids": [] }
                """);

        ApiException error = assertThrows(ApiException.class, () -> BrickOwlCatalog.resolveSetBoid(lookup, "75192-1"));
        assertTrue(error.getMessage().contains("no catalog match"));
        assertTrue(error.getMessage().contains("75192-1"));
    }
}
