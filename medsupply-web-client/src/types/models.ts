export interface BatchLotDto {
  batchLotNumber: string;
  availableUnits: number;
  expiryDate: string;
  warehouseZone: string;
}

export interface CatalogItemDto {
  sku: string;
  name: string;
  category: string;
  storageCondition: string;
  unitOfMeasure: string;
  unitPriceEur: number;
  totalAvailableUnits: number;
  activeBatchCount: number;
  earliestExpiryDate: string;
  coldChainRequired: boolean;
  batches: BatchLotDto[];
}

export interface DepartmentDto {
  departmentCode: string;
  name: string;
  headPhysician: string;
  quarterlyBudgetEur: number;
  committedSpendEur: number;
  remainingBudgetEur: number;
}

export interface AllocatedLineDto {
  sku: string;
  itemName: string;
  batchLotNumber: string;
  expiryDate: string;
  storageCondition: string;
  allocatedQuantity: number;
  unitPriceEur: number;
  lineTotalEur: number;
}

export interface OrderResponseDto {
  orderReference: string;
  idempotencyKey: string;
  hospitalId: string;
  departmentCode: string;
  departmentName: string;
  requestedByClinician: string;
  priority: string;
  status: 'CONFIRMED' | 'CANCELLED';
  warehouseReservationId: string;
  totalAmountEur: number;
  clinicalNotes: string;
  createdAtIso: string;
  cancelledAtIso: string | null;
  idempotentReplay: boolean;
  allocatedBatches: AllocatedLineDto[];
}

export interface PlatformMetricsDto {
  hospitalId: string;
  warehouseNodeId: string;
  catalogSkuCount: number;
  totalWarehouseUnitsAvailable: number;
  coldChainSkuCount: number;
  confirmedOrdersCount: number;
  cancelledOrdersCount: number;
  totalCommittedSpendEur: number;
  totalRemainingBudgetEur: number;
  warehouseRpcHealthy: boolean;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId: string;
  fieldErrors: Record<string, string>;
}

export interface CreateOrderPayload {
  departmentCode: string;
  requestedByClinician: string;
  priority: string;
  clinicalNotes: string;
  idempotencyKey?: string;
  items: Array<{ sku: string; quantity: number }>;
}
