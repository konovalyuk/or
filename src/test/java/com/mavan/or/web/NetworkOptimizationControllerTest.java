package com.mavan.or.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NetworkOptimizationControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new NetworkOptimizationController()).build();

    @Test
    void exampleIsTheDubaiNetwork() throws Exception {
        mvc.perform(get("/api/network-optimization/example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations", hasSize(3)))
                .andExpect(jsonPath("$.locations[0]").value("Jebel Ali Port"))
                .andExpect(jsonPath("$.currency").value("AED"))
                .andExpect(jsonPath("$.ownedVehicles").value(nullValue()));
    }

    @Test
    void solveReturnsTheProvenPlanWithScenarios() throws Exception {
        String example = mvc.perform(get("/api/network-optimization/example")).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/network-optimization/solve").contentType(MediaType.APPLICATION_JSON).content(example))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.plan.globallyOptimal").value(true))
                .andExpect(jsonPath("$.plan.totalCost", closeTo(3958.0, 1e-6)))
                .andExpect(jsonPath("$.plan.lowerBound", closeTo(3958.0, 1e-6)))
                .andExpect(jsonPath("$.plan.gapPercent", closeTo(0.0, 1e-9)))
                .andExpect(jsonPath("$.plan.terminalLoads", hasSize(3)))
                .andExpect(jsonPath("$.plan.terminalLoads[1].transitTonnage", closeTo(10.0, 1e-6)))
                .andExpect(jsonPath("$.plan.explanation", hasSize(11)))
                .andExpect(jsonPath("$.scenarios.directOnly.totalCost", closeTo(4127.2, 1e-6)))
                .andExpect(jsonPath("$.savingsVsDirect", closeTo(169.2, 1e-6)));
    }

    @Test
    void emptyFleetLimitMeansUnlimited() throws Exception {
        String example = mvc.perform(get("/api/network-optimization/example")).andReturn().getResponse().getContentAsString();
        String request = example.replace("\"ownedVehicles\":null", "\"ownedVehicles\":[null,null,null]");
        mvc.perform(post("/api/network-optimization/solve").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.totalCost", closeTo(3958.0, 1e-6)));
    }

    @Test
    void discountBandsAreRejected() throws Exception {
        String example = mvc.perform(get("/api/network-optimization/example")).andReturn().getResponse().getContentAsString();
        String request = example.replace("\"discountBands\":null", "\"discountBands\":[{\"upToTonnage\":10,\"tariffPerKm\":5}]");
        mvc.perform(post("/api/network-optimization/solve").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
