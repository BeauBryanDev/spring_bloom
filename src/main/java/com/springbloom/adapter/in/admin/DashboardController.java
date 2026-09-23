package com.springbloom.adapter.in.admin;

import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.springbloom.domain.port.in.DashboardQueryUseCase;

import lombok.RequiredArgsConstructor;

/** The private sales overview behind /admin/**, authenticated against store_owner. */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private static final NumberFormat COP = NumberFormat.getIntegerInstance(new Locale("es", "CO"));

    private final DashboardQueryUseCase dashboardQueryUseCase;
//calls dashboardQueryUseCase.summarize() — that's the
//one method on the inbound port, returning a Summary object with
//the totals and other stats.
    @GetMapping("/admin")
    public String dashboard(Model model) {
        DashboardQueryUseCase.Summary summary = dashboardQueryUseCase.summarize();

        model.addAttribute("summary", summary);
        model.addAttribute("totalQuotedFormatted", COP.format(summary.totalQuoted().amount()));
        return "admin/dashboard";
    }  // the template is in /templates/admin/dashboard.html
}

// I dnot keep beusiness logic here     
// folowwing Hexagonal Architecture, the use case is injected into the controller
// I do not write direct DB accesss here, not business logic, but the use case
// that does the work, this is why I decide to  inject the use case into the controller.