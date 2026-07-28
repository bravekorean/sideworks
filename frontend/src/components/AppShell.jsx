import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'


const navigationGroups = [
  {
    label: 'WORKSPACE',
    items: [
      { id: 'dashboard', label: '대시보드', icon: 'home', path: '/dashboard' },
    ],
  },
  {
    label: '전자결재',
    items: [
      { id: 'new', label: '새 결재 작성', icon: 'edit' },
      { id: 'drafts', label: '임시저장함', icon: 'file', count: 3 },
      { id: 'sent', label: '내가 작성한 문서', icon: 'send' },
      { id: 'pending', label: '결재 대기함', icon: 'inbox', count: 7, path: '/approvals/pending' },
      { id: 'processed', label: '결재 처리함', icon: 'check' },
      { id: 'cc', label: '참조 문서함', icon: 'eye', count: 2 },
    ],
  },
  {
    label: '관리',
    role: 'ADMIN',
    items: [
      { id: 'users', label: '사용자 관리', icon: 'users' },
      { id: 'departments', label: '부서 관리', icon: 'building' },
      { id: 'positions', label: '직급 관리', icon: 'badge' },
    ],
  },
]

const iconPaths = {
  home: 'M3 11.5 12 4l9 7.5M5.5 10v10h13V10M9 20v-6h6v6',
  edit: 'M4 20h4l11-11-4-4L4 16v4ZM13.5 6.5l4 4',
  file: 'M6 3h8l4 4v14H6V3Zm8 0v5h5',
  send: 'm3 4 18 8-18 8 3-8-3-8Zm3 8h15',
  inbox: 'M4 5h16v14H4V5Zm0 9h5l2 2h2l2-2h5',
  check: 'M4 12.5 9 17l11-11',
  eye: 'M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Zm9.5 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  users: 'M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm9-1a3 3 0 0 1 0 6m4 5v-2a4 4 0 0 0-3-3.87',
  building: 'M4 21V5l8-3 8 3v16M8 8h1m3 0h1m3 0h1M8 12h1m3 0h1m3 0h1M8 16h1m3 0h1m3 0h1M2 21h20',
  badge: 'M12 3 8 5v5c0 3 1.8 5.8 4 7 2.2-1.2 4-4 4-7V5l-4-2Zm0 14v4m-3 0h6',
  search: 'm20 20-4.5-4.5m2.5-4.5a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Zm-8 12h4',
  menu: 'M4 7h16M4 12h16M4 17h16',
  user: 'M20 21a8 8 0 0 0-16 0m8-9a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z',
}

function LineIcon({ name, size = 18 }) {
  return (
    <svg
      aria-hidden="true"
      className="line-icon"
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
    >
      <path d={iconPaths[name]} stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.7" />
    </svg>
  )
}

function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="app-frame">
      <header className="topbar">
        <div className="topbar__brand-area">
          <button
            aria-label="메뉴 열기"
            className="icon-button mobile-menu"
            onClick={() => setSidebarOpen((open) => !open)}
            type="button"
          >
            <LineIcon name="menu" size={20} />
          </button>

          <a className="brand" href="#dashboard" aria-label="SideWorks 홈">
            <span className="brand__mark">S</span>
            <span className="brand__name">SideWorks</span>
          </a>
        </div>

        <label className="global-search">
          <LineIcon name="search" size={17} />
          <input aria-label="통합 검색" placeholder="문서, 사용자 검색" type="search" />
          <kbd>⌘ K</kbd>
        </label>

        <div className="topbar__actions">
          <button aria-label="알림" className="icon-button notification-button" type="button">
            <LineIcon name="bell" size={19} />
            <span className="notification-dot" />
          </button>
          <button className="profile-button" type="button">
            <span className="avatar">관리</span>
            <span className="profile-button__copy">
              <strong>김관리</strong>
              <small>SUPER ADMIN</small>
            </span>
            <span className="profile-button__chevron">⌄</span>
          </button>
        </div>
      </header>

      <aside className={`sidebar ${sidebarOpen ? 'sidebar--open' : ''}`}>
        <div className="sidebar__workspace">
          <span className="workspace-icon">SW</span>
          <div>
            <strong>SideWorks HQ</strong>
            <small>그룹웨어 워크스페이스</small>
          </div>
        </div>

        <nav className="sidebar__nav" aria-label="주요 메뉴">
          {navigationGroups.map((group) => (
            <section className="nav-group" key={group.label}>
              <div className="nav-group__label">
                <span>{group.label}</span>
                {group.role && <span className="role-chip">관리자</span>}
              </div>
              {group.items.map((item) => {
                const itemContent = (
                  <>
                    <LineIcon name={item.icon} />
                    <span>{item.label}</span>
                    {item.count !== undefined && (
                      <span className="nav-item__count">{item.count}</span>
                    )}
                  </>
                )

                if (item.path) {
                  return (
                    <NavLink
                      className={({ isActive }) =>
                        `nav-item ${isActive ? 'nav-item--active' : ''}`
                      }
                      key={item.id}
                      onClick={() => setSidebarOpen(false)}
                      to={item.path}
                    >
                      {itemContent}
                    </NavLink>
                  )
                }

                return (
                  <button className="nav-item" key={item.id} type="button">
                    {itemContent}
                  </button>
                )
              })}
            </section>
          ))}
        </nav>

        <div className="sidebar__footer">
          <button className="mypage-link" type="button">
            <LineIcon name="user" />
            <span>마이페이지</span>
          </button>
        </div>
      </aside>

      {sidebarOpen && <button aria-label="메뉴 닫기" className="sidebar-backdrop" onClick={() => setSidebarOpen(false)} type="button" />}

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  )
}

export default AppShell
