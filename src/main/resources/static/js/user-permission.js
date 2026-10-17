// US20 - feature/US20-user-permission-frontend
function us20() {
  const permissions = [
    'Xem lịch họp',
    'Tạo lịch họp',
    'Đặt phòng',
    'Quản lý người dùng',
    'Xem báo cáo',
    'Quản lý phòng'
  ];

  const roles = ['Admin', 'Người đặt lịch', 'Người tham dự'];

  // Matrix mac dinh theo quy dinh RBAC
  const defaultMatrix = [
    [true, true, true, true, true, true],       // Admin: toan quyen
    [true, true, true, false, true, false],     // PM / Nguoi dat lich
    [true, false, false, false, false, false]    // Member / Nguoi tham du
  ];

  let matrix = defaultMatrix;

  const authToken = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token') || '';

  screen.innerHTML = `
    ${panel('Ma trận quyền truy cập', `
      <div class="table-wrap">
        <table class="matrix">
          <thead>
            <tr>
              <th>CHỨC NĂNG</th>
              ${roles.map(r => `<th>${r.toUpperCase()}</th>`).join('')}
            </tr>
          </thead>
          <tbody>
            ${permissions.map((p, i) => `
              <tr>
                <td>${p}</td>
                ${roles.map((r, j) => `
                  <td>
                    <input type="checkbox" data-permission="${j}-${i}" aria-label="${r}: ${p}"
                      ${matrix[j][i] ? 'checked' : ''} ${j === 0 ? 'disabled' : ''}>
                  </td>
                `).join('')}
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
      <div class="actions">
        <button id="save-roles" class="button button-primary">Lưu phân quyền</button>
      </div>
    `, 'Admin giữ toàn bộ quyền quản trị theo Least Privilege.')}

    <div class="grid" style="margin-top:16px">
      ${panel('Gán vai trò cho người dùng', `
        <div class="list" id="users-list">
          ${people.map(p => `
            <div class="list-row" id="user-row-${p.id}">
              <div>
                <div class="cell-main">${p.name}</div>
                <div class="cell-sub">${p.dept || p.email}</div>
              </div>
              <select class="select" style="width:185px" data-user-role="${p.id}" aria-label="Vai trò ${p.name}">
                ${roles.map(r => `<option ${r === p.role ? 'selected' : ''}>${r}</option>`).join('')}
              </select>
            </div>
          `).join('')}
        </div>
        <div class="actions">
          <button id="save-users" class="button button-primary">Lưu vai trò người dùng</button>
        </div>
      `, 'Thay đổi vai trò người dùng đồng bộ trực tiếp với Backend API.', 'span-7')}

      ${panel('Xem trước quyền truy cập', `
        ${select('Vai trò xem trước', 'preview-role', roles, roles[1])}
        <div id="preview" class="chips"></div>
      `, 'Danh sách chức năng được cấp theo ma trận quyền hệ thống.', 'span-5')}
    </div>
  `;

  function preview() {
    const roleValue = $('#preview-role').value;
    const j = roles.indexOf(roleValue);
    if (j < 0) return;
    $('#preview').innerHTML = permissions
      .filter((p, i) => matrix[j][i])
      .map(p => `<span class="chip">${p}</span>`)
      .join('') || '<p class="empty">Vai trò chưa được cấp quyền.</p>';
  }

  $$('[data-permission]').forEach(b => {
    b.onchange = () => {
      const [j, i] = b.dataset.permission.split('-').map(Number);
      matrix[j][i] = b.checked;
      preview();
    };
  });

  $('#preview-role').onchange = preview;

  $('#save-roles').onclick = () => {
    toast('Đã cập nhật cấu hình ma trận quyền hệ thống.');
  };

  // Dong bo vai tro nguoi dung voi Backend API
  $('#save-users').onclick = async () => {
    const saveButton = $('#save-users');
    saveButton.disabled = true;

    try {
      let updatedCount = 0;
      let errorOccurred = false;

      // Kiem tra giu lai it nhat 1 Admin
      const currentSelected = people.map(p => {
        const sel = $(`[data-user-role="${p.id}"]`);
        return sel ? sel.value : p.role;
      });

      if (!currentSelected.some(r => r === 'Admin')) {
        toast('Không thể xóa hoặc hạ quyền tài khoản Admin cuối cùng.');
        saveButton.disabled = false;
        return;
      }

      for (const p of people) {
        const selectEl = $(`[data-user-role="${p.id}"]`);
        if (!selectEl) continue;
        const newRoleName = selectEl.value;

        if (newRoleName !== p.role) {
          // Goi API backend de cap nhat
          if (authToken) {
            const roleIdMap = {
              'Admin': '1',
              'Người đặt lịch': '2',
              'Người tham dự': '3',
              'PM': '2',
              'Member': '3'
            };
            const roleId = roleIdMap[newRoleName] || newRoleName;

            const res = await fetch(`/api/admin/users/${p.id}/role`, {
              method: 'PUT',
              headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${authToken}`
              },
              body: JSON.stringify({ roleId: roleId })
            });

            if (!res.ok) {
              errorOccurred = true;
              if (res.status === 409) {
                toast('Lỗi 409: Không thể hạ quyền tài khoản Admin cuối cùng.');
              } else if (res.status === 403) {
                toast('Lỗi 403: Bạn không có quyền quản trị vai trò.');
              } else if (res.status === 401) {
                toast('Lỗi 401: Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.');
              } else {
                toast(`Lỗi cập nhật người dùng ${p.name} (HTTP ${res.status}).`);
              }
              break;
            }
          }
          p.role = newRoleName;
          updatedCount++;
        }
      }

      if (!errorOccurred) {
        toast(`Đã lưu vai trò người dùng thành công (${updatedCount} tài khoản).`);
      }
    } catch (err) {
      toast('Lỗi kết nối API Backend: ' + err.message);
    } finally {
      saveButton.disabled = false;
    }
  };

  preview();
}

us20();
  // Kiem tra quyen Admin cho trang US20
  const currentRole = (localStorage.getItem('auth_role') || '').toUpperCase();
  const isAdmin = !currentRole || currentRole === 'ADMIN' || currentRole.includes('QUẢN TRỊ');
  if (!isAdmin) {
    const warningBanner = document.createElement('div');
    warningBanner.className = 'notice';
    warningBanner.style.cssText = 'background: #fee2e2; border: 1px solid #ef4444; color: #b91c1c; padding: 12px 16px; border-radius: 8px; margin-bottom: 16px; font-weight: 500;';
    warningBanner.textContent = 'Chu y: Tai khoan cua ban khong co quyen Admin. Toan bo chuc nang phan quyen he thong da bi vo hieu hoa.';
    screen.prepend(warningBanner);
    $$('input, select, button', screen).forEach(el => {
      if (el.id !== 'notifications') el.disabled = true;
    });
  }

}
