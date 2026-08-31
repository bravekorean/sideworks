import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { logout } from '../api/authApi'
import { getMyProfile } from '../api/userApi'

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
      { id: 'new', label: '새 결재 작성', icon: 'edit', path: '/approvals/new' },
      { id: 'drafts', label: '임시저장함', icon: 'file', path: '/approvals/drafts' },
      { id: 'sent', label: '내가 작성한 문서', icon: 'send', path: '/approvals/sent' },
      { id: 'pending', label: '결재 대기함', icon: 'inbox', path: '/approvals/pending' },
      { id: 'processed', label: '결재 처리함', icon: 'check', path: '/approvals/processed' },
      { id: 'cc', label: '참조 문서함', icon: 'eye', path: '/approvals/cc' },
    ],
  },
  {
    label: '관리',
    role: 'ADMIN',
    items: [
      { id: 'users', label: '사용자 관리', icon: 'users', path: '/admin/users' },
      { id: 'departments', label: '부서 관리', icon: 'building', path: '/admin/departments' },
      { id: 'positions', label: '직급 관리', icon: 'badge', path: '/admin/positions' },
    ],
  },
]

const roleLevel = {
  USER: 0,
  ADMIN: 1,
  SUPER_ADMIN: 2,
}

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
  const [profileMenuOpen, setProfileMenuOpen] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const [globalSearchQuery, setGlobalSearchQuery] = useState('')
  const globalSearchInputRef = useRef(null)
  const navigate = useNavigate()

  useEffect(() => {
    let isActive = true

    const loadCurrentUser = async () => {
      try {
        const profile = await getMyProfile()

        if (isActive) {
          setCurrentUser(profile)
        }
      } catch {
        if (isActive) {
          sessionStorage.removeItem('accessToken')
          navigate('/login', { replace: true })
        }
      }
    }

    loadCurrentUser()

    return () => {
      isActive = false
    }
  }, [navigate])

  useEffect(() => {
    const focusGlobalSearch = (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        globalSearchInputRef.current?.focus()
      }
    }

    window.addEventListener('keydown', focusGlobalSearch)
    return () => window.removeEventListener('keydown', focusGlobalSearch)
  }, [])

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      sessionStorage.removeItem('accessToken')
      setProfileMenuOpen(false)
      navigate('/login', { replace: true })
    }
  }

  const handleGlobalSearch = (event) => {
    event.preventDefault()
    const keyword = globalSearchQuery.trim()

    if (keyword) {
      navigate(`/search?keyword=${encodeURIComponent(keyword)}`)
    }
  }

  const visibleNavigationGroups = navigationGroups.filter(
    (group) =>
      !group.role ||
      roleLevel[currentUser?.userRole] >= roleLevel[group.role],
  )

  const userName = currentUser?.userName ?? '사용자'
  const userRole = currentUser?.userRole ?? 'USER'
  const avatarText = userName.slice(0, 2)

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

          <Link className="brand" to="/dashboard" aria-label="SideWorks 홈">
            <span className="brand__mark">S</span>
            <span className="brand__name">SideWorks</span>
          </Link>
        </div>

        <form className="global-search" onSubmit={handleGlobalSearch} role="search">
          <button aria-label="통합 검색 실행" className="global-search__submit" type="submit"><LineIcon name="search" size={17} /></button>
          <input
            aria-label="통합 검색"
            onChange={(event) => setGlobalSearchQuery(event.target.value)}
            placeholder="결재 제목 또는 참여 사용자 검색"
            ref={globalSearchInputRef}
            type="search"
            value={globalSearchQuery}
          />
          <kbd>Ctrl K</kbd>
        </form>

        <div className="topbar__actions">
          <button aria-label="알림" className="icon-button notification-button" type="button">
            <LineIcon name="bell" size={19} />
            <span className="notification-dot" />
          </button>
          <div className="profile-menu-wrap">
            <button
              aria-expanded={profileMenuOpen}
              aria-haspopup="menu"
              className="profile-button"
              onClick={() => setProfileMenuOpen((open) => !open)}
              type="button"
            >
              <span className="avatar">{avatarText}</span>
              <span className="profile-button__copy">
                <strong>{userName}</strong>
                <small>{userRole.replace('_', ' ')}</small>
              </span>
              <span className="profile-button__chevron">⌄</span>
            </button>

            {profileMenuOpen && (
              <div className="profile-dropdown" role="menu">
                <header>
                  <span className="avatar">{avatarText}</span>
                  <div>
                    <strong>{userName}</strong>
                    <small>{userRole}</small>
                  </div>
                </header>
                <Link
                  onClick={() => setProfileMenuOpen(false)}
                  role="menuitem"
                  to="/mypage"
                >
                  내 정보 관리
                </Link>
                <div className="profile-dropdown__divider" />
                <button
                  className="profile-dropdown__logout"
                  onClick={handleLogout}
                  role="menuitem"
                  type="button"
                >
                  로그아웃
                </button>
              </div>
            )}
          </div>
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
          {visibleNavigationGroups.map((group) => (
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
          <NavLink
            className={({ isActive }) =>
              `mypage-link ${isActive ? 'mypage-link--active' : ''}`
            }
            to="/mypage"
          >
            <LineIcon name="user" />
            <span>마이페이지</span>
          </NavLink>
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
