import React, { useState, useRef, useEffect } from 'react';
import { BookOpen, SlidersHorizontal, Bell, User, LogOut, Ticket } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../hooks/useAuthStore';

export const StudentHeader: React.FC = () => {
  const user = useAuthStore((state) => state.user);
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const navigate = useNavigate();
  
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Get initials from user full name
  const getInitials = (name: string) => {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  };

  const initials = user?.full_name ? getInitials(user.full_name) : 'AS';

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <header className="h-[77.33px] bg-white border-b-[1.33px] border-[#E0E0E0] shadow-[0px_1px_3px_rgba(0,0,0,0.1),0px_1px_2px_-1px_rgba(0,0,0,0.1)] w-full flex justify-center px-[90px]">
      <div className="w-full max-w-[1280px] h-full flex items-center justify-between py-3">
        
        {/* Left Side: Logo & Title */}
        <div className="flex items-center gap-3 cursor-pointer" onClick={() => navigate('/')}>
          <div className="w-10 h-10 bg-primary rounded-lg flex items-center justify-center flex-shrink-0">
            <BookOpen className="text-white w-5 h-5" />
          </div>
          <div className="flex flex-col">
            <h1 className="text-[24px] font-semibold text-[#1A1A1A] leading-[36px]">
              UniHub Workshop System
            </h1>
            <span className="text-[12px] font-medium text-[#757575] leading-[16px]">
              Skills & Career Week
            </span>
          </div>
        </div>

        {/* Right Side: Actions & Avatar */}
        <div className="flex items-center gap-2 relative" ref={dropdownRef}>

          <button 
            className="w-10 h-10 rounded-lg flex items-center justify-center hover:bg-gray-100 transition-colors" 
            aria-label="My Tickets"
            onClick={() => navigate('/my-tickets')}
          >
            <Ticket size={20} className="text-[#1A1A1A]" />
          </button>
          
          <button className="relative w-10 h-10 rounded-lg flex items-center justify-center hover:bg-gray-100 transition-colors" aria-label="Notifications">
            <Bell size={20} className="text-[#1A1A1A]" />
            <span className="absolute top-[6px] right-[6px] w-5 h-5 bg-[#C62828] text-white text-[12px] font-medium rounded-full flex items-center justify-center leading-[16px]">
              3
            </span>
          </button>

          <button 
            className="ml-2 w-10 h-10 rounded-full flex items-center justify-center text-white font-semibold text-[14px] hover:ring-2 hover:ring-primary/50 transition-all cursor-pointer" 
            style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            aria-expanded={isDropdownOpen}
            aria-haspopup="true"
          >
            {initials}
          </button>

          {/* Avatar Dropdown Menu */}
          {isDropdownOpen && (
            <div className="absolute right-0 top-[56px] w-[224px] bg-white border-[1.33px] border-[#E0E0E0] rounded-lg shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] flex flex-col z-50">
              {/* Profile Summary */}
              <div className="border-b-[1.33px] border-[#E0E0E0] p-4 flex flex-col">
                <span className="text-[16px] font-semibold text-[#1A1A1A] leading-[24px]">
                  {user?.full_name || 'Sarah Johnson'}
                </span>
                <span className="text-[12px] font-normal text-[#757575] leading-[16px] mt-1">
                  {user?.student_id || 'STU-2024-1001'}
                </span>
                <span className="text-[12px] font-normal text-[#757575] leading-[16px] mt-1">
                  {user?.email || 'sarah.johnson@university.edu'}
                </span>
              </div>
              
              {/* Links */}
              <div className="p-2 flex flex-col">
                <button 
                  onClick={() => {
                    setIsDropdownOpen(false);
                    navigate('/profile');
                  }}
                  className="w-full flex items-center gap-3 px-4 py-2 hover:bg-gray-50 rounded-md transition-colors"
                >
                  <User size={16} className="text-[#757575]" />
                  <span className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">
                    Profile Settings
                  </span>
                </button>
                <button 
                  onClick={handleLogout}
                  className="w-full flex items-center gap-3 px-4 py-2 hover:bg-gray-50 rounded-md transition-colors"
                >
                  <LogOut size={16} className="text-[#757575]" />
                  <span className="text-[14px] font-medium text-[#1A1A1A] leading-[20px]">
                    Logout
                  </span>
                </button>
              </div>
            </div>
          )}

        </div>

      </div>
    </header>
  );
};
