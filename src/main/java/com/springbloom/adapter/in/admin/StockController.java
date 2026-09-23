package com.springbloom.adapter.in.admin;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.springbloom.domain.model.FlowerStockStatus;
import com.springbloom.domain.port.in.ListCatalogUseCase;
import com.springbloom.domain.port.in.UpdateStockUseCase;
import com.springbloom.domain.port.in.UpdateStockUseCase.UpdateStockCommand;

import lombok.RequiredArgsConstructor;

/** Admin editing of price, quantity and status per species. */
@Controller
@RequiredArgsConstructor
public class StockController {

    private final ListCatalogUseCase listCatalogUseCase;
    private final UpdateStockUseCase updateStockUseCase;

    @GetMapping("/admin/stock")
    public String list(Model model) {
        model.addAttribute("entries", listCatalogUseCase.listAll());
        return "admin/stock-list";
    }  //GET /admin/stock — lists every species (listAll()) → admin/stock-list table view.

    @GetMapping("/admin/stock/{speciesKey}")  //GET /admin/stock/{speciesKey} — edit form for one species.
    public String edit(@PathVariable String speciesKey, Model model) {
        ListCatalogUseCase.CatalogEntry entry = listCatalogUseCase.findByKey(speciesKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)
            ); //404 if not found

        model.addAttribute("entry", entry);
        model.addAttribute("statuses", FlowerStockStatus.values());
        return "admin/stock-edit";
    }

    @PostMapping("/admin/stock/{speciesKey}")
    public String update(   //POST /admin/stock/{speciesKey} — update one species.
            @PathVariable String speciesKey,
            @RequestParam FlowerStockStatus status,
            @RequestParam int quantity,
            @RequestParam(required = false) Integer etaDays,
            @RequestParam BigDecimal basePrice,
            @RequestParam BigDecimal importPriceMultiplier) {

        updateStockUseCase.update(new UpdateStockCommand(
                speciesKey, 
                status, 
                quantity, 
                etaDays, 
                basePrice, 
                importPriceMultiplier
            ));

        return "redirect:/admin/stock";
    }
}
