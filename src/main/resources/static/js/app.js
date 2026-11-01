/* ============================================================================
 * app.js — Shared Navigation, Staff Roster, and Role View Sync
 * ============================================================================ */
(function (global) {
  'use strict';

  const { el, clearAlert, showAlert } = global.BrewApi;

  const VALID_SCREENS = ['pos', 'menu', 'orders', 'reports'];
  const STAFF_STORAGE_KEY = 'brewpos.selectedStaffId';
  let staffList = [];
  let selectedStaffId = null;
  let currentScreen = 'pos';

  function readSavedStaffId() {
    try {
      const raw = global.sessionStorage && global.sessionStorage.getItem(STAFF_STORAGE_KEY);
      return raw && /^\d+$/.test(raw) ? Number(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function saveSelectedStaffId(id) {
    try {
      if (global.sessionStorage && id != null) {
        global.sessionStorage.setItem(STAFF_STORAGE_KEY, String(id));
      }
    } catch (_) {
      // Ignore storage restrictions (e.g., private browsing modes)
    }
  }

  function formatStaffOptionLabel(staff) {
    const roleLabel = staff.role === 'MANAGER' ? 'Manager' : 'Cashier';
    if (staff.fullName && staff.fullName.toLowerCase().includes(roleLabel.toLowerCase())) {
      return staff.fullName;
    }
    return `${staff.fullName} · ${roleLabel}`;
  }

  function getSelectedStaff() {
    return staffList.find((s) => Number(s.staffId) === Number(selectedStaffId)) || null;
  }

  /**
   * Returns 'CASHIER' or 'MANAGER' from the selected staff member for UI view
   * switching (not an authorization boundary; authentication is omitted by spec).
   */
  function getEffectiveRole() {
    const hasAnyManager = staffList.some((s) => s.role === 'MANAGER');
    if (!hasAnyManager) {
      return 'MANAGER';
    }
    const staff = getSelectedStaff();
    return staff && staff.role === 'MANAGER' ? 'MANAGER' : 'CASHIER';
  }

  function setSelectedStaffId(id, source) {
    selectedStaffId = id;
    saveSelectedStaffId(id);

    const navSelect = document.getElementById('navStaffSelect');
    const posSelect = document.getElementById('posStaffSelect');

    if (source !== 'nav' && navSelect && navSelect.value !== String(id)) {
      navSelect.value = String(id);
    }
    if (source !== 'pos' && posSelect && posSelect.value !== String(id)) {
      posSelect.value = String(id);
    }

    syncRoleDependentViews();
  }

  function syncRoleDependentViews() {
    if (global.BrewMenu) {
      global.BrewMenu.applyRoleVisibility();
    }
  }

  async function loadStaff() {
    const navSelect = document.getElementById('navStaffSelect');
    try {
      const list = await global.BrewApi.getStaff();
      staffList = Array.isArray(list) ? list : [];

      navSelect.replaceChildren();
      if (staffList.length === 0) {
        navSelect.appendChild(el('option', { value: '', text: 'No staff found' }));
        navSelect.disabled = true;
        selectedStaffId = null;
        showAlert(
          'globalStatusBanner',
          'warning',
          'No staff records exist in grp_brewposdb. Run database/seed.sql in psql so orders can be processed.',
          false
        );
      } else {
        clearAlert('globalStatusBanner');
        navSelect.disabled = false;
        const savedId = readSavedStaffId();
        const savedStaff = savedId != null
          ? staffList.find((s) => Number(s.staffId) === savedId)
          : null;
        // Restore saved selection in this tab, or default to first CASHIER / first staff record
        const preferred = savedStaff || staffList.find((s) => s.role === 'CASHIER') || staffList[0];
        selectedStaffId = preferred.staffId;
        saveSelectedStaffId(selectedStaffId);

        staffList.forEach((s) => {
          navSelect.appendChild(
            el('option', {
              value: String(s.staffId),
              selected: Number(s.staffId) === Number(selectedStaffId),
              text: formatStaffOptionLabel(s)
            })
          );
        });
      }

      if (global.BrewPos) {
        global.BrewPos.syncStaffDropdown(staffList, selectedStaffId);
      }
      syncRoleDependentViews();
    } catch (err) {
      navSelect.replaceChildren(el('option', { value: '', text: 'Staff unavailable' }));
      navSelect.disabled = true;
      showAlert('globalStatusBanner', 'danger', err.message || 'Could not load staff records.', false);
    }
  }

  function showScreen(screenName) {
    const target = VALID_SCREENS.includes(screenName) ? screenName : 'pos';
    currentScreen = target;

    VALID_SCREENS.forEach((name) => {
      const section = document.getElementById(`screen-${name}`);
      if (section) {
        section.classList.toggle('d-none', name !== target);
      }
    });

    document.querySelectorAll('[data-screen-tab]').forEach((btn) => {
      const active = btn.getAttribute('data-screen-tab') === target;
      btn.classList.toggle('active', active);
      if (active) {
        btn.setAttribute('aria-current', 'page');
      } else {
        btn.removeAttribute('aria-current');
      }
    });

    if (window.location.hash !== `#${target}`) {
      history.replaceState(null, '', `#${target}`);
    }

    // Refresh data from PostgreSQL when entering each screen so tabs never show stale state
    if (target === 'pos' && global.BrewPos) {
      global.BrewPos.loadActiveMenu(false);
    } else if (target === 'menu' && global.BrewMenu) {
      global.BrewMenu.loadAll(false);
    } else if (target === 'orders' && global.BrewOrders) {
      global.BrewOrders.loadOrders(false);
    } else if (target === 'reports' && global.BrewReports) {
      global.BrewReports.loadReport(false);
    }
  }

  function bindNavigationAndRoleControls() {
    document.querySelectorAll('[data-screen-tab]').forEach((btn) => {
      btn.addEventListener('click', () => showScreen(btn.getAttribute('data-screen-tab')));
    });

    document.querySelectorAll('[data-screen-link]').forEach((link) => {
      link.addEventListener('click', (e) => {
        e.preventDefault();
        showScreen(link.getAttribute('data-screen-link'));
      });
    });

    document.getElementById('navStaffSelect').addEventListener('change', (e) => {
      if (e.target.value) {
        setSelectedStaffId(Number(e.target.value), 'nav');
      }
    });

    window.addEventListener('hashchange', () => {
      const hashScreen = window.location.hash.replace('#', '');
      if (VALID_SCREENS.includes(hashScreen) && hashScreen !== currentScreen) {
        showScreen(hashScreen);
      }
    });
  }

  async function bootstrap() {
    bindNavigationAndRoleControls();
    global.BrewPos.init();
    global.BrewMenu.init();
    global.BrewOrders.init();
    global.BrewReports.init();

    await loadStaff();

    // Open directly to POS unless a valid screen hash was explicitly requested
    const initialHash = window.location.hash.replace('#', '');
    showScreen(VALID_SCREENS.includes(initialHash) ? initialHash : 'pos');
  }

  document.addEventListener('DOMContentLoaded', bootstrap);

  global.BrewApp = {
    formatStaffOptionLabel,
    getEffectiveRole,
    getSelectedStaff,
    setSelectedStaffId,
    showScreen
  };
})(window);
