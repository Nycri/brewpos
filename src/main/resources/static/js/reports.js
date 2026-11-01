/* ============================================================================
 * reports.js — Screen 4: PostgreSQL Daily Sales & Payment-Method Breakdown
 * ============================================================================ */
(function (global) {
  'use strict';

  const {
    el,
    toCents,
    formatMoney,
    formatMoneyFromCents,
    formatPaymentMethod,
    localTodayIso,
    clearAlert,
    showAlert
  } = global.BrewApi;

  let reportRequestSeq = 0;

  function init() {
    const dateInput = document.getElementById('reportDateInput');
    dateInput.value = localTodayIso();

    document.getElementById('reportsFilterForm').addEventListener('submit', (e) => {
      e.preventDefault();
      loadReport(true);
    });

    dateInput.addEventListener('change', () => {
      if (dateInput.value) {
        loadReport(true);
      }
    });

    document.getElementById('reportTodayBtn').addEventListener('click', () => {
      dateInput.value = localTodayIso();
      loadReport(true);
    });
  }

  async function loadReport(clearMessages) {
    if (clearMessages) {
      clearAlert('reportsAlertArea');
    }

    const dateInput = document.getElementById('reportDateInput');
    if (!dateInput.value) {
      dateInput.value = localTodayIso();
    }
    const targetDate = dateInput.value;

    const seq = ++reportRequestSeq;
    const loadingEl = document.getElementById('reportsLoading');
    const refreshBtn = document.getElementById('reportRefreshBtn');
    loadingEl.classList.remove('d-none');
    refreshBtn.disabled = true;

    try {
      const [daily, byPayment] = await Promise.all([
        global.BrewApi.getDailySales(targetDate),
        global.BrewApi.getSalesByPayment(targetDate)
      ]);
      if (seq !== reportRequestSeq) return;
      renderReport(targetDate, daily, Array.isArray(byPayment) ? byPayment : []);
    } catch (err) {
      if (seq !== reportRequestSeq) return;
      showAlert('reportsAlertArea', 'danger', err.message || 'Failed to load daily sales report.');
    } finally {
      if (seq === reportRequestSeq) {
        loadingEl.classList.add('d-none');
        refreshBtn.disabled = false;
      }
    }
  }

  function renderReport(requestedDate, daily, paymentRows) {
    document.getElementById('reportDisplayDate').textContent = (daily && daily.date) || requestedDate;
    document.getElementById('reportTotalSales').textContent = formatMoney(daily ? daily.totalSales : 0);
    document.getElementById('reportOrderCount').textContent = String(daily ? daily.orderCount : 0);

    const tbody = document.getElementById('reportPaymentTableBody');
    tbody.replaceChildren();

    let sumCents = 0;
    let sumOrders = 0;

    paymentRows.forEach((row) => {
      sumCents += toCents(row.totalSales);
      sumOrders += Number(row.orderCount) || 0;
    });

    paymentRows.forEach((row) => {
      const cents = toCents(row.totalSales);
      const count = Number(row.orderCount) || 0;
      const sharePct = sumCents > 0 ? Math.round((cents / sumCents) * 100) : 0;

      const methodBadge = el('span', {
        className: row.paymentMethod === 'ONLINE'
          ? 'pos-status-pill pos-status-online'
          : 'pos-status-pill pos-status-active',
        text: formatPaymentMethod(row.paymentMethod)
      });

      const shareBar = el('div', { className: 'd-flex align-items-center gap-2' }, [
        el('div', { className: 'pos-share-track' }, [
          el('div', {
            className: 'pos-share-fill',
            style: `width: ${sharePct}%`
          })
        ]),
        el('span', { className: 'small text-muted tabular', text: `${sharePct}%` })
      ]);

      tbody.appendChild(
        el('tr', {}, [
          el('td', {}, [methodBadge]),
          el('td', {}, [shareBar]),
          el('td', { className: 'text-end tabular', text: String(count) }),
          el('td', { className: 'text-end fw-semibold tabular', text: formatMoneyFromCents(cents) })
        ])
      );
    });

    if (paymentRows.length === 0) {
      tbody.appendChild(
        el('tr', {}, [
          el('td', {
            colSpan: 4,
            className: 'pos-empty-state',
            text: 'No payment breakdown rows returned.'
          })
        ])
      );
    }

    document.getElementById('reportPaymentSumOrders').textContent = String(sumOrders);
    document.getElementById('reportPaymentSumSales').textContent = formatMoneyFromCents(sumCents);
  }

  global.BrewReports = {
    init,
    loadReport
  };
})(window);
