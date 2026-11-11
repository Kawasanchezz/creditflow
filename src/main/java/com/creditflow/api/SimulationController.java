package com.creditflow.api;

import com.creditflow.simulation.SimulationResult;
import com.creditflow.simulation.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {
    private final SimulationService service;

    public SimulationController(SimulationService service) {
        this.service = service;
    }

    @PostMapping
    public SimulationResult simulate(@Valid @RequestBody ApiDtos.SimulationRequest r) {
        return service.simulate(r.system(), r.amount(), r.monthlyRate(), r.term());
    }
}
