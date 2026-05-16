import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { LayoutDashboard, Building2, Users, Calendar, Bell } from 'lucide-react';

interface OrganizerLayoutProps {
  children: React.ReactNode;
}

export const OrganizerLayout: React.FC<OrganizerLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = [
    { icon: LayoutDashboard, label: 'Dashboard', path: '/organizer' },
    { icon: Building2, label: 'My Organizations', path: '/organizer/organizations' },
    { icon: Calendar, label: 'Workshops', path: '/organizer/workshops' },
    { icon: Users, label: 'Staff Management', path: '/organizer/staff' },
  ];

  return (
    <div className="flex h-screen bg-[#FAFAFA] overflow-hidden">
      {/* Sidebar */}
      <aside className="w-[256px] bg-white border-r border-[#E0E0E0] flex flex-col shrink-0">
        <div className="p-6 border-b border-[#E0E0E0] flex items-center gap-3">
          <div className="w-10 h-10 bg-[#1B5E20] rounded-lg flex items-center justify-center shrink-0">
            {/* Simple logo shape to mimic the vector */}
            <div className="w-5 h-5 bg-white opacity-90 rounded-[2px] relative flex flex-col gap-1 items-center justify-center">
               <div className="w-2.5 h-2.5 border-2 border-[#1B5E20] rounded-full absolute" />
            </div>
          </div>
          <div>
            <h1 className="text-[20px] font-semibold text-[#1A1A1A] leading-[30px] tracking-[-0.45px]">UniHub</h1>
            <p className="text-[12px] text-[#757575] leading-[16px]">Organizer Portal</p>
          </div>
        </div>

        <nav className="flex-1 px-4 py-4 flex flex-col gap-1">
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path || (item.path === '/organizer' && location.pathname === '/organizer');
            return (
              <button
                key={item.label}
                onClick={() => navigate(item.path)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-[16px] font-medium transition-colors ${
                  isActive 
                    ? 'bg-[#E8F5E9] text-[#1B5E20]' 
                    : 'text-[#1A1A1A] hover:bg-gray-50'
                }`}
              >
                <item.icon size={20} className={isActive ? 'text-[#1B5E20]' : 'text-[#1A1A1A]'} />
                {item.label}
              </button>
            );
          })}
        </nav>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Header */}
        <header className="h-[64px] bg-white border-b border-[#E0E0E0] flex items-center justify-between px-6 shrink-0 shadow-[0px_1px_3px_rgba(0,0,0,0.1),0px_1px_2px_-1px_rgba(0,0,0,0.1)]">
          <div className="flex items-center gap-2">
            <div className="w-10 h-10 bg-[#1B5E20] rounded-lg flex items-center justify-center shrink-0 lg:hidden">
              <span className="text-white font-bold">U</span>
            </div>
            <h2 className="text-[24px] font-semibold text-[#1A1A1A] leading-[36px] hidden lg:block">UniHub <span className="text-[12px] font-normal text-[#757575] ml-2">Organizer Portal</span></h2>
          </div>
          <div className="flex items-center gap-4">
            <button className="relative w-10 h-10 flex items-center justify-center rounded-full hover:bg-gray-100">
              <Bell size={20} className="text-[#1A1A1A]" />
              <div className="absolute top-1 right-1 w-4 h-4 bg-[#1B5E20] rounded-full flex items-center justify-center text-[10px] font-medium text-white border border-white">
                2
              </div>
            </button>
            <button className="w-10 h-10 rounded-full flex items-center justify-center text-white font-medium text-[14px]" style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}>
              SC
            </button>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto bg-[#FAFAFA] p-6 lg:p-8">
          <div className="max-w-[1004px] mx-auto w-full">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
};
