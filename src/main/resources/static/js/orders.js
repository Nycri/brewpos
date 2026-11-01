/* ============================================================================
 * orders.js — Screen 3: Order History & Authoritative Order Receipt View
 * ============================================================================ */
(function (global) {
  'use strict';

  const {
    el,
    formatMoney,
    formatTimestamp,
    formatPaymentMethod,
    localTodayIso,
    clearAlert,
    showAlert
  } = global.BrewApi;

  let listRequestSeq = 0;
  let detailRequestSeq = 0;

  function init() {
    document.getElementById('ordersFilterForm').addEventListener('submit', (e) => {
      e.preventDefault();
      loadOrders(true);
    });

    document.getElementById('ordersTodayBtn').addEventListener('click', () => {
      const today = localTodayIso();
      document.getElementById('ordersFromDate').value = today;
      document.getElementById('ordersToDate').value = today;
      loadOrders(true);
    });

    document.getElementById('ordersResetFilterBtn').addEventListener('click', () => {
      document.getElementById('ordersFromDate').value = '';
      document.getElementById('ordersToDate').value = '';
      loadOrders(true);
    });

    const modal = document.getElementById('orderDetailModal');
    document.getElementById('closeOrderDetailBtn').addEventListener('click', closeDetailModal);
    document.getElementById('closeOrderDetailFooterBtn').addEventListener('click', closeDetailModal);
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        closeDetailModal();
      }
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !modal.classList.contains('d-none')) {
        closeDetailModal();
      }
    });
  }

  async function loadOrders(clearMessages) {
    if (clearMessages) {
      clearAlert('ordersAlertArea');
    }

    const from = document.getElementById('ordersFromDate').value || undefined;
    const to = document.getElementById('ordersToDate').value || undefined;

    if (from && to && to < from) {
      showAlert('ordersAlertArea', 'warning', 'End date must not be earlier than start date.');
      return;
    }

    const seq = ++listRequestSeq;
    const loadingEl = document.getElementById('ordersLoading');
    const applyBtn = document.getElementById('ordersApplyFilterBtn');
    loadingEl.classList.remove('d-none');
    applyBtn.disabled = true;

    try {
      const orders = await global.BrewApi.getOrders(from, to);
      if (seq !== listRequestSeq) return;
      renderOrdersTable(Array.isArray(orders) ? orders : []);
    } catch (err) {
      if (seq !== listRequestSeq) return;
      showAlert('ordersAlertArea', 'danger', err.message || 'Failed to load order history.');
    } finally {
      if (seq === listRequestSeq) {
        loadingEl.classList.add('d-none');
        applyBtn.disabled = false;
      }
    }
  }

  function renderOrdersTable(orders) {
    const tbody = document.getElementById('ordersTableBody');
    const badge = document.getElementById('ordersSummaryBadge');
    tbody.replaceChildren();

    badge.textContent = String(orders.length);

    if (orders.length === 0) {
      tbody.appendChild(
        el('tr', {}, [
          el('td', {
            colSpan: 6,
            className: 'pos-empty-state',
            text: 'No orders found for the selected range.'
          })
        ])
      );
      return;
    }

    orders.forEach((order) => {
      const paymentBadge = el('span', {
        className: order.paymentMethod === 'ONLINE'
          ? 'pos-status-pill pos-status-online'
          : 'pos-status-pill pos-status-active',
        text: formatPaymentMethod(order.paymentMethod)
      });

      const viewBtn = el('button', {
        type: 'button',
        className: 'btn btn-sm pos-btn-outline',
        text: 'Receipt',
        onClick: () => openOrderDetail(order.orderId)
      });

      tbody.appendChild(
        el('tr', {}, [
          el('td', { className: 'fw-semibold tabular', text: `#${order.orderId}` }),
          el('td', { className: 'text-muted tabular', text: formatTimestamp(order.orderTimestamp) }),
          el('td', { text: order.staffName || `Staff #${order.staffId}` }),
          el('td', {}, [paymentBadge]),
          el('td', { className: 'text-end fw-bold tabular', text: formatMoney(order.total) }),
          el('td', { className: 'text-end' }, [viewBtn])
        ])
      );
    });
  }

  async function openOrderDetail(orderId) {
    const seq = ++detailRequestSeq;
    const modal = document.getElementById('orderDetailModal');
    const loadingEl = document.getElementById('orderDetailLoading');
    const contentEl = document.getElementById('orderDetailContent');

    clearAlert('orderDetailAlertArea');
    modal.classList.remove('d-none');
    loadingEl.classList.remove('d-none');
    contentEl.classList.add('d-none');

    try {
      const detail = await global.BrewApi.getOrderDetail(orderId);
      if (seq !== detailRequestSeq) return;
      renderOrderDetail(detail);
      contentEl.classList.remove('d-none');
    } catch (err) {
      if (seq !== detailRequestSeq) return;
      showAlert('orderDetailAlertArea', 'danger', err.message || `Could not load order #${orderId}.`);
    } finally {
      if (seq === detailRequestSeq) {
        loadingEl.classList.add('d-none');
      }
    }
  }

  function renderOrderDetail(detail) {
    document.getElementById('detailOrderId').textContent = `#${detail.orderId}`;
    document.getElementById('detailOrderTimestamp').textContent = formatTimestamp(detail.orderTimestamp);
    document.getElementById('detailOrderStaff').textContent = detail.staffName || `Staff #${detail.staffId}`;
    document.getElementById('detailOrderPayment').textContent = formatPaymentMethod(detail.paymentMethod);

    const linesBody = document.getElementById('detailOrderLinesBody');
    linesBody.replaceChildren();

    (detail.lines || []).forEach((line) => {
      linesBody.appendChild(
        el('tr', {}, [
          el('td', { text: line.itemName }),
          el('td', { className: 'text-center tabular', text: String(line.quantity) }),
          el('td', { className: 'text-end tabular', text: formatMoney(line.soldPrice) }),
          el('td', { className: 'text-end fw-semibold tabular', text: formatMoney(line.subtotal) })
        ])
      );
    });

    // Authoritative total derived from historical sold_price in PostgreSQL
    document.getElementById('detailOrderTotal').textContent = formatMoney(detail.total);
  }

  function closeDetailModal() {
    detailRequestSeq += 1;
    document.getElementById('orderDetailModal').classList.add('d-none');
  }

  global.BrewOrders = {
    init,
    loadOrders
  };
})(window);
