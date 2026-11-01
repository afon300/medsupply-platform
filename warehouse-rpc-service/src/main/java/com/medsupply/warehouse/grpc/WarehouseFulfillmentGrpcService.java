package com.medsupply.warehouse.grpc;

import com.medsupply.rpc.warehouse.*;
import com.medsupply.warehouse.entity.InventoryBatch;
import com.medsupply.warehouse.entity.StockReservation;
import com.medsupply.warehouse.entity.StockReservationLine;
import com.medsupply.warehouse.exception.InsufficientWarehouseStockException;
import com.medsupply.warehouse.exception.ReservationNotFoundException;
import com.medsupply.warehouse.exception.SupplyItemNotFoundException;
import com.medsupply.warehouse.service.WarehouseInventoryService;
import com.medsupply.warehouse.service.model.CatalogSummaryView;
import com.medsupply.warehouse.service.model.ReservationOutcome;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@GrpcService
public class WarehouseFulfillmentGrpcService extends WarehouseFulfillmentServiceGrpc.WarehouseFulfillmentServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseFulfillmentGrpcService.class);

    private final WarehouseInventoryService inventoryService;
    private final String warehouseNodeId;

    @Autowired
    public WarehouseFulfillmentGrpcService(
            WarehouseInventoryService inventoryService,
            @Value("${medsupply.warehouse.node-id:EU-WEST-PARIS-HUB-01}") String warehouseNodeId) {
        this.inventoryService = inventoryService;
        this.warehouseNodeId = warehouseNodeId;
    }

    @Override
    public void listCatalog(ListCatalogRequest request, StreamObserver<ListCatalogResponse> responseObserver) {
        try {
            List<CatalogSummaryView> items = inventoryService.listCatalog(
                    request.getCategoryFilter(), request.getSearchQuery());

            ListCatalogResponse.Builder builder = ListCatalogResponse.newBuilder()
                    .setWarehouseNodeId(warehouseNodeId)
                    .setGeneratedAtIso(Instant.now().toString());

            for (CatalogSummaryView view : items) {
                builder.addItems(toProtoItem(view));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            handleRpcException("listCatalog", ex, responseObserver);
        }
    }

    @Override
    public void getItemAvailability(ItemAvailabilityRequest request, StreamObserver<ItemAvailabilityResponse> responseObserver) {
        try {
            CatalogSummaryView summary = inventoryService.getCatalogSummaryBySku(request.getSku());
            List<InventoryBatch> batches = inventoryService.getBatchesForSku(request.getSku());

            ItemAvailabilityResponse.Builder builder = ItemAvailabilityResponse.newBuilder()
                    .setItem(toProtoItem(summary));

            for (InventoryBatch batch : batches) {
                builder.addBatches(BatchLotInfo.newBuilder()
                        .setBatchLotNumber(batch.getBatchLotNumber())
                        .setAvailableUnits(batch.getAvailableUnits())
                        .setExpiryDate(batch.getExpiryDate().toString())
                        .setWarehouseZone(batch.getWarehouseZone())
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            handleRpcException("getItemAvailability", ex, responseObserver);
        }
    }

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        try {
            Map<String, Integer> requestedLines = new LinkedHashMap<>();
            for (ReservationLineRequest line : request.getLinesList()) {
                requestedLines.merge(line.getSku(), line.getRequestedQuantity(), Integer::sum);
            }

            ReservationOutcome outcome = inventoryService.reserveStock(
                    request.getIdempotencyKey(),
                    request.getHospitalId(),
                    request.getDepartmentCode(),
                    request.getExternalOrderRef(),
                    request.getPriorityEmergency(),
                    requestedLines);

            responseObserver.onNext(toProtoReservation(outcome.reservation(), outcome.replayedFromIdempotency()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            handleRpcException("reserveStock", ex, responseObserver);
        }
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest request, StreamObserver<ReleaseReservationResponse> responseObserver) {
        try {
            int restored = inventoryService.releaseReservation(request.getReservationId(), request.getReason());
            StockReservation updated = inventoryService.getReservation(request.getReservationId());

            ReleaseReservationResponse response = ReleaseReservationResponse.newBuilder()
                    .setReservationId(updated.getReservationId())
                    .setStatus(updated.getStatus().name())
                    .setRestoredTotalUnits(restored)
                    .setReleasedAtIso(updated.getReleasedAt() != null
                            ? updated.getReleasedAt().toString()
                            : Instant.now().toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            handleRpcException("releaseReservation", ex, responseObserver);
        }
    }

    @Override
    public void getReservation(GetReservationRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        try {
            StockReservation reservation = inventoryService.getReservation(request.getReservationId());
            responseObserver.onNext(toProtoReservation(reservation, false));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            handleRpcException("getReservation", ex, responseObserver);
        }
    }

    private SupplyCatalogItem toProtoItem(CatalogSummaryView view) {
        return SupplyCatalogItem.newBuilder()
                .setSku(view.sku())
                .setName(view.name())
                .setCategory(view.category())
                .setStorageCondition(view.storageCondition())
                .setUnitOfMeasure(view.unitOfMeasure())
                .setUnitPriceEur(view.unitPriceEur().doubleValue())
                .setTotalAvailableUnits(view.totalAvailableUnits())
                .setActiveBatchCount(view.activeBatchCount())
                .setEarliestExpiryDate(view.earliestExpiryDate())
                .setColdChainRequired(view.coldChainRequired())
                .build();
    }

    private ReserveStockResponse toProtoReservation(StockReservation reservation, boolean replayed) {
        ReserveStockResponse.Builder builder = ReserveStockResponse.newBuilder()
                .setReservationId(reservation.getReservationId())
                .setStatus(reservation.getStatus().name())
                .setTotalReservedCostEur(reservation.getTotalReservedCostEur().doubleValue())
                .setReplayedFromIdempotency(replayed)
                .setReservedAtIso(reservation.getReservedAt().toString());

        for (StockReservationLine line : reservation.getLines()) {
            builder.addAllocations(BatchAllocation.newBuilder()
                    .setSku(line.getSku())
                    .setItemName(line.getItemName())
                    .setBatchLotNumber(line.getBatchLotNumber())
                    .setExpiryDate(line.getExpiryDate().toString())
                    .setStorageCondition(line.getStorageCondition().name())
                    .setAllocatedQuantity(line.getAllocatedQuantity())
                    .setUnitPriceEur(line.getUnitPriceEur().doubleValue())
                    .setLineTotalEur(line.getLineTotalEur().doubleValue())
                    .build());
        }

        return builder.build();
    }

    private void handleRpcException(String rpcMethod, Exception ex, StreamObserver<?> observer) {
        if (ex instanceof SupplyItemNotFoundException || ex instanceof ReservationNotFoundException) {
            logger.warn("gRPC {} -> NOT_FOUND: {}", rpcMethod, ex.getMessage());
            observer.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } else if (ex instanceof InsufficientWarehouseStockException) {
            logger.warn("gRPC {} -> FAILED_PRECONDITION: {}", rpcMethod, ex.getMessage());
            observer.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } else if (ex instanceof IllegalArgumentException) {
            logger.warn("gRPC {} -> INVALID_ARGUMENT: {}", rpcMethod, ex.getMessage());
            observer.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } else {
            logger.error("gRPC {} -> INTERNAL unexpected failure: {}", rpcMethod, ex.getMessage(), ex);
            observer.onError(Status.INTERNAL.withDescription("Internal warehouse RPC failure").asRuntimeException());
        }
    }
}
