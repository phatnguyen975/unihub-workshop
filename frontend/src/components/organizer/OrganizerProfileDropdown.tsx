import React, { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Settings, LogOut } from 'lucide-react';
import { useAuthStore } from '../../hooks/useAuthStore';

export const OrganizerProfileDropdown: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="w-10 h-10 rounded-full flex items-center justify-center text-white font-medium text-[14px]" 
        style={{ background: 'linear-gradient(135deg, #1B5E20 0%, #43A047 100%)' }}
      >
        AO
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-[224px] bg-white border border-[#E0E0E0] rounded-lg shadow-[0px_20px_25px_-5px_rgba(0,0,0,0.1),0px_8px_10px_-6px_rgba(0,0,0,0.1)] z-50">
          <div className="p-4 border-b border-[#E0E0E0]">
            <h3 className="text-[16px] font-semibold text-[#1A1A1A] leading-[24px]">Admin Organizer</h3>
            <p className="text-[12px] text-[#757575] leading-[16px] mt-1">ORG-2024-001</p>
            <p className="text-[12px] text-[#757575] leading-[16px] mt-1">admin@techuni.edu</p>
          </div>
          <div className="py-2">
            <button 
              onClick={() => {
                setIsOpen(false);
                navigate('/organizer/settings');
              }}
              className="w-full px-4 py-2 flex items-center gap-3 hover:bg-gray-50 transition-colors text-[#1A1A1A]"
            >
              <Settings size={16} className="text-[#757575]" />
              <span className="text-[14px] font-medium leading-[20px]">Profile Settings</span>
            </button>
            <button 
              onClick={handleLogout}
              className="w-full px-4 py-2 flex items-center gap-3 hover:bg-gray-50 transition-colors text-[#1A1A1A]"
            >
              <LogOut size={16} className="text-[#757575]" />
              <span className="text-[14px] font-medium leading-[20px]">Logout</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
