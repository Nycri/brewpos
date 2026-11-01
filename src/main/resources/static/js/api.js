/* ============================================================================
 * api.js — Shared REST client, exact-cent money formatting, local-date helper,
 * and safe DOM construction (no raw HTML injection of user/API values).
 * ============================================================================ */
(function (global) {
  'use strict';

  const BASE_URL = '/api';

  class ApiError extends Error {
    constructor(message, status, payload) {
      super(message);
      this.name = 'ApiError';
      this.status = status || 0;
      this.payload = payload || null;
    }
  }

  function buildUrl(path, query) {
    const params = new URLSearchParams();
    if (query) {
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          params.append(key, String(value));
        }
      });
    }
    const qs = params.toString();
    return qs ? `${BASE_URL}${path}?${qs}` : `${BASE_URL}${path}`;
  }

  async function request(path, options) {
    const opts = options || {};
    const fetchOpts = {
      method: opts.method || 'GET',
      headers: {
        Accept: 'application/json'
      }
    };

    if (opts.body !== undefined) {
      fetchOpts.headers['Content-Type'] = 'application/json';
      fetchOpts.body = JSON.stringify(opts.body);
    }

    let response;
    try {
      response = await fetch(buildUrl(path, opts.query), fetchOpts);
    } catch (_networkErr) {
      throw new ApiError(
        'Cannot reach the BrewPOS server. Verify that Spring Boot and PostgreSQL are running.',
        0,
        null
      );
    }

    if (response.status === 204) {
      return null;
    }

    let data = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      try {
        data = await response.json();
      } catch (_parseErr) {
        data = null;
      }
    }

    if (!response.ok) {
      const serverMsg = data && typeof data.message === 'string' && data.message.trim()
        ? data.message.trim()
        : `Request failed with HTTP ${response.status}`;
      throw new ApiError(serverMsg, response.status, data);
    }

    return data;
  }

  /** Converts a decimal currency number/string into exact integer cents. */
  function toCents(value) {
    const num = Number(value);
    if (!Number.isFinite(num)) {
      return 0;
    }
    return Math.round(num * 100);
  }

  /** Formats integer cents as a 2-decimal currency string (e.g. "275.50"). */
  function formatMoneyFromCents(cents) {
    const safe = Object.is(cents, -0) || !Number.isFinite(cents) ? 0 : cents;
    const formatted = (safe / 100).toFixed(2);
    return formatted === '-0.00' ? '0.00' : formatted;
  }

  /** Formats a decimal JSON number/string to 2 decimal places. */
  function formatMoney(value) {
    return formatMoneyFromCents(toCents(value));
  }

  /**
   * Returns YYYY-MM-DD in the browser's LOCAL calendar timezone (never UTC),
   * matching PostgreSQL TIMESTAMP without time zone (local cafe time).
   */
  function localTodayIso() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /** Formats an ISO local date-time string (e.g. "2026-11-01T10:30:12.345") cleanly. */
  function formatTimestamp(isoString) {
    if (!isoString) {
      return '—';
    }
    const clean = String(isoString).replace('T', ' ');
    const dotIdx = clean.indexOf('.');
    return dotIdx > 0 ? clean.slice(0, dotIdx) : clean;
  }

  /** Canonical CASH / ONLINE display label. */
  function formatPaymentMethod(method) {
    if (method === 'CASH') return 'Cash';
    if (method === 'ONLINE') return 'Online';
    return String(method || '—');
  }

  /** Safe DOM element factory using textContent (never raw HTML). */
  function el(tag, attrs, children) {
    const node = document.createElement(tag);
    if (attrs) {
      Object.entries(attrs).forEach(([k, v]) => {
        if (v === undefined || v === null || v === false) return;
        if (k === 'className') {
          node.className = v;
        } else if (k === 'text') {
          node.textContent = String(v);
        } else if (k === 'disabled' || k === 'checked' || k === 'selected' || k === 'required') {
          node[k] = Boolean(v);
        } else if (k.startsWith('on') && typeof v === 'function') {
          node.addEventListener(k.slice(2).toLowerCase(), v);
        } else {
          node.setAttribute(k, String(v));
        }
      });
    }
    if (Array.isArray(children)) {
      children.forEach((child) => {
        if (child === null || child === undefined || child === false) return;
        if (child instanceof Node) {
          node.appendChild(child);
        } else {
          node.appendChild(document.createTextNode(String(child)));
        }
      });
    }
    return node;
  }

  function clearAlert(container) {
    if (!container) return;
    const target = typeof container === 'string' ? document.getElementById(container) : container;
    if (target) {
      target.replaceChildren();
    }
  }

  function showAlert(container, type, message, dismissible) {
    const target = typeof container === 'string' ? document.getElementById(container) : container;
    if (!target) return;
    target.replaceChildren();

    const alertDiv = el('div', {
      className: `alert alert-${type} d-flex justify-content-between align-items-start gap-2 py-2 mb-3`,
      role: 'alert'
    }, [
      el('div', { text: message })
    ]);

    if (dismissible !== false) {
      const closeBtn = el('button', {
        type: 'button',
        className: 'btn-close btn-sm',
        'aria-label': 'Dismiss message',
        onClick: () => alertDiv.remove()
      });
      alertDiv.appendChild(closeBtn);
    }

    target.appendChild(alertDiv);
  }

  global.BrewApi = {
    ApiError,
    // Staff
    getStaff: (role) => request('/staff', { query: { role } }),
    // Categories
    getCategories: () => request('/categories'),
    createCategory: (categoryName) => request('/categories', {
      method: 'POST',
      body: { categoryName }
    }),
    updateCategory: (id, categoryName) => request(`/categories/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: { categoryName }
    }),
    deleteCategory: (id) => request(`/categories/${encodeURIComponent(id)}`, {
      method: 'DELETE'
    }),
    // Menu Items & Active Menu
    getActiveMenu: () => request('/menu/active'),
    getMenuItems: (activeOnly) => request('/menu-items', {
      query: activeOnly ? { activeOnly: true } : undefined
    }),
    createMenuItem: (payload) => request('/menu-items', {
      method: 'POST',
      body: payload
    }),
    updateMenuItem: (id, payload) => request(`/menu-items/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: payload
    }),
    setMenuItemActive: (id, isActive) => request(`/menu-items/${encodeURIComponent(id)}/active`, {
      method: 'PATCH',
      body: { isActive: Boolean(isActive) }
    }),
    // Orders (strictly sends staffId, paymentMethod, and items[{itemId, quantity}] only)
    createOrder: (payload) => request('/orders', {
      method: 'POST',
      body: {
        staffId: Number(payload.staffId),
        paymentMethod: payload.paymentMethod,
        items: payload.items.map((line) => ({
          itemId: Number(line.itemId),
          quantity: Number(line.quantity)
        }))
      }
    }),
    getOrders: (from, to) => request('/orders', { query: { from, to } }),
    getOrderDetail: (id) => request(`/orders/${encodeURIComponent(id)}`),
    // Sales Reports
    getDailySales: (date) => request('/reports/daily-sales', { query: { date } }),
    getSalesByPayment: (date) => request('/reports/daily-sales/by-payment', { query: { date } }),
    // Formatting & Safe DOM Helpers
    toCents,
    formatMoney,
    formatMoneyFromCents,
    localTodayIso,
    formatTimestamp,
    formatPaymentMethod,
    el,
    clearAlert,
    showAlert
  };
})(window);

