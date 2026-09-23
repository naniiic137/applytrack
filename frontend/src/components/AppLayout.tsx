import { BarChart3, BellRing, KanbanSquare, LogOut, Plus, Table2 } from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { usePanels } from '../hooks/usePanels';
import { ApplicationDrawer } from './ApplicationDrawer';
import { ApplicationFormDialog } from './ApplicationForm';
import { Logo } from './Logo';

const NAV = [
  { to: '/board', label: 'Board', icon: KanbanSquare },
  { to: '/applications', label: 'List', icon: Table2 },
  { to: '/dashboard', label: 'Dashboard', icon: BarChart3 },
  { to: '/follow-ups', label: 'Follow-ups', icon: BellRing },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const panels = usePanels();

  return (
    <div className="shell">
      <aside className="sidebar">
        <Logo />
        <button type="button" className="btn btn-primary sidebar-new" onClick={panels.startCreate}>
          <Plus size={16} /> New application
        </button>
        <nav className="side-nav" aria-label="Main">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className="side-link">
              <Icon size={17} aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-user">
          <div className="avatar" aria-hidden="true">
            {user?.displayName.charAt(0).toUpperCase()}
          </div>
          <div className="sidebar-user-text">
            <strong>{user?.displayName}</strong>
            <span className="muted small">{user?.email}</span>
          </div>
          <button type="button" className="icon-btn" onClick={logout} aria-label="Log out" title="Log out">
            <LogOut size={16} />
          </button>
        </div>
      </aside>

      <header className="topbar">
        <Logo />
        <div className="topbar-actions">
          <button type="button" className="btn btn-primary btn-sm" onClick={panels.startCreate}>
            <Plus size={16} /> New
          </button>
          <button type="button" className="icon-btn" onClick={logout} aria-label="Log out">
            <LogOut size={16} />
          </button>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <nav className="bottom-nav" aria-label="Main">
        {NAV.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} className="bottom-link">
            <Icon size={19} aria-hidden="true" />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>

      {panels.openAppId !== null && panels.editAppId === null && !panels.isCreating && (
        <ApplicationDrawer applicationId={panels.openAppId} onClose={panels.closeApp} onEdit={panels.startEdit} />
      )}
      {(panels.isCreating || panels.editAppId !== null) && (
        <ApplicationFormDialog
          applicationId={panels.editAppId}
          onClose={panels.closeForm}
          onSaved={(id) => panels.openApp(id)}
        />
      )}
    </div>
  );
}
