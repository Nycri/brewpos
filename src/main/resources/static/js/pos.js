/* ============================================================================
 * pos.js — Screen 1: Active Menu Tiles, Duplicate-Merging Ticket & Checkout
 * ============================================================================ */
(function (global) {
  'use strict';

  const {
    el,
    toCents,
    formatMoney,
    formatMoneyFromCents,
    formatTimestamp,
    formatPaymentMethod,
    clearAlert,
    showAlert
  } = global.BrewApi;

  // Cart keyed by itemId -> { itemId, itemName, categoryName, unitCents, quantity, unavailable }
  const MAX_LINE_QTY = 9999;
  const cart = new Map();
  let activeCategories = [];
  let selectedCategoryId = 'ALL';
  let searchQuery = '';
  let isSubmitting = false;

  function init() {
    const refreshBtn = document.getElementById('posRefreshMenuBtn');
    const clearBtn = document.getElementById('posClearCartBtn');
    const submitBtn = document.getElementById('posSubmitOrderBtn');
    const dismissReceiptBtn = document.getElementById('posDismissReceiptBtn');
    const posStaffSelect = document.getElementById('posStaffSelect');
    const searchInput = document.getElementById('posSearchInput');

    refreshBtn.addEventListener('click', () => loadActiveMenu(true));
    clearBtn.addEventListener('click', () => {
      cart.clear();
      clearAlert('posAlertArea');
      renderCart();
      renderActiveMenu();
    });
    submitBtn.addEventListener('click', submitOrder);
    dismissReceiptBtn.addEventListener('click', () => {
      document.getElementById('posReceiptCard').classList.add('d-none');
    });

    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        searchQuery = e.target.value || '';
        renderActiveMenu();
      });
    }

    document.querySelectorAll('.pos-pay-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        if (isSubmitting) return;
        setPaymentMethod(btn.getAttribute('data-payment') || 'CASH');
      });
    });

    posStaffSelect.addEventListener('change', () => {
      if (global.BrewApp && posStaffSelect.value) {
        global.BrewApp.setSelectedStaffId(Number(posStaffSelect.value), 'pos');
      }
      updateSubmitButtonState();
    });

    setPaymentMethod('CASH');
    renderCart();
  }

  function setPaymentMethod(method) {
    const normalized = method === 'ONLINE' ? 'ONLINE' : 'CASH';
    const paymentSelect = document.getElementById('posPaymentMethod');
    if (paymentSelect) {
      paymentSelect.value = normalized;
    }
    document.querySelectorAll('.pos-pay-btn').forEach((btn) => {
      const active = btn.getAttribute('data-payment') === normalized;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  async function loadActiveMenu(manualRefresh) {
    const loadingEl = document.getElementById('posMenuLoading');
    const refreshBtn = document.getElementById('posRefreshMenuBtn');
    loadingEl.classList.remove('d-none');
    refreshBtn.disabled = true;
    if (manualRefresh) {
      clearAlert('posAlertArea');
    }

    try {
      const grouped = await global.BrewApi.getActiveMenu();
      activeCategories = Array.isArray(grouped) ? grouped : [];

      if (selectedCategoryId !== 'ALL') {
        const stillExists = activeCategories.some((c) => String(c.categoryId) === String(selectedCategoryId));
        if (!stillExists) {
          selectedCategoryId = 'ALL';
        }
      }

      reconcileCartWithActiveMenu();
      renderCategoryFilters();
      renderActiveMenu();
      renderCart();
    } catch (err) {
      showAlert('posAlertArea', 'danger', err.message || 'Failed to load active menu.');
    } finally {
      loadingEl.classList.add('d-none');
      refreshBtn.disabled = false;
    }
  }

  /**
   * Keeps ticket preview prices accurate and flags items if a manager just
   * deactivated or repriced an item in Menu Management.
   */
  function reconcileCartWithActiveMenu() {
    if (cart.size === 0) return;

    const latestById = new Map();
    activeCategories.forEach((cat) => {
      (cat.items || []).forEach((item) => {
        latestById.set(item.itemId, { item, categoryName: cat.categoryName });
      });
    });

    let priceChanged = false;
    let becameInactive = false;

    cart.forEach((line, itemId) => {
      const found = latestById.get(itemId);
      if (!found) {
        if (!line.unavailable) {
          becameInactive = true;
        }
        line.unavailable = true;
      } else {
        const newCents = toCents(found.item.price);
        if (line.unitCents !== newCents) {
          priceChanged = true;
          line.unitCents = newCents;
        }
        line.itemName = found.item.itemName;
        line.categoryName = found.categoryName;
        line.unavailable = false;
      }
    });

    if (becameInactive) {
      showAlert(
        'posAlertArea',
        'warning',
        'One or more items in the ticket were deactivated and cannot be ordered. Remove them before charging.'
      );
    } else if (priceChanged) {
      showAlert(
        'posAlertArea',
        'info',
        'Menu prices were updated; ticket preview totals now reflect current prices.'
      );
    }
  }

  function renderCategoryFilters() {
    const filterBar = document.getElementById('posCategoryFilter');
    filterBar.replaceChildren();

    const makeFilterBtn = (id, label) => {
      const isActive = String(selectedCategoryId) === String(id);
      return el('button', {
        type: 'button',
        className: `pos-cat-pill${isActive ? ' active' : ''}`,
        text: label,
        onClick: () => {
          selectedCategoryId = id;
          renderCategoryFilters();
          renderActiveMenu();
        }
      });
    };

    filterBar.appendChild(makeFilterBtn('ALL', 'All Items'));
    activeCategories.forEach((cat) => {
      filterBar.appendChild(makeFilterBtn(cat.categoryId, cat.categoryName));
    });
  }

  function renderActiveMenu() {
    const container = document.getElementById('posMenuContainer');
    container.replaceChildren();

    if (activeCategories.length === 0) {
      container.appendChild(
        el('div', {
          className: 'pos-empty-state',
          text: 'No active menu items available. Add or activate items in Menu.'
        })
      );
      return;
    }

    const query = searchQuery.trim().toLowerCase();
    const categoryScoped = selectedCategoryId === 'ALL'
      ? activeCategories
      : activeCategories.filter((c) => String(c.categoryId) === String(selectedCategoryId));

    const visibleGroups = [];
    categoryScoped.forEach((group) => {
      const items = (group.items || []).filter((item) => {
        if (!query) return true;
        return item.itemName.toLowerCase().includes(query)
          || group.categoryName.toLowerCase().includes(query);
      });
      if (items.length > 0) {
        visibleGroups.push({ categoryId: group.categoryId, categoryName: group.categoryName, items });
      }
    });

    if (visibleGroups.length === 0) {
      container.appendChild(
        el('div', {
          className: 'pos-empty-state',
          text: `No menu items match "${searchQuery.trim()}".`
        })
      );
      return;
    }

    visibleGroups.forEach((group) => {
      const section = el('div', { className: 'pos-category-section' }, [
        el('h3', {
          className: 'pos-category-heading',
          text: group.categoryName
        })
      ]);

      const grid = el('div', { className: 'pos-item-grid' });
      group.items.forEach((item) => {
        const existing = cart.get(item.itemId);
        const qtyInCart = existing ? existing.quantity : 0;

        const tileChildren = [
          el('span', { className: 'pos-tile-name', text: item.itemName })
        ];

        if (qtyInCart > 0) {
          tileChildren.push(
            el('span', {
              className: 'pos-tile-qty-badge tabular',
              text: String(qtyInCart)
            })
          );
        }

        tileChildren.push(
          el('span', { className: 'pos-tile-footer' }, [
            el('span', { className: 'pos-tile-price tabular', text: formatMoney(item.price) }),
            el('span', { className: 'pos-tile-plus', 'aria-hidden': 'true', text: '+' })
          ])
        );

        const tile = el('button', {
          type: 'button',
          className: `pos-item-tile${qtyInCart > 0 ? ' in-cart' : ''}`,
          disabled: isSubmitting,
          onClick: () => addToCart(item, group.categoryName)
        }, tileChildren);

        grid.appendChild(tile);
      });

      section.appendChild(grid);
      container.appendChild(section);
    });
  }

  function addToCart(item, categoryName) {
    clearAlert('posAlertArea');
    const existing = cart.get(item.itemId);
    if (existing) {
      if (existing.quantity >= MAX_LINE_QTY) {
        showAlert('posAlertArea', 'warning', `Quantity per item cannot exceed ${MAX_LINE_QTY.toLocaleString()}.`);
        return;
      }
      // Composite key (order_id, item_id) allows each item once per order; merge quantities
      existing.quantity += 1;
      existing.unitCents = toCents(item.price);
      existing.unavailable = false;
    } else {
      cart.set(item.itemId, {
        itemId: item.itemId,
        itemName: item.itemName,
        categoryName: categoryName || item.categoryName || '',
        unitCents: toCents(item.price),
        quantity: 1,
        unavailable: false
      });
    }
    renderCart();
    renderActiveMenu();
  }

  function setLineQuantity(itemId, rawValue) {
    const line = cart.get(itemId);
    if (!line) return;

    const trimmed = String(rawValue).trim();
    const parsed = /^\d+$/.test(trimmed) ? Number(trimmed) : NaN;
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > MAX_LINE_QTY) {
      showAlert(
        'posAlertArea',
        'warning',
        `Quantity must be a whole number between 1 and ${MAX_LINE_QTY.toLocaleString()}.`
      );
      renderCart();
      return;
    }
    line.quantity = parsed;
    clearAlert('posAlertArea');
    renderCart();
    renderActiveMenu();
  }

  function removeLine(itemId) {
    cart.delete(itemId);
    renderCart();
    renderActiveMenu();
  }

  function getCartTotalCents() {
    let total = 0;
    cart.forEach((line) => {
      total += line.unitCents * line.quantity;
    });
    return total;
  }

  function renderCart() {
    const tbody = document.getElementById('posCartBody');
    const previewTotalEl = document.getElementById('posPreviewTotal');
    const countBadge = document.getElementById('posItemCountBadge');
    const clearBtn = document.getElementById('posClearCartBtn');

    document.querySelectorAll('.pos-pay-btn').forEach((btn) => {
      btn.disabled = isSubmitting;
    });

    tbody.replaceChildren();

    if (cart.size === 0) {
      tbody.appendChild(
        el('tr', {}, [
          el('td', {
            colSpan: 4,
            className: 'pos-empty-state',
            text: 'Tap items on the menu to build an order'
          })
        ])
      );
      previewTotalEl.textContent = '0.00';
      countBadge.textContent = '0';
      clearBtn.disabled = true;
      updateSubmitButtonState();
      return;
    }

    let totalCents = 0;
    let totalUnits = 0;

    cart.forEach((line) => {
      const subtotalCents = line.unitCents * line.quantity;
      totalCents += subtotalCents;
      totalUnits += line.quantity;

      const qtyInput = el('input', {
        type: 'number',
        min: '1',
        max: String(MAX_LINE_QTY),
        step: '1',
        value: String(line.quantity),
        disabled: isSubmitting,
        className: 'pos-qty-input tabular',
        'aria-label': `Quantity for ${line.itemName}`,
        onChange: (e) => setLineQuantity(line.itemId, e.target.value)
      });

      const minusBtn = el('button', {
        type: 'button',
        className: 'pos-step-btn',
        disabled: isSubmitting,
        text: '−',
        'aria-label': `Decrease quantity for ${line.itemName}`,
        onClick: () => {
          if (line.quantity <= 1) {
            removeLine(line.itemId);
          } else {
            setLineQuantity(line.itemId, line.quantity - 1);
          }
        }
      });

      const plusBtn = el('button', {
        type: 'button',
        className: 'pos-step-btn',
        disabled: isSubmitting || line.quantity >= MAX_LINE_QTY,
        text: '+',
        'aria-label': `Increase quantity for ${line.itemName}`,
        onClick: () => setLineQuantity(line.itemId, line.quantity + 1)
      });

      const nameCellChildren = [
        el('div', { className: 'fw-semibold small', text: line.itemName }),
        el('div', { className: 'text-muted small tabular', text: `@ ${formatMoneyFromCents(line.unitCents)}` })
      ];
      if (line.unavailable) {
        nameCellChildren.push(
          el('span', { className: 'pos-status-pill bg-danger text-white mt-1', text: 'Inactive — remove' })
        );
      }

      const row = el('tr', { className: line.unavailable ? 'table-danger' : '' }, [
        el('td', {}, nameCellChildren),
        el('td', { className: 'text-center' }, [
          el('div', { className: 'pos-stepper' }, [minusBtn, qtyInput, plusBtn])
        ]),
        el('td', { className: 'text-end small fw-bold tabular', text: formatMoneyFromCents(subtotalCents) }),
        el('td', { className: 'text-end' }, [
          el('button', {
            type: 'button',
            className: 'pos-remove-btn',
            disabled: isSubmitting,
            title: 'Remove item',
            'aria-label': `Remove ${line.itemName}`,
            text: '×',
            onClick: () => removeLine(line.itemId)
          })
        ])
      ]);

      tbody.appendChild(row);
    });

    previewTotalEl.textContent = formatMoneyFromCents(totalCents);
    countBadge.textContent = String(totalUnits);
    clearBtn.disabled = isSubmitting;
    updateSubmitButtonState();
  }

  function updateSubmitButtonState() {
    const submitBtn = document.getElementById('posSubmitOrderBtn');
    const staffVal = document.getElementById('posStaffSelect').value;
    const hasInactive = Array.from(cart.values()).some((l) => l.unavailable);
    submitBtn.disabled = isSubmitting || cart.size === 0 || !staffVal || hasInactive;

    if (isSubmitting) {
      submitBtn.textContent = 'Processing...';
    } else if (cart.size > 0) {
      submitBtn.textContent = `Charge · ${formatMoneyFromCents(getCartTotalCents())}`;
    } else {
      submitBtn.textContent = 'Charge Order';
    }
  }

  async function submitOrder() {
    if (isSubmitting) return;
    clearAlert('posAlertArea');

    const staffSelect = document.getElementById('posStaffSelect');
    const paymentSelect = document.getElementById('posPaymentMethod');

    if (!staffSelect.value) {
      showAlert('posAlertArea', 'warning', 'Please select a staff member before charging an order.');
      return;
    }
    if (cart.size === 0) {
      showAlert('posAlertArea', 'warning', 'Ticket is empty. Tap at least one menu item first.');
      return;
    }

    const itemsPayload = [];
    for (const line of cart.values()) {
      if (!Number.isInteger(line.quantity) || line.quantity <= 0 || line.quantity > MAX_LINE_QTY) {
        showAlert('posAlertArea', 'danger', `Invalid quantity for "${line.itemName}".`);
        return;
      }
      itemsPayload.push({
        itemId: line.itemId,
        quantity: line.quantity
      });
    }

    isSubmitting = true;
    renderCart();
    renderActiveMenu();

    try {
      const savedOrder = await global.BrewApi.createOrder({
        staffId: Number(staffSelect.value),
        paymentMethod: paymentSelect.value,
        items: itemsPayload
      });

      // Only clear ticket and display receipt AFTER confirmed 201 server response
      cart.clear();
      renderConfirmedReceipt(savedOrder);
    } catch (err) {
      // Preserve ticket so cashier does not lose entered items
      showAlert('posAlertArea', 'danger', err.message || 'Order creation failed.');
    } finally {
      isSubmitting = false;
      renderCart();
      renderActiveMenu();
    }
  }

  function renderConfirmedReceipt(order) {
    const card = document.getElementById('posReceiptCard');
    const title = document.getElementById('posReceiptTitle');
    const meta = document.getElementById('posReceiptMeta');
    const linesBody = document.getElementById('posReceiptLines');
    const totalEl = document.getElementById('posReceiptTotal');

    title.textContent = `Order #${order.orderId} Completed`;
    meta.replaceChildren(
      el('div', {}, [
        el('strong', { text: 'Time: ' }),
        el('span', { className: 'tabular', text: formatTimestamp(order.orderTimestamp) })
      ]),
      el('div', {}, [
        el('strong', { text: 'Staff: ' }),
        document.createTextNode(order.staffName || `#${order.staffId}`)
      ]),
      el('div', {}, [
        el('strong', { text: 'Payment: ' }),
        document.createTextNode(formatPaymentMethod(order.paymentMethod))
      ])
    );

    linesBody.replaceChildren();
    (order.lines || []).forEach((line) => {
      linesBody.appendChild(
        el('tr', {}, [
          el('td', { text: line.itemName }),
          el('td', { className: 'text-center tabular', text: String(line.quantity) }),
          el('td', { className: 'text-end tabular', text: formatMoney(line.soldPrice) }),
          el('td', { className: 'text-end fw-semibold tabular', text: formatMoney(line.subtotal) })
        ])
      );
    });

    totalEl.textContent = formatMoney(order.total);
    card.classList.remove('d-none');
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function syncStaffDropdown(staffList, selectedStaffId) {
    const posStaffSelect = document.getElementById('posStaffSelect');
    posStaffSelect.replaceChildren();

    if (!staffList || staffList.length === 0) {
      posStaffSelect.appendChild(el('option', { value: '', text: 'No staff in database' }));
      posStaffSelect.disabled = true;
      updateSubmitButtonState();
      return;
    }

    posStaffSelect.disabled = false;
    staffList.forEach((s) => {
      const label = global.BrewApp && global.BrewApp.formatStaffOptionLabel
        ? global.BrewApp.formatStaffOptionLabel(s)
        : `${s.fullName} · ${s.role}`;
      posStaffSelect.appendChild(
        el('option', {
          value: String(s.staffId),
          selected: Number(s.staffId) === Number(selectedStaffId),
          text: label
        })
      );
    });
    updateSubmitButtonState();
  }

  global.BrewPos = {
    init,
    loadActiveMenu,
    syncStaffDropdown
  };
})(window);
