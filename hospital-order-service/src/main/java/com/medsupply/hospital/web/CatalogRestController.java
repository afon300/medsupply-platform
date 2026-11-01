package com.medsupply.hospital.web;

import com.medsupply.hospital.dto.CatalogItemDto;
import com.medsupply.hospital.service.WarehouseRpcGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogRestController {

    private static final Logger logger = LoggerFactory.getLogger(CatalogRestController.class);

    private final WarehouseRpcGateway warehouseGateway;

    @Autowired
    public CatalogRestController(WarehouseRpcGateway warehouseGateway) {
        this.warehouseGateway = warehouseGateway;
    }

    @GetMapping
    public ResponseEntity<List<CatalogItemDto>> listCatalog(
            @RequestParam(value = "category", required = false, defaultValue = "") String category,
            @RequestParam(value = "q", required = false, defaultValue = "") String query) {
        logger.info("REST GET /api/v1/catalog?category='{}'&q='{}'", category, query);
        WarehouseRpcGateway.CatalogSnapshot snapshot = warehouseGateway.fetchCatalog(category, query);
        return ResponseEntity.ok()
                .header("X-Warehouse-Node", snapshot.warehouseNodeId())
                .body(snapshot.items());
    }

    @GetMapping("/{sku}")
    public ResponseEntity<CatalogItemDto> getCatalogItemDetails(@PathVariable("sku") String sku) {
        logger.info("REST GET /api/v1/catalog/{}", sku);
        return ResponseEntity.ok(warehouseGateway.fetchItemWithBatches(sku));
    }
}
