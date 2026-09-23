package com.springbloom.adapter.in.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.springbloom.domain.model.Complaint;
import com.springbloom.domain.model.ComplaintStatus;
import com.springbloom.domain.port.in.ReviewComplaintsUseCase;

import lombok.RequiredArgsConstructor;

/**
 * The shop's side of a claim Florabelle filed: she may record one, never
 * close it - only this screen may take one into review or resolve/reject it.
 */
@Controller
@RequiredArgsConstructor // this is dependency injection  - constructor,
    //Spring wires the real implementation in for me.
public class AdminQueryController {

    private final ReviewComplaintsUseCase reviewComplaintsUseCase;

    @GetMapping("/admin/complaints")
    public String list(Model model) {
        model.addAttribute("complaints", reviewComplaintsUseCase.listAll());
        return "admin/complaints";
    }

    @PostMapping("/admin/complaints/{complaintNumber}/review")
    public String takeIntoReview(@PathVariable String complaintNumber) {
        find(complaintNumber, () -> reviewComplaintsUseCase.takeIntoReview(complaintNumber));
        return "redirect:/admin/complaints";
    }

    @PostMapping("/admin/complaints/{complaintNumber}/resolve")
    public String resolve(
            @PathVariable String complaintNumber,
            @RequestParam ComplaintStatus outcome,
            @RequestParam String resolution) {

        find(complaintNumber, () -> reviewComplaintsUseCase.resolve(complaintNumber, outcome, resolution));
        return "redirect:/admin/complaints";
    }

    // helper if the use case throughs an exception
    private void find(String complaintNumber, java.util.function.Supplier<Complaint> action) {
        try {
            action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}

// folowwing Hexagonal Architecture, the use case is injected into the controller
// I do not write direct DB accesss here, not business logic, but the use case
// that does the work.