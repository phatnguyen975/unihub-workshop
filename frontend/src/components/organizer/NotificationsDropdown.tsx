import React, { useState, useRef, useEffect } from 'react';
import { Bell, CheckCircle2, FileText, UploadCloud, AlertTriangle } from 'lucide-react';

export const NotificationsDropdown: React.FC = () => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="relative" ref={dropdownRef}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="relative w-10 h-10 flex items-center justify-center rounded-full hover:bg-gray-100 transition-colors"
      >
        <Bell size={20} className="text-[#1A1A1A]" />
        <div className="absolute top-1 right-1 w-4 h-4 bg-[#1B5E20] rounded-full flex items-center justify-center text-[10px] font-medium text-white border border-white">
          4
        </div>
      </button>

      {isOpen && (
        <div className="absolute top-12 right-0 w-[360px] bg-white rounded-xl shadow-lg border border-[#E0E0E0] overflow-hidden z-50">
          <div className="flex items-center justify-between p-4 border-b border-[#E0E0E0] bg-gray-50">
            <h3 className="font-semibold text-[#1A1A1A] text-[16px]">Notifications</h3>
            <button className="text-[#1B5E20] text-[14px] font-medium hover:underline">
              Mark all as read
            </button>
          </div>
          
          <div className="max-h-[400px] overflow-y-auto">
            {/* Notification Item */}
            <div className="p-4 border-b border-[#E0E0E0] hover:bg-gray-50 flex gap-4 transition-colors cursor-pointer">
              <div className="w-10 h-10 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                <FileText size={20} />
              </div>
              <div>
                <h4 className="text-[14px] font-semibold text-[#1A1A1A]">AI Summarization Complete</h4>
                <p className="text-[14px] text-[#757575] mt-1">Summary for "Machine Learning Fundamentals" is ready.</p>
                <p className="text-[12px] text-gray-400 mt-2">10 minutes ago</p>
              </div>
            </div>

            {/* Notification Item */}
            <div className="p-4 border-b border-[#E0E0E0] hover:bg-gray-50 flex gap-4 transition-colors cursor-pointer bg-green-50/30">
              <div className="w-10 h-10 rounded-full bg-green-50 text-green-600 flex items-center justify-center shrink-0">
                <CheckCircle2 size={20} />
              </div>
              <div>
                <h4 className="text-[14px] font-semibold text-[#1A1A1A]">New Workshop Registration</h4>
                <p className="text-[14px] text-[#757575] mt-1">Sarah Connor registered for "Cloud Architecture".</p>
                <p className="text-[12px] text-gray-400 mt-2">1 hour ago</p>
              </div>
            </div>

            {/* Notification Item */}
            <div className="p-4 border-b border-[#E0E0E0] hover:bg-gray-50 flex gap-4 transition-colors cursor-pointer">
              <div className="w-10 h-10 rounded-full bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
                <UploadCloud size={20} />
              </div>
              <div>
                <h4 className="text-[14px] font-semibold text-[#1A1A1A]">Nightly CSV Ingestion Successful</h4>
                <p className="text-[14px] text-[#757575] mt-1">All workshop data has been synchronized successful</p>
                <p className="text-[12px] text-gray-400 mt-2">2 hours ago</p>
              </div>
            </div>

            {/* Notification Item */}
            <div className="p-4 hover:bg-gray-50 flex gap-4 transition-colors cursor-pointer">
              <div className="w-10 h-10 rounded-full bg-orange-50 text-orange-600 flex items-center justify-center shrink-0">
                <AlertTriangle size={20} />
              </div>
              <div>
                <h4 className="text-[14px] font-semibold text-[#1A1A1A]">Workshop Capacity Alert</h4>
                <p className="text-[14px] text-[#757575] mt-1">"Design Thinking" only has 2 seats remaining.</p>
                <p className="text-[12px] text-gray-400 mt-2">1 day ago</p>
              </div>
            </div>
          </div>

          <div className="p-3 border-t border-[#E0E0E0] bg-gray-50">
            <button className="w-full py-2 text-center text-[#1B5E20] font-medium text-[14px] hover:bg-white rounded-lg transition-colors border border-transparent hover:border-[#E0E0E0]">
              View All Notifications
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
