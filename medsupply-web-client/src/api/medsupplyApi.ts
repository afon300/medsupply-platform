import {
  ApiErrorResponse,
  CatalogItemDto,
  CreateOrderPayload,
  DepartmentDto,
  OrderResponseDto,
  PlatformMetricsDto,
} from '../types/models';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export class StructuredApiException extends Error {
  public readonly payload: ApiErrorResponse;

  constructor(payload: ApiErrorResponse) {
    super(payload.message);
    this.payload = payload;
  }
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
  });

  if (!response.ok) {
    let errorBody: ApiErrorResponse;
    try {
      errorBody = (await response.json()) as ApiErrorResponse;
    } catch {
      errorBody = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText || 'HTTP Error',
        code: 'NETWORK_OR_HTTP_ERROR',
        message: `HTTP ${response.status} returned from ${path}`,
        path,
        correlationId: response.headers.get('X-Correlation-Id') || 'N/A',
        fieldErrors: {},
      };
    }
    throw new StructuredApiException(errorBody);
  }

  return (await response.json()) as T;
}

export const medsupplyApi = {
  fetchMetrics(): Promise<PlatformMetricsDto> {
    return requestJson<PlatformMetricsDto>('/metrics/summary');
  },

  fetchCatalog(category = '', query = ''): Promise<CatalogItemDto[]> {
    const params = new URLSearchParams();
    if (category && category !== 'ALL') params.set('category', category);
    if (query.trim()) params.set('q', query.trim());
    const qs = params.toString();
    return requestJson<CatalogItemDto[]>(`/catalog${qs ? `?${qs}` : ''}`);
  },

  fetchCatalogItemBatches(sku: string): Promise<CatalogItemDto> {
    return requestJson<CatalogItemDto>(`/catalog/${encodeURIComponent(sku)}`);
  },

  fetchDepartments(): Promise<DepartmentDto[]> {
    return requestJson<DepartmentDto[]>('/departments');
  },

  fetchOrders(departmentCode = '', status = ''): Promise<OrderResponseDto[]> {
    const params = new URLSearchParams();
    if (departmentCode) params.set('departmentCode', departmentCode);
    if (status && status !== 'ALL') params.set('status', status);
    const qs = params.toString();
    return requestJson<OrderResponseDto[]>(`/orders${qs ? `?${qs}` : ''}`);
  },

  fetchOrderByRef(orderReference: string): Promise<OrderResponseDto> {
    return requestJson<OrderResponseDto>(`/orders/${encodeURIComponent(orderReference)}`);
  },

  submitOrder(payload: CreateOrderPayload, idempotencyHeader?: string): Promise<OrderResponseDto> {
    const headers: Record<string, string> = {};
    if (idempotencyHeader) {
      headers['Idempotency-Key'] = idempotencyHeader;
    }
    return requestJson<OrderResponseDto>('/orders', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
  },

  cancelOrder(orderReference: string, reason = 'Cancelled via clinical portal'): Promise<OrderResponseDto> {
    return requestJson<OrderResponseDto>(
      `/orders/${encodeURIComponent(orderReference)}/cancel?reason=${encodeURIComponent(reason)}`,
      { method: 'POST' }
    );
  },
};
