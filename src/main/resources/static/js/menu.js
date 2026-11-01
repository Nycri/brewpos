/* ============================================================================
 * menu.js — Screen 2: Category & Menu Item Maintenance (Manager / Demo Mode)
 * ============================================================================ */
(function (global) {
  'use strict';

  const { el, formatMoney, clearAlert, showAlert } = global.BrewApi;

  let categories = [];
  let menuItems = [];
  let isBusy = false;

  function isManagerMode() {
    return global.BrewApp ? global.BrewApp.getEffectiveRole() === 'MANAGER' : true;
  }

  function init() {
    document.getElementById('menuRefreshAllBtn').addEventListener('click', () => loadAll(true));
    document.getElementById('categoryForm').addEventListener('submit', onCategorySubmit);
    document.getElementById('categoryCancelEditBtn').addEventListener('click', resetCategoryForm);

    document.getElementById('menuItemForm').addEventListener('submit', onMenuItemSubmit);
    document.getElementById('itemCancelEditBtn').addEventListener('click', resetMenuItemForm);

    document.getElementById('itemFilterCategory').addEventListener('change', renderMenuItemsTable);
    document.getElementById('itemFilterStatus').addEventListener('change', renderMenuItemsTable);
  }

  async function loadAll(clearMessages) {
    const catLoading = document.getElementById('categoriesLoading');
    const itemLoading = document.getElementById('menuItemsLoading');
    const refreshBtn = document.getElementById('menuRefreshAllBtn');

    catLoading.classList.remove('d-none');
    itemLoading.classList.remove('d-none');
    refreshBtn.disabled = true;

    if (clearMessages) {
      clearAlert('menuCategoryAlertArea');
      clearAlert('menuItemAlertArea');
    }

    try {
      const [loadedCategories, loadedItems] = await Promise.all([
        global.BrewApi.getCategories(),
        global.BrewApi.getMenuItems(false)
      ]);
      categories = Array.isArray(loadedCategories) ? loadedCategories : [];
      menuItems = Array.isArray(loadedItems) ? loadedItems : [];

      syncCategoryDropdowns();
      applyRoleVisibility();
    } catch (err) {
      showAlert('menuItemAlertArea', 'danger', err.message || 'Failed to load menu management records.');
    } finally {
      catLoading.classList.add('d-none');
      itemLoading.classList.add('d-none');
      refreshBtn.disabled = false;
    }
  }

  function applyRoleVisibility() {
    const manager = isManagerMode();
    if (!manager) {
      resetCategoryForm();
      resetMenuItemForm();
      clearAlert('menuCategoryAlertArea');
      clearAlert('menuItemAlertArea');
    }
    document.getElementById('menuCashierNotice').classList.toggle('d-none', manager);
    document.getElementById('categoryForm').classList.toggle('d-none', !manager);
    document.getElementById('menuItemForm').classList.toggle('d-none', !manager);

    document.querySelectorAll('.manager-only-col').forEach((th) => {
      th.classList.toggle('d-none', !manager);
    });

    renderCategoriesTable();
    renderMenuItemsTable();
  }

  function syncCategoryDropdowns() {
    const formSelect = document.getElementById('itemCategorySelect');
    const filterSelect = document.getElementById('itemFilterCategory');
    const currentFormVal = formSelect.value;
    const currentFilterVal = filterSelect.value || 'ALL';

    formSelect.replaceChildren();
    if (categories.length === 0) {
      formSelect.appendChild(el('option', { value: '', text: 'Add category first' }));
    } else {
      formSelect.appendChild(el('option', { value: '', text: 'Category...' }));
      categories.forEach((c) => {
        formSelect.appendChild(
          el('option', {
            value: String(c.categoryId),
            selected: String(c.categoryId) === String(currentFormVal),
            text: c.categoryName
          })
        );
      });
    }

    filterSelect.replaceChildren(el('option', { value: 'ALL', text: 'All Categories' }));
    categories.forEach((c) => {
      filterSelect.appendChild(
        el('option', {
          value: String(c.categoryId),
          selected: String(c.categoryId) === String(currentFilterVal),
          text: c.categoryName
        })
      );
    });
  }

  function renderCategoriesTable() {
    const tbody = document.getElementById('categoriesTableBody');
    const manager = isManagerMode();
    tbody.replaceChildren();

    if (categories.length === 0) {
      tbody.appendChild(
        el('tr', {}, [
          el('td', {
            colSpan: manager ? 3 : 2,
            className: 'pos-empty-state',
            text: 'No categories found.'
          })
        ])
      );
      return;
    }

    // Count items per category to clearly show whether safe deletion is available
    const itemCountByCategory = new Map();
    menuItems.forEach((item) => {
      itemCountByCategory.set(item.categoryId, (itemCountByCategory.get(item.categoryId) || 0) + 1);
    });

    categories.forEach((cat) => {
      const count = itemCountByCategory.get(cat.categoryId) || 0;
      const cells = [
        el('td', { className: 'fw-semibold', text: cat.categoryName }),
        el('td', { className: 'text-center' }, [
          el('span', { className: 'pos-count-pill tabular', text: String(count) })
        ])
      ];

      if (manager) {
        const editBtn = el('button', {
          type: 'button',
          className: 'btn btn-sm pos-btn-outline me-1',
          disabled: isBusy,
          text: 'Edit',
          onClick: () => startCategoryEdit(cat)
        });

        const deleteBtn = el('button', {
          type: 'button',
          className: 'btn btn-sm btn-outline-danger',
          disabled: isBusy || count > 0,
          title: count > 0
            ? 'Cannot delete a category while menu items reference it'
            : 'Delete unused category',
          text: 'Delete',
          onClick: () => handleCategoryDelete(cat)
        });

        cells.push(el('td', { className: 'text-end text-nowrap' }, [editBtn, deleteBtn]));
      }

      tbody.appendChild(el('tr', {}, cells));
    });
  }

  function startCategoryEdit(cat) {
    clearAlert('menuCategoryAlertArea');
    document.getElementById('categoryEditId').value = String(cat.categoryId);
    document.getElementById('categoryFormTitle').textContent = 'Edit Category';
    document.getElementById('categoryNameInput').value = cat.categoryName;
    document.getElementById('categorySaveBtn').textContent = 'Save';
    document.getElementById('categoryCancelEditBtn').classList.remove('d-none');
    document.getElementById('categoryNameInput').focus();
  }

  function resetCategoryForm() {
    document.getElementById('categoryEditId').value = '';
    document.getElementById('categoryFormTitle').textContent = 'New Category';
    document.getElementById('categoryNameInput').value = '';
    document.getElementById('categorySaveBtn').textContent = 'Add';
    document.getElementById('categoryCancelEditBtn').classList.add('d-none');
  }

  async function onCategorySubmit(event) {
    event.preventDefault();
    if (isBusy || !isManagerMode()) return;

    clearAlert('menuCategoryAlertArea');
    const editId = document.getElementById('categoryEditId').value;
    const rawName = document.getElementById('categoryNameInput').value;
    const trimmed = rawName.trim();

    if (!trimmed) {
      showAlert('menuCategoryAlertArea', 'warning', 'Category name is required.');
      return;
    }
    if (trimmed.length > 50) {
      showAlert('menuCategoryAlertArea', 'warning', 'Category name must be at most 50 characters.');
      return;
    }

    const saveBtn = document.getElementById('categorySaveBtn');
    isBusy = true;
    saveBtn.disabled = true;

    try {
      if (editId) {
        const updated = await global.BrewApi.updateCategory(Number(editId), trimmed);
        resetCategoryForm();
        await loadAll(false);
        showAlert('menuCategoryAlertArea', 'success', `Updated category "${updated.categoryName}".`);
      } else {
        const created = await global.BrewApi.createCategory(trimmed);
        resetCategoryForm();
        await loadAll(false);
        showAlert('menuCategoryAlertArea', 'success', `Added category "${created.categoryName}".`);
      }
    } catch (err) {
      // Preserve form input so user can correct duplicate/invalid names easily
      showAlert('menuCategoryAlertArea', 'danger', err.message || 'Category operation failed.');
    } finally {
      isBusy = false;
      saveBtn.disabled = false;
      renderCategoriesTable();
      renderMenuItemsTable();
    }
  }

  async function handleCategoryDelete(cat) {
    if (isBusy || !isManagerMode()) return;
    clearAlert('menuCategoryAlertArea');

    isBusy = true;
    renderCategoriesTable();
    try {
      await global.BrewApi.deleteCategory(cat.categoryId);
      if (document.getElementById('categoryEditId').value === String(cat.categoryId)) {
        resetCategoryForm();
      }
      await loadAll(false);
      showAlert('menuCategoryAlertArea', 'success', `Deleted category "${cat.categoryName}".`);
    } catch (err) {
      showAlert('menuCategoryAlertArea', 'danger', err.message || 'Could not delete category.');
    } finally {
      isBusy = false;
      renderCategoriesTable();
    }
  }

  function renderMenuItemsTable() {
    const tbody = document.getElementById('menuItemsTableBody');
    const filterCat = document.getElementById('itemFilterCategory').value || 'ALL';
    const filterStatus = document.getElementById('itemFilterStatus').value || 'ALL';
    const manager = isManagerMode();

    tbody.replaceChildren();

    const filtered = menuItems.filter((item) => {
      if (filterCat !== 'ALL' && String(item.categoryId) !== String(filterCat)) {
        return false;
      }
      if (filterStatus === 'ACTIVE' && !item.isActive) {
        return false;
      }
      if (filterStatus === 'INACTIVE' && item.isActive) {
        return false;
      }
      return true;
    });

    if (filtered.length === 0) {
      tbody.appendChild(
        el('tr', {}, [
          el('td', {
            colSpan: manager ? 5 : 4,
            className: 'pos-empty-state',
            text: menuItems.length === 0
              ? 'No menu items exist yet.'
              : 'No menu items match the selected filter.'
          })
        ])
      );
      return;
    }

    filtered.forEach((item) => {
      const statusBadge = item.isActive
        ? el('span', { className: 'pos-status-pill pos-status-active', text: 'Active' })
        : el('span', { className: 'pos-status-pill pos-status-inactive', text: 'Inactive' });

      const cells = [
        el('td', { className: 'fw-semibold', text: item.itemName }),
        el('td', { className: 'text-muted', text: item.categoryName }),
        el('td', { className: 'text-end fw-semibold tabular', text: formatMoney(item.price) }),
        el('td', { className: 'text-center' }, [statusBadge])
      ];

      if (manager) {
        const editBtn = el('button', {
          type: 'button',
          className: 'btn btn-sm pos-btn-outline me-1',
          disabled: isBusy,
          text: 'Edit',
          onClick: () => startMenuItemEdit(item)
        });

        const toggleBtn = el('button', {
          type: 'button',
          className: item.isActive
            ? 'btn btn-sm pos-btn-outline'
            : 'btn btn-sm btn-outline-success',
          disabled: isBusy,
          text: item.isActive ? 'Deactivate' : 'Activate',
          onClick: () => handleToggleActive(item)
        });

        cells.push(el('td', { className: 'text-end text-nowrap' }, [editBtn, toggleBtn]));
      }

      tbody.appendChild(
        el('tr', { className: item.isActive ? '' : 'text-muted' }, cells)
      );
    });
  }

  function startMenuItemEdit(item) {
    clearAlert('menuItemAlertArea');
    document.getElementById('itemEditId').value = String(item.itemId);
    document.getElementById('itemFormTitle').textContent = 'Edit Menu Item';
    document.getElementById('itemNameInput').value = item.itemName;
    document.getElementById('itemCategorySelect').value = String(item.categoryId);
    document.getElementById('itemPriceInput').value = formatMoney(item.price);
    document.getElementById('itemActiveCheckbox').checked = Boolean(item.isActive);
    document.getElementById('itemSaveBtn').textContent = 'Save';
    document.getElementById('itemCancelEditBtn').classList.remove('d-none');
    document.getElementById('itemNameInput').focus();
  }

  function resetMenuItemForm() {
    document.getElementById('itemEditId').value = '';
    document.getElementById('itemFormTitle').textContent = 'New Menu Item';
    document.getElementById('itemNameInput').value = '';
    document.getElementById('itemPriceInput').value = '';
    document.getElementById('itemActiveCheckbox').checked = true;
    document.getElementById('itemSaveBtn').textContent = 'Add Item';
    document.getElementById('itemCancelEditBtn').classList.add('d-none');
  }

  async function onMenuItemSubmit(event) {
    event.preventDefault();
    if (isBusy || !isManagerMode()) return;

    clearAlert('menuItemAlertArea');
    const editId = document.getElementById('itemEditId').value;
    const itemName = document.getElementById('itemNameInput').value.trim();
    const categoryVal = document.getElementById('itemCategorySelect').value;
    const rawPrice = document.getElementById('itemPriceInput').value.trim();
    const isActive = document.getElementById('itemActiveCheckbox').checked;

    if (!itemName) {
      showAlert('menuItemAlertArea', 'warning', 'Item name is required.');
      return;
    }
    if (itemName.length > 100) {
      showAlert('menuItemAlertArea', 'warning', 'Item name must be at most 100 characters.');
      return;
    }
    if (!categoryVal) {
      showAlert('menuItemAlertArea', 'warning', 'Please select a category.');
      return;
    }
    if (rawPrice === '' || !/^\d+(\.\d{1,2})?$/.test(rawPrice)) {
      showAlert('menuItemAlertArea', 'warning', 'Enter a valid non-negative price with up to 2 decimal places.');
      return;
    }
    if (Number(rawPrice) > 99999999.99) {
      showAlert('menuItemAlertArea', 'warning', 'Price must not exceed 99,999,999.99.');
      return;
    }

    const payload = {
      categoryId: Number(categoryVal),
      itemName,
      price: Number(rawPrice),
      isActive
    };

    const saveBtn = document.getElementById('itemSaveBtn');
    isBusy = true;
    saveBtn.disabled = true;

    try {
      if (editId) {
        const updated = await global.BrewApi.updateMenuItem(Number(editId), payload);
        resetMenuItemForm();
        await loadAll(false);
        showAlert('menuItemAlertArea', 'success', `Updated item "${updated.itemName}".`);
      } else {
        const created = await global.BrewApi.createMenuItem(payload);
        resetMenuItemForm();
        await loadAll(false);
        showAlert('menuItemAlertArea', 'success', `Added item "${created.itemName}".`);
      }
    } catch (err) {
      showAlert('menuItemAlertArea', 'danger', err.message || 'Menu item save failed.');
    } finally {
      isBusy = false;
      saveBtn.disabled = false;
      renderCategoriesTable();
      renderMenuItemsTable();
    }
  }

  async function handleToggleActive(item) {
    if (isBusy || !isManagerMode()) return;
    clearAlert('menuItemAlertArea');

    isBusy = true;
    renderMenuItemsTable();
    try {
      const updated = await global.BrewApi.setMenuItemActive(item.itemId, !item.isActive);
      await loadAll(false);
      showAlert(
        'menuItemAlertArea',
        'success',
        `"${updated.itemName}" is now ${updated.isActive ? 'Active (available on Register)' : 'Inactive (hidden from Register)'}.`
      );
    } catch (err) {
      showAlert('menuItemAlertArea', 'danger', err.message || 'Could not update item status.');
    } finally {
      isBusy = false;
      renderMenuItemsTable();
    }
  }

  global.BrewMenu = {
    init,
    loadAll,
    applyRoleVisibility
  };
})(window);
