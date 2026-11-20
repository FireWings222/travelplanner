package com.example.travelplanner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagesController {
    @GetMapping("/") public String home() { return "index"; }
    @GetMapping("/search") public String search() { return "search"; }
    @GetMapping("/itinerary") public String itinerary() { return "itinerary"; }
    @GetMapping("/tours") public String tours() { return "tours"; }
    @GetMapping("/compare") public String compare() { return "compare"; }
}
