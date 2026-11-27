package com.springbloom.adapter.in.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.springbloom.domain.model.Creation;
import com.springbloom.domain.model.ProductType;
import com.springbloom.domain.model.vo.Money;
import com.springbloom.domain.port.in.ListCatalogUseCase;
import com.springbloom.domain.port.in.ListCatalogUseCase.CatalogEntry;
import com.springbloom.domain.port.in.ListCreationsUseCase;
import com.springbloom.adapter.in.web.CatalogFilters.Availability;
import com.springbloom.adapter.in.web.CatalogFilters.PriceBand;
import com.springbloom.adapter.in.web.CatalogFilters.SortOrder;
import com.springbloom.domain.service.pricing.BouquetDiscountPolicy;
import com.springbloom.domain.service.pricing.PricingStrategyFactory;

/** The public storefront: the home preview grid and the full catalog page. */
@Controller
public class CatalogController {

    private static final int FEATURED_LIMIT = 12;
    private static final int RELATED_LIMIT = 4;

    /**
     * The reference bundle shown on a card - what 15 stems of this one species
     * would cost as a bouquet. It is a display figure, not a quotation: the real
     * document is composed by QuotationComposer from what the customer asks for.
     * The discount is applied through the bouquet strategy so the card and the
     * quotation can never disagree on the arithmetic.
     */
    private static final int BOUQUET_PREVIEW_STEMS = 15;

    private final ListCatalogUseCase listCatalog;
    private final PricingStrategyFactory pricing;
    private final ListCreationsUseCase listCreations;

    public CatalogController(ListCatalogUseCase listCatalog, 
                            PricingStrategyFactory pricing,
                            ListCreationsUseCase listCreations) {
        this.listCatalog = listCatalog;
        this.pricing = pricing;
        this.listCreations = listCreations;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("flowers", toView(listCatalog.listFeatured(FEATURED_LIMIT)));
        model.addAttribute("activePage", "inicio");
        return "pages/index";
    }

    /**
     * The whole shelf, NOT_FOR_SALE included: a customer may see what is not
     * sold. The three parameters are the pills and the two dropdowns, and they
     * are in the URL so a narrowed shelf can be linked and reloaded.
     */
    @GetMapping("/catalogo")
    public String catalog(
            Model model,
            @RequestParam(name = "disponibilidad", required = false) String availabilityParam,
            @RequestParam(name = "precio", required = false) String priceParam,
            @RequestParam(name = "orden", required = false) String sortParam) {

        Availability availability =
                CatalogFilters.parse(Availability.class, availabilityParam, Availability.TODOS);
        PriceBand priceBand = CatalogFilters.parse(PriceBand.class, priceParam, PriceBand.TODOS);
        SortOrder sort = CatalogFilters.parse(SortOrder.class, sortParam, SortOrder.NOMBRE_ASC);

        List<CatalogEntry> shelf = listCatalog.listAll().stream()
                .filter(availability.matches())
                .filter(priceBand.matches())
                .sorted(sort.comparator())
                .toList();

        model.addAttribute("flowers", toView(shelf));
        model.addAttribute("availabilities", Availability.values());
        model.addAttribute("priceBands", PriceBand.values());
        model.addAttribute("sortOrders", SortOrder.values());
        model.addAttribute("selectedAvailability", availability.name());
        model.addAttribute("selectedPriceBand", priceBand.name());
        model.addAttribute("selectedSort", sort.name());
        model.addAttribute("garlands", creationsOf(ProductType.GARLAND));
        model.addAttribute("crowns", creationsOf(ProductType.CROWN));
        model.addAttribute("activePage", "catalogo");
        return "pages/catalog";
    }

    /**
     * The product page. Keyed on species_key rather than species_id: the key is
     * already the unique, stable, readable identifier the whole codebase uses,
     * and a numeric id in a public URL is a database detail nobody asked to see.
     */
    /** The garlands and crowns on their own page, at their fixed prices from the creation table. */
    @GetMapping("/arreglos")
    public String arrangements(Model model) {
        model.addAttribute("garlands", creationsOf(ProductType.GARLAND));
        model.addAttribute("crowns", creationsOf(ProductType.CROWN));
        model.addAttribute("activePage", "arreglos");
        return "pages/arrangements";
    }

    @GetMapping("/flores/{speciesKey}")
    public String flower(@PathVariable String speciesKey, Model model) {
        CatalogEntry entry = listCatalog.findByKey(speciesKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No conocemos esa flor"));

        model.addAttribute("flower", toDetail(entry));
        model.addAttribute("related", toView(related(entry)));
        model.addAttribute("activePage", "catalogo");
        return "pages/flower";
    }

    /** A few other sellable flowers, so the page is not a dead end. */
    private List<CatalogEntry> related(CatalogEntry entry) {
        return listCatalog.listSellable().stream()
                .filter(other -> !other.speciesKey().equals(entry.speciesKey()))
                .limit(RELATED_LIMIT)
                .toList();
    }

    private FlowerDetailView toDetail(CatalogEntry entry) {
        Money unitPrice = entry.unitPrice().orElse(null);

        return new FlowerDetailView(
                entry.speciesKey(),
                entry.commonName(),
                entry.scientificName(),
                entry.speciesKey() + ".jpg",
                entry.availability(),
                entry.availabilityLabel(),
                entry.quantity(),
                entry.availableImmediately(),
                unitPrice == null ? null : formatCop(unitPrice.amount()),
                BOUQUET_PREVIEW_STEMS,
                unitPrice == null ? null : formatCop(bouquetPreview(unitPrice).amount()),
                BouquetDiscountPolicy.discountFor(BOUQUET_PREVIEW_STEMS).stripTrailingZeros().toPlainString(),
                entry.description().orElse(null),
                entry.symbolicMeaning().orElse(null),
                entry.originCountry().orElse(null),
                entry.etaDays().orElse(null));
    }

    private List<FlowerCardView> toView(List<CatalogEntry> entries) {
        return entries.stream().map(this::toCard).toList();
    }

    private FlowerCardView toCard(CatalogEntry entry) {
        Money unitPrice = entry.unitPrice().orElse(null);

        return new FlowerCardView(
                entry.speciesKey(),
                entry.commonName(),
                entry.scientificName(),
                entry.speciesKey() + ".jpg",
                entry.availability(),
                entry.availabilityLabel(),
                entry.quantity(),
                unitPrice == null ? null : formatCop(unitPrice.amount()),
                BOUQUET_PREVIEW_STEMS,
                unitPrice == null ? null : formatCop(bouquetPreview(unitPrice).amount()),
                entry.description().orElse(null),
                entry.symbolicMeaning().orElse(null));
    }

    private Money bouquetPreview(Money unitPrice) {
        return pricing.forType(ProductType.BOUQUET)
                .calculatePrice(unitPrice.multiply(BOUQUET_PREVIEW_STEMS),
                        BouquetDiscountPolicy.discountFor(BOUQUET_PREVIEW_STEMS));
    }

    /**
     * What the card template consumes. availability is the enum name as a
     * String because the template compares it to a literal, and an enum would
     * never match one.
     *
     * price and bouquetPrice are null exactly when the flower may not be sold,
     * which is what makes the card unable to quote a NOT_FOR_SALE species.
     */
    public record FlowerCardView(
            String speciesKey,
            String commonName,
            String scientificName,
            String imagePath,
            String availability,
            String availabilityLabel,
            int quantity,
            String price,
            int bouquetStems,
            String bouquetPrice,
            String description,
            String symbolicMeaning) {
    }

    /** What the product page consumes. Same nullability rule: no price, no sale. */
    public record FlowerDetailView(
            String speciesKey,
            String commonName,
            String scientificName,
            String imagePath,
            String availability,
            String availabilityLabel,
            int quantity,
            boolean availableNow,
            String price,
            int bouquetStems,
            String bouquetPrice,
            String bouquetDiscount,
            String description,
            String symbolicMeaning,
            String originCountry,
            Integer etaDays) {
    }

    /** A garland or crown card. price is formatted; ingredients link to their flower pages. */
    public record CreationView(String creationKey, 
                                String name, 
                                List<Creation.Ingredient> ingredients,
                               String price, 
                               String imagePath, 
                               String description) {

        static CreationView of(Creation creation) {

            return new CreationView(creation.creationKey(), creation.name(), creation.ingredients(),
                    formatCop(creation.price().amount()), creation.imageFile(), creation.description());
        }
    }

    private List<CreationView> creationsOf(ProductType type) {

        return listCreations.listActive().stream()
                .filter(creation -> creation.productType() == type)
                .map(CreationView::of)
                .toList();
    }

    /** Colombian grouping, whole pesos: a per-stem price in centavos is noise on a card. */
    private static String formatCop(BigDecimal amount) {
        
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return "$" + format.format(amount) + " COP";
    }
}
