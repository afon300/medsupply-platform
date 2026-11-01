import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { medsupplyApi, StructuredApiException } from './api/medsupplyApi';
import {
  ApiErrorResponse,
  CatalogItemDto,
  DepartmentDto,
  OrderResponseDto,
  PlatformMetricsDto,
} from './types/models';

const CATEGORIES = ['ALL', 'VACCINE', 'BIOLOGIC', 'SURGICAL', 'CARDIOLOGY', 'ANESTHESIA', 'ONCOLOGY'];

function formatStorage(raw: string): string {
  switch (raw) {
    case 'ULTRA_COLD_MINUS_70C':
      return '-70°C Ultra-Cold';
    case 'REFRIGERATED_2_TO_8C':
      return '2–8°C Cold Chain';
    default:
      return '15–25°C Ambient';
  }
}

function formatEur(val: number): string {
  return new Intl.NumberFormat('en-IE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
  }).format(val);
}

function generateIdempotencyKey(): string {
  return 'IDEMP-' + Math.random().toString(36).substring(2, 10).toUpperCase();
}

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'catalog' | 'orders' | 'governance'>('catalog');
  const [metrics, setMetrics] = useState<PlatformMetricsDto | null>(null);
  const [catalog, setCatalog] = useState<CatalogItemDto[]>([]);
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);
  const [orders, setOrders] = useState<OrderResponseDto[]>([]);

  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [inspectedItem, setInspectedItem] = useState<CatalogItemDto | null>(null);

  // Requisition form state
  const [departmentCode, setDepartmentCode] = useState<string>('DEPT-SURG');
  const [clinician, setClinician] = useState<string>('Dr. Antoine Lefevre, MD');
  const [priority, setPriority] = useState<string>('URGENT');
  const [clinicalNotes, setClinicalNotes] = useState<string>(
    'Operating Theater B2 scheduled vascular procedures'
  );
  const [idempotencyKey, setIdempotencyKey] = useState<string>(generateIdempotencyKey());
  const [cart, setCart] = useState<Record<string, number>>({
    'MED-SUR-003': 10,
    'MED-VAC-001': 3,
  });

  const [submitting, setSubmitting] = useState<boolean>(false);
  const [apiError, setApiError] = useState<ApiErrorResponse | null>(null);
  const [successBanner, setSuccessBanner] = useState<string | null>(null);

  const refreshAll = useCallback(async () => {
    try {
      const [m, c, d, o] = await Promise.all([
        medsupplyApi.fetchMetrics(),
        medsupplyApi.fetchCatalog(selectedCategory, searchQuery),
        medsupplyApi.fetchDepartments(),
        medsupplyApi.fetchOrders(),
      ]);
      setMetrics(m);
      setCatalog(c);
      setDepartments(d);
      setOrders(o);
    } catch (err) {
      if (err instanceof StructuredApiException) {
        setApiError(err.payload);
      }
    }
  }, [selectedCategory, searchQuery]);

  useEffect(() => {
    void refreshAll();
  }, [refreshAll]);

  const selectedDeptObj = useMemo(
    () => departments.find((d) => d.departmentCode === departmentCode) || null,
    [departments, departmentCode]
  );

  const estimatedTotal = useMemo(() => {
    let sum = 0;
    for (const [sku, qty] of Object.entries(cart)) {
      const item = catalog.find((c) => c.sku === sku);
      if (item && qty > 0) {
        sum += item.unitPriceEur * qty;
      }
    }
    return sum;
  }, [cart, catalog]);

  const handleInspectBatches = async (sku: string) => {
    setApiError(null);
    try {
      const detailed = await medsupplyApi.fetchCatalogItemBatches(sku);
      setInspectedItem(detailed);
    } catch (err) {
      if (err instanceof StructuredApiException) setApiError(err.payload);
    }
  };

  const updateCartQty = (sku: string, delta: number) => {
    setCart((prev) => {
      const next = { ...prev };
      const updated = (next[sku] || 0) + delta;
      if (updated <= 0) {
        delete next[sku];
      } else {
        next[sku] = updated;
      }
      return next;
    });
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setApiError(null);
    setSuccessBanner(null);
    try {
      const items = Object.entries(cart).map(([sku, quantity]) => ({ sku, quantity }));
      const created = await medsupplyApi.submitOrder(
        {
          departmentCode,
          requestedByClinician: clinician,
          priority,
          clinicalNotes,
          items,
        },
        idempotencyKey
      );
      const replayNote = created.idempotentReplay
        ? ' [IDEMPOTENT REPLAY — no duplicate warehouse deduction]'
        : '';
      setSuccessBanner(
        `Order ${created.orderReference} confirmed via gRPC reservation ${created.warehouseReservationId} (${created.allocatedBatches.length} FEFO batch lot allocation(s), total ${formatEur(created.totalAmountEur)})${replayNote}`
      );
      setIdempotencyKey(generateIdempotencyKey());
      await refreshAll();
    } catch (err) {
      if (err instanceof StructuredApiException) {
        setApiError(err.payload);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelOrder = async (orderRef: string) => {
    setApiError(null);
    setSuccessBanner(null);
    try {
      const cancelled = await medsupplyApi.cancelOrder(
        orderRef,
        'Clinical schedule update — Saga RPC stock release'
      );
      setSuccessBanner(
        `Order ${cancelled.orderReference} CANCELLED. Compensating gRPC ReleaseReservation restored stock for ${cancelled.warehouseReservationId} and refunded ${formatEur(cancelled.totalAmountEur)} to ${cancelled.departmentCode}.`
      );
      await refreshAll();
    } catch (err) {
      if (err instanceof StructuredApiException) setApiError(err.payload);
    }
  };

  // Diagnostic fault triggers for demonstrating @RestControllerAdvice HTTP error handling
  const triggerDiagnosticFault = async (mode: '404' | '409' | '422' | '400') => {
    setApiError(null);
    setSuccessBanner(null);
    try {
      if (mode === '404') {
        await medsupplyApi.fetchOrderByRef('ORD-UNKNOWN-999');
      } else if (mode === '409') {
        await medsupplyApi.submitOrder({
          departmentCode: 'DEPT-ICU',
          requestedByClinician: 'Prof. Claire Moreau, MD',
          priority: 'URGENT',
          clinicalNotes: 'Diagnostic stock conflict probe',
          items: [{ sku: 'MED-ONC-006', quantity: 950 }],
        });
      } else if (mode === '422') {
        // DEPT-EMERG only has 3,900 EUR remaining; 5 stent kits = 6,250 EUR -> triggers gRPC ReserveStock then Saga ReleaseReservation rollback!
        await medsupplyApi.submitOrder({
          departmentCode: 'DEPT-EMERG',
          requestedByClinician: 'Dr. Julien Garnier, MD',
          priority: 'URGENT',
          clinicalNotes: 'Budget ceiling & Saga rollback probe',
          items: [{ sku: 'MED-SUR-003', quantity: 5 }],
        });
      } else {
        await medsupplyApi.submitOrder({
          departmentCode: '',
          requestedByClinician: '',
          priority: 'STANDARD',
          clinicalNotes: '',
          items: [],
        });
      }
    } catch (err) {
      if (err instanceof StructuredApiException) {
        setApiError(err.payload);
        await refreshAll();
      }
    }
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-title">
          <h1>MedSupply · Clinical Procurement &amp; Warehouse RPC Platform</h1>
          <p className="topbar-subtitle">
            Stateless Spring Boot REST Service (DBMS #1) &rarr; Protocol Buffers / gRPC Client &rarr;
            Central Pharmaceutical Warehouse Server (DBMS #2)
          </p>
        </div>
        <div className="topology-pills">
          <span className="topo-pill">
            <span className="status-dot online" />
            Hospital REST: <strong>{metrics?.hospitalId || 'APHP-SAINT-LOUIS-PARIS'}</strong>
          </span>
          <span className="topo-pill">
            <span className={`status-dot ${metrics?.warehouseRpcHealthy ? 'online' : 'offline'}`} />
            Warehouse gRPC: <strong>{metrics?.warehouseNodeId || 'CONNECTING...'}</strong>
          </span>
        </div>
      </header>

      <section className="metrics-grid">
        <div className="metric-card">
          <div className="metric-label">Central Warehouse Catalog</div>
          <div className="metric-value">{metrics ? `${metrics.catalogSkuCount} SKUs` : '—'}</div>
          <div className="metric-foot">
            {metrics ? `${metrics.coldChainSkuCount} Cold-Chain Regulated SKUs` : 'Loading gRPC...'}
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-label">Live FEFO Batch Inventory</div>
          <div className="metric-value">
            {metrics ? `${metrics.totalWarehouseUnitsAvailable} Units` : '—'}
          </div>
          <div className="metric-foot">Synchronized via unary gRPC ListCatalog</div>
        </div>
        <div className="metric-card">
          <div className="metric-label">Hospital Procurement Orders</div>
          <div className="metric-value">
            {metrics ? `${metrics.confirmedOrdersCount} Confirmed` : '—'}
          </div>
          <div className="metric-foot">
            {metrics ? `${metrics.cancelledOrdersCount} Cancelled (Saga Released)` : '—'}
          </div>
        </div>
        <div className="metric-card">
          <div className="metric-label">Remaining Clinical Budgets</div>
          <div className="metric-value">
            {metrics ? formatEur(metrics.totalRemainingBudgetEur) : '—'}
          </div>
          <div className="metric-foot">
            Committed: {metrics ? formatEur(metrics.totalCommittedSpendEur) : '—'}
          </div>
        </div>
      </section>

      {successBanner && (
        <div className="alert-banner success" data-testid="success-banner">
          <div>
            <strong>Transaction Committed Across REST &amp; gRPC Boundaries</strong>
            <div style={{ fontSize: '0.86rem', marginTop: 3 }}>{successBanner}</div>
          </div>
          <button className="btn btn-outline" onClick={() => setSuccessBanner(null)}>
            Dismiss
          </button>
        </div>
      )}

      {apiError && (
        <div className="alert-banner error" data-testid="error-banner">
          <div style={{ flex: 1 }}>
            <strong>
              HTTP {apiError.status} {apiError.error} &middot; [{apiError.code}]
            </strong>
            <div style={{ fontSize: '0.87rem', marginTop: 4 }}>{apiError.message}</div>
            <div className="alert-meta">
              Path: {apiError.path} | X-Correlation-Id: {apiError.correlationId} | Timestamp:{' '}
              {apiError.timestamp}
              {Object.keys(apiError.fieldErrors || {}).length > 0 &&
                ` | Fields: ${JSON.stringify(apiError.fieldErrors)}`}
            </div>
          </div>
          <button className="btn btn-outline" onClick={() => setApiError(null)}>
            Dismiss
          </button>
        </div>
      )}

      <nav className="nav-tabs">
        <button
          className={`tab-btn ${activeTab === 'catalog' ? 'active' : ''}`}
          onClick={() => setActiveTab('catalog')}
        >
          1. Warehouse Catalog &amp; Order Requisition
        </button>
        <button
          className={`tab-btn ${activeTab === 'orders' ? 'active' : ''}`}
          onClick={() => setActiveTab('orders')}
        >
          2. Orders &amp; FEFO Batch Allocations ({orders.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'governance' ? 'active' : ''}`}
          onClick={() => setActiveTab('governance')}
        >
          3. Department Budgets &amp; HTTP Exception Inspector
        </button>
      </nav>

      {activeTab === 'catalog' && (
        <div className="workspace-split">
          <div className="panel">
            <div className="panel-header">
              <h2>Central Pharmaceutical Catalog (via gRPC :9090)</h2>
              <input
                className="search-input"
                style={{ maxWidth: 230 }}
                placeholder="Filter by SKU or name..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>

            <div className="filter-bar">
              {CATEGORIES.map((cat) => (
                <button
                  key={cat}
                  className={`chip ${selectedCategory === cat ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(cat)}
                >
                  {cat}
                </button>
              ))}
            </div>

            <div className="catalog-grid">
              {catalog.map((item) => (
                <div className="sku-card" key={item.sku}>
                  <div>
                    <div className="sku-top">
                      <span className="sku-code">{item.sku}</span>
                      <span className={`badge ${item.coldChainRequired ? 'cold' : 'ambient'}`}>
                        {formatStorage(item.storageCondition)}
                      </span>
                    </div>
                    <div className="sku-name">{item.name}</div>
                    <div className="sku-meta">
                      <span>Category: {item.category}</span>
                      <span>Unit: {item.unitOfMeasure}</span>
                      <span>
                        Stock: <strong>{item.totalAvailableUnits}</strong> ({item.activeBatchCount}{' '}
                        lots)
                      </span>
                      <span>FEFO Exp: {item.earliestExpiryDate}</span>
                    </div>
                  </div>
                  <div className="sku-actions">
                    <span className="price-tag">{formatEur(item.unitPriceEur)}</span>
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button
                        type="button"
                        className="btn btn-outline"
                        onClick={() => void handleInspectBatches(item.sku)}
                      >
                        Lots
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={item.totalAvailableUnits === 0}
                        onClick={() => updateCartQty(item.sku, 1)}
                      >
                        + Add
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {inspectedItem && (
              <div className="batch-inspector-box">
                <div className="panel-header" style={{ marginBottom: 8 }}>
                  <strong>
                    FEFO Warehouse Batch Breakdown &middot; {inspectedItem.sku} (
                    {inspectedItem.name})
                  </strong>
                  <button className="btn btn-outline" onClick={() => setInspectedItem(null)}>
                    Close
                  </button>
                </div>
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Batch Lot Number</th>
                      <th>Warehouse Zone</th>
                      <th>Expiry Date (FEFO Order)</th>
                      <th>Available Units</th>
                    </tr>
                  </thead>
                  <tbody>
                    {inspectedItem.batches.map((b) => (
                      <tr key={b.batchLotNumber}>
                        <td className="mono">{b.batchLotNumber}</td>
                        <td>{b.warehouseZone}</td>
                        <td>{b.expiryDate}</td>
                        <td>
                          <strong>{b.availableUnits}</strong>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="panel">
            <div className="panel-header">
              <h2>Clinical Procurement Requisition</h2>
              <span className="badge confirmed">Stateless POST</span>
            </div>

            <form onSubmit={handlePlaceOrder}>
              <div className="form-group">
                <label>Hospital Department (DBMS #1 Budget Owner)</label>
                <select
                  className="form-control"
                  value={departmentCode}
                  onChange={(e) => {
                    const code = e.target.value;
                    setDepartmentCode(code);
                    const found = departments.find((d) => d.departmentCode === code);
                    if (found) setClinician(found.headPhysician);
                  }}
                >
                  {departments.map((d) => (
                    <option key={d.departmentCode} value={d.departmentCode}>
                      {d.departmentCode} — {d.name} (Rem: {formatEur(d.remainingBudgetEur)})
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: 10 }}>
                <div className="form-group">
                  <label>Prescribing Clinician</label>
                  <input
                    className="form-control"
                    value={clinician}
                    onChange={(e) => setClinician(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label>Fulfillment Priority</label>
                  <select
                    className="form-control"
                    value={priority}
                    onChange={(e) => setPriority(e.target.value)}
                  >
                    <option value="STANDARD">STANDARD</option>
                    <option value="URGENT">URGENT</option>
                    <option value="EMERGENCY_COLD_CHAIN">EMERGENCY_COLD_CHAIN</option>
                  </select>
                </div>
              </div>

              <div className="form-group">
                <label>Idempotency-Key Header (Safe Retry Token)</label>
                <div style={{ display: 'flex', gap: 6 }}>
                  <input
                    className="form-control mono"
                    value={idempotencyKey}
                    onChange={(e) => setIdempotencyKey(e.target.value)}
                  />
                  <button
                    type="button"
                    className="btn btn-outline"
                    onClick={() => setIdempotencyKey(generateIdempotencyKey())}
                  >
                    Rotate
                  </button>
                </div>
              </div>

              <div className="form-group">
                <label>Clinical Justification Notes</label>
                <input
                  className="form-control"
                  value={clinicalNotes}
                  onChange={(e) => setClinicalNotes(e.target.value)}
                />
              </div>

              <div className="cart-list">
                <div
                  style={{
                    fontSize: '0.77rem',
                    fontWeight: 700,
                    color: '#475569',
                    marginBottom: 6,
                  }}
                >
                  REQUESTED LINE ITEMS ({Object.keys(cart).length})
                </div>
                {Object.keys(cart).length === 0 ? (
                  <div style={{ fontSize: '0.82rem', color: '#64748b', padding: '8px 0' }}>
                    No SKUs selected. Click &ldquo;+ Add&rdquo; on any catalog item.
                  </div>
                ) : (
                  Object.entries(cart).map(([sku, qty]) => {
                    const item = catalog.find((c) => c.sku === sku);
                    return (
                      <div className="cart-row" key={sku}>
                        <div>
                          <strong className="mono">{sku}</strong>
                          <div style={{ fontSize: '0.76rem', color: '#475569' }}>
                            {item?.name || sku}
                          </div>
                        </div>
                        <div className="qty-stepper">
                          <button
                            type="button"
                            className="qty-btn"
                            onClick={() => updateCartQty(sku, -1)}
                          >
                            -
                          </button>
                          <strong style={{ minWidth: 22, textAlign: 'center' }}>{qty}</strong>
                          <button
                            type="button"
                            className="qty-btn"
                            onClick={() => updateCartQty(sku, 1)}
                          >
                            +
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 12,
                  fontSize: '0.88rem',
                }}
              >
                <span>
                  Dept Remaining:{' '}
                  <strong>
                    {selectedDeptObj ? formatEur(selectedDeptObj.remainingBudgetEur) : '—'}
                  </strong>
                </span>
                <span>
                  Order Total: <strong style={{ fontSize: '1.08rem' }}>{formatEur(estimatedTotal)}</strong>
                </span>
              </div>

              <button
                type="submit"
                className="btn btn-primary"
                style={{ width: '100%', padding: '10px 14px', fontSize: '0.88rem' }}
                disabled={submitting || Object.keys(cart).length === 0}
              >
                {submitting
                  ? 'Executing REST -> gRPC ReserveStock...'
                  : 'Submit Stateless Procurement Order'}
              </button>
            </form>
          </div>
        </div>
      )}

      {activeTab === 'orders' && (
        <div className="panel" style={{ marginTop: 18 }}>
          <div className="panel-header">
            <h2>Hospital Procurement Orders &amp; FEFO Warehouse Batch Allocations</h2>
            <button className="btn btn-outline" onClick={() => void refreshAll()}>
              Refresh Ledger
            </button>
          </div>

          {orders.length === 0 ? (
            <p style={{ color: '#64748b', fontSize: '0.9rem' }}>
              No procurement orders placed yet. Submit an order in Tab 1 to inspect multi-batch FEFO
              allocations.
            </p>
          ) : (
            orders.map((order) => (
              <div className="order-card" key={order.orderReference}>
                <div className="order-card-head">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                    <strong className="mono" style={{ fontSize: '0.98rem' }}>
                      {order.orderReference}
                    </strong>
                    <span
                      className={`badge ${order.status === 'CONFIRMED' ? 'confirmed' : 'cancelled'}`}
                    >
                      {order.status}
                    </span>
                    <span className="badge urgent">{order.priority}</span>
                    <span style={{ fontSize: '0.8rem', color: '#475569' }}>
                      gRPC Reservation: <strong className="mono">{order.warehouseReservationId}</strong>
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <strong style={{ fontSize: '1.05rem' }}>{formatEur(order.totalAmountEur)}</strong>
                    {order.status === 'CONFIRMED' && (
                      <button
                        className="btn btn-danger"
                        onClick={() => void handleCancelOrder(order.orderReference)}
                      >
                        Cancel &amp; Release Stock (Saga RPC)
                      </button>
                    )}
                  </div>
                </div>

                <div style={{ fontSize: '0.8rem', color: '#475569', marginBottom: 10 }}>
                  Department: <strong>{order.departmentCode}</strong> ({order.departmentName}) &middot;
                  Clinician: <strong>{order.requestedByClinician}</strong> &middot; Idempotency-Key:{' '}
                  <span className="mono">{order.idempotencyKey}</span>
                  {order.clinicalNotes && ` · Notes: "${order.clinicalNotes}"`}
                </div>

                <table className="data-table">
                  <thead>
                    <tr>
                      <th>SKU</th>
                      <th>Medical Supply Item</th>
                      <th>Allocated Batch Lot (FEFO)</th>
                      <th>Batch Expiry</th>
                      <th>Storage Condition</th>
                      <th>Allocated Qty</th>
                      <th>Subtotal</th>
                    </tr>
                  </thead>
                  <tbody>
                    {order.allocatedBatches.map((line, idx) => (
                      <tr key={`${line.sku}-${line.batchLotNumber}-${idx}`}>
                        <td className="mono">{line.sku}</td>
                        <td>{line.itemName}</td>
                        <td className="mono">
                          <strong>{line.batchLotNumber}</strong>
                        </td>
                        <td>{line.expiryDate}</td>
                        <td>{formatStorage(line.storageCondition)}</td>
                        <td>
                          <strong>{line.allocatedQuantity}</strong>
                        </td>
                        <td>{formatEur(line.lineTotalEur)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))
          )}
        </div>
      )}

      {activeTab === 'governance' && (
        <div className="workspace-split">
          <div className="panel">
            <div className="panel-header">
              <h2>Clinical Department Budgets (Hospital DBMS #1)</h2>
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Clinical Department</th>
                  <th>Quarterly Budget</th>
                  <th>Committed</th>
                  <th>Remaining</th>
                  <th style={{ width: 140 }}>Utilization</th>
                </tr>
              </thead>
              <tbody>
                {departments.map((d) => {
                  const pct = Math.min(
                    100,
                    Math.round((d.committedSpendEur / d.quarterlyBudgetEur) * 100)
                  );
                  const barClass = pct >= 85 ? 'high' : pct >= 60 ? 'warn' : '';
                  return (
                    <tr key={d.departmentCode}>
                      <td className="mono">
                        <strong>{d.departmentCode}</strong>
                      </td>
                      <td>
                        <div>{d.name}</div>
                        <div style={{ fontSize: '0.74rem', color: '#64748b' }}>
                          {d.headPhysician}
                        </div>
                      </td>
                      <td>{formatEur(d.quarterlyBudgetEur)}</td>
                      <td>{formatEur(d.committedSpendEur)}</td>
                      <td>
                        <strong>{formatEur(d.remainingBudgetEur)}</strong>
                      </td>
                      <td>
                        <div style={{ fontSize: '0.74rem', fontWeight: 600 }}>{pct}%</div>
                        <div className="progress-track">
                          <div className={`progress-fill ${barClass}`} style={{ width: `${pct}%` }} />
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="panel">
            <div className="panel-header">
              <h2>HTTP Exception Handler &amp; Saga Rollback Probes</h2>
            </div>
            <p style={{ fontSize: '0.83rem', color: '#475569', marginTop: 0 }}>
              Invoke live requests against <code>GlobalRestExceptionHandler</code> (
              <code>@RestControllerAdvice</code>) to verify structured HTTP error translation and
              cross-service Saga stock rollback:
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <button className="btn btn-outline" onClick={() => void triggerDiagnosticFault('400')}>
                Probe HTTP 400 (Bean Validation)
              </button>
              <button className="btn btn-outline" onClick={() => void triggerDiagnosticFault('404')}>
                Probe HTTP 404 (Order Not Found)
              </button>
              <button className="btn btn-outline" onClick={() => void triggerDiagnosticFault('409')}>
                Probe HTTP 409 (gRPC Stock Shortage)
              </button>
              <button className="btn btn-danger" onClick={() => void triggerDiagnosticFault('422')}>
                Probe HTTP 422 (Budget + Saga Rollback)
              </button>
            </div>

            {apiError && (
              <pre className="json-box">{JSON.stringify(apiError, null, 2)}</pre>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
