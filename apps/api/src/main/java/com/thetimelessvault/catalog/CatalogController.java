package com.thetimelessvault.catalog;

import com.thetimelessvault.bricklink.BrickLinkClient;
import com.thetimelessvault.inventory.InventoryDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final BrickLinkClient brickLinkClient;

    public CatalogController(CatalogService catalogService, BrickLinkClient brickLinkClient) {
        this.catalogService = catalogService;
        this.brickLinkClient = brickLinkClient;
    }

    @GetMapping("/lookup")
    public InventoryDtos.CatalogView lookup(
            @RequestParam String setNumber,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return withPackage(InventoryDtos.CatalogView.from(catalogService.lookup(setNumber, refresh)));
    }

    @PostMapping("/{id}/refresh")
    public InventoryDtos.CatalogView refresh(@PathVariable UUID id) {
        CatalogItem item = catalogService.get(id);
        return withPackage(InventoryDtos.CatalogView.from(catalogService.refresh(item.getSetNumber())));
    }

    private InventoryDtos.CatalogView withPackage(InventoryDtos.CatalogView view) {
        InventoryDtos.BrickLinkPackage pkg = brickLinkClient.packageForSet(view.setNumber());
        return pkg == null ? view : view.withBricklinkPackage(pkg);
    }
}
